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
    @Mock private StorePartnerStage1CommissionRepo storeStage1CommissionRepo;
    @Mock private StorePartnerStage2CommissionRepo storeStage2CommissionRepo;

    private StoreOrderEventHandler handler;

    @BeforeEach
    void setUp() {
        handler = new StoreOrderEventHandler(
                pushNotificationService, adminOnlyNotificationService, emailNotificationService,
                deviceService, userProfileService, reconService,
                userProfileRepo, foodCustomerCommissionRepo, storeStage1CommissionRepo, storeStage2CommissionRepo
        );
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
}
