package io.curiousoft.izinga.ordermanagement.service.order;

import io.curiousoft.izinga.commons.model.*;
import io.curiousoft.izinga.commons.order.OrderRepository;
import io.curiousoft.izinga.commons.repo.DeviceRepository;
import io.curiousoft.izinga.commons.repo.StoreRepository;
import io.curiousoft.izinga.commons.repo.UserProfileRepo;
import io.curiousoft.izinga.commons.security.AgentIdentityAuditService;
import io.curiousoft.izinga.commons.security.CallerIdentityContext;
import io.curiousoft.izinga.commons.security.CallerIdentityViolationException;
import io.curiousoft.izinga.messaging.AdminOnlyNotificationService;
import io.curiousoft.izinga.messaging.firebase.FirebaseNotificationService;
import io.curiousoft.izinga.messaging.whatsapp.lines.Audience;
import io.curiousoft.izinga.ordermanagement.leads.LeadService;
import io.curiousoft.izinga.ordermanagement.notification.EmailNotificationService;
import io.curiousoft.izinga.ordermanagement.orders.OrderServiceImpl;
import io.curiousoft.izinga.ordermanagement.utils.DeliveryCalculationService;
import io.curiousoft.izinga.ordermanagement.orders.RestrictedRegionService;
import io.curiousoft.izinga.ordermanagement.orders.quote.OrderQuoteRepository;
import io.curiousoft.izinga.ordermanagement.promocodes.PromoCodeClient;
import io.curiousoft.izinga.ordermanagement.security.StoreScopeContext;
import io.curiousoft.izinga.ordermanagement.service.paymentverify.PaymentService;
import org.assertj.core.util.Lists;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

/**
 * ADR-024 / SEC-MCP-01/02: Caller identity enforcement tests for MCP tool methods in OrderServiceImpl.
 *
 * Key invariants verified:
 * - CUSTOMER and DRIVER audiences receive identity enforcement.
 * - STORE audience is a no-op (regression test).
 * - SA-022-01: startOrder blocks third-party customerId for CUSTOMER audience.
 * - Audit writes are dispatched on every violation.
 * - SEC-MCP-01: null-audience on MCP path is hard-rejected.
 * - SEC-MCP-02 CORRECTION 2: find_orders_by_messenger_id scope gates.
 */
@ExtendWith(MockitoExtension.class)
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
    @Mock private DeliveryCalculationService deliveryCalculationService;

    private OrderServiceImpl sut;

    private static final String CALLER_PHONE = "+27812345678";
    private static final String CALLER_LAST9 = "812345678";
    private static final String CALLER_USER_ID = "caller-user-id";
    private static final String OTHER_USER_ID = "other-user-id";

    @BeforeEach
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
        ReflectionTestUtils.setField(sut, "agentIdentityAuditService", agentIdentityAuditService);
        ReflectionTestUtils.setField(sut, "deliveryCalculationService", deliveryCalculationService);
    }

    @AfterEach
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
        o.setShopId("shop-1");
        ShippingData sd = new ShippingData("From", "To", ShippingData.ShippingType.DELIVERY);
        sd.setMessengerId(CALLER_USER_ID);
        o.setShippingData(sd);
        return o;
    }

    private Order orderOwnedByOther() {
        Order o = new Order();
        o.setId("order-2");
        o.setCustomerId(OTHER_USER_ID);
        o.setShopId("shop-1");
        ShippingData sd = new ShippingData("From", "To", ShippingData.ShippingType.DELIVERY);
        sd.setMessengerId(OTHER_USER_ID);
        o.setShippingData(sd);
        return o;
    }

    private void stubCallerPhoneResolution() {
        when(userProfileRepo.findByMobileNumber("0" + CALLER_LAST9)).thenReturn(null);
        when(userProfileRepo.findByMobileNumber("+27" + CALLER_LAST9)).thenReturn(callerProfile());
    }

    // ===== findOrderByPhone =====

    @Test
    public void findOrderByPhone_customerAudience_ownPhone_passes() {
        StoreScopeContext.setAudience(Audience.CUSTOMER);
        StoreScopeContext.setCallerPhone(CALLER_PHONE);
        // stubCallerPhoneResolution stubs "0"+last9 → null, "+27"+last9 → callerProfile.
        // Do NOT re-stub "0"+last9 here — that would shadow the first stub and cause
        // UnnecessaryStubbingException under Mockito strict mode.
        stubCallerPhoneResolution();
        when(orderRepo.findByCustomerId(CALLER_USER_ID)).thenReturn(Optional.of(new ArrayList<>()));

        sut.findOrderByPhone(CALLER_PHONE);
        // No exception → pass
    }

    @Test
    public void findOrderByPhone_customerAudience_thirdPartyPhone_throws() {
        StoreScopeContext.setAudience(Audience.CUSTOMER);
        StoreScopeContext.setCallerPhone(CALLER_PHONE);

        assertThrows(CallerIdentityViolationException.class, () ->
                sut.findOrderByPhone("+27811111111"));
    }

    @Test
    public void findOrderByPhone_storeAudience_thirdPartyPhone_noOp() {
        StoreScopeContext.setAudience(Audience.STORE);
        StoreScopeContext.setCallerPhone(CALLER_PHONE);
        when(userProfileRepo.findByMobileNumber(anyString())).thenReturn(null);
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

    @Test
    public void findOrder_customerAudience_thirdPartyOrder_throws() {
        StoreScopeContext.setAudience(Audience.CUSTOMER);
        StoreScopeContext.setCallerPhone(CALLER_PHONE);
        stubCallerPhoneResolution();
        when(orderRepo.findById("order-2")).thenReturn(Optional.of(orderOwnedByOther()));

        assertThrows(CallerIdentityViolationException.class, () ->
                sut.findOrder("order-2"));
    }

    @Test
    public void findOrder_driverAudience_assignedOrder_passes() {
        StoreScopeContext.setAudience(Audience.DRIVER);
        StoreScopeContext.setCallerPhone(CALLER_PHONE);
        stubCallerPhoneResolution();
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

    @Test
    public void findOrder_driverAudience_notAssignedOrder_throws() {
        StoreScopeContext.setAudience(Audience.DRIVER);
        StoreScopeContext.setCallerPhone(CALLER_PHONE);
        stubCallerPhoneResolution();
        when(orderRepo.findById("order-2")).thenReturn(Optional.of(orderOwnedByOther()));

        assertThrows(CallerIdentityViolationException.class, () ->
                sut.findOrder("order-2"));
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

    @Test
    public void findOrderByUserId_customerAudience_thirdPartyId_throws() {
        StoreScopeContext.setAudience(Audience.CUSTOMER);
        StoreScopeContext.setCallerPhone(CALLER_PHONE);
        stubCallerPhoneResolution();

        assertThrows(CallerIdentityViolationException.class, () ->
                sut.findOrderByUserId(OTHER_USER_ID));
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
        o.setShopId("shop-1");
        ShippingData sd = new ShippingData("From", "To", ShippingData.ShippingType.DELIVERY);
        sd.setMessengerId(CALLER_USER_ID);
        o.setShippingData(sd);
        o.setBasket(new Basket());
        when(orderRepo.findById("order-1")).thenReturn(Optional.of(o));

        sut.findOrderForMessenger("order-1");
        // No exception → pass
    }

    @Test
    public void findOrderForMessenger_driverAudience_notAssignedOrder_throws() {
        StoreScopeContext.setAudience(Audience.DRIVER);
        StoreScopeContext.setCallerPhone(CALLER_PHONE);
        stubCallerPhoneResolution();
        Order o = orderOwnedByOther();
        o.setBasket(new Basket());
        when(orderRepo.findById("order-2")).thenReturn(Optional.of(o));

        assertThrows(CallerIdentityViolationException.class, () ->
                sut.findOrderForMessenger("order-2"));
    }

    // ===== findOrderForMessenger — CUSTOMER audience (FIX-03) =====

    @Test
    public void findOrderForMessenger_customerAudience_ownOrder_passes() {
        StoreScopeContext.setAudience(Audience.CUSTOMER);
        StoreScopeContext.setCallerPhone(CALLER_PHONE);
        stubCallerPhoneResolution();
        Order o = orderOwnedByCaller();
        o.setBasket(new Basket());
        when(orderRepo.findById("order-1")).thenReturn(Optional.of(o));

        sut.findOrderForMessenger("order-1");
        // No exception → CUSTOMER can access their own order
    }

    @Test
    public void findOrderForMessenger_customerAudience_thirdPartyOrder_throws() {
        StoreScopeContext.setAudience(Audience.CUSTOMER);
        StoreScopeContext.setCallerPhone(CALLER_PHONE);
        stubCallerPhoneResolution();
        Order o = orderOwnedByOther();
        o.setBasket(new Basket());
        when(orderRepo.findById("order-2")).thenReturn(Optional.of(o));

        assertThrows(CallerIdentityViolationException.class, () ->
                sut.findOrderForMessenger("order-2"));
    }

    @Test
    public void findOrderForMessenger_storeAudience_anyOrder_noOp() {
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

    // ===== NOTE-03: null resolved callerId is a hard reject (null == null must NOT pass) =====

    @Test
    public void findOrder_driverAudience_unresolvedCaller_nullMessengerId_throws() {
        // DRIVER audience set but phone cannot be resolved to any user profile
        StoreScopeContext.setAudience(Audience.DRIVER);
        StoreScopeContext.setCallerPhone("+27899999999");
        // All three prefix lookups return null → callerId resolves to null
        when(userProfileRepo.findByMobileNumber("0899999999")).thenReturn(null);
        when(userProfileRepo.findByMobileNumber("+27899999999")).thenReturn(null);
        when(userProfileRepo.findByMobileNumber("27899999999")).thenReturn(null);

        // Order has no messenger assigned (null shippingData → messengerId = null)
        Order o = new Order();
        o.setId("order-1");
        o.setCustomerId(OTHER_USER_ID);
        o.setShippingData(null);
        when(orderRepo.findById("order-1")).thenReturn(Optional.of(o));

        // NOTE-03: null callerId must throw even if id is also null (null == null must NOT pass)
        assertThrows(CallerIdentityViolationException.class, () ->
                sut.findOrder("order-1"));
    }

    @Test
    public void findOrderForMessenger_driverAudience_unresolvedCaller_nullMessengerId_throws() {
        StoreScopeContext.setAudience(Audience.DRIVER);
        StoreScopeContext.setCallerPhone("+27899999999");
        when(userProfileRepo.findByMobileNumber("0899999999")).thenReturn(null);
        when(userProfileRepo.findByMobileNumber("+27899999999")).thenReturn(null);
        when(userProfileRepo.findByMobileNumber("27899999999")).thenReturn(null);

        Order o = new Order();
        o.setId("order-1");
        o.setCustomerId(OTHER_USER_ID);
        o.setShippingData(null);
        o.setBasket(new Basket());
        when(orderRepo.findById("order-1")).thenReturn(Optional.of(o));

        // NOTE-03: null callerId must throw even if id is also null
        assertThrows(CallerIdentityViolationException.class, () ->
                sut.findOrderForMessenger("order-1"));
    }

    // ===== findOrder — SEC-MCP-01: null-audience hard reject =====

    @Test
    public void findOrder_mcpNullAudience_throws() {
        // After FIX-03 the tokenless guard is FIRST — orderRepo is never consulted.
        CallerIdentityContext.setMcpRequest(true);

        assertThrows(CallerIdentityViolationException.class, () ->
                sut.findOrder("order-1"));
        verify(orderRepo, never()).findById(anyString());
    }

    @Test
    public void findOrder_mcpNullAudience_rejectionMessageContainsIdentityRefusal() {
        // After FIX-03 the tokenless guard is FIRST — orderRepo is never consulted.
        CallerIdentityContext.setMcpRequest(true);

        CallerIdentityViolationException ex = assertThrows(CallerIdentityViolationException.class, () ->
                sut.findOrder("order-1"));
        assertTrue(ex.getMessage().startsWith("IDENTITY_REFUSAL"),
                "Message must start with IDENTITY_REFUSAL");
        verify(orderRepo, never()).findById(anyString());
    }

    /**
     * SEC-MCP-BUG-01 regression: tokenless MCP call with a NON-EXISTENT order id must throw
     * IDENTITY_REFUSAL and must NOT call orderRepo.findById at all.
     */
    @Test
    public void findOrder_mcpNullAudience_nonExistentOrderId_throwsAndNeverCallsRepo() {
        CallerIdentityContext.setMcpRequest(true);
        // No stub for orderRepo.findById — if reached it would return empty Optional (Mockito default).

        CallerIdentityViolationException ex = assertThrows(CallerIdentityViolationException.class, () ->
                sut.findOrder("non-existent-order-id"));
        assertTrue(ex.getMessage().startsWith("IDENTITY_REFUSAL"),
                "Message must start with IDENTITY_REFUSAL for non-existent order on tokenless MCP path");
        verify(orderRepo, never()).findById(anyString());
    }

    @Test
    public void findOrder_nonMcpNullAudience_passes() {
        when(orderRepo.findById("order-1")).thenReturn(Optional.of(orderOwnedByCaller()));
        sut.findOrder("order-1"); // must not throw
    }

    /**
     * REST regression: non-MCP call with a missing order id must return null (not throw).
     */
    @Test
    public void findOrder_nonMcp_missingOrder_returnsNull() {
        when(orderRepo.findById("missing-order")).thenReturn(Optional.empty());
        Order result = sut.findOrder("missing-order");
        assertNull(result, "findOrder must return null for a missing order on non-MCP path");
    }

    // ===== findOrderForMessenger — SEC-MCP-01: null-audience hard reject =====

    @Test
    public void findOrderForMessenger_mcpNullAudience_throws() {
        // After FIX-04 the tokenless guard is FIRST — orderRepo is never consulted.
        CallerIdentityContext.setMcpRequest(true);

        assertThrows(CallerIdentityViolationException.class, () ->
                sut.findOrderForMessenger("order-1"));
        verify(orderRepo, never()).findById(anyString());
    }

    /**
     * SEC-MCP-BUG-01 regression: tokenless MCP call with a NON-EXISTENT order id must throw
     * IDENTITY_REFUSAL and must NOT call orderRepo.findById at all.
     */
    @Test
    public void findOrderForMessenger_mcpNullAudience_nonExistentOrderId_throwsAndNeverCallsRepo() {
        CallerIdentityContext.setMcpRequest(true);
        // No stub — if orderRepo.findById were reached it would throw (empty Optional with orElseThrow).

        CallerIdentityViolationException ex = assertThrows(CallerIdentityViolationException.class, () ->
                sut.findOrderForMessenger("non-existent-order-id"));
        assertTrue(ex.getMessage().startsWith("IDENTITY_REFUSAL"),
                "Message must start with IDENTITY_REFUSAL for non-existent order on tokenless MCP path");
        verify(orderRepo, never()).findById(anyString());
    }

    @Test
    public void findOrderForMessenger_nonMcpNullAudience_passes() {
        Order o = orderOwnedByCaller();
        o.setBasket(new Basket());
        when(orderRepo.findById("order-1")).thenReturn(Optional.of(o));
        sut.findOrderForMessenger("order-1"); // must not throw
    }

    // ===== findOrderByMessengerId — SEC-MCP-02 CORRECTION 2 =====

    @Test
    public void findOrderByMessengerId_mcpNullAudience_throws() {
        CallerIdentityContext.setMcpRequest(true);

        assertThrows(CallerIdentityViolationException.class, () ->
                sut.findOrderByMessengerId(CALLER_USER_ID, false));
    }

    @Test
    public void findOrderByMessengerId_mcpCustomerAudience_throws() {
        CallerIdentityContext.setMcpRequest(true);
        StoreScopeContext.setAudience(Audience.CUSTOMER);
        StoreScopeContext.setCallerPhone(CALLER_PHONE);

        assertThrows(CallerIdentityViolationException.class, () ->
                sut.findOrderByMessengerId(CALLER_USER_ID, false));
    }

    @Test
    public void findOrderByMessengerId_driverOwnId_passes() {
        CallerIdentityContext.setMcpRequest(true);
        StoreScopeContext.setAudience(Audience.DRIVER);
        StoreScopeContext.setCallerPhone(CALLER_PHONE);
        stubCallerPhoneResolution();
        when(orderRepo.findByShippingDataMessengerIdAndStageNot(CALLER_USER_ID, OrderStage.STAGE_0_CUSTOMER_NOT_PAID))
                .thenReturn(new ArrayList<>());
        when(orderQuoteRepository.findBySentToMessengerIds(CALLER_USER_ID)).thenReturn(new ArrayList<>());

        sut.findOrderByMessengerId(CALLER_USER_ID, false); // must not throw
    }

    @Test
    public void findOrderByMessengerId_driverOtherUserId_throws() {
        CallerIdentityContext.setMcpRequest(true);
        StoreScopeContext.setAudience(Audience.DRIVER);
        StoreScopeContext.setCallerPhone(CALLER_PHONE);
        stubCallerPhoneResolution();

        assertThrows(CallerIdentityViolationException.class, () ->
                sut.findOrderByMessengerId(OTHER_USER_ID, false));
    }

    /**
     * AMD-01 (REQ-A7): ADMIN audience passes findOrderByMessengerId without any ownership check.
     * Both orders from any store should be returned (unfiltered).
     */
    @Test
    public void findOrderByMessengerId_adminAudience_passes() {
        CallerIdentityContext.setMcpRequest(true);
        StoreScopeContext.setAudience(Audience.ADMIN);

        when(orderRepo.findByShippingDataMessengerIdAndStageNot("messenger-1", OrderStage.STAGE_0_CUSTOMER_NOT_PAID))
                .thenReturn(new ArrayList<>());
        when(orderQuoteRepository.findBySentToMessengerIds("messenger-1")).thenReturn(new ArrayList<>());

        sut.findOrderByMessengerId("messenger-1", false); // must not throw
        // no ownership check — userProfileRepo must not be queried
        verify(userProfileRepo, never()).findByMobileNumber(anyString());
    }

    /**
     * AMD-01 (REQ-A7): STORE audience is now a hard reject on findOrderByMessengerId.
     * Previously: STORE with permittedStoreId passed. Now: hard reject regardless.
     */
    @Test
    public void findOrderByMessengerId_storeAudience_nowRejects() {
        CallerIdentityContext.setMcpRequest(true);
        StoreScopeContext.setAudience(Audience.STORE);
        // Even with a permittedStoreId set, STORE must be rejected
        StoreScopeContext.setPermittedStoreId("permitted-store-1");

        assertThrows(CallerIdentityViolationException.class, () ->
                sut.findOrderByMessengerId("messenger-1", false));
    }

    @Test
    public void findOrderByMessengerId_nonMcpRequest_noGate() {
        when(orderRepo.findByShippingDataMessengerIdAndStageNot("messenger-1", OrderStage.STAGE_0_CUSTOMER_NOT_PAID))
                .thenReturn(new ArrayList<>());
        when(orderQuoteRepository.findBySentToMessengerIds("messenger-1")).thenReturn(new ArrayList<>());

        sut.findOrderByMessengerId("messenger-1", false); // must not throw
    }

    // ===== AMD-01 (REQ-A10): ADMIN audience passes without identity enforcement =====

    /**
     * AMD-01 (REQ-A10): ADMIN audience passes find_order_by_id for any order (no ownership check).
     */
    @Test
    public void findOrder_adminAudience_anyOrder_passes() {
        CallerIdentityContext.setMcpRequest(true);
        StoreScopeContext.setAudience(Audience.ADMIN);
        when(orderRepo.findById("order-2")).thenReturn(Optional.of(orderOwnedByOther()));

        sut.findOrder("order-2"); // must not throw — ADMIN has no ownership restriction
        verify(userProfileRepo, never()).findByMobileNumber(anyString());
    }

    /**
     * AMD-01 (REQ-A10): ADMIN audience passes find_order_for_messenger for any order.
     */
    @Test
    public void findOrderForMessenger_adminAudience_anyOrder_passes() {
        CallerIdentityContext.setMcpRequest(true);
        StoreScopeContext.setAudience(Audience.ADMIN);
        Order o = orderOwnedByOther();
        o.setBasket(new Basket());
        when(orderRepo.findById("order-2")).thenReturn(Optional.of(o));

        sut.findOrderForMessenger("order-2"); // must not throw
        verify(userProfileRepo, never()).findByMobileNumber(anyString());
    }

    /**
     * AMD-01 (REQ-A10): ADMIN audience passes find_orders_by_user_id for another user's id.
     */
    @Test
    public void findOrderByUserId_adminAudience_otherUserId_passes() {
        CallerIdentityContext.setMcpRequest(true);
        StoreScopeContext.setAudience(Audience.ADMIN);
        when(orderRepo.findByCustomerId(OTHER_USER_ID)).thenReturn(Optional.of(new ArrayList<>()));

        sut.findOrderByUserId(OTHER_USER_ID); // must not throw
        verify(userProfileRepo, never()).findByMobileNumber(anyString());
    }

    /**
     * AMD-01 (REQ-A10): ADMIN audience passes find_orders_by_phone_number for any phone.
     */
    @Test
    public void findOrderByPhone_adminAudience_otherPhone_passes() {
        CallerIdentityContext.setMcpRequest(true);
        StoreScopeContext.setAudience(Audience.ADMIN);
        // Phone resolution stubs for the OTHER_PHONE, not CALLER_PHONE —
        // ADMIN early return fires before the lookup, so no stub is needed here.
        // Call must complete without CallerIdentityViolationException.
        when(userProfileRepo.findByMobileNumber(anyString())).thenReturn(null);
        try {
            sut.findOrderByPhone("+27811111111");
        } catch (IllegalArgumentException e) {
            // "User not found" is expected when there is no profile — not an identity violation
        } catch (CallerIdentityViolationException e) {
            fail("ADMIN audience must not trigger identity enforcement: " + e.getMessage());
        }
    }

    // ===== startOrder SA-022-01 =====

    @Test
    public void startOrder_customerAudience_thirdPartyCustomerId_throws() {
        StoreScopeContext.setAudience(Audience.CUSTOMER);
        StoreScopeContext.setCallerPhone(CALLER_PHONE);
        stubCallerPhoneResolution();
        when(userProfileRepo.existsById(OTHER_USER_ID)).thenReturn(true);

        Order order = new Order();
        order.setOrderType(OrderType.ONLINE);
        order.setCustomerId(OTHER_USER_ID);
        order.setShopId("shop-1");
        ShippingData sd = new ShippingData("From", "To", ShippingData.ShippingType.DELIVERY);
        order.setShippingData(sd);

        assertThrows(CallerIdentityViolationException.class, () ->
                sut.startOrder(order));
    }
}
