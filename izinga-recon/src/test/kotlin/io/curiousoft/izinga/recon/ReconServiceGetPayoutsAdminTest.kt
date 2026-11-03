package io.curiousoft.izinga.recon

import io.curiousoft.izinga.commons.referral.FoodCustomerReferralCommissionRepo
import io.curiousoft.izinga.commons.referral.FurnitureCustomerReferralCommissionRepo
import io.curiousoft.izinga.commons.referral.StorePartnerStage1CommissionRepo
import io.curiousoft.izinga.commons.referral.StorePartnerStage2CommissionRepo
import io.curiousoft.izinga.commons.repo.StoreRepository
import io.curiousoft.izinga.commons.repo.UserProfileRepo
import io.curiousoft.izinga.commons.security.AgentIdentityAuditService
import io.curiousoft.izinga.commons.security.CallerIdentityContext
import io.curiousoft.izinga.recon.ambassador.AmbassadorProperties
import io.curiousoft.izinga.recon.payout.PayoutType
import io.curiousoft.izinga.recon.payout.repo.AmbassadorPayoutRepository
import io.curiousoft.izinga.recon.payout.repo.MessengerPayoutRepository
import io.curiousoft.izinga.recon.payout.repo.PayoutRepository
import io.curiousoft.izinga.recon.payout.repo.ReferralPartnerPayoutRepository
import io.curiousoft.izinga.recon.payout.repo.ShopPayoutRepository
import io.mockk.every
import io.mockk.mockk
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.context.ApplicationEventPublisher
import java.util.Date

/**
 * AMD-01 (REQ-A10): JUnit 5 tests verifying that ADMIN audience passes the identity gate in
 * get_payouts_for_user without CallerIdentityViolationException.
 *
 * Note: ReconServiceTest (ReconServiceImplTest.kt) uses JUnit 4 annotations and is silently
 * skipped in izinga-recon (no junit-vintage-engine on the test classpath).  This JUnit 5 class
 * provides the AMD-01 proof-of-pass that surefire will pick up.
 */
class ReconServiceGetPayoutsAdminTest {

    private val storeRepo = mockk<StoreRepository>()
    private val messengerRepo = mockk<UserProfileRepo>()
    private val shopPayoutRepository = mockk<ShopPayoutRepository>()
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

    @BeforeEach
    fun setUp() {
        sut = ReconServiceImpl(
            storeRepo = storeRepo,
            userProfileRepo = messengerRepo,
            shopPayoutRepo = shopPayoutRepository,
            messengerPayoutRepository = messengerPayoutRepository,
            ambassadorPayoutRepository = ambassadorPayoutRepository,
            referralPartnerPayoutRepository = referralPartnerPayoutRepository,
            payoutBundleRepository = payoutBundleRepository,
            foodCustomerCommissionRepo = foodCustomerCommissionRepo,
            furnitureCustomerCommissionRepo = furnitureCustomerCommissionRepo,
            storeStage1CommissionRepo = storeStage1CommissionRepo,
            storeStage2CommissionRepo = storeStage2CommissionRepo,
            applicationEventPublisher = applicationEventPublisher,
            ambassadorProperties = AmbassadorProperties(),
            agentIdentityAuditService = mockk(relaxed = true)
        )
    }

    @AfterEach
    fun tearDown() {
        CallerIdentityContext.clear()
    }

    /**
     * AMD-01 (REQ-A10): ADMIN audience must pass the identity gate in getAllPayouts and be able
     * to query payouts for another user's id without CallerIdentityViolationException.
     * CallerIdentityValidator.validateCallerOwnsId ADMIN early-return fires before the
     * callerIdResolver lambda is ever invoked.
     */
    @Test
    fun `get payouts for user adminAudience otherUserId passes`() {
        val otherUserId = "other-user-id"
        val from = Date(System.currentTimeMillis() - 10000)
        val to = Date()

        CallerIdentityContext.setMcpRequest(true)
        CallerIdentityContext.setAudience("ADMIN")

        every { messengerPayoutRepository.findByModifiedDateBetweenAndToId(from, to, otherUserId) } returns emptyList()

        val payouts = sut.getAllPayouts(PayoutType.MESSENGER, from, to, otherUserId)

        // ADMIN passes the identity gate and gets results (empty list is fine)
        assertNotNull(payouts)
    }
}
