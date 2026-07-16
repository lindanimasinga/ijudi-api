package io.curiousoft.izinga.recon

import io.curiousoft.izinga.commons.model.Bank
import io.curiousoft.izinga.commons.model.BankAccType
import io.curiousoft.izinga.commons.model.ProfileRoles
import io.curiousoft.izinga.commons.model.UserProfile
import io.curiousoft.izinga.commons.payout.events.ReferralPartnerPayoutEvent
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
import io.curiousoft.izinga.recon.payout.ReferralPartnerPayout
import io.curiousoft.izinga.recon.payout.repo.AmbassadorPayoutRepository
import io.curiousoft.izinga.recon.payout.repo.MessengerPayoutRepository
import io.curiousoft.izinga.recon.payout.repo.ReferralPartnerPayoutRepository
import io.curiousoft.izinga.recon.payout.repo.ShopPayoutRepository
import io.mockk.*
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.context.ApplicationEvent
import org.springframework.context.ApplicationEventPublisher
import java.math.BigDecimal
import java.util.Date

class GenerateReferralPartnerPayoutTest {

    private val storeRepo = mockk<StoreRepository>()
    private val userProfileRepo = mockk<UserProfileRepo>()
    private val shopPayoutRepository = mockk<ShopPayoutRepository>()
    private val messengerPayoutRepository = mockk<MessengerPayoutRepository>()
    private val ambassadorPayoutRepository = mockk<AmbassadorPayoutRepository>()
    private val referralPartnerPayoutRepository = mockk<ReferralPartnerPayoutRepository>()
    private val foodCustomerCommissionRepo = mockk<FoodCustomerReferralCommissionRepo>()
    private val storeStage1CommissionRepo = mockk<StorePartnerStage1CommissionRepo>()
    private val storeStage2CommissionRepo = mockk<StorePartnerStage2CommissionRepo>()
    private val applicationEventPublisher = mockk<ApplicationEventPublisher>()

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

    private fun makePartner(id: String, withBank: Boolean = true): UserProfile {
        val bank = if (withBank) Bank().apply {
            name = "FNB"; accountId = "0821234567"; type = BankAccType.EWALLET; branchCode = "250655"
        } else null
        return UserProfile(
            "Partner One",
            UserProfile.SignUpReason.DELIVERY_DRIVER,
            "10 Partner St",
            "img.jpg",
            "0821234567",
            ProfileRoles.CUSTOMER
        ).also {
            it.id = id
            it.emailAddress = "partner@mail.com"
            it.bank = bank
        }
    }

    // ─── generatePayoutForReferralPartner ────────────────────────────────────

    /**
     * QA contract test: UserProfile has NO enabled/deactivated/active field (confirmed against
     * model as of RP-009). The only partner-blocking guards in generatePayoutForReferralPartner
     * are: (a) partner not found in DB (hard-delete), and (b) partner has no bank details.
     * A partner with profileApproved=false (the closest analog to "soft-disabled") MUST still
     * receive payouts for commissions legitimately earned — deactivation stops future attribution,
     * not payment of money already owed.
     */
    @Test
    fun `partner with profileApproved=false but with bank details still receives payout`() {
        val partnerId = "rp-deactivated"
        val partner = makePartner(partnerId).also { it.profileApproved = false }
        val triggerRef = "store-abc"
        val amount = BigDecimal("100.00")
        val type = ReferralCommissionType.STORE_PARTNER_STAGE_1

        every { referralPartnerPayoutRepository.findByCommissionTypeAndTriggerReferenceId(type, triggerRef) } returns null
        every { userProfileRepo.findById(partnerId) } returns java.util.Optional.of(partner)
        val capturedPayout = slot<ReferralPartnerPayout>()
        every { referralPartnerPayoutRepository.save(capture(capturedPayout)) } answers {
            capturedPayout.captured.also { it.id = "RP-DEACT-001" }
        }
        every { applicationEventPublisher.publishEvent(any<ApplicationEvent>()) } just runs

        val result = sut.generatePayoutForReferralPartner(partnerId, amount, type, triggerRef)

        assertNotNull(result, "Payout must be created even when partner's profileApproved is false")
        assertEquals(partnerId, result!!.toId)
        assertEquals(amount, result.commissionAmount)
        assertEquals(PayoutStage.PENDING, result.payoutStage)
        verify(exactly = 1) { referralPartnerPayoutRepository.save(any()) }
        verify(exactly = 1) { applicationEventPublisher.publishEvent(any<ApplicationEvent>()) }
    }

    @Test
    fun `happy path - creates ReferralPartnerPayout and publishes event`() {
        val partnerId = "rp-001"
        val partner = makePartner(partnerId)
        val triggerRef = "customer-abc"
        val amount = BigDecimal("15.00")
        val type = ReferralCommissionType.FOOD_CUSTOMER_REFERRAL

        every { referralPartnerPayoutRepository.findByCommissionTypeAndTriggerReferenceId(type, triggerRef) } returns null
        every { userProfileRepo.findById(partnerId) } returns java.util.Optional.of(partner)
        val capturedPayout = slot<ReferralPartnerPayout>()
        every { referralPartnerPayoutRepository.save(capture(capturedPayout)) } answers {
            capturedPayout.captured.also { it.id = "RP001" }
        }
        val capturedEvent = slot<ApplicationEvent>()
        every { applicationEventPublisher.publishEvent(capture(capturedEvent)) } just runs

        val result = sut.generatePayoutForReferralPartner(partnerId, amount, type, triggerRef)

        assertNotNull(result)
        assertEquals(partnerId, result!!.toId)
        assertEquals(amount, result.commissionAmount)
        assertEquals(type, result.commissionType)
        assertEquals(triggerRef, result.triggerReferenceId)
        assertEquals(PayoutStage.PENDING, result.payoutStage)

        verify(exactly = 1) { referralPartnerPayoutRepository.save(any()) }
        verify(exactly = 1) { applicationEventPublisher.publishEvent(any<ApplicationEvent>()) }

        val event = capturedEvent.captured as ReferralPartnerPayoutEvent
        assertEquals(partnerId, event.partnerId)
        assertEquals(type, event.commissionType)
        assertEquals(triggerRef, event.triggerReferenceId)
        assertEquals(amount, event.commissionAmount)
        assertEquals("RP001", event.payoutId)
    }

    @Test
    fun `returns null when payout already exists for same commission type and trigger`() {
        val partnerId = "rp-001"
        val triggerRef = "customer-abc"
        val type = ReferralCommissionType.FOOD_CUSTOMER_REFERRAL
        val existingPayout = mockk<ReferralPartnerPayout> { every { id } returns "EXIST" }

        every { referralPartnerPayoutRepository.findByCommissionTypeAndTriggerReferenceId(type, triggerRef) } returns existingPayout

        val result = sut.generatePayoutForReferralPartner(partnerId, BigDecimal("15.00"), type, triggerRef)

        assertNull(result)
        verify(exactly = 0) { referralPartnerPayoutRepository.save(any()) }
        verify(exactly = 0) { applicationEventPublisher.publishEvent(any<ApplicationEvent>()) }
    }

    @Test
    fun `returns null when partner profile not found`() {
        val type = ReferralCommissionType.STORE_PARTNER_STAGE_1
        val triggerRef = "store-xyz"

        every { referralPartnerPayoutRepository.findByCommissionTypeAndTriggerReferenceId(type, triggerRef) } returns null
        every { userProfileRepo.findById("missing-partner") } returns java.util.Optional.empty()

        val result = sut.generatePayoutForReferralPartner("missing-partner", BigDecimal("100.00"), type, triggerRef)

        assertNull(result)
        verify(exactly = 0) { referralPartnerPayoutRepository.save(any()) }
        verify(exactly = 0) { applicationEventPublisher.publishEvent(any<ApplicationEvent>()) }
    }

    @Test
    fun `returns null and logs WARN when partner has no bank details`() {
        val partnerId = "rp-nob"
        val partner = makePartner(partnerId, withBank = false)
        val type = ReferralCommissionType.STORE_PARTNER_STAGE_2
        val triggerRef = "store-nob"

        every { referralPartnerPayoutRepository.findByCommissionTypeAndTriggerReferenceId(type, triggerRef) } returns null
        every { userProfileRepo.findById(partnerId) } returns java.util.Optional.of(partner)

        val result = sut.generatePayoutForReferralPartner(partnerId, BigDecimal("150.00"), type, triggerRef)

        assertNull(result)
        verify(exactly = 0) { referralPartnerPayoutRepository.save(any()) }
        verify(exactly = 0) { applicationEventPublisher.publishEvent(any<ApplicationEvent>()) }
    }

    @Test
    fun `handles DuplicateKeyException gracefully and returns null`() {
        val partnerId = "rp-dup"
        val partner = makePartner(partnerId)
        val type = ReferralCommissionType.FOOD_CUSTOMER_REFERRAL
        val triggerRef = "customer-dup"

        every { referralPartnerPayoutRepository.findByCommissionTypeAndTriggerReferenceId(type, triggerRef) } returns null
        every { userProfileRepo.findById(partnerId) } returns java.util.Optional.of(partner)
        every { referralPartnerPayoutRepository.save(any()) } throws
            org.springframework.dao.DuplicateKeyException("duplicate key error")

        val result = sut.generatePayoutForReferralPartner(partnerId, BigDecimal("15.00"), type, triggerRef)

        assertNull(result)
        verify(exactly = 0) { applicationEventPublisher.publishEvent(any<ApplicationEvent>()) }
    }

    // ─── reconcilePendingReferralCommissions ─────────────────────────────────

    @Test
    fun `reconcile creates payouts for PENDING commissions with no existing payout`() {
        val partnerId = "rp-rec"
        val partner = makePartner(partnerId)

        val foodCommission = FoodCustomerReferralCommission(
            id = "fc1", customerId = "cust-1", referralPartnerId = partnerId,
            triggeringOrderId = "ord-1", amount = BigDecimal("15.00"),
            status = ReferralCommissionStatus.PENDING, createdAt = Date()
        )
        val stage1Commission = StorePartnerStage1Commission(
            id = "s1", storeId = "store-1", referralPartnerId = partnerId,
            amount = BigDecimal("100.00"), status = ReferralCommissionStatus.PENDING, createdAt = Date()
        )
        val stage2Commission = StorePartnerStage2Commission(
            id = "s2", storeId = "store-2", referralPartnerId = partnerId,
            triggeringOrderId = "ord-2", amount = BigDecimal("150.00"),
            status = ReferralCommissionStatus.PENDING, createdAt = Date()
        )

        every { foodCustomerCommissionRepo.findAll() } returns listOf(foodCommission)
        every { storeStage1CommissionRepo.findAll() } returns listOf(stage1Commission)
        every { storeStage2CommissionRepo.findAll() } returns listOf(stage2Commission)

        every { referralPartnerPayoutRepository.findByCommissionTypeAndTriggerReferenceId(ReferralCommissionType.FOOD_CUSTOMER_REFERRAL, "cust-1") } returns null
        every { referralPartnerPayoutRepository.findByCommissionTypeAndTriggerReferenceId(ReferralCommissionType.STORE_PARTNER_STAGE_1, "store-1") } returns null
        every { referralPartnerPayoutRepository.findByCommissionTypeAndTriggerReferenceId(ReferralCommissionType.STORE_PARTNER_STAGE_2, "store-2") } returns null

        every { userProfileRepo.findById(partnerId) } returns java.util.Optional.of(partner)
        val capturedPayout = slot<ReferralPartnerPayout>()
        every { referralPartnerPayoutRepository.save(capture(capturedPayout)) } answers {
            capturedPayout.captured.also { it.id = "REC${capturedPayout.captured.commissionType}" }
        }
        every { applicationEventPublisher.publishEvent(any<ApplicationEvent>()) } just runs

        sut.reconcilePendingReferralCommissions()

        verify(exactly = 3) { referralPartnerPayoutRepository.save(any()) }
        verify(exactly = 3) { applicationEventPublisher.publishEvent(any<ApplicationEvent>()) }
    }

    @Test
    fun `reconcile skips commissions that already have a linked payout`() {
        val partnerId = "rp-rec2"
        val existingPayout = mockk<ReferralPartnerPayout> { every { id } returns "EXIST" }

        val foodCommission = FoodCustomerReferralCommission(
            id = "fc2", customerId = "cust-2", referralPartnerId = partnerId,
            triggeringOrderId = "ord-2", amount = BigDecimal("15.00"),
            status = ReferralCommissionStatus.PENDING, createdAt = Date()
        )

        every { foodCustomerCommissionRepo.findAll() } returns listOf(foodCommission)
        every { storeStage1CommissionRepo.findAll() } returns emptyList()
        every { storeStage2CommissionRepo.findAll() } returns emptyList()
        every { referralPartnerPayoutRepository.findByCommissionTypeAndTriggerReferenceId(ReferralCommissionType.FOOD_CUSTOMER_REFERRAL, "cust-2") } returns existingPayout

        sut.reconcilePendingReferralCommissions()

        verify(exactly = 0) { referralPartnerPayoutRepository.save(any()) }
    }

    @Test
    fun `reconcile skips PAID commissions`() {
        val paidCommission = FoodCustomerReferralCommission(
            id = "fc3", customerId = "cust-3", referralPartnerId = "rp-003",
            triggeringOrderId = "ord-3", amount = BigDecimal("15.00"),
            status = ReferralCommissionStatus.PAID, createdAt = Date()
        )

        every { foodCustomerCommissionRepo.findAll() } returns listOf(paidCommission)
        every { storeStage1CommissionRepo.findAll() } returns emptyList()
        every { storeStage2CommissionRepo.findAll() } returns emptyList()

        sut.reconcilePendingReferralCommissions()

        verify(exactly = 0) { referralPartnerPayoutRepository.findByCommissionTypeAndTriggerReferenceId(any(), any()) }
        verify(exactly = 0) { referralPartnerPayoutRepository.save(any()) }
    }

    @Test
    fun `reconcile skips partners without bank details and continues to next`() {
        val partnerNoBank = makePartner("rp-nobank", withBank = false)
        val partnerWithBank = makePartner("rp-bank")

        val commissionNoBank = FoodCustomerReferralCommission(
            id = "fc4", customerId = "cust-4", referralPartnerId = "rp-nobank",
            triggeringOrderId = "ord-4", amount = BigDecimal("15.00"),
            status = ReferralCommissionStatus.PENDING, createdAt = Date()
        )
        val commissionWithBank = StorePartnerStage1Commission(
            id = "s3", storeId = "store-3", referralPartnerId = "rp-bank",
            amount = BigDecimal("100.00"), status = ReferralCommissionStatus.PENDING, createdAt = Date()
        )

        every { foodCustomerCommissionRepo.findAll() } returns listOf(commissionNoBank)
        every { storeStage1CommissionRepo.findAll() } returns listOf(commissionWithBank)
        every { storeStage2CommissionRepo.findAll() } returns emptyList()

        every { referralPartnerPayoutRepository.findByCommissionTypeAndTriggerReferenceId(ReferralCommissionType.FOOD_CUSTOMER_REFERRAL, "cust-4") } returns null
        every { referralPartnerPayoutRepository.findByCommissionTypeAndTriggerReferenceId(ReferralCommissionType.STORE_PARTNER_STAGE_1, "store-3") } returns null

        every { userProfileRepo.findById("rp-nobank") } returns java.util.Optional.of(partnerNoBank)
        every { userProfileRepo.findById("rp-bank") } returns java.util.Optional.of(partnerWithBank)

        val capturedPayout = slot<ReferralPartnerPayout>()
        every { referralPartnerPayoutRepository.save(capture(capturedPayout)) } answers {
            capturedPayout.captured.also { it.id = "NEW1" }
        }
        every { applicationEventPublisher.publishEvent(any<ApplicationEvent>()) } just runs

        sut.reconcilePendingReferralCommissions()

        // only the one with bank details should produce a payout
        verify(exactly = 1) { referralPartnerPayoutRepository.save(any()) }
        verify(exactly = 1) { applicationEventPublisher.publishEvent(any<ApplicationEvent>()) }
    }
}
