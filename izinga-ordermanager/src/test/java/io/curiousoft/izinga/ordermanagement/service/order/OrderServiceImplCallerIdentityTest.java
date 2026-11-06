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
 * ADR-026 Phase 3: AC-04 service-layer ungated tests for OrderServiceImpl.
 *
 * <p>After Phase 3 migration, all five order-read @Tool gates (null-audience rejection,
 * CUSTOMER/DRIVER ownership enforcement, AMD-01 scope gates for findOrderByMessengerId)
 * have been moved from {@link OrderServiceImpl} into {@link
 * io.curiousoft.izinga.ordermanagement.mcp.OrderMcpController}.
 *
 * <p>These tests verify AC-04: calling the service with any MCP context, including
 * contexts that previously triggered identity gates, does NOT produce a
 * {@link CallerIdentityViolationException}. The service simply delegates to the
 * repository. Gate enforcement is covered in {@link
 * io.curiousoft.izinga.ordermanagement.mcp.OrderMcpControllerTest}.
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

    private static final String CALLER_PHONE  = "+27812345678";
    private static final String CALLER_USER_ID = "caller-user-id";
    private static final String OTHER_USER_ID  = "other-user-id";

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
        CallerIdentityContext.clear();
    }

    // ===== AC-04: findOrder is ungated =====================================================

    /**
     * AC-04: findOrder() must not enforce null-audience gate after Phase 3.
     * Gate was moved to OrderMcpController. Service delegates directly to the repo.
     */
    @Test
    public void findOrder_mcpNullAudience_serviceDoesNotThrowCive() {
        CallerIdentityContext.setMcpRequest(true);
        // No audience — this previously triggered null-audience gate in service
        when(orderRepo.findById("order-1")).thenReturn(Optional.of(new Order()));

        try {
            sut.findOrder("order-1");
        } catch (CallerIdentityViolationException e) {
            fail("ADR-026 Phase 3: service must not enforce null-audience gate. CIVE: "
                    + e.getMessage());
        } catch (Exception e) {
            // Other downstream failures (repo miss, missing data) are acceptable
        }
    }

    /**
     * AC-04: findOrder() must not enforce ownership gate after Phase 3.
     * CUSTOMER requesting another user's order previously threw CIVE in service.
     */
    @Test
    public void findOrder_mcpCustomerAudience_thirdPartyOrder_serviceDoesNotThrowCive() {
        CallerIdentityContext.setMcpRequest(true);
        CallerIdentityContext.setAudience(Audience.CUSTOMER.name());
        StoreScopeContext.setCallerPhone(CALLER_PHONE);

        Order order = new Order();
        order.setCustomerId(OTHER_USER_ID);
        when(orderRepo.findById("order-1")).thenReturn(Optional.of(order));

        try {
            sut.findOrder("order-1");
        } catch (CallerIdentityViolationException e) {
            fail("ADR-026 Phase 3: ownership gate moved to controller. Service must not throw CIVE: "
                    + e.getMessage());
        } catch (Exception e) {
            // OK
        }
    }

    @Test
    public void findOrder_nonMcp_delegatesToRepo() {
        Order order = new Order();
        order.setId("order-1");
        when(orderRepo.findById("order-1")).thenReturn(Optional.of(order));

        Order result = sut.findOrder("order-1");
        assertNotNull(result);
        verify(orderRepo).findById("order-1");
    }

    @Test
    public void findOrder_nonMcp_missingOrder_returnsNull() {
        when(orderRepo.findById("order-1")).thenReturn(Optional.empty());

        Order result = sut.findOrder("order-1");
        assertNull(result);
    }

    // ===== AC-04: findOrderByUserId is ungated ============================================

    /**
     * AC-04: findOrderByUserId() must not enforce ownership gate after Phase 3.
     */
    @Test
    public void findOrderByUserId_mcpCustomerAudience_thirdPartyId_serviceDoesNotThrowCive() {
        CallerIdentityContext.setMcpRequest(true);
        CallerIdentityContext.setAudience(Audience.CUSTOMER.name());
        StoreScopeContext.setCallerPhone(CALLER_PHONE);
        when(orderRepo.findByCustomerId(OTHER_USER_ID)).thenReturn(Optional.of(new ArrayList<>()));

        try {
            sut.findOrderByUserId(OTHER_USER_ID);
        } catch (CallerIdentityViolationException e) {
            fail("ADR-026 Phase 3: ownership gate moved to controller. Service must not throw CIVE: "
                    + e.getMessage());
        } catch (Exception e) {
            // OK
        }
    }

    // ===== AC-04: findOrderForMessenger is ungated ========================================

    /**
     * AC-04: findOrderForMessenger() must not enforce null-audience gate after Phase 3.
     */
    @Test
    public void findOrderForMessenger_mcpNullAudience_serviceDoesNotThrowCive() {
        CallerIdentityContext.setMcpRequest(true);
        when(orderRepo.findById("order-1")).thenReturn(Optional.of(new Order()));

        try {
            sut.findOrderForMessenger("order-1");
        } catch (CallerIdentityViolationException e) {
            fail("ADR-026 Phase 3: null-audience gate moved to controller. Service must not throw CIVE: "
                    + e.getMessage());
        } catch (Exception e) {
            // OK
        }
    }

    @Test
    public void findOrderForMessenger_nonMcpNullAudience_passes() {
        // Non-MCP: null audience is never enforced — always was the case
        when(orderRepo.findById("order-1")).thenReturn(Optional.of(new Order()));

        try {
            sut.findOrderForMessenger("order-1");
        } catch (CallerIdentityViolationException e) {
            fail("Non-MCP path must never enforce gates: " + e.getMessage());
        } catch (Exception e) {
            // OK
        }
    }

    // ===== AC-04: findOrderByPhone is ungated =============================================

    /**
     * AC-04: findOrderByPhone() must not enforce phone-ownership gate after Phase 3.
     * CUSTOMER requesting another user's phone previously threw CIVE in service.
     */
    @Test
    public void findOrderByPhone_mcpCustomerAudience_thirdPartyPhone_serviceDoesNotThrowCive() {
        CallerIdentityContext.setMcpRequest(true);
        CallerIdentityContext.setAudience(Audience.CUSTOMER.name());
        StoreScopeContext.setCallerPhone(CALLER_PHONE);

        // The service will try to resolve the phone to a user and may throw a non-CIVE
        // exception if the user is not found — that is acceptable.
        try {
            sut.findOrderByPhone("+27800000000");
        } catch (CallerIdentityViolationException e) {
            fail("ADR-026 Phase 3: phone gate moved to controller. Service must not throw CIVE: "
                    + e.getMessage());
        } catch (Exception e) {
            // OK — e.g. user not found
        }
    }

    // ===== AC-04: findOrderByMessengerId is ungated =======================================

    /**
     * AC-04: findOrderByMessengerId() must not enforce null-audience gate after Phase 3.
     */
    @Test
    public void findOrderByMessengerId_mcpNullAudience_serviceDoesNotThrowCive() {
        CallerIdentityContext.setMcpRequest(true);
        // No stub needed: service calls findByShippingDataMessengerIdAndStageNot when allStages=false

        try {
            sut.findOrderByMessengerId(CALLER_USER_ID, false);
        } catch (CallerIdentityViolationException e) {
            fail("ADR-026 Phase 3: null-audience gate moved to controller. Service must not throw CIVE: "
                    + e.getMessage());
        } catch (Exception e) {
            // OK
        }
    }

    /**
     * AC-04: findOrderByMessengerId() DRIVER ownership gate must not fire in service.
     */
    @Test
    public void findOrderByMessengerId_mcpDriverAudience_otherUserId_serviceDoesNotThrowCive() {
        CallerIdentityContext.setMcpRequest(true);
        CallerIdentityContext.setAudience(Audience.DRIVER.name());
        StoreScopeContext.setCallerPhone(CALLER_PHONE);
        // No stub needed: service calls findByShippingDataMessengerIdAndStageNot when allStages=false

        try {
            sut.findOrderByMessengerId(OTHER_USER_ID, false);
        } catch (CallerIdentityViolationException e) {
            fail("ADR-026 Phase 3: DRIVER ownership gate moved to controller. Service must not throw CIVE: "
                    + e.getMessage());
        } catch (Exception e) {
            // OK
        }
    }

    @Test
    public void findOrderByMessengerId_nonMcpRequest_delegatesToRepo() {
        // allStages=false → service calls findByShippingDataMessengerIdAndStageNot
        when(orderRepo.findByShippingDataMessengerIdAndStageNot(OTHER_USER_ID, OrderStage.STAGE_0_CUSTOMER_NOT_PAID))
                .thenReturn(List.of());

        Set<Order> result = sut.findOrderByMessengerId(OTHER_USER_ID, false);
        assertNotNull(result);
        verify(orderRepo).findByShippingDataMessengerIdAndStageNot(OTHER_USER_ID, OrderStage.STAGE_0_CUSTOMER_NOT_PAID);
    }

    // ===== startOrder SA-022-01 ===========================================================

    /**
     * ADR-026 Phase 1: ownership enforcement was moved from {@link OrderServiceImpl} to
     * {@link io.curiousoft.izinga.ordermanagement.mcp.OrderCreationMcpController}.
     * {@code OrderServiceImpl.startOrder()} must NOT throw {@link CallerIdentityViolationException}
     * for a third-party customerId — that check now lives exclusively at the controller layer.
     */
    @Test
    public void startOrder_customerAudienceThirdPartyId_ownershipNotEnforcedAtServiceLayer() {
        StoreScopeContext.setAudience(Audience.CUSTOMER);
        StoreScopeContext.setCallerPhone(CALLER_PHONE);
        // No phone resolution or existsById stubs required: Phase 1 moved those checks
        // to OrderCreationMcpController. The service no longer reads caller identity here.

        Order order = new Order();
        order.setOrderType(OrderType.ONLINE);
        order.setCustomerId(OTHER_USER_ID);
        order.setShopId("shop-1");
        ShippingData sd = new ShippingData("From", "To", ShippingData.ShippingType.DELIVERY);
        order.setShippingData(sd);

        // Phase 1: ownership enforcement lives at the controller layer, not the service layer.
        // The service must NOT throw CIVE. Any other downstream failure is acceptable.
        try {
            sut.startOrder(order);
        } catch (CallerIdentityViolationException e) {
            fail("ADR-026 Phase 1: ownership enforcement moved to controller layer; "
                    + "OrderServiceImpl must not throw CIVE: " + e.getMessage());
        } catch (Exception e) {
            // Other downstream failures (e.g. shop not found, missing data) are acceptable.
        }
    }
}
