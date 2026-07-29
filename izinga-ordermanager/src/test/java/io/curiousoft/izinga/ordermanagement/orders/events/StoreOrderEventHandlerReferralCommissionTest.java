package io.curiousoft.izinga.ordermanagement.orders.events;

import io.curiousoft.izinga.commons.model.*;
import io.curiousoft.izinga.commons.model.ShippingData;
import io.curiousoft.izinga.commons.order.events.OrderUpdatedEvent;
import io.curiousoft.izinga.commons.referral.*;
import io.curiousoft.izinga.commons.repo.UserProfileRepo;
import io.curiousoft.izinga.messaging.AdminOnlyNotificationService;
import io.curiousoft.izinga.messaging.firebase.FirebaseNotificationService;
import io.curiousoft.izinga.ordermanagement.notification.EmailNotificationService;
import io.curiousoft.izinga.ordermanagement.service.DeviceService;
import io.curiousoft.izinga.recon.ReconService;
import io.curiousoft.izinga.usermanagement.users.UserProfileService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.time.DayOfWeek;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * RP-006 and RP-008: Commission triggers in StoreOrderEventHandler when an order
 * reaches STAGE_7_ALL_PAID for a FOOD store.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class StoreOrderEventHandlerReferralCommissionTest {

    @Mock private FirebaseNotificationService pushNotificationService;
    @Mock private AdminOnlyNotificationService adminOnlyNotificationService;
    @Mock private EmailNotificationService emailNotificationService;
    @Mock private DeviceService deviceService;
    @Mock private UserProfileService userProfileService;
    @Mock private ReconService reconService;
    @Mock private UserProfileRepo userProfileRepo;
    @Mock private FoodCustomerReferralCommissionRepo foodCustomerCommissionRepo;
    @Mock private FurnitureCustomerReferralCommissionRepo furnitureCustomerCommissionRepo;
    @Mock private StorePartnerStage1CommissionRepo storeStage1CommissionRepo;
    @Mock private StorePartnerStage2CommissionRepo storeStage2CommissionRepo;

    private StoreOrderEventHandler handler;

    /** Service fee percentage matching application.properties: 6.5% → 0.065 */
    private static final double SERVICE_FEE_PERC = 0.065;

    @BeforeEach
    void setUp() {
        handler = new StoreOrderEventHandler(
                pushNotificationService, adminOnlyNotificationService, emailNotificationService,
                deviceService, userProfileService, reconService,
                userProfileRepo, foodCustomerCommissionRepo, furnitureCustomerCommissionRepo,
                storeStage1CommissionRepo, storeStage2CommissionRepo
        );
        // @Value field — must be set via reflection in unit tests (Spring not in context)
        ReflectionTestUtils.setField(handler, "serviceFeePerc", SERVICE_FEE_PERC);
    }

    // -------------------------------------------------------------------------
    // RP-006: Food customer referral commission (R15 first order)
    // -------------------------------------------------------------------------

    @Test
    void handleOrderUpdatedEvent_createsCustomerCommission_whenReferredCustomerCompletesFirstFoodOrder() {
        var store = foodStore("store-001", null);
        var order = completedOrder("order-001", "store-001", "customer-001");
        var customer = customer("customer-001", "partner-rp-1");

        when(userProfileRepo.findById("customer-001")).thenReturn(Optional.of(customer));
        when(storeStage1CommissionRepo.findByStoreId("store-001")).thenReturn(null); // RP-008 guard
        when(storeStage2CommissionRepo.findByStoreId("store-001")).thenReturn(null);

        handler.handleOrderUpdatedEvent(new OrderUpdatedEvent(this, order, "", store));

        ArgumentCaptor<FoodCustomerReferralCommission> captor =
                ArgumentCaptor.forClass(FoodCustomerReferralCommission.class);
        verify(foodCustomerCommissionRepo).insert(captor.capture());
        assertEquals("customer-001", captor.getValue().getCustomerId());
        assertEquals("partner-rp-1", captor.getValue().getReferralPartnerId());
        assertEquals("order-001", captor.getValue().getTriggeringOrderId());
        assertEquals(new BigDecimal("15.00"), captor.getValue().getAmount());
        assertEquals(ReferralCommissionStatus.PENDING, captor.getValue().getStatus());
    }

    @Test
    void handleOrderUpdatedEvent_skipsCustomerCommission_whenCustomerHasNoReferral() {
        var store = foodStore("store-002", null);
        var order = completedOrder("order-002", "store-002", "customer-002");
        var customer = customer("customer-002", null); // no referral

        when(userProfileRepo.findById("customer-002")).thenReturn(Optional.of(customer));
        when(storeStage1CommissionRepo.findByStoreId("store-002")).thenReturn(null);

        handler.handleOrderUpdatedEvent(new OrderUpdatedEvent(this, order, "", store));

        verify(foodCustomerCommissionRepo, never()).insert(any(FoodCustomerReferralCommission.class));
    }

    @Test
    void handleOrderUpdatedEvent_skipsCustomerCommission_whenCustomerNotFound() {
        var store = foodStore("store-003", null);
        var order = completedOrder("order-003", "store-003", "customer-003");

        when(userProfileRepo.findById("customer-003")).thenReturn(Optional.empty());
        when(storeStage1CommissionRepo.findByStoreId("store-003")).thenReturn(null);

        handler.handleOrderUpdatedEvent(new OrderUpdatedEvent(this, order, "", store));

        verify(foodCustomerCommissionRepo, never()).insert(any(FoodCustomerReferralCommission.class));
    }

    @Test
    void handleOrderUpdatedEvent_handlesCustomerCommissionDuplicateKeyGracefully() {
        var store = foodStore("store-004", null);
        var order = completedOrder("order-004", "store-004", "customer-004");
        var customer = customer("customer-004", "partner-rp-4");

        when(userProfileRepo.findById("customer-004")).thenReturn(Optional.of(customer));
        when(storeStage1CommissionRepo.findByStoreId("store-004")).thenReturn(null);
        when(foodCustomerCommissionRepo.insert(any(FoodCustomerReferralCommission.class)))
                .thenThrow(new DuplicateKeyException("duplicate"));

        // must not throw
        assertDoesNotThrow(() -> handler.handleOrderUpdatedEvent(new OrderUpdatedEvent(this, order, "", store)));
        verify(foodCustomerCommissionRepo).insert(any(FoodCustomerReferralCommission.class));
    }

    @Test
    void handleOrderUpdatedEvent_skipsCustomerCommission_whenOrderNotStage7() {
        var store = foodStore("store-005", null);
        var order = orderAtStage("order-005", "store-005", "customer-005", OrderStage.STAGE_6_WITH_CUSTOMER);

        handler.handleOrderUpdatedEvent(new OrderUpdatedEvent(this, order, "", store));

        verify(foodCustomerCommissionRepo, never()).insert(any(FoodCustomerReferralCommission.class));
        verify(storeStage2CommissionRepo, never()).insert(any(StorePartnerStage2Commission.class));
    }

    @Test
    void handleOrderUpdatedEvent_skipsCustomerCommission_whenStoreTypeIsNotFood() {
        var store = nonFoodStore("store-006");
        var order = completedOrder("order-006", "store-006", "customer-006");

        handler.handleOrderUpdatedEvent(new OrderUpdatedEvent(this, order, "", store));

        verify(foodCustomerCommissionRepo, never()).insert(any(FoodCustomerReferralCommission.class));
        verify(userProfileRepo, never()).findById(anyString());
    }

    // -------------------------------------------------------------------------
    // RP-008: Store partner stage 2 commission (R150 on store's first order)
    // -------------------------------------------------------------------------

    @Test
    void handleOrderUpdatedEvent_createsStage2Commission_whenStage1ExistsAndStoreIsReferred() {
        var store = foodStore("store-007", "partner-rp-7");
        var order = completedOrder("order-007", "store-007", "customer-007");
        var customer = customer("customer-007", null); // no customer referral, focus on store

        var stage1 = new StorePartnerStage1Commission(
                UUID.randomUUID().toString(), "store-007", "partner-rp-7",
                new BigDecimal("100.00"), ReferralCommissionStatus.PENDING, new Date()
        );

        when(userProfileRepo.findById("customer-007")).thenReturn(Optional.of(customer));
        when(storeStage1CommissionRepo.findByStoreId("store-007")).thenReturn(stage1);

        handler.handleOrderUpdatedEvent(new OrderUpdatedEvent(this, order, "", store));

        ArgumentCaptor<StorePartnerStage2Commission> captor =
                ArgumentCaptor.forClass(StorePartnerStage2Commission.class);
        verify(storeStage2CommissionRepo).insert(captor.capture());
        assertEquals("store-007", captor.getValue().getStoreId());
        assertEquals("partner-rp-7", captor.getValue().getReferralPartnerId());
        assertEquals("order-007", captor.getValue().getTriggeringOrderId());
        assertEquals(new BigDecimal("150.00"), captor.getValue().getAmount());
        assertEquals(ReferralCommissionStatus.PENDING, captor.getValue().getStatus());
    }

    @Test
    void handleOrderUpdatedEvent_skipsStage2Commission_whenNoStage1Exists() {
        var store = foodStore("store-008", "partner-rp-8");
        var order = completedOrder("order-008", "store-008", "customer-008");
        var customer = customer("customer-008", null);

        when(userProfileRepo.findById("customer-008")).thenReturn(Optional.of(customer));
        when(storeStage1CommissionRepo.findByStoreId("store-008")).thenReturn(null);

        handler.handleOrderUpdatedEvent(new OrderUpdatedEvent(this, order, "", store));

        verify(storeStage2CommissionRepo, never()).insert(any(StorePartnerStage2Commission.class));
    }

    @Test
    void handleOrderUpdatedEvent_skipsStage2Commission_whenStoreHasNoReferral() {
        var store = foodStore("store-009", null); // not referred
        var order = completedOrder("order-009", "store-009", "customer-009");
        var customer = customer("customer-009", null);

        when(userProfileRepo.findById("customer-009")).thenReturn(Optional.of(customer));

        handler.handleOrderUpdatedEvent(new OrderUpdatedEvent(this, order, "", store));

        verify(storeStage1CommissionRepo, never()).findByStoreId(anyString());
        verify(storeStage2CommissionRepo, never()).insert(any(StorePartnerStage2Commission.class));
    }

    @Test
    void handleOrderUpdatedEvent_handlesStage2DuplicateKeyGracefully() {
        var store = foodStore("store-010", "partner-rp-10");
        var order = completedOrder("order-010", "store-010", "customer-010");
        var customer = customer("customer-010", null);
        var stage1 = new StorePartnerStage1Commission(
                UUID.randomUUID().toString(), "store-010", "partner-rp-10",
                new BigDecimal("100.00"), ReferralCommissionStatus.PENDING, new Date()
        );

        when(userProfileRepo.findById("customer-010")).thenReturn(Optional.of(customer));
        when(storeStage1CommissionRepo.findByStoreId("store-010")).thenReturn(stage1);
        when(storeStage2CommissionRepo.insert(any(StorePartnerStage2Commission.class)))
                .thenThrow(new DuplicateKeyException("duplicate"));

        assertDoesNotThrow(() -> handler.handleOrderUpdatedEvent(new OrderUpdatedEvent(this, order, "", store)));
        verify(storeStage2CommissionRepo).insert(any(StorePartnerStage2Commission.class));
    }

    // -------------------------------------------------------------------------
    // RP-009: Payout wiring — generatePayoutForReferralPartner called after insert
    // -------------------------------------------------------------------------

    @Test
    void handleOrderUpdatedEvent_callsGeneratePayoutForReferralPartner_afterFoodCustomerCommissionInsert() {
        var store = foodStore("store-011", null);
        var order = completedOrder("order-011", "store-011", "customer-011");
        var customer = customer("customer-011", "partner-rp-11");

        when(userProfileRepo.findById("customer-011")).thenReturn(Optional.of(customer));
        when(storeStage1CommissionRepo.findByStoreId("store-011")).thenReturn(null);

        handler.handleOrderUpdatedEvent(new OrderUpdatedEvent(this, order, "", store));

        verify(reconService).generatePayoutForReferralPartner(
                "partner-rp-11",
                new BigDecimal("15.00"),
                ReferralCommissionType.FOOD_CUSTOMER_REFERRAL,
                "customer-011"
        );
    }

    @Test
    void handleOrderUpdatedEvent_doesNotCallGeneratePayoutForReferralPartner_whenCustomerCommissionInsertFails_DuplicateKey() {
        // When DuplicateKeyException is thrown, the payout call must NOT happen
        var store = foodStore("store-012", null);
        var order = completedOrder("order-012", "store-012", "customer-012");
        var customer = customer("customer-012", "partner-rp-12");

        when(userProfileRepo.findById("customer-012")).thenReturn(Optional.of(customer));
        when(storeStage1CommissionRepo.findByStoreId("store-012")).thenReturn(null);
        when(foodCustomerCommissionRepo.insert(any(FoodCustomerReferralCommission.class)))
                .thenThrow(new DuplicateKeyException("duplicate"));

        assertDoesNotThrow(() -> handler.handleOrderUpdatedEvent(new OrderUpdatedEvent(this, order, "", store)));
        verify(reconService, never()).generatePayoutForReferralPartner(
                anyString(), any(BigDecimal.class), eq(ReferralCommissionType.FOOD_CUSTOMER_REFERRAL), anyString());
    }

    @Test
    void handleOrderUpdatedEvent_callsGeneratePayoutForReferralPartner_afterStage2CommissionInsert() {
        var store = foodStore("store-013", "partner-rp-13");
        var order = completedOrder("order-013", "store-013", "customer-013");
        var customer = customer("customer-013", null);
        var stage1 = new StorePartnerStage1Commission(
                UUID.randomUUID().toString(), "store-013", "partner-rp-13",
                new BigDecimal("100.00"), ReferralCommissionStatus.PENDING, new Date()
        );

        when(userProfileRepo.findById("customer-013")).thenReturn(Optional.of(customer));
        when(storeStage1CommissionRepo.findByStoreId("store-013")).thenReturn(stage1);

        handler.handleOrderUpdatedEvent(new OrderUpdatedEvent(this, order, "", store));

        verify(reconService).generatePayoutForReferralPartner(
                "partner-rp-13",
                new BigDecimal("150.00"),
                ReferralCommissionType.STORE_PARTNER_STAGE_2,
                "store-013"
        );
    }

    // -------------------------------------------------------------------------
    // RP-012: Furniture customer referral commission (5% of Total Delivery Charge)
    // -------------------------------------------------------------------------

    /**
     * Worked example from Schedule 1 Clause 2:
     *   R500.00 base fee × 1.065 = R532.50 total → × 0.05 = R26.625 → HALF_UP → R26.63
     */
    @Test
    void handleOrderUpdatedEvent_createsFurnitureCommission_correctAmount_workedExample() {
        var store = moversStore("mstore-001");
        var order = completedMoversOrder("morder-001", "mstore-001", "mcustomer-001", 500.00);
        var customer = customer("mcustomer-001", "partner-rp-m1");

        when(userProfileRepo.findById("mcustomer-001")).thenReturn(Optional.of(customer));

        handler.handleOrderUpdatedEvent(new OrderUpdatedEvent(this, order, "", store));

        ArgumentCaptor<FurnitureCustomerReferralCommission> captor =
                ArgumentCaptor.forClass(FurnitureCustomerReferralCommission.class);
        verify(furnitureCustomerCommissionRepo).insert(captor.capture());
        var commission = captor.getValue();
        assertEquals("mcustomer-001", commission.getCustomerId());
        assertEquals("partner-rp-m1", commission.getReferralPartnerId());
        assertEquals("morder-001", commission.getTriggeringOrderId());
        // R500 × 1.065 × 0.05 = R26.625 → HALF_UP → R26.63 (NOT R26.62 which HALF_EVEN would give)
        assertEquals(new BigDecimal("26.63"), commission.getAmount());
        assertEquals(ReferralCommissionStatus.PENDING, commission.getStatus());
    }

    /**
     * Edge case: R0 base delivery fee — commission is R0.00 but the record is still persisted.
     */
    @Test
    void handleOrderUpdatedEvent_createsFurnitureCommission_zeroBaseFee_persistsZeroAmount() {
        var store = moversStore("mstore-002");
        var order = completedMoversOrder("morder-002", "mstore-002", "mcustomer-002", 0.00);
        var customer = customer("mcustomer-002", "partner-rp-m2");

        when(userProfileRepo.findById("mcustomer-002")).thenReturn(Optional.of(customer));

        handler.handleOrderUpdatedEvent(new OrderUpdatedEvent(this, order, "", store));

        ArgumentCaptor<FurnitureCustomerReferralCommission> captor =
                ArgumentCaptor.forClass(FurnitureCustomerReferralCommission.class);
        verify(furnitureCustomerCommissionRepo).insert(captor.capture());
        assertEquals(new BigDecimal("0.00"), captor.getValue().getAmount());
    }

    /**
     * Duplicate STAGE_7_ALL_PAID event for the same customer — DuplicateKeyException must be
     * caught and logged as INFO; no exception propagates and no second payout is created.
     */
    @Test
    void handleOrderUpdatedEvent_handlesFornitureCommissionDuplicateKeyGracefully_noExceptionNoSecondPayout() {
        var store = moversStore("mstore-003");
        var order = completedMoversOrder("morder-003", "mstore-003", "mcustomer-003", 500.00);
        var customer = customer("mcustomer-003", "partner-rp-m3");

        when(userProfileRepo.findById("mcustomer-003")).thenReturn(Optional.of(customer));
        when(furnitureCustomerCommissionRepo.insert(any(FurnitureCustomerReferralCommission.class)))
                .thenThrow(new DuplicateKeyException("duplicate"));

        assertDoesNotThrow(() -> handler.handleOrderUpdatedEvent(new OrderUpdatedEvent(this, order, "", store)));
        verify(furnitureCustomerCommissionRepo).insert(any(FurnitureCustomerReferralCommission.class));
        // payout must not be called when insert throws DuplicateKeyException
        verify(reconService, never()).generatePayoutForReferralPartner(
                anyString(), any(BigDecimal.class), eq(ReferralCommissionType.FURNITURE_CUSTOMER_REFERRAL), anyString());
    }

    /**
     * Null shippingData — log warn and skip; no insert, no exception.
     */
    @Test
    void handleOrderUpdatedEvent_skipsFurnitureCommission_whenShippingDataIsNull() {
        var store = moversStore("mstore-004");
        var order = completedMoversOrderNullShipping("morder-004", "mstore-004", "mcustomer-004");
        var customer = customer("mcustomer-004", "partner-rp-m4");

        when(userProfileRepo.findById("mcustomer-004")).thenReturn(Optional.of(customer));

        assertDoesNotThrow(() -> handler.handleOrderUpdatedEvent(new OrderUpdatedEvent(this, order, "", store)));
        verify(furnitureCustomerCommissionRepo, never()).insert(any(FurnitureCustomerReferralCommission.class));
    }

    /**
     * Customer has no referredByPartnerId — skip silently, no commission, no payout.
     */
    @Test
    void handleOrderUpdatedEvent_skipsFurnitureCommission_whenCustomerHasNoReferral() {
        var store = moversStore("mstore-005");
        var order = completedMoversOrder("morder-005", "mstore-005", "mcustomer-005", 500.00);
        var customer = customer("mcustomer-005", null); // no referral

        when(userProfileRepo.findById("mcustomer-005")).thenReturn(Optional.of(customer));

        handler.handleOrderUpdatedEvent(new OrderUpdatedEvent(this, order, "", store));

        verify(furnitureCustomerCommissionRepo, never()).insert(any(FurnitureCustomerReferralCommission.class));
        verify(reconService, never()).generatePayoutForReferralPartner(
                anyString(), any(BigDecimal.class), eq(ReferralCommissionType.FURNITURE_CUSTOMER_REFERRAL), anyString());
    }

    /**
     * Customer not found in DB — log warn and skip; no commission, no exception.
     */
    @Test
    void handleOrderUpdatedEvent_skipsFurnitureCommission_whenCustomerNotFound() {
        var store = moversStore("mstore-006");
        var order = completedMoversOrder("morder-006", "mstore-006", "mcustomer-006", 500.00);

        when(userProfileRepo.findById("mcustomer-006")).thenReturn(Optional.empty());

        assertDoesNotThrow(() -> handler.handleOrderUpdatedEvent(new OrderUpdatedEvent(this, order, "", store)));
        verify(furnitureCustomerCommissionRepo, never()).insert(any(FurnitureCustomerReferralCommission.class));
    }

    /**
     * Happy path: after successful insert, generatePayoutForReferralPartner is called with
     * FURNITURE_CUSTOMER_REFERRAL type and the computed amount.
     */
    @Test
    void handleOrderUpdatedEvent_callsGeneratePayoutForReferralPartner_afterFurnitureCommissionInsert() {
        var store = moversStore("mstore-007");
        var order = completedMoversOrder("morder-007", "mstore-007", "mcustomer-007", 500.00);
        var customer = customer("mcustomer-007", "partner-rp-m7");

        when(userProfileRepo.findById("mcustomer-007")).thenReturn(Optional.of(customer));

        handler.handleOrderUpdatedEvent(new OrderUpdatedEvent(this, order, "", store));

        verify(reconService).generatePayoutForReferralPartner(
                "partner-rp-m7",
                new BigDecimal("26.63"),
                ReferralCommissionType.FURNITURE_CUSTOMER_REFERRAL,
                "mcustomer-007"
        );
    }

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    private StoreProfile foodStore(String storeId, String referredByPartnerId) {
        Bank bank = new Bank();
        bank.setAccountId("acc-1");
        ArrayList<BusinessHours> hours = new ArrayList<>();
        hours.add(new BusinessHours(DayOfWeek.MONDAY, new Date(), new Date()));
        ArrayList<String> tags = new ArrayList<>();
        tags.add("food");
        var store = new StoreProfile(
                StoreType.FOOD, "Food Store", "food-store-" + storeId,
                "1 Food St", "https://img.test/s.png", "0811111111",
                tags, hours, "owner-001", bank
        );
        store.setId(storeId);
        store.setReferredByPartnerId(referredByPartnerId);
        store.setProfileApproved(true);
        return store;
    }

    private StoreProfile nonFoodStore(String storeId) {
        Bank bank = new Bank();
        bank.setAccountId("acc-1");
        ArrayList<BusinessHours> hours = new ArrayList<>();
        hours.add(new BusinessHours(DayOfWeek.MONDAY, new Date(), new Date()));
        ArrayList<String> tags = new ArrayList<>();
        tags.add("clothing");
        var store = new StoreProfile(
                StoreType.CLOTHING, "Clothing Store", "clothing-store-" + storeId,
                "1 Fashion St", "https://img.test/s.png", "0822222222",
                tags, hours, "owner-002", bank
        );
        store.setId(storeId);
        return store;
    }

    private Order completedOrder(String orderId, String storeId, String customerId) {
        return orderAtStage(orderId, storeId, customerId, OrderStage.STAGE_7_ALL_PAID);
    }

    private Order orderAtStage(String orderId, String storeId, String customerId, OrderStage stage) {
        var basket = new Basket();
        basket.setItems(new ArrayList<>());
        var shippingData = new ShippingData("1 From St", "1 To St",
                ShippingData.ShippingType.DELIVERY);
        shippingData.setDistance(5.0);
        var order = new Order();
        order.setId(orderId);
        order.setCustomerId(customerId);
        order.setShopId(storeId);
        order.setBasket(basket);
        order.setShippingData(shippingData);
        order.setStage(stage);
        order.addStatusHistory(stage);
        return order;
    }

    private UserProfile customer(String customerId, String referredByPartnerId) {
        var p = new UserProfile("Customer", UserProfile.SignUpReason.BUY,
                "1 Customer St", "https://img.test/c.png", "0831234567", ProfileRoles.CUSTOMER);
        p.setId(customerId);
        p.setReferredByPartnerId(referredByPartnerId);
        return p;
    }

    private StoreProfile moversStore(String storeId) {
        Bank bank = new Bank();
        bank.setAccountId("acc-m");
        ArrayList<BusinessHours> hours = new ArrayList<>();
        hours.add(new BusinessHours(DayOfWeek.MONDAY, new Date(), new Date()));
        ArrayList<String> tags = new ArrayList<>();
        tags.add("movers");
        var store = new StoreProfile(
                StoreType.MOVERS, "Movers Store", "movers-" + storeId,
                "1 Mover St", "https://img.test/m.png", "0844444444",
                tags, hours, "owner-m", bank
        );
        store.setId(storeId);
        store.setProfileApproved(true);
        return store;
    }

    private Order completedMoversOrder(String orderId, String storeId, String customerId, double baseFee) {
        var basket = new Basket();
        basket.setItems(new ArrayList<>());
        var shippingData = new ShippingData("1 From St", "1 To St",
                ShippingData.ShippingType.DELIVERY);
        shippingData.setDistance(10.0);
        shippingData.setDeliveryFee(baseFee); // fee = deliveryFee + weigthFee + volumeFee + labourFee
        var order = new Order();
        order.setId(orderId);
        order.setCustomerId(customerId);
        order.setShopId(storeId);
        order.setBasket(basket);
        order.setShippingData(shippingData);
        order.setStage(OrderStage.STAGE_7_ALL_PAID);
        order.addStatusHistory(OrderStage.STAGE_7_ALL_PAID);
        return order;
    }

    private Order completedMoversOrderNullShipping(String orderId, String storeId, String customerId) {
        var basket = new Basket();
        basket.setItems(new ArrayList<>());
        var order = new Order();
        order.setId(orderId);
        order.setCustomerId(customerId);
        order.setShopId(storeId);
        order.setBasket(basket);
        order.setShippingData(null);
        order.setStage(OrderStage.STAGE_7_ALL_PAID);
        order.addStatusHistory(OrderStage.STAGE_7_ALL_PAID);
        return order;
    }
}
