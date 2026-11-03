package io.curiousoft.izinga.recon

import io.curiousoft.izinga.commons.model.ProfileRoles
import io.curiousoft.izinga.commons.model.UserProfile
import io.curiousoft.izinga.commons.referral.FoodCustomerReferralCommissionRepo
import io.curiousoft.izinga.commons.referral.FurnitureCustomerReferralCommissionRepo
import io.curiousoft.izinga.commons.referral.StorePartnerStage1CommissionRepo
import io.curiousoft.izinga.commons.referral.StorePartnerStage2CommissionRepo
import io.curiousoft.izinga.commons.repo.StoreRepository
import io.curiousoft.izinga.commons.repo.UserProfileRepo
import io.curiousoft.izinga.commons.security.AgentIdentityAuditLog
import io.curiousoft.izinga.commons.security.AgentIdentityAuditService
import io.curiousoft.izinga.commons.security.CallerIdentityContext
import io.curiousoft.izinga.commons.security.CallerIdentityViolationException
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
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test
import org.springframework.context.ApplicationEventPublisher
import java.util.Date

/**
 * ADR-024: Caller identity enforcement tests for get_payouts_for_user in ReconServiceImpl.
 *
 * Uses CallerIdentityContext from izinga-commons directly (izinga-recon does not depend on
 * izinga-ordermanager, so StoreScopeContext is not in scope here).
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
    private val agentIdentityAuditService = mockk<AgentIdentityAuditService>(relaxed = true)

    private lateinit var sut: ReconServiceImpl

    private val callerPhone = "+27812345678"
    private val callerLast9 = "812345678"
    private val callerUserId = "caller-user-id"
    private val otherUserId = "other-user-id"

    @Before
    fun setUp() {
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
            ambassadorProperties = AmbassadorProperties(),
            agentIdentityAuditService = agentIdentityAuditService
        )
    }

    @After
    fun tearDown() {
        CallerIdentityContext.clear()
    }

    private fun callerProfile(): UserProfile {
        val p = UserProfile(
            "Caller", UserProfile.SignUpReason.DELIVERY_DRIVER,
            "1 Main St", "https://img.test/1.png", callerPhone, ProfileRoles.MESSENGER
        )
        p.id = callerUserId
        return p
    }

    private fun stubCallerPhoneResolution() {
        every { userProfileRepo.findByMobileNumber("0$callerLast9") } returns null
        every { userProfileRepo.findByMobileNumber("+27$callerLast9") } returns callerProfile()
    }

    @Test
    fun `getAllPayouts - DRIVER audience own id passes`() {
        CallerIdentityContext.setAudience("DRIVER")
        CallerIdentityContext.setCallerPhone(callerPhone)
        stubCallerPhoneResolution()
        every {
            messengerPayoutRepository.findByModifiedDateBetweenAndToId(any(), any(), callerUserId)
        } returns emptyList()

        sut.getAllPayouts(PayoutType.MESSENGER, Date(), Date(), callerUserId)
        // No exception → pass
    }

    @Test(expected = CallerIdentityViolationException::class)
    fun `getAllPayouts - DRIVER audience third-party id throws`() {
        CallerIdentityContext.setAudience("DRIVER")
        CallerIdentityContext.setCallerPhone(callerPhone)
        stubCallerPhoneResolution()

        sut.getAllPayouts(PayoutType.MESSENGER, Date(), Date(), otherUserId)
    }

    @Test
    fun `getAllPayouts - STORE audience any id passes - regression test`() {
        // STORE audience must NOT be restricted by ADR-024
        CallerIdentityContext.setAudience("STORE")
        CallerIdentityContext.setCallerPhone(callerPhone)
        every {
            shopPayoutRepo.findByModifiedDateBetweenAndToId(any(), any(), any())
        } returns emptyList()

        sut.getAllPayouts(PayoutType.SHOP, Date(), Date(), "any-store-id")
        // No exception → STORE is exempt
    }

    @Test(expected = CallerIdentityViolationException::class)
    fun `getAllPayouts - DRIVER audience null resolved caller throws NOTE-03`() {
        // NOTE-03: null resolved callerId is a hard reject — null == null must NOT pass.
        // Caller phone is set but all prefix lookups fail → callerId resolves to null.
        CallerIdentityContext.setAudience("DRIVER")
        CallerIdentityContext.setCallerPhone(callerPhone)
        // All prefix variants return null → resolver returns null
        every { userProfileRepo.findByMobileNumber("0$callerLast9") } returns null
        every { userProfileRepo.findByMobileNumber("+27$callerLast9") } returns null
        every { userProfileRepo.findByMobileNumber("27$callerLast9") } returns null

        sut.getAllPayouts(PayoutType.MESSENGER, Date(), Date(), callerUserId)
    }

    @Test
    fun `getAllPayouts - audit record written on violation`() {
        CallerIdentityContext.setAudience("DRIVER")
        CallerIdentityContext.setCallerPhone(callerPhone)
        stubCallerPhoneResolution()

        try {
            sut.getAllPayouts(PayoutType.MESSENGER, Date(), Date(), otherUserId)
            fail("Expected CallerIdentityViolationException")
        } catch (_: CallerIdentityViolationException) {
            // expected
        }

        // FIX-04: audience is now captured on request thread and passed explicitly
        verify {
            agentIdentityAuditService.recordViolation(
                callerPhone,
                otherUserId,
                AgentIdentityAuditLog.ViolationType.PAYOUT_NOT_OWNED,
                "get_payouts_for_user",
                "DRIVER"  // CallerIdentityContext.setAudience("DRIVER") was set in this test
            )
        }
    }
}
