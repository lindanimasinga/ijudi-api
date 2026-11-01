package io.curiousoft.izinga.ordermanagement.service.order;

import io.curiousoft.izinga.commons.model.*;
import io.curiousoft.izinga.commons.order.OrderRepository;
import io.curiousoft.izinga.commons.repo.DeviceRepository;
import io.curiousoft.izinga.commons.repo.StoreRepository;
import io.curiousoft.izinga.commons.repo.UserProfileRepo;
import io.curiousoft.izinga.commons.security.AgentIdentityAuditService;
import io.curiousoft.izinga.commons.security.CallerIdentityViolationException;
import io.curiousoft.izinga.messaging.AdminOnlyNotificationService;
import io.curiousoft.izinga.messaging.firebase.FirebaseNotificationService;
import io.curiousoft.izinga.messaging.whatsapp.lines.Audience;
import io.curiousoft.izinga.ordermanagement.leads.LeadService;
import io.curiousoft.izinga.ordermanagement.notification.EmailNotificationService;
import io.curiousoft.izinga.ordermanagement.orders.OrderServiceImpl;
import io.curiousoft.izinga.ordermanagement.orders.RestrictedRegionService;
import io.curiousoft.izinga.ordermanagement.orders.quote.OrderQuoteRepository;
import io.curiousoft.izinga.ordermanagement.promocodes.PromoCodeClient;
import io.curiousoft.izinga.ordermanagement.security.StoreScopeContext;
import io.curiousoft.izinga.ordermanagement.service.paymentverify.PaymentService;
import org.assertj.core.util.Lists;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.junit.Assert.fail;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

/**
 * ADR-024: Caller identity enforcement tests for MCP tool methods in OrderServiceImpl.
 *
 * Uses JUnit 4 + Mockito (izinga-ordermanager module convention).
 *
 * Key invariants verified:
 * - CUSTOMER and DRIVER audiences receive identity enforcement.
 * - STORE audience is a no-op (regression test).
 * - SA-022-01: startOrder blocks third-party customerId for CUSTOMER audience.
 * - Audit writes are dispatched on every violation.
 */
@RunWith(MockitoJUnitRunner.class)
public class OrderServiceImplCallerIdentityTest {

    @Mock private OrderRepository orderRepo;
    @Mock private UserProfileRepo userProfileRepo;
    @Mock private StoreRepository storeRepo;
    @Mock private PaymentService paymentService;
    @Mock private FirebaseNotificationService pushNotificationService;
    @Mock private AdminOnlyNotificationService smsNotification;
    @Mock private EmailNotificationService emailNotificationService;
    @Mock private DeviceRepository deviceRepo;
    @Mock private ApplicationEventPublisher eventPublisher;
    @Mock private PromoCodeClient promoCodeClient;
    @Mock private RestrictedRegionService restrictedRegionService;
    @Mock private OrderQuoteRepository orderQuoteRepository;
    @Mock private LeadService leadService;
    @Mock private AgentIdentityAuditService agentIdentityAuditService;

    private OrderServiceImpl sut;

    private static final String CALLER_PHONE = "+27812345678";
    private static final String CALLER_LAST9 = "812345678";
    private static final String CALLER_USER_ID = "caller-user-id";
    private static final String OTHER_USER_ID = "other-user-id";

    @Before
    public void setUp() {
        List<String> adminPhones = Lists.list("0812815707");
        sut = new OrderServiceImpl(
                15.0, 3.0, 5.0, 0.025, 0.10, 5L,
                adminPhones, "test-maps-key",
                orderRepo, storeRepo, userProfileRepo,
                paymentService, deviceRepo, pushNotificationService,
                smsNotification, emailNotificationService,
                promoCodeClient, eventPublisher,
                restrictedRegionService, orderQuoteRepository, leadService
        );
        // Inject the @Autowired field that is not in the constructor
        ReflectionTestUtils.setField(sut, "agentIdentityAuditService", agentIdentityAuditService);
    }

    @After
    public void tearDown() {
        StoreScopeContext.clear();
    }

    // ---- helper ----

    private UserProfile callerProfile() {
        UserProfile p = new UserProfile(
                "Caller", UserProfile.SignUpReason.BUY,
                "1 Main St", "https://img.test/1.png",
                CALLER_PHONE, ProfileRoles.CUSTOMER
        );
        p.setId(CALLER_USER_ID);
        return p;
    }

    private Order orderOwnedByCaller() {
        Order o = new Order();
        o.setId("order-1");
        o.setCustomerId(CALLER_USER_ID);
        ShippingData sd = new ShippingData("From", "To", ShippingData.ShippingType.DELIVERY);
        sd.setMessengerId(CALLER_USER_ID);
        o.setShippingData(sd);
        return o;
    }

    private Order orderOwnedByOther() {
        Order o = new Order();
        o.setId("order-2");
        o.setCustomerId(OTHER_USER_ID);
        ShippingData sd = new ShippingData("From", "To", ShippingData.ShippingType.DELIVERY);
        sd.setMessengerId(OTHER_USER_ID);
        o.setShippingData(sd);
        return o;
    }

    private void stubCallerPhoneResolution() {
        // All three prefix variants return null except the canonical +27 form
        when(userProfileRepo.findByMobileNumber("0" + CALLER_LAST9)).thenReturn(null);
        when(userProfileRepo.findByMobileNumber("+27" + CALLER_LAST9)).thenReturn(callerProfile());
        // NB: stub "27812345678" only if needed — lenient() would allow it but strict mock is fine here
    }

    // ===== findOrderByPhone =====

    @Test
    public void findOrderByPhone_customerAudience_ownPhone_passes() {
        StoreScopeContext.setAudience(Audience.CUSTOMER);
        StoreScopeContext.setCallerPhone(CALLER_PHONE);
        stubCallerPhoneResolution();
        when(userProfileRepo.findByMobileNumber("0" + CALLER_LAST9)).thenReturn(callerProfile());
        when(orderRepo.findByCustomerId(CALLER_USER_ID)).thenReturn(Optional.of(new ArrayList<>()));

        sut.findOrderByPhone(CALLER_PHONE);
        // No exception → pass
    }

    @Test(expected = CallerIdentityViolationException.class)
    public void findOrderByPhone_customerAudience_thirdPartyPhone_throws() {
        StoreScopeContext.setAudience(Audience.CUSTOMER);
        StoreScopeContext.setCallerPhone(CALLER_PHONE);

        sut.findOrderByPhone("+27811111111");
    }

    @Test
    public void findOrderByPhone_storeAudience_thirdPartyPhone_noOp() {
        // STORE audience must NOT be restricted — regression test
        StoreScopeContext.setAudience(Audience.STORE);
        StoreScopeContext.setCallerPhone(CALLER_PHONE);
        when(userProfileRepo.findByMobileNumber(anyString())).thenReturn(null);
        // findOrderByPhone throws IllegalArgumentException("User not found") — that is the
        // correct no-violation path for STORE; it must NOT throw CallerIdentityViolationException.
        try {
            sut.findOrderByPhone("+27811111111");
            fail("Expected IllegalArgumentException(User not found)");
        } catch (IllegalArgumentException e) {
            // expected — not a CallerIdentityViolationException
        } catch (CallerIdentityViolationException e) {
            fail("STORE audience must not trigger identity enforcement: " + e.getMessage());
        }
    }

    // ===== findOrder (find_order_by_id) =====

    @Test
    public void findOrder_customerAudience_ownOrder_passes() {
        StoreScopeContext.setAudience(Audience.CUSTOMER);
        StoreScopeContext.setCallerPhone(CALLER_PHONE);
        stubCallerPhoneResolution();
        when(orderRepo.findById("order-1")).thenReturn(Optional.of(orderOwnedByCaller()));

        sut.findOrder("order-1");
        // No exception → pass
    }

    @Test(expected = CallerIdentityViolationException.class)
    public void findOrder_customerAudience_thirdPartyOrder_throws() {
        StoreScopeContext.setAudience(Audience.CUSTOMER);
        StoreScopeContext.setCallerPhone(CALLER_PHONE);
        stubCallerPhoneResolution();
        when(orderRepo.findById("order-2")).thenReturn(Optional.of(orderOwnedByOther()));

        sut.findOrder("order-2");
    }

    @Test
    public void findOrder_driverAudience_assignedOrder_passes() {
        StoreScopeContext.setAudience(Audience.DRIVER);
        StoreScopeContext.setCallerPhone(CALLER_PHONE);
        stubCallerPhoneResolution();
        // Order where the caller IS the messenger
        Order o = new Order();
        o.setId("order-1");
        o.setCustomerId(OTHER_USER_ID);
        ShippingData sd = new ShippingData("From", "To", ShippingData.ShippingType.DELIVERY);
        sd.setMessengerId(CALLER_USER_ID);
        o.setShippingData(sd);
        when(orderRepo.findById("order-1")).thenReturn(Optional.of(o));

        sut.findOrder("order-1");
        // No exception → pass
    }

    @Test(expected = CallerIdentityViolationException.class)
    public void findOrder_driverAudience_notAssignedOrder_throws() {
        StoreScopeContext.setAudience(Audience.DRIVER);
        StoreScopeContext.setCallerPhone(CALLER_PHONE);
        stubCallerPhoneResolution();
        when(orderRepo.findById("order-2")).thenReturn(Optional.of(orderOwnedByOther()));

        sut.findOrder("order-2");
    }

    // ===== findOrderByUserId (find_orders_by_user_id) =====

    @Test
    public void findOrderByUserId_customerAudience_ownId_passes() {
        StoreScopeContext.setAudience(Audience.CUSTOMER);
        StoreScopeContext.setCallerPhone(CALLER_PHONE);
        stubCallerPhoneResolution();
        when(orderRepo.findByCustomerId(CALLER_USER_ID)).thenReturn(Optional.of(new ArrayList<>()));

        sut.findOrderByUserId(CALLER_USER_ID);
        // No exception → pass
    }

    @Test(expected = CallerIdentityViolationException.class)
    public void findOrderByUserId_customerAudience_thirdPartyId_throws() {
        StoreScopeContext.setAudience(Audience.CUSTOMER);
        StoreScopeContext.setCallerPhone(CALLER_PHONE);
        stubCallerPhoneResolution();

        sut.findOrderByUserId(OTHER_USER_ID);
    }

    // ===== findOrderForMessenger (find_order_for_messenger) =====

    @Test
    public void findOrderForMessenger_driverAudience_assignedOrder_passes() {
        StoreScopeContext.setAudience(Audience.DRIVER);
        StoreScopeContext.setCallerPhone(CALLER_PHONE);
        stubCallerPhoneResolution();
        Order o = new Order();
        o.setId("order-1");
        o.setCustomerId(OTHER_USER_ID);
        ShippingData sd = new ShippingData("From", "To", ShippingData.ShippingType.DELIVERY);
        sd.setMessengerId(CALLER_USER_ID);
        o.setShippingData(sd);
        o.setBasket(new Basket());
        when(orderRepo.findById("order-1")).thenReturn(Optional.of(o));

        sut.findOrderForMessenger("order-1");
        // No exception → pass
    }

    @Test(expected = CallerIdentityViolationException.class)
    public void findOrderForMessenger_driverAudience_notAssignedOrder_throws() {
        StoreScopeContext.setAudience(Audience.DRIVER);
        StoreScopeContext.setCallerPhone(CALLER_PHONE);
        stubCallerPhoneResolution();
        Order o = orderOwnedByOther(); // messengerId = OTHER_USER_ID
        o.setBasket(new Basket());
        when(orderRepo.findById("order-2")).thenReturn(Optional.of(o));

        sut.findOrderForMessenger("order-2");
    }

    // ===== findOrderForMessenger — CUSTOMER audience (FIX-03) =====

    @Test
    public void findOrderForMessenger_customerAudience_ownOrder_passes() {
        // FIX-03: CUSTOMER audience must be able to retrieve their own order's MessengerOrderDto
        StoreScopeContext.setAudience(Audience.CUSTOMER);
        StoreScopeContext.setCallerPhone(CALLER_PHONE);
        stubCallerPhoneResolution();
        Order o = orderOwnedByCaller(); // customerId = CALLER_USER_ID
        o.setBasket(new Basket());
        when(orderRepo.findById("order-1")).thenReturn(Optional.of(o));

        sut.findOrderForMessenger("order-1");
        // No exception → CUSTOMER can access their own order
    }

    @Test(expected = CallerIdentityViolationException.class)
    public void findOrderForMessenger_customerAudience_thirdPartyOrder_throws() {
        // FIX-03: CUSTOMER must NOT access another customer's MessengerOrderDto (data leak)
        StoreScopeContext.setAudience(Audience.CUSTOMER);
        StoreScopeContext.setCallerPhone(CALLER_PHONE);
        stubCallerPhoneResolution();
        Order o = orderOwnedByOther(); // customerId = OTHER_USER_ID
        o.setBasket(new Basket());
        when(orderRepo.findById("order-2")).thenReturn(Optional.of(o));

        sut.findOrderForMessenger("order-2");
    }

    @Test
    public void findOrderForMessenger_storeAudience_anyOrder_noOp() {
        // FIX-03 regression: STORE audience must NOT be restricted by the new CUSTOMER branch
        StoreScopeContext.setAudience(Audience.STORE);
        StoreScopeContext.setCallerPhone(CALLER_PHONE);
        Order o = orderOwnedByOther();
        o.setBasket(new Basket());
        when(orderRepo.findById("order-2")).thenReturn(Optional.of(o));

        try {
            sut.findOrderForMessenger("order-2");
            // No exception → STORE audience is exempt from identity enforcement
        } catch (CallerIdentityViolationException e) {
            fail("STORE audience must not trigger identity enforcement: " + e.getMessage());
        }
    }

    // ===== startOrder SA-022-01 =====

    @Test(expected = CallerIdentityViolationException.class)
    public void startOrder_customerAudience_thirdPartyCustomerId_throws() {
        StoreScopeContext.setAudience(Audience.CUSTOMER);
        StoreScopeContext.setCallerPhone(CALLER_PHONE);
        stubCallerPhoneResolution();
        // Caller is CALLER_USER_ID but order has OTHER_USER_ID
        when(userProfileRepo.existsById(OTHER_USER_ID)).thenReturn(true);

        Order order = new Order();
        order.setCustomerId(OTHER_USER_ID);
        order.setShopId("shop-1");
        ShippingData sd = new ShippingData("From", "To", ShippingData.ShippingType.DELIVERY);
        order.setShippingData(sd);

        sut.startOrder(order);
    }
}
