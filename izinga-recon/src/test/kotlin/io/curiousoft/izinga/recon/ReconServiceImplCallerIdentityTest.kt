package io.curiousoft.izinga.recon

import io.curiousoft.izinga.commons.referral.FoodCustomerReferralCommissionRepo
import io.curiousoft.izinga.commons.referral.FurnitureCustomerReferralCommissionRepo
import io.curiousoft.izinga.commons.referral.StorePartnerStage1CommissionRepo
import io.curiousoft.izinga.commons.referral.StorePartnerStage2CommissionRepo
import io.curiousoft.izinga.commons.repo.StoreRepository
import io.curiousoft.izinga.commons.repo.UserProfileRepo
import io.curiousoft.izinga.recon.ambassador.AmbassadorProperties
import io.curiousoft.izinga.recon.payout.PayoutType
import io.curiousoft.izinga.recon.payout.repo.AmbassadorPayoutRepository
import io.curiousoft.izinga.recon.payout.repo.MessengerPayoutRepository
import io.curiousoft.izinga.recon.payout.repo.PayoutRepository
import io.curiousoft.izinga.recon.payout.repo.ReferralPartnerPayoutRepository
import io.curiousoft.izinga.recon.payout.repo.ShopPayoutRepository
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.springframework.context.ApplicationEventPublisher
import java.util.Date

/**
 * ADR-026 Phase 5 / AC-04 — ungated service-layer tests for {@link ReconServiceImpl#getAllPayouts}.
 *
 * <p>The identity gate (ADR-024) was migrated to {@code PayoutMcpController} in Phase 5.
 * {@code ReconServiceImpl.getAllPayouts} is now a plain repository-delegation method with
 * no caller-identity checks. These tests verify AC-04: the service delegates to the correct
 * repository for each {@link PayoutType}, regardless of any audience or identity context.</p>
 *
 * <p>Gate enforcement tests (DRIVER-audience throws, audit-record on violation, etc.) live
 * in {@code PayoutMcpControllerTest} in izinga-ordermanager.</p>
 */
class ReconServiceImplCallerIdentityTest {

    private val storeRepo = mockk<StoreRepository>()
    private val userProfileRepo = mockk<UserProfileRepo>()
    private val shopPayoutRepo = mockk<ShopPayoutRepository>()
    private val messengerPayoutRepository = mockk<MessengerPayoutRepository>()
    private val ambassadorPayoutRepository = mockk<AmbassadorPayoutRepository>()
    private val referralPartnerPayoutRepository = mockk<ReferralPartnerPayoutRepository>()
    private val payoutBundleRepository = mockk<PayoutRepository>()
    private val foodCustomerCommissionRepo = mockk<FoodCustomerReferralCommissionRepo>()
    private val furnitureCustomerCommissionRepo = mockk<FurnitureCustomerReferralCommissionRepo>()
    private val storeStage1CommissionRepo = mockk<StorePartnerStage1CommissionRepo>()
    private val storeStage2CommissionRepo = mockk<StorePartnerStage2CommissionRepo>()
    private val applicationEventPublisher = mockk<ApplicationEventPublisher>()

    private lateinit var sut: ReconServiceImpl

    private val userId = "user-id"

    @Before
    fun setUp() {
        // ADR-026 Phase 5: agentIdentityAuditService removed from ReconServiceImpl constructor
        sut = ReconServiceImpl(
            storeRepo = storeRepo,
            userProfileRepo = userProfileRepo,
            shopPayoutRepo = shopPayoutRepo,
            messengerPayoutRepository = messengerPayoutRepository,
            ambassadorPayoutRepository = ambassadorPayoutRepository,
            referralPartnerPayoutRepository = referralPartnerPayoutRepository,
            payoutBundleRepository = payoutBundleRepository,
            foodCustomerCommissionRepo = foodCustomerCommissionRepo,
            furnitureCustomerCommissionRepo = furnitureCustomerCommissionRepo,
            storeStage1CommissionRepo = storeStage1CommissionRepo,
            storeStage2CommissionRepo = storeStage2CommissionRepo,
            applicationEventPublisher = applicationEventPublisher,
            ambassadorProperties = AmbassadorProperties()
        )
    }

    // ---- AC-04: service delegates to messengerPayoutRepository for MESSENGER payouts ---

    @Test
    fun `getAllPayouts MESSENGER delegates to messengerPayoutRepository`() {
        every {
            messengerPayoutRepository.findByModifiedDateBetweenAndToId(any(), any(), userId)
        } returns emptyList()

        sut.getAllPayouts(PayoutType.MESSENGER, Date(), Date(), userId)

        verify(exactly = 1) {
            messengerPayoutRepository.findByModifiedDateBetweenAndToId(any(), any(), userId)
        }
    }

    // ---- AC-04: service delegates to shopPayoutRepo for SHOP payouts --------------------

    @Test
    fun `getAllPayouts SHOP delegates to shopPayoutRepo`() {
        every {
            shopPayoutRepo.findByModifiedDateBetweenAndToId(any(), any(), userId)
        } returns emptyList()

        sut.getAllPayouts(PayoutType.SHOP, Date(), Date(), userId)

        verify(exactly = 1) {
            shopPayoutRepo.findByModifiedDateBetweenAndToId(any(), any(), userId)
        }
    }

    // ---- AC-04: service delegates to ambassadorPayoutRepository for AMBASSADOR payouts --

    @Test
    fun `getAllPayouts AMBASSADOR delegates to ambassadorPayoutRepository`() {
        every {
            ambassadorPayoutRepository.findByModifiedDateBetweenAndToId(any(), any(), userId)
        } returns emptyList()

        sut.getAllPayouts(PayoutType.AMBASSADOR, Date(), Date(), userId)

        verify(exactly = 1) {
            ambassadorPayoutRepository.findByModifiedDateBetweenAndToId(any(), any(), userId)
        }
    }

    // ---- AC-04: service delegates to referralPartnerPayoutRepository for REFERRAL_PARTNER

    @Test
    fun `getAllPayouts REFERRAL_PARTNER delegates to referralPartnerPayoutRepository`() {
        every {
            referralPartnerPayoutRepository.findByModifiedDateBetweenAndToId(any(), any(), userId)
        } returns emptyList()

        sut.getAllPayouts(PayoutType.REFERRAL_PARTNER, Date(), Date(), userId)

        verify(exactly = 1) {
            referralPartnerPayoutRepository.findByModifiedDateBetweenAndToId(any(), any(), userId)
        }
    }

    // ---- AC-04: no caller-identity check in service — passes without any context --------

    @Test
    fun `getAllPayouts succeeds without any caller identity context set`() {
        // No CallerIdentityContext setup — the service must NOT check caller identity.
        // Gate belongs to PayoutMcpController (see PayoutMcpControllerTest).
        every {
            messengerPayoutRepository.findByModifiedDateBetweenAndToId(any(), any(), userId)
        } returns emptyList()

        // Must not throw, regardless of audience or identity context
        sut.getAllPayouts(PayoutType.MESSENGER, Date(), Date(), userId)
    }
}
