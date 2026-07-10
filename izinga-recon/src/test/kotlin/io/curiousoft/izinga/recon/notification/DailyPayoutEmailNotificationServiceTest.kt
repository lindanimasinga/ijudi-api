package io.curiousoft.izinga.recon.notification

import io.curiousoft.izinga.commons.model.ProfileRoles
import io.curiousoft.izinga.commons.model.UserProfile
import io.curiousoft.izinga.commons.repo.StoreRepository
import io.curiousoft.izinga.commons.repo.UserProfileRepo
import io.curiousoft.izinga.recon.ReconService
import io.curiousoft.izinga.recon.payout.AmbassadorPayout
import io.curiousoft.izinga.recon.payout.PayoutStage
import io.curiousoft.izinga.recon.payout.repo.AmbassadorPayoutRepository
import io.mockk.*
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.math.BigDecimal

/**
 * Decision (2026-07-07): Ambassador always gets paid when their recruited driver is approved.
 * No VOID check on ambassador approval status. processAmbassadorPayouts() must process ALL
 * PENDING ambassador payouts regardless of ambassador profile state.
 */
class DailyPayoutEmailNotificationServiceTest {

    private val userProfileRepo = mockk<UserProfileRepo>()
    private val storeRepository = mockk<StoreRepository>()
    private val reconService = mockk<ReconService>(relaxed = true)
    private val ambassadorPayoutRepository = mockk<AmbassadorPayoutRepository>()

    private lateinit var service: DailyPayoutEmailNotificationService

    @BeforeEach
    fun setUp() {
        service = DailyPayoutEmailNotificationService(
            userProfileRepo = userProfileRepo,
            storeRepository = storeRepository,
            reconService = reconService,
            ambassadorPayoutRepository = ambassadorPayoutRepository,
            apiKey = "test-api-key",
            dailyPayoutTemplate = "test-template-id",
            ambassadorPayoutTemplate = "test-ambassador-payout-template-id"
        )
    }

    // -----------------------------------------------------------------------
    // Happy path — ambassador with no email address: save is still called,
    // emailSent stays false (sendPayoutEmail is a no-op when emailAddress == null)
    // -----------------------------------------------------------------------

    @Test
    fun `processAmbassadorPayouts processes payout regardless of ambassador approval status`() {
        val approvedPayout = buildPendingPayout(ambassadorId = "amb-approved")
        approvedPayout.emailAddress = null // suppress HTTP call

        val unapprovedPayout = buildPendingPayout(ambassadorId = "amb-unapproved")
        unapprovedPayout.emailAddress = null // suppress HTTP call

        every { ambassadorPayoutRepository.findByPayoutStage(PayoutStage.PENDING) } returns
                listOf(approvedPayout, unapprovedPayout)
        every { ambassadorPayoutRepository.save(any()) } answers { firstArg() }

        service.notifyDailyPayouts()

        // Neither payout must be VOIDED — both must remain PENDING (no status change in this flow)
        assertEquals(PayoutStage.PENDING, approvedPayout.payoutStage)
        assertEquals(PayoutStage.PENDING, unapprovedPayout.payoutStage)

        // Both must be persisted (emailSent branch saves after sendPayoutEmail)
        verify(exactly = 1) { ambassadorPayoutRepository.save(approvedPayout) }
        verify(exactly = 1) { ambassadorPayoutRepository.save(unapprovedPayout) }

        // userProfileRepo must never be consulted — ambassador status is irrelevant
        verify(exactly = 0) { userProfileRepo.findById(any()) }
    }

    // -----------------------------------------------------------------------
    // Edge: empty payout list — no interactions with repo.save or userProfileRepo
    // -----------------------------------------------------------------------

    @Test
    fun `processAmbassadorPayouts does nothing when no PENDING ambassador payouts exist`() {
        every { ambassadorPayoutRepository.findByPayoutStage(PayoutStage.PENDING) } returns emptyList()

        service.notifyDailyPayouts()

        verify(exactly = 0) { ambassadorPayoutRepository.save(any()) }
        verify(exactly = 0) { userProfileRepo.findById(any()) }
    }

    // -----------------------------------------------------------------------
    // Helpers
    // -----------------------------------------------------------------------

    private fun buildPendingPayout(ambassadorId: String): AmbassadorPayout {
        return AmbassadorPayout(
            toId = ambassadorId,
            toName = "Ambassador",
            toBankName = "FNB",
            toType = io.curiousoft.izinga.commons.model.BankAccType.EWALLET,
            toAccountNumber = "0821110000",
            toBranchCode = "250655",
            fromReference = "iZinga Ambassador Commission",
            toReference = "iZinga pay",
            emailNotify = "",
            emailAddress = null,
            emailSubject = "Ambassador payout",
            commissionAmount = BigDecimal("70.00"),
            triggerDriverId = "driver-test-001"
        )
    }
}
