package io.curiousoft.izinga.recon

import io.curiousoft.izinga.commons.model.BankAccType
import io.curiousoft.izinga.commons.model.Order
import io.curiousoft.izinga.commons.model.ShippingData
import io.curiousoft.izinga.commons.payout.events.OrderPayoutEvent
import io.curiousoft.izinga.commons.referral.FoodCustomerReferralCommissionRepo
import io.curiousoft.izinga.commons.referral.FurnitureCustomerReferralCommissionRepo
import io.curiousoft.izinga.commons.referral.StorePartnerStage1CommissionRepo
import io.curiousoft.izinga.commons.referral.StorePartnerStage2CommissionRepo
import io.curiousoft.izinga.commons.repo.StoreRepository
import io.curiousoft.izinga.commons.repo.UserProfileRepo
import io.curiousoft.izinga.recon.ambassador.AmbassadorProperties
import io.curiousoft.izinga.recon.payout.*
import io.curiousoft.izinga.recon.payout.repo.*
import io.mockk.*
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.context.ApplicationEvent
import org.springframework.context.ApplicationEventPublisher

/**
 * GAP-3: Verifies payout failure handling — failure reason persisted, attempt count incremented,
 * and status reverted to PENDING on paid=false so the payout is retried in the next CSV cycle.
 */
class PayoutFailureStatusUpdateTest {

    private val storeRepo = mockk<StoreRepository>()
    private val userProfileRepo = mockk<UserProfileRepo>()
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
            userProfileRepo = userProfileRepo,
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
            ambassadorProperties = AmbassadorProperties()
        )
    }

    // ── Messenger payout failure ─────────────────────────────────────────────

    @Test
    fun `GAP-3 - messenger payout paid=false reverts to PENDING with reason and incremented count`() {
        val toId = "messenger-1"
        val failureReason = "Insufficient funds in source account"
        val payout = makeMessengerPayout(toId).also {
            it.payoutStage = PayoutStage.PROCESSING
            it.attemptCount = 0
        }
        val bundleResponse = PayoutBundleResults(
            bundleId = "BNDL-001",
            payoutItemResults = listOf(
                PayoutItemResults(toId = toId, paid = false, message = failureReason, type = PayoutType.MESSENGER)
            )
        )

        every { messengerPayoutRepository.findByToIdAndPayoutStage(toId, PayoutStage.PROCESSING) } returns payout
        val savedSlot = slot<MessengerPayout>()
        every { messengerPayoutRepository.save(capture(savedSlot)) } returnsArgument 0

        sut.updatePayoutStatus(bundleResponse)

        val saved = savedSlot.captured
        assertEquals(PayoutStage.PENDING, saved.payoutStage)
        assertEquals(failureReason, saved.failureReason)
        assertEquals(1, saved.attemptCount)
        assertFalse(saved.paid)
        // No OrderPayoutEvent fired for a failed payout
        verify(exactly = 0) { applicationEventPublisher.publishEvent(any<ApplicationEvent>()) }
    }

    @Test
    fun `GAP-3 - messenger payout paid=false increments attempt count on second failure`() {
        val toId = "messenger-retry"
        val payout = makeMessengerPayout(toId).also {
            it.payoutStage = PayoutStage.PROCESSING
            it.attemptCount = 1
            it.failureReason = "Previous failure"
        }
        val bundleResponse = PayoutBundleResults(
            bundleId = "BNDL-002",
            payoutItemResults = listOf(
                PayoutItemResults(toId = toId, paid = false, message = "New failure reason", type = PayoutType.MESSENGER)
            )
        )

        every { messengerPayoutRepository.findByToIdAndPayoutStage(toId, PayoutStage.PROCESSING) } returns payout
        val savedSlot = slot<MessengerPayout>()
        every { messengerPayoutRepository.save(capture(savedSlot)) } returnsArgument 0

        sut.updatePayoutStatus(bundleResponse)

        val saved = savedSlot.captured
        assertEquals(2, saved.attemptCount)
        assertEquals("New failure reason", saved.failureReason)
        assertEquals(PayoutStage.PENDING, saved.payoutStage)
    }

    @Test
    fun `GAP-3 - messenger payout paid=true sets COMPLETED and clears failure reason`() {
        val toId = "messenger-success"
        val order = makeOrder("order-1", toId)
        val payout = makeMessengerPayout(toId, orders = mutableSetOf(order)).also {
            it.payoutStage = PayoutStage.PROCESSING
            it.attemptCount = 1
            it.failureReason = "Previous bank failure"
        }
        val bundleResponse = PayoutBundleResults(
            bundleId = "BNDL-003",
            payoutItemResults = listOf(
                PayoutItemResults(toId = toId, paid = true, message = null, type = PayoutType.MESSENGER)
            )
        )

        every { messengerPayoutRepository.findByToIdAndPayoutStage(toId, PayoutStage.PROCESSING) } returns payout
        val savedSlot = slot<MessengerPayout>()
        every { messengerPayoutRepository.save(capture(savedSlot)) } returnsArgument 0
        every { applicationEventPublisher.publishEvent(any<ApplicationEvent>()) } just runs

        sut.updatePayoutStatus(bundleResponse)

        val saved = savedSlot.captured
        assertEquals(PayoutStage.COMPLETED, saved.payoutStage)
        assertNull(saved.failureReason)
        assertEquals(2, saved.attemptCount)
        assertTrue(saved.paid)
        verify(exactly = 1) {
            applicationEventPublisher.publishEvent(match {
                it is OrderPayoutEvent && it.isMessengerPaid && it.orderId == "order-1"
            })
        }
    }

    // ── Shop payout failure ──────────────────────────────────────────────────

    @Test
    fun `GAP-3 - shop payout paid=false reverts to PENDING with reason and incremented count`() {
        val toId = "shop-1"
        val failureReason = "Invalid account number"
        val payout = makeShopPayout(toId).also {
            it.payoutStage = PayoutStage.PROCESSING
            it.attemptCount = 0
        }
        val bundleResponse = PayoutBundleResults(
            bundleId = "BNDL-004",
            payoutItemResults = listOf(
                PayoutItemResults(toId = toId, paid = false, message = failureReason, type = PayoutType.SHOP)
            )
        )

        every { shopPayoutRepository.findByToIdAndPayoutStage(toId, PayoutStage.PROCESSING) } returns payout
        val savedSlot = slot<ShopPayout>()
        every { shopPayoutRepository.save(capture(savedSlot)) } returnsArgument 0

        sut.updatePayoutStatus(bundleResponse)

        val saved = savedSlot.captured
        assertEquals(PayoutStage.PENDING, saved.payoutStage)
        assertEquals(failureReason, saved.failureReason)
        assertEquals(1, saved.attemptCount)
        assertFalse(saved.paid)
        verify(exactly = 0) { applicationEventPublisher.publishEvent(any<ApplicationEvent>()) }
    }

    @Test
    fun `GAP-3 - shop payout paid=true sets COMPLETED clears failure reason and fires event`() {
        val toId = "shop-success"
        val order = makeOrder("order-2", null)
        val payout = makeShopPayout(toId, orders = mutableSetOf(order)).also {
            it.payoutStage = PayoutStage.PROCESSING
            it.attemptCount = 0
        }
        val bundleResponse = PayoutBundleResults(
            bundleId = "BNDL-005",
            payoutItemResults = listOf(
                PayoutItemResults(toId = toId, paid = true, message = null, type = PayoutType.SHOP)
            )
        )

        every { shopPayoutRepository.findByToIdAndPayoutStage(toId, PayoutStage.PROCESSING) } returns payout
        val savedSlot = slot<ShopPayout>()
        every { shopPayoutRepository.save(capture(savedSlot)) } returnsArgument 0
        every { applicationEventPublisher.publishEvent(any<ApplicationEvent>()) } just runs

        sut.updatePayoutStatus(bundleResponse)

        val saved = savedSlot.captured
        assertEquals(PayoutStage.COMPLETED, saved.payoutStage)
        assertNull(saved.failureReason)
        assertEquals(1, saved.attemptCount)
        assertTrue(saved.paid)
        verify(exactly = 1) {
            applicationEventPublisher.publishEvent(match {
                it is OrderPayoutEvent && it.isStorePaid && it.orderId == "order-2"
            })
        }
    }

    @Test
    fun `GAP-3 - no PROCESSING payout found means no save and no event`() {
        val toId = "messenger-unknown"
        val bundleResponse = PayoutBundleResults(
            bundleId = "BNDL-006",
            payoutItemResults = listOf(
                PayoutItemResults(toId = toId, paid = false, message = "Some reason", type = PayoutType.MESSENGER)
            )
        )

        every { messengerPayoutRepository.findByToIdAndPayoutStage(toId, PayoutStage.PROCESSING) } returns null

        sut.updatePayoutStatus(bundleResponse)

        verify(exactly = 0) { messengerPayoutRepository.save(any()) }
        verify(exactly = 0) { applicationEventPublisher.publishEvent(any<ApplicationEvent>()) }
    }

    // ── Payout model field defaults ──────────────────────────────────────────

    @Test
    fun `GAP-3 - new payout has attemptCount 0 and null failureReason by default`() {
        val payout = makeMessengerPayout("messenger-new")
        assertEquals(0, payout.attemptCount)
        assertNull(payout.failureReason)
    }

    // ── Helpers ──────────────────────────────────────────────────────────────

    private fun makeMessengerPayout(toId: String, orders: MutableSet<Order> = mutableSetOf()) =
        MessengerPayout(
            toId = toId, toName = "Test Driver $toId", toBankName = "FNB",
            toType = BankAccType.CHEQUE, toAccountNumber = "1234567890", toBranchCode = "250655",
            fromReference = "Payment to driver", toReference = "iZinga pay",
            emailNotify = "", emailAddress = "driver@test.com", emailSubject = "iZinga pay",
            orders = orders, payoutStage = PayoutStage.PROCESSING
        ).also { it.id = "PAY-$toId" }

    private fun makeShopPayout(toId: String, orders: MutableSet<Order> = mutableSetOf()) =
        ShopPayout(
            toId = toId, toName = "Test Store $toId", toBankName = "FNB",
            toType = BankAccType.CHEQUE, toAccountNumber = "9876543210", toBranchCode = "250655",
            fromReference = "Payment to store", toReference = "iZinga pay",
            emailNotify = "", emailAddress = "store@test.com", emailSubject = "iZinga pay",
            orders = orders, payoutStage = PayoutStage.PROCESSING
        ).also { it.id = "PAY-$toId" }

    private fun makeOrder(orderId: String, messengerId: String?): Order {
        return Order().also {
            it.id = orderId
            it.shippingData = ShippingData().apply {
                this.messengerId = messengerId
                deliveryFee = 50.0
            }
        }
    }
}
