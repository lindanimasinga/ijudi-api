package io.curiousoft.izinga.recon

import io.curiousoft.izinga.commons.referral.FoodCustomerReferralCommission
import io.curiousoft.izinga.commons.referral.FoodCustomerReferralCommissionRepo
import io.curiousoft.izinga.commons.referral.ReferralCommissionStatus
import io.curiousoft.izinga.commons.referral.ReferralCommissionType
import io.curiousoft.izinga.commons.referral.StorePartnerStage1Commission
import io.curiousoft.izinga.commons.referral.StorePartnerStage1CommissionRepo
import io.curiousoft.izinga.commons.referral.StorePartnerStage2Commission
import io.curiousoft.izinga.commons.referral.StorePartnerStage2CommissionRepo
import io.curiousoft.izinga.commons.repo.StoreRepository
import io.curiousoft.izinga.commons.repo.UserProfileRepo
import io.curiousoft.izinga.recon.ambassador.AmbassadorProperties
import io.curiousoft.izinga.recon.payout.PayoutStage
import io.curiousoft.izinga.recon.payout.PayoutBundleResults
import io.curiousoft.izinga.recon.payout.PayoutItemResults
import io.curiousoft.izinga.recon.payout.PayoutType
import io.curiousoft.izinga.recon.payout.ReferralPartnerPayout
import io.curiousoft.izinga.recon.payout.repo.AmbassadorPayoutRepository
import io.curiousoft.izinga.recon.payout.repo.MessengerPayoutRepository
import io.curiousoft.izinga.recon.payout.repo.ReferralPartnerPayoutRepository
import io.curiousoft.izinga.recon.payout.repo.ShopPayoutRepository
import io.mockk.*
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.context.ApplicationEventPublisher
import java.math.BigDecimal
import java.util.Date

class ReferralPartnerPayoutStatusSyncTest {

    private val storeRepo = mockk<StoreRepository>()
    private val userProfileRepo = mockk<UserProfileRepo>()
    private val shopPayoutRepository = mockk<ShopPayoutRepository>()
    private val messengerPayoutRepository = mockk<MessengerPayoutRepository>()
    private val ambassadorPayoutRepository = mockk<AmbassadorPayoutRepository>()
    private val referralPartnerPayoutRepository = mockk<ReferralPartnerPayoutRepository>()
    private val foodCustomerCommissionRepo = mockk<FoodCustomerReferralCommissionRepo>()
    private val storeStage1CommissionRepo = mockk<StorePartnerStage1CommissionRepo>()
    private val storeStage2CommissionRepo = mockk<StorePartnerStage2CommissionRepo>()
    private val applicationEventPublisher = mockk<ApplicationEventPublisher>(relaxed = true)

    private lateinit var sut: ReconServiceImpl

    @BeforeEach
    fun setUp() {
        sut = ReconServiceImpl(
            storeRepo = storeRepo,
            userProfileRepo = userProfileRepo,
            shopPayoutRepo = shopPayoutRepository,
            messengerPayoutRepository = messengerPayoutRepository,
            ambassadorPayoutRepository = ambassadorPayoutRepository,
            referralPartnerPayoutRepository = referralPartnerPayoutRepository,
            foodCustomerCommissionRepo = foodCustomerCommissionRepo,
            storeStage1CommissionRepo = storeStage1CommissionRepo,
            storeStage2CommissionRepo = storeStage2CommissionRepo,
            applicationEventPublisher = applicationEventPublisher,
            ambassadorProperties = AmbassadorProperties(commissionAmount = BigDecimal("70.00"))
        )
    }

    private fun makeReferralPayout(
        toId: String,
        commissionType: ReferralCommissionType,
        triggerRef: String,
        paid: Boolean = true
    ): ReferralPartnerPayout {
        return ReferralPartnerPayout(
            toId = toId, toName = "Partner", toBankName = "FNB",
            toType = io.curiousoft.izinga.commons.model.BankAccType.CHEQUE,
            toAccountNumber = "123", toBranchCode = "250655",
            fromReference = "ref", toReference = "iZinga pay",
            emailNotify = "", emailAddress = null, emailSubject = null,
            commissionAmount = BigDecimal("15.00"),
            commissionType = commissionType,
            triggerReferenceId = triggerRef
        ).also {
            it.id = "PAY-${commissionType.name}"
            it.paid = paid
        }
    }

    @Test
    fun `updatePayoutStatus marks REFERRAL_PARTNER payout COMPLETED and syncs food customer commission to PAID`() {
        val partnerId = "rp-001"
        val customerId = "cust-001"
        val payout = makeReferralPayout(partnerId, ReferralCommissionType.FOOD_CUSTOMER_REFERRAL, customerId, paid = true)

        val bundleResult = PayoutBundleResults(
            bundleId = "BND01",
            payoutItemResults = listOf(PayoutItemResults(toId = partnerId, paid = true, type = PayoutType.REFERRAL_PARTNER))
        )

        // stub other payout type lookups to return empty
        every { shopPayoutRepository.findByToIdAndPayoutStage(any(), any()) } returns null
        every { messengerPayoutRepository.findByToIdAndPayoutStage(any(), any()) } returns null
        every { ambassadorPayoutRepository.findAllByToIdAndPayoutStage(any(), any()) } returns emptyList()
        every { referralPartnerPayoutRepository.findAllByToIdAndPayoutStage(partnerId, PayoutStage.PROCESSING) } returns listOf(payout)
        every { referralPartnerPayoutRepository.saveAll(any<Iterable<ReferralPartnerPayout>>()) } returns emptyList()
        val savedSlot = slot<ReferralPartnerPayout>()
        every { referralPartnerPayoutRepository.save(capture(savedSlot)) } answers { savedSlot.captured }

        val commission = FoodCustomerReferralCommission(
            id = "fc1", customerId = customerId, referralPartnerId = partnerId,
            triggeringOrderId = "ord-1", amount = BigDecimal("15.00"),
            status = ReferralCommissionStatus.PENDING, createdAt = Date()
        )
        every { foodCustomerCommissionRepo.findByCustomerId(customerId) } returns commission
        val savedCommissionSlot = slot<FoodCustomerReferralCommission>()
        every { foodCustomerCommissionRepo.save(capture(savedCommissionSlot)) } answers { savedCommissionSlot.captured }

        sut.updatePayoutStatus(bundleResult)

        assertEquals(PayoutStage.COMPLETED, savedSlot.captured.payoutStage)
        assertEquals(ReferralCommissionStatus.PAID, savedCommissionSlot.captured.status)
    }

    @Test
    fun `updatePayoutStatus syncs stage1 commission to PAID when payout completes`() {
        val partnerId = "rp-002"
        val storeId = "store-001"
        val payout = makeReferralPayout(partnerId, ReferralCommissionType.STORE_PARTNER_STAGE_1, storeId, paid = true)

        val bundleResult = PayoutBundleResults(
            bundleId = "BND02",
            payoutItemResults = listOf(PayoutItemResults(toId = partnerId, paid = true, type = PayoutType.REFERRAL_PARTNER))
        )

        every { shopPayoutRepository.findByToIdAndPayoutStage(any(), any()) } returns null
        every { messengerPayoutRepository.findByToIdAndPayoutStage(any(), any()) } returns null
        every { ambassadorPayoutRepository.findAllByToIdAndPayoutStage(any(), any()) } returns emptyList()
        every { referralPartnerPayoutRepository.findAllByToIdAndPayoutStage(partnerId, PayoutStage.PROCESSING) } returns listOf(payout)
        every { referralPartnerPayoutRepository.saveAll(any<Iterable<ReferralPartnerPayout>>()) } returns emptyList()
        val savedSlot = slot<ReferralPartnerPayout>()
        every { referralPartnerPayoutRepository.save(capture(savedSlot)) } answers { savedSlot.captured }

        val commission = StorePartnerStage1Commission(
            id = "s1", storeId = storeId, referralPartnerId = partnerId,
            amount = BigDecimal("100.00"), status = ReferralCommissionStatus.PENDING, createdAt = Date()
        )
        every { storeStage1CommissionRepo.findByStoreId(storeId) } returns commission
        val savedCommissionSlot = slot<StorePartnerStage1Commission>()
        every { storeStage1CommissionRepo.save(capture(savedCommissionSlot)) } answers { savedCommissionSlot.captured }

        sut.updatePayoutStatus(bundleResult)

        assertEquals(PayoutStage.COMPLETED, savedSlot.captured.payoutStage)
        assertEquals(ReferralCommissionStatus.PAID, savedCommissionSlot.captured.status)
    }

    @Test
    fun `updatePayoutStatus syncs stage2 commission to PAID when payout completes`() {
        val partnerId = "rp-003"
        val storeId = "store-002"
        val payout = makeReferralPayout(partnerId, ReferralCommissionType.STORE_PARTNER_STAGE_2, storeId, paid = true)

        val bundleResult = PayoutBundleResults(
            bundleId = "BND03",
            payoutItemResults = listOf(PayoutItemResults(toId = partnerId, paid = true, type = PayoutType.REFERRAL_PARTNER))
        )

        every { shopPayoutRepository.findByToIdAndPayoutStage(any(), any()) } returns null
        every { messengerPayoutRepository.findByToIdAndPayoutStage(any(), any()) } returns null
        every { ambassadorPayoutRepository.findAllByToIdAndPayoutStage(any(), any()) } returns emptyList()
        every { referralPartnerPayoutRepository.findAllByToIdAndPayoutStage(partnerId, PayoutStage.PROCESSING) } returns listOf(payout)
        every { referralPartnerPayoutRepository.saveAll(any<Iterable<ReferralPartnerPayout>>()) } returns emptyList()
        val savedSlot = slot<ReferralPartnerPayout>()
        every { referralPartnerPayoutRepository.save(capture(savedSlot)) } answers { savedSlot.captured }

        val commission = StorePartnerStage2Commission(
            id = "s2", storeId = storeId, referralPartnerId = partnerId,
            triggeringOrderId = "ord-2", amount = BigDecimal("150.00"),
            status = ReferralCommissionStatus.PENDING, createdAt = Date()
        )
        every { storeStage2CommissionRepo.findByStoreId(storeId) } returns commission
        val savedCommissionSlot = slot<StorePartnerStage2Commission>()
        every { storeStage2CommissionRepo.save(capture(savedCommissionSlot)) } answers { savedCommissionSlot.captured }

        sut.updatePayoutStatus(bundleResult)

        assertEquals(PayoutStage.COMPLETED, savedSlot.captured.payoutStage)
        assertEquals(ReferralCommissionStatus.PAID, savedCommissionSlot.captured.status)
    }

    @Test
    fun `updatePayoutStatus does not sync commission when payout is not paid`() {
        val partnerId = "rp-004"
        val customerId = "cust-004"
        val payout = makeReferralPayout(partnerId, ReferralCommissionType.FOOD_CUSTOMER_REFERRAL, customerId, paid = false)

        val bundleResult = PayoutBundleResults(
            bundleId = "BND04",
            payoutItemResults = listOf(PayoutItemResults(toId = partnerId, paid = false, type = PayoutType.REFERRAL_PARTNER))
        )

        every { shopPayoutRepository.findByToIdAndPayoutStage(any(), any()) } returns null
        every { messengerPayoutRepository.findByToIdAndPayoutStage(any(), any()) } returns null
        every { ambassadorPayoutRepository.findAllByToIdAndPayoutStage(any(), any()) } returns emptyList()
        every { referralPartnerPayoutRepository.findAllByToIdAndPayoutStage(partnerId, PayoutStage.PROCESSING) } returns listOf(payout)
        every { referralPartnerPayoutRepository.saveAll(any<Iterable<ReferralPartnerPayout>>()) } returns emptyList()

        sut.updatePayoutStatus(bundleResult)

        // commission sync must NOT happen when payout.paid = false
        verify(exactly = 0) { foodCustomerCommissionRepo.findByCustomerId(any()) }
        verify(exactly = 0) { foodCustomerCommissionRepo.save(any()) }
        verify(exactly = 0) { referralPartnerPayoutRepository.save(any()) }
    }
}
