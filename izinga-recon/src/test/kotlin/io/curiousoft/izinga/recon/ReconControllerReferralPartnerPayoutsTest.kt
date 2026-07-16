package io.curiousoft.izinga.recon

import io.curiousoft.izinga.commons.model.BankAccType
import io.curiousoft.izinga.commons.referral.ReferralCommissionType
import io.curiousoft.izinga.recon.payout.PayoutStage
import io.curiousoft.izinga.recon.payout.ReferralPartnerPayout
import io.curiousoft.izinga.recon.payout.repo.ReferralPartnerPayoutRepository
import io.mockk.*
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.data.domain.Page
import org.springframework.data.domain.PageImpl
import org.springframework.data.domain.PageRequest
import org.springframework.data.domain.Sort
import java.math.BigDecimal
import java.security.Principal

/**
 * RP-010: Tests for the GET /recon/referral-partner/me/payouts endpoint.
 *
 * Auth scoping — a partner must never see another partner's payouts — is the
 * primary invariant tested here. The partnerId comes from the JWT principal only.
 */
class ReconControllerReferralPartnerPayoutsTest {

    private val reconService = mockk<ReconService>()
    private val referralPartnerPayoutRepository = mockk<ReferralPartnerPayoutRepository>()

    private lateinit var controller: ReconController

    private val partnerId = "rp-001"
    private val principal = Principal { partnerId }

    @BeforeEach
    fun setUp() {
        controller = ReconController(reconService, referralPartnerPayoutRepository)
    }

    @Test
    fun `getReferralPartnerPayouts returns paginated payouts for authenticated partner`() {
        val payout = payout(partnerId)
        val pageable = PageRequest.of(0, 20, Sort.by(Sort.Direction.DESC, "modifiedDate"))
        val page: Page<ReferralPartnerPayout> = PageImpl(listOf(payout), pageable, 1)

        every { referralPartnerPayoutRepository.findAllByToId(partnerId, any()) } returns page

        val result = controller.getReferralPartnerPayouts(principal, 0, 20)

        assertEquals(1, result.content.size)
        assertEquals(partnerId, result.content[0].toId)
        verify { referralPartnerPayoutRepository.findAllByToId(partnerId, any()) }
    }

    @Test
    fun `getReferralPartnerPayouts uses principal name as partnerId - never a request param`() {
        val pageable = PageRequest.of(0, 20, Sort.by(Sort.Direction.DESC, "modifiedDate"))
        val emptyPage: Page<ReferralPartnerPayout> = PageImpl(emptyList(), pageable, 0)

        every { referralPartnerPayoutRepository.findAllByToId(partnerId, any()) } returns emptyPage

        controller.getReferralPartnerPayouts(principal, 0, 20)

        // Must only query for the principal's own data
        verify(exactly = 1) { referralPartnerPayoutRepository.findAllByToId(partnerId, any()) }
        verify(exactly = 0) { referralPartnerPayoutRepository.findAllByToId("rp-other", any()) }
    }

    @Test
    fun `getReferralPartnerPayouts returns empty page when partner has no payouts`() {
        val pageable = PageRequest.of(0, 20, Sort.by(Sort.Direction.DESC, "modifiedDate"))
        val emptyPage: Page<ReferralPartnerPayout> = PageImpl(emptyList(), pageable, 0)

        every { referralPartnerPayoutRepository.findAllByToId(partnerId, any()) } returns emptyPage

        val result = controller.getReferralPartnerPayouts(principal, 0, 20)

        assertTrue(result.content.isEmpty())
        assertEquals(0L, result.totalElements)
    }

    @Test
    fun `getReferralPartnerPayouts sorts by modifiedDate descending`() {
        val pageable = PageRequest.of(0, 20, Sort.by(Sort.Direction.DESC, "modifiedDate"))
        val page: Page<ReferralPartnerPayout> = PageImpl(emptyList(), pageable, 0)

        val capturedPageable = slot<org.springframework.data.domain.Pageable>()
        every { referralPartnerPayoutRepository.findAllByToId(partnerId, capture(capturedPageable)) } returns page

        controller.getReferralPartnerPayouts(principal, 0, 20)

        val sort = capturedPageable.captured.sort
        val order = sort.getOrderFor("modifiedDate")
        assertNotNull(order)
        assertEquals(Sort.Direction.DESC, order?.direction)
    }

    @Test
    fun `getReferralPartnerPayouts respects custom page and size params`() {
        val capturedPageable = slot<org.springframework.data.domain.Pageable>()
        val page: Page<ReferralPartnerPayout> = PageImpl(emptyList())

        every { referralPartnerPayoutRepository.findAllByToId(partnerId, capture(capturedPageable)) } returns page

        controller.getReferralPartnerPayouts(principal, 2, 5)

        assertEquals(2, capturedPageable.captured.pageNumber)
        assertEquals(5, capturedPageable.captured.pageSize)
    }

    // ─── Helpers ───────────────────────────────────────────────────────────────

    private fun payout(toId: String) = ReferralPartnerPayout(
        toId = toId,
        toName = "Test Partner",
        toBankName = "FNB",
        toType = BankAccType.CHEQUE,
        toAccountNumber = "62000000001",
        toBranchCode = "250655",
        fromReference = "iZingaRef",
        toReference = "RPRef",
        emailNotify = null,
        emailAddress = null,
        emailSubject = null,
        orders = mutableSetOf(),
        payoutStage = PayoutStage.PENDING,
        commissionAmount = BigDecimal("15.00"),
        commissionType = ReferralCommissionType.FOOD_CUSTOMER_REFERRAL,
        triggerReferenceId = "cust-1"
    )
}
