package io.curiousoft.izinga.recon

import io.curiousoft.izinga.commons.model.BankAccType
import io.curiousoft.izinga.commons.model.Order
import io.curiousoft.izinga.commons.model.OrderStage
import io.curiousoft.izinga.commons.model.ShippingData
import io.curiousoft.izinga.commons.model.StoreProfile
import io.curiousoft.izinga.commons.model.UserProfile
import io.curiousoft.izinga.commons.referral.FoodCustomerReferralCommissionRepo
import io.curiousoft.izinga.commons.referral.FurnitureCustomerReferralCommissionRepo
import io.curiousoft.izinga.commons.referral.StorePartnerStage1CommissionRepo
import io.curiousoft.izinga.commons.referral.StorePartnerStage2CommissionRepo
import io.curiousoft.izinga.commons.repo.StoreRepository
import io.curiousoft.izinga.commons.repo.UserProfileRepo
import io.curiousoft.izinga.recon.ambassador.AmbassadorProperties
import io.curiousoft.izinga.recon.payout.MessengerPayout
import io.curiousoft.izinga.recon.payout.PayoutStage
import io.curiousoft.izinga.recon.payout.ShopPayout
import io.curiousoft.izinga.recon.payout.repo.AmbassadorPayoutRepository
import io.curiousoft.izinga.recon.payout.repo.MessengerPayoutRepository
import io.curiousoft.izinga.recon.payout.repo.PayoutRepository
import io.curiousoft.izinga.recon.payout.repo.ReferralPartnerPayoutRepository
import io.curiousoft.izinga.recon.payout.repo.ShopPayoutRepository
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.springframework.context.ApplicationEventPublisher
import java.util.Optional

/**
 * IDEMPOTENCY regression tests for ReconServiceImpl.
 *
 * Root cause: Order.equals() is id-based (override at Order.kt ~line 117) but hashCode() is
 * identity-based (no override). A MutableSet<Order> therefore uses identity for bucketing, so
 * two Order instances with the same id but different object identities are both added — shop
 * payout total doubles, shop is overpaid.
 *
 * Fix: generatePayoutForShopAndOrder guards with `payout.orders.none { it.id == order.id }`.
 * Fix: generatePayoutForMessengerAndOrder uses `firstOrNull { it.id == order.id }` (explicit).
 */
class ReconServiceImplIdempotencyTest {

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
        )
    }

    // ── helpers ──────────────────────────────────────────────────────────────────────────────────

    /** Creates an Order at STAGE_7_ALL_PAID with the given id and shopId. basket is not set. */
    private fun shopOrder(id: String, shopId: String): Order {
        val o = Order()
        o.id = id
        o.shopId = shopId
        o.stage = OrderStage.STAGE_7_ALL_PAID
        return o
    }

    private fun shopOrderWithMessenger(id: String, messengerId: String): Order {
        val o = Order()
        o.id = id
        o.stage = OrderStage.STAGE_7_ALL_PAID
        val sd = ShippingData("from", "to", ShippingData.ShippingType.DELIVERY)
        sd.messengerId = messengerId
        o.shippingData = sd
        return o
    }

    private fun existingShopPayout(shopId: String, vararg orders: Order): ShopPayout =
        ShopPayout(
            toId = shopId,
            toName = "Test Shop",
            toBankName = "FNB",
            toType = BankAccType.CHEQUE,
            toAccountNumber = "1234567890",
            orders = mutableSetOf(*orders),
            toBranchCode = "250655",
            fromReference = "ref",
            toReference = "ref",
            emailNotify = null,
            emailAddress = null,
            emailSubject = null
        )

    private fun existingMessengerPayout(messengerId: String, vararg orders: Order): MessengerPayout =
        MessengerPayout(
            toId = messengerId,
            toName = "Test Messenger",
            toBankName = "FNB",
            toType = BankAccType.CHEQUE,
            toAccountNumber = "9876543210",
            toBranchCode = "250655",
            fromReference = "ref",
            toReference = "ref",
            emailNotify = null,
            orders = mutableSetOf(*orders),
            emailAddress = "test@test.com",
            emailSubject = "payout"
        )

    // findByIdOrNull is a Kotlin extension fn; mock via the underlying findById (CrudRepository).
    private fun stubStore(shopId: String) {
        val store = mockk<StoreProfile>()
        every { store.id } returns shopId
        every { store.hasPaymentAgreement } returns true
        every { storeRepo.findById(shopId) } returns Optional.of(store)
    }

    private fun stubMessenger(messengerId: String) {
        val messenger = mockk<UserProfile>()
        every { messenger.id } returns messengerId
        every { messenger.isPermanentEmployed } returns false
        every { userProfileRepo.findById(messengerId) } returns Optional.of(messenger)
    }

    // ── shop idempotency tests ────────────────────────────────────────────────────────────────────

    /**
     * IDEMPOTENCY-SHOP-01: when the scheduler processes the same order twice (two different Order
     * instances with the same id), the order must NOT be added to the shop payout a second time.
     *
     * Without the fix, MutableSet.add(order2) succeeds because identity hashCodes differ, even
     * though order1.equals(order2) is true — causing ShopPayout.total to double and shop to be
     * overpaid.
     */
    @Test
    fun `generatePayoutForShopAndOrder - same order id not added twice to existing payout`() {
        val orderId = "order-shop-idempotency-1"
        val shopId = "shop-idempotency-1"

        val firstOrder = shopOrder(orderId, shopId)
        val existingPayout = existingShopPayout(shopId, firstOrder)

        // Second instance: same id, different JVM object (simulates DB reload by scheduler)
        val secondOrder = shopOrder(orderId, shopId)
        assert(firstOrder !== secondOrder) { "precondition: different object instances" }
        assertEquals("precondition: same id means equals()", firstOrder, secondOrder)

        stubStore(shopId)
        every { shopPayoutRepo.findByToIdAndPayoutStage(shopId, PayoutStage.PENDING) } returns existingPayout
        val savedSlot = slot<ShopPayout>()
        every { shopPayoutRepo.save(capture(savedSlot)) } answers { firstArg() }

        sut.generatePayoutForShopAndOrder(secondOrder)

        assertEquals(
            "Duplicate order id must NOT inflate shop payout orders set (prevents overpayment)",
            1, savedSlot.captured.orders.size
        )
    }

    /**
     * IDEMPOTENCY-SHOP-02: a genuinely new order (different id) must still be added to the
     * existing payout. The idempotency guard must not block legitimate new orders.
     */
    @Test
    fun `generatePayoutForShopAndOrder - new order id is added to existing payout`() {
        val shopId = "shop-idempotency-2"
        val existingPayout = existingShopPayout(shopId, shopOrder("order-existing-1", shopId))
        val newOrder = shopOrder("order-new-1", shopId)

        stubStore(shopId)
        every { shopPayoutRepo.findByToIdAndPayoutStage(shopId, PayoutStage.PENDING) } returns existingPayout
        val savedSlot = slot<ShopPayout>()
        every { shopPayoutRepo.save(capture(savedSlot)) } answers { firstArg() }

        sut.generatePayoutForShopAndOrder(newOrder)

        assertEquals(
            "A genuinely new order id must be added to the shop payout",
            2, savedSlot.captured.orders.size
        )
    }

    // ── messenger idempotency tests ───────────────────────────────────────────────────────────────

    /**
     * IDEMPOTENCY-MSG-01: same order id must not be added twice to an existing messenger payout.
     * The explicit `firstOrNull { it.id == order.id }` check prevents this regardless of any
     * class-hierarchy asymmetry in equals() between Order and MessengerOrderDto.
     */
    @Test
    fun `generatePayoutForMessengerAndOrder - same order id not added twice to existing payout`() {
        val orderId = "order-msg-idempotency-1"
        val messengerId = "messenger-idempotency-1"

        val firstOrder = shopOrderWithMessenger(orderId, messengerId)
        val existingPayout = existingMessengerPayout(messengerId, firstOrder)
        val secondOrder = shopOrderWithMessenger(orderId, messengerId)

        assert(firstOrder !== secondOrder) { "precondition: different object instances" }

        stubMessenger(messengerId)
        every { messengerPayoutRepository.findByToIdAndPayoutStage(messengerId, PayoutStage.PENDING) } returns existingPayout
        val savedSlot = slot<MessengerPayout>()
        every { messengerPayoutRepository.save(capture(savedSlot)) } answers { firstArg() }

        sut.generatePayoutForMessengerAndOrder(secondOrder)

        assertEquals(
            "Duplicate order id must NOT inflate messenger payout orders set",
            1, savedSlot.captured.orders.size
        )
    }
}
