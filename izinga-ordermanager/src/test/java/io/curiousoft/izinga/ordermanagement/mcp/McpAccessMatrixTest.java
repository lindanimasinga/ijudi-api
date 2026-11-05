package io.curiousoft.izinga.ordermanagement.mcp;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.curiousoft.izinga.commons.model.*;
import io.curiousoft.izinga.commons.order.OrderRepository;
import io.curiousoft.izinga.commons.referral.FoodCustomerReferralCommissionRepo;
import io.curiousoft.izinga.commons.referral.FurnitureCustomerReferralCommissionRepo;
import io.curiousoft.izinga.commons.referral.StorePartnerStage1CommissionRepo;
import io.curiousoft.izinga.commons.referral.StorePartnerStage2CommissionRepo;
import io.curiousoft.izinga.commons.repo.DeviceRepository;
import io.curiousoft.izinga.commons.repo.IcaAcceptanceLogRepo;
import io.curiousoft.izinga.commons.repo.StoreRepository;
import io.curiousoft.izinga.commons.repo.UserProfileRepo;
import io.curiousoft.izinga.commons.security.AgentIdentityAuditService;
import io.curiousoft.izinga.commons.security.CallerIdentityContext;
import io.curiousoft.izinga.commons.security.CallerIdentityViolationException;
import io.curiousoft.izinga.messaging.AdminOnlyNotificationService;
import io.curiousoft.izinga.messaging.firebase.FirebaseNotificationService;
import io.curiousoft.izinga.messaging.whatsapp.lines.Audience;
import io.curiousoft.izinga.ordermanagement.McpConfig;
import io.curiousoft.izinga.ordermanagement.leads.LeadService;
import io.curiousoft.izinga.ordermanagement.notification.EmailNotificationService;
import io.curiousoft.izinga.ordermanagement.orders.McpOrderService;
import io.curiousoft.izinga.ordermanagement.orders.OrderServiceImpl;
import io.curiousoft.izinga.ordermanagement.orders.RestrictedRegionService;
import io.curiousoft.izinga.ordermanagement.orders.quote.OrderQuoteRepository;
import io.curiousoft.izinga.ordermanagement.promocodes.PromoCodeClient;
import io.curiousoft.izinga.ordermanagement.security.AudienceViolationException;
import io.curiousoft.izinga.ordermanagement.security.StoreScopeContext;
import io.curiousoft.izinga.ordermanagement.security.StoreScopeViolationException;
import io.curiousoft.izinga.ordermanagement.service.paymentverify.PaymentService;
import io.curiousoft.izinga.ordermanagement.stores.StoreAgreementAuditRepository;
import io.curiousoft.izinga.ordermanagement.stores.StoreTierChangeAuditRepository;
import io.curiousoft.izinga.ordermanagement.stores.StoreService;
import io.curiousoft.izinga.ordermanagement.utils.DeliveryCalculationService;
import io.curiousoft.izinga.recon.ReconServiceImpl;
import io.curiousoft.izinga.recon.ambassador.AmbassadorProperties;
import io.curiousoft.izinga.recon.payout.repo.AmbassadorPayoutRepository;
import io.curiousoft.izinga.recon.payout.repo.MessengerPayoutRepository;
import io.curiousoft.izinga.recon.payout.repo.PayoutRepository;
import io.curiousoft.izinga.recon.payout.repo.ReferralPartnerPayoutRepository;
import io.curiousoft.izinga.recon.payout.repo.ShopPayoutRepository;
import io.curiousoft.izinga.usermanagement.referral.ReferralCodeService;
import io.curiousoft.izinga.usermanagement.userconfig.UserConfigService;
import io.curiousoft.izinga.ordermanagement.mcp.DocumentUploadMcpController;
import io.curiousoft.izinga.ordermanagement.mcp.McpCaller;
import io.curiousoft.izinga.ordermanagement.mcp.OrderCreationMcpController;
import io.curiousoft.izinga.ordermanagement.mcp.OrderMcpController;
import io.curiousoft.izinga.ordermanagement.mcp.PayoutMcpController;
import io.curiousoft.izinga.ordermanagement.mcp.StoreMcpController;
import io.curiousoft.izinga.ordermanagement.mcp.UserMcpController;
import io.curiousoft.izinga.usermanagement.users.DocumentUploadMcpService;
import io.curiousoft.izinga.usermanagement.users.DocumentUploadSessionRepo;
import io.curiousoft.izinga.usermanagement.users.UserProfileService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.ToolCallbackProvider;
import org.springframework.ai.tool.execution.ToolExecutionException;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.*;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.fail;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * ADR-026 D5 / REQ-05 / REQ-13: Full MCP access-control matrix characterization test — ROUND 2.
 *
 * <p><strong>Coverage</strong>: ALL 17 tools × {null/tokenless, DRIVER, CUSTOMER, STORE, ADMIN, OTP}
 * × {OWN, OTHER, N/A} through the tool-callback path ({@link ToolCallback#call(String)}).
 * create_user removed from the MCP surface in ADR-026 Phase 2b (Lindani 2026-11-05).
 *
 * <p><strong>Three refusal shapes (ADR-026 F3, frozen by OQ-4 deferral)</strong>:
 * <ol>
 *   <li>DENY_IDENTITY_REFUSAL: {@link CallerIdentityViolationException} with {@code IDENTITY_REFUSAL:} prefix</li>
 *   <li>DENY_AUDIENCE: {@link AudienceViolationException} without prefix — find_stores, find_store_or_shops_by_id</li>
 *   <li>REJECTED_PAYLOAD_IDENTITY: start_order returns {@code "success":false} with {@code IDENTITY_REFUSAL:} message</li>
 * </ol>
 *
 * <p><strong>REQ-05 (round 2)</strong>: Every DENY row asserts the FULL exact refusal message
 * by equality (not prefix). Message literals sourced from production constants.
 *
 * <p><strong>Non-vacuity (round 2)</strong>: Every ALLOW row verifies that at least one
 * downstream collaborator beyond the security gate was actually invoked.
 *
 * <p><strong>FIX-03</strong>: Every DENY row asserts that
 * {@link AgentIdentityAuditService#recordViolation} was invoked at least once.
 * Every ALLOW row asserts it was not invoked.
 *
 * <p><strong>Non-vacuity</strong>: The test exercises real service instances with mocked
 * collaborators — services are NOT mocked. Each row drives the guard path end-to-end
 * through {@link ToolCallback#call(String)}.
 *
 * <p><strong>Absence test</strong>: a separate {@code @Test} asserts that no
 * {@code ToolCallback} named {@code "create_user"} is registered (ADR-026 Phase 2b).
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class McpAccessMatrixTest {

    // ---- Outcome types ------------------------------------------------------------------

    enum OutcomeType {
        /** No security exception; method returns or throws a domain-level exception. */
        ALLOW,
        /** CallerIdentityViolationException with IDENTITY_REFUSAL: prefix. */
        DENY_IDENTITY_REFUSAL,
        /** AudienceViolationException (no prefix) — find_stores and find_store_or_shops_by_id. */
        DENY_AUDIENCE,
        /**
         * StoreScopeViolationException — find_store_or_shops_by_id when STORE audience requests a
         * storeId that differs from the token's permittedStoreId.
         * StoreScopeValidator.validate() does NOT call recordViolation, so expectAuditCall=false.
         */
        DENY_STORE_SCOPE,
        /** start_order returns JSON {"success":false} with message containing IDENTITY_REFUSAL:. */
        REJECTED_PAYLOAD_IDENTITY
    }

    // ---- Test data constants ------------------------------------------------------------

    private static final String CALLER_PHONE       = "+27812345678";
    private static final String CALLER_LAST9       = "812345678";
    private static final String CALLER_USER_ID     = "caller-user-id";
    private static final String OTHER_PHONE        = "+27899999999";
    private static final String OTHER_USER_ID      = "other-user-id";
    private static final String CALLER_ORDER_ID    = "order-caller-owned";
    private static final String OTHER_ORDER_ID     = "order-other-owned";
    private static final String PERMITTED_STORE_ID = "store-id-1";
    private static final String OWNER_ID           = "owner-id";
    private static final String WRONG_OWNER_ID     = "wrong-owner-id";

    // ---- Exact refusal message literals (ADR-026 REQ-05) --------------------------------
    // Source: izinga-commons/src/main/kotlin/io/curiousoft/izinga/commons/security/CallerIdentityValidator.kt

    /**
     * Tokenless / null-audience refusal.
     * Thrown by rejectTokenlessMcp() and validateCallerPhone(null-audience).
     */
    private static final String UNAUTHENTICATED_MSG =
        "IDENTITY_REFUSAL: This tool requires an authenticated scope token. " +
        "Unauthenticated or tokenless requests are not permitted on the MCP surface. " +
        "Please contact iZinga support if you need further help.";

    /**
     * Identity-mismatch refusal.
     * Thrown by validateCallerPhone(wrong-phone) and validateCallerOwnsId(wrong-id).
     */
    private static final String VIOLATION_MSG =
        "IDENTITY_REFUSAL: I can only assist with queries for your own account. " +
        "For privacy and security reasons, I am not authorised to access another person's information. " +
        "Please contact iZinga support if you need further help.";

    // Source: izinga-ordermanager/.../security/StoreScopeValidator.java

    /** Null/missing audience on audience-restricted tools (find_stores, find_store_or_shops_by_id). */
    private static final String AUDIENCE_NULL_MSG =
        "Audience required but no scope token is present on this request";

    /** OTP audience on find_stores — permitted set [CUSTOMER, DRIVER, STORE, ADMIN]. */
    private static final String FIND_STORES_OTP_MSG =
        "Audience violation: caller audience 'OTP' is not in permitted set [CUSTOMER, DRIVER, STORE, ADMIN]";

    /** OTP audience on find_store_or_shops_by_id — permitted set [STORE, DRIVER, CUSTOMER, ADMIN]. */
    private static final String FIND_STORE_BY_ID_OTP_MSG =
        "Audience violation: caller audience 'OTP' is not in permitted set [STORE, DRIVER, CUSTOMER, ADMIN]";

    /** STORE requesting a storeId outside its permitted scope. */
    private static final String STORE_SCOPE_OTHER_MSG =
        "Store scope violation: access to store 'other-store-id' is not permitted by this token";

    // ---- Mocked collaborators -----------------------------------------------------------

    @Mock private UserProfileRepo            userProfileRepo;
    @Mock private ApplicationEventPublisher  eventPublisher;
    @Mock private UserConfigService          userConfig;
    @Mock private IcaAcceptanceLogRepo       icaAcceptanceLogRepo;
    @Mock private ReferralCodeService        referralCodeService;
    @Mock private AgentIdentityAuditService  agentIdentityAuditService;
    @Mock private DocumentUploadSessionRepo  documentUploadSessionRepo;

    // OrderServiceImpl collaborators
    @Mock private OrderRepository          orderRepo;
    @Mock private StoreRepository          storeRepository;
    @Mock private PaymentService           paymentService;
    @Mock private DeviceRepository         deviceRepo;
    @Mock private FirebaseNotificationService pushNotificationService;
    @Mock private AdminOnlyNotificationService smsNotification;
    @Mock private EmailNotificationService emailNotificationService;
    @Mock private PromoCodeClient          promoCodeClient;
    @Mock private RestrictedRegionService  restrictedRegionService;
    @Mock private OrderQuoteRepository     orderQuoteRepository;
    @Mock private LeadService              leadService;
    @Mock private DeliveryCalculationService deliveryCalculationService;

    // StoreService collaborators
    @Mock private StoreAgreementAuditRepository storeAgreementAuditRepository;
    @Mock private StoreTierChangeAuditRepository storeTierChangeAuditRepository;
    @Mock private com.google.firebase.auth.FirebaseAuth firebaseAuth;
    @Mock private ApplicationContext applicationContext;

    // ReconServiceImpl collaborators
    @Mock private ShopPayoutRepository                shopPayoutRepo;
    @Mock private MessengerPayoutRepository           messengerPayoutRepository;
    @Mock private AmbassadorPayoutRepository          ambassadorPayoutRepository;
    @Mock private ReferralPartnerPayoutRepository     referralPartnerPayoutRepository;
    @Mock private PayoutRepository                    payoutBundleRepository;
    @Mock private FoodCustomerReferralCommissionRepo  foodCustomerCommissionRepo;
    @Mock private FurnitureCustomerReferralCommissionRepo furnitureCustomerCommissionRepo;
    @Mock private StorePartnerStage1CommissionRepo    storeStage1CommissionRepo;
    @Mock private StorePartnerStage2CommissionRepo    storeStage2CommissionRepo;

    // ---- Services under test (REAL instances) -------------------------------------------

    private UserProfileService       userProfileService;
    private DocumentUploadMcpService documentUploadMcpService;
    private OrderServiceImpl         orderService;
    private McpOrderService          mcpOrderService;
    private StoreService             storeService;
    private ReconServiceImpl         reconService;

    // ---- Provider and callback map ------------------------------------------------------

    private Map<String, ToolCallback> toolCallbacks;

    // ---- Setup / teardown --------------------------------------------------------------

    @BeforeEach
    void setUp() {
        // Build real service instances (never mock the services themselves)
        userProfileService = new UserProfileService(
                userProfileRepo, eventPublisher, userConfig,
                icaAcceptanceLogRepo, referralCodeService);

        documentUploadMcpService = new DocumentUploadMcpService(
                userProfileService, userConfig, documentUploadSessionRepo);

        orderService = new OrderServiceImpl(
                15.0, 3.0, 5.0, 0.025, 0.10, 5L,
                Collections.singletonList("0812815707"), "test-maps-key",
                orderRepo, storeRepository, userProfileRepo,
                paymentService, deviceRepo, pushNotificationService,
                smsNotification, emailNotificationService,
                promoCodeClient, eventPublisher,
                restrictedRegionService, orderQuoteRepository, leadService);
        ReflectionTestUtils.setField(orderService, "agentIdentityAuditService", agentIdentityAuditService);
        ReflectionTestUtils.setField(orderService, "deliveryCalculationService", deliveryCalculationService);

        mcpOrderService = new McpOrderService(
                orderService, userProfileRepo, orderRepo,
                new ObjectMapper(), 24L);

        storeService = new StoreService(
                storeRepository, userProfileRepo,
                "main-pay-account", 0.05,
                eventPublisher, referralCodeService,
                storeAgreementAuditRepository, storeTierChangeAuditRepository,
                firebaseAuth);

        // ADR-026 Phase 5: agentIdentityAuditService removed from ReconServiceImpl constructor
        reconService = new ReconServiceImpl(
                storeRepository, userProfileRepo, shopPayoutRepo,
                messengerPayoutRepository, ambassadorPayoutRepository,
                referralPartnerPayoutRepository, payoutBundleRepository,
                foodCustomerCommissionRepo, furnitureCustomerCommissionRepo,
                storeStage1CommissionRepo, storeStage2CommissionRepo,
                eventPublisher, new AmbassadorProperties());

        // Build Phase 1–5 controller instances backed by the real (non-mocked) services
        McpCaller mcpCaller = new McpCaller(userProfileRepo);
        OrderCreationMcpController orderCreationController =
                new OrderCreationMcpController(mcpOrderService, mcpCaller, agentIdentityAuditService);
        DocumentUploadMcpController documentUploadController =
                new DocumentUploadMcpController(documentUploadMcpService, agentIdentityAuditService);
        UserMcpController userMcpController =
                new UserMcpController(userProfileService, agentIdentityAuditService);
        OrderMcpController orderMcpController =
                new OrderMcpController(orderService, mcpCaller, agentIdentityAuditService);
        StoreMcpController storeMcpController =
                new StoreMcpController(storeService, agentIdentityAuditService, "main-pay-account", 0.05);
        // ADR-026 Phase 5: PayoutMcpController added — owns get_payouts_for_user
        PayoutMcpController payoutMcpController =
                new PayoutMcpController(reconService, mcpCaller, agentIdentityAuditService);
        Map<String, Object> controllerBeans = new HashMap<>();
        controllerBeans.put("orderCreationMcpController", orderCreationController);
        controllerBeans.put("documentUploadMcpController", documentUploadController);
        controllerBeans.put("userMcpController", userMcpController);
        controllerBeans.put("orderMcpController", orderMcpController);
        controllerBeans.put("storeMcpController", storeMcpController);
        controllerBeans.put("payoutMcpController", payoutMcpController);
        when(applicationContext.getBeansWithAnnotation(McpToolController.class))
                .thenReturn(controllerBeans);

        // ADR-026 Phase 6: McpConfig.userProfileToolCallbackProvider takes 1 param (no reconService).
        McpConfig mcpConfig = new McpConfig();
        ToolCallbackProvider provider = mcpConfig.userProfileToolCallbackProvider(applicationContext);

        // Build lookup map by tool name
        toolCallbacks = new HashMap<>();
        for (ToolCallback tc : provider.getToolCallbacks()) {
            toolCallbacks.put(tc.getToolDefinition().name(), tc);
        }

        // ---- Global lenient stubs ----
        // Caller phone resolves to callerProfile via "+27" prefix
        UserProfile callerProfile = buildCallerProfile();
        lenient().when(userProfileRepo.findByMobileNumber("+27" + CALLER_LAST9)).thenReturn(callerProfile);
        lenient().when(userProfileRepo.findByMobileNumber("0" + CALLER_LAST9)).thenReturn(null);
        lenient().when(userProfileRepo.findByMobileNumber("27" + CALLER_LAST9)).thenReturn(null);

        // Orders for ownership test cases.
        // IMPORTANT: Register the anyString() fallback FIRST. Mockito matches stubs in LIFO order
        // (most-recently-registered wins). By registering the general fallback first and specific
        // stubs afterwards, the specific stubs take precedence when their argument matches.
        lenient().when(orderRepo.findById(anyString())).thenReturn(Optional.empty());
        lenient().when(orderRepo.findById(CALLER_ORDER_ID)).thenReturn(Optional.of(buildCallerOwnedOrder()));
        lenient().when(orderRepo.findById(OTHER_ORDER_ID)).thenReturn(Optional.of(buildOtherOwnedOrder()));

        // Store for find_stores_by_owner ownership check
        lenient().when(storeRepository.findById(PERMITTED_STORE_ID)).thenReturn(
                Optional.of(buildStoreOwnedBy(OWNER_ID)));

        // Order queries for ALLOW cells
        lenient().when(orderRepo.findByCustomerId(anyString())).thenReturn(Optional.of(new ArrayList<>()));
        lenient().when(orderRepo.findByShippingDataMessengerIdAndStageNot(anyString(), any()))
                .thenReturn(new ArrayList<>());
        lenient().when(orderRepo.findAllById(anyList())).thenReturn(new ArrayList<>());
        lenient().when(orderQuoteRepository.findBySentToMessengerIds(anyString()))
                .thenReturn(Collections.emptyList());

        // User profile queries for ALLOW cells
        lenient().when(userProfileRepo.findByRoleAndServiceTypeAndLatitudeBetweenAndLongitudeBetween(
                any(), any(), anyDouble(), anyDouble(), anyDouble(), anyDouble()))
                .thenReturn(Collections.emptyList());
        // Register anyString() fallback first; specific stubs below override via Mockito LIFO matching.
        lenient().when(userProfileRepo.existsById(anyString())).thenReturn(false);
        // start_order CUSTOMER own/other rows — customer must exist to reach validateCallerOwnsId.
        lenient().when(userProfileRepo.existsById(CALLER_USER_ID)).thenReturn(true);
        lenient().when(userProfileRepo.existsById(OTHER_USER_ID)).thenReturn(true);

        // lenient save() stub — still needed for attach_uploaded_document_to_user_tag ALLOW rows
        // that may call userProfileRepo.save() through the service.
        lenient().when(userProfileRepo.save(any())).thenAnswer(inv -> inv.getArgument(0));

        // Store queries for ALLOW cells
        lenient().when(storeRepository.findAll()).thenReturn(Collections.emptyList());
        lenient().when(storeRepository.findByOwnerId(anyString())).thenReturn(Collections.emptyList());

        // Payout queries for ALLOW cells
        lenient().when(messengerPayoutRepository.findByModifiedDateBetweenAndToId(any(), any(), anyString()))
                .thenReturn(Collections.emptyList());
        lenient().when(shopPayoutRepo.findByModifiedDateBetweenAndToId(any(), any(), anyString()))
                .thenReturn(Collections.emptyList());
    }

    @AfterEach
    void clearContextAndResetAudit() {
        // Must always clear — same unconditional-finally rule as the filter
        StoreScopeContext.clear();
        CallerIdentityContext.clear();
        // Reset audit mock to avoid cross-row contamination in the parametrized test
        reset(agentIdentityAuditService);
    }

    // ==================================================================================
    // REQ-05 + REQ-13: Explicit literal outcome matrix — all 18 tools × all audiences
    // All rows exercise the tool-callback path: toolCallbacks.get(toolName).call(json)
    // ==================================================================================

    /**
     * Explicit literal outcome table for all 18 MCP tools × audience combinations.
     *
     * <p>Row format: (toolName, audienceStr, callerPhone, permittedStoreId, json,
     * expectedOutcome, expectAuditCall, expectedMsg)
     *
     * <ul>
     *   <li>audienceStr = null → tokenless (no scope token, isMcpRequest=true but audience=null)</li>
     *   <li>permittedStoreId = null → no store scope in token</li>
     *   <li>expectAuditCall = true → agentIdentityAuditService.recordViolation must be invoked</li>
     *   <li>expectAuditCall = false → recordViolation must NOT be invoked</li>
     *   <li>expectedMsg: for DENY/REJECTED rows, the EXACT exception/payload message.
     *       Null for ALLOW rows (no security gate message to assert).</li>
     * </ul>
     *
     * <p><strong>Token context per audience (production truth):</strong>
     * <ul>
     *   <li>DRIVER: storeId=null — AiCustomerServiceAgent.java:206, storeId arg is always null</li>
     *   <li>CUSTOMER: storeId=null — AiCustomerServiceAgent.java:206,323, storeId arg is always null</li>
     *   <li>STORE: storeId=&lt;storeId&gt; (non-null) — StoreAiAgent.java:252, hardcodes Audience.STORE + passes storeId</li>
     *   <li>ADMIN: storeId=null — AiCustomerServiceAgent.java:105, mintStoreId set to null for ADMIN</li>
     *   <li>OTP: storeId=null — OTP is a WhatsApp-line audience not served by StoreAiAgent; storeId=null</li>
     * </ul>
     */
    static Stream<Arguments> matrixRows() {
        // JSON constants
        String ownPhoneJson        = "{\"phone\":\"" + CALLER_PHONE + "\"}";
        String otherPhoneJson      = "{\"phone\":\"" + OTHER_PHONE + "\"}";
        String findUsersJson       = "{\"role\":\"MESSENGER\",\"latitude\":-26.0,\"longitude\":28.0," +
                                     "\"range\":0.1,\"storeType\":\"FOOD\"}";
        String ownMobileJson       = "{\"mobileNumber\":\"" + CALLER_PHONE + "\"}";
        String otherMobileJson     = "{\"mobileNumber\":\"" + OTHER_PHONE + "\"}";
        String callerOrderJson     = "{\"orderId\":\"" + CALLER_ORDER_ID + "\"}";
        String otherOrderJson      = "{\"orderId\":\"" + OTHER_ORDER_ID + "\"}";
        String anyOrderJson        = "{\"orderId\":\"order-1\"}";
        String callerUserIdJson    = "{\"userId\":\"" + CALLER_USER_ID + "\"}";
        String otherUserIdJson     = "{\"userId\":\"" + OTHER_USER_ID + "\"}";
        String callerMsgIdJson     = "{\"id\":\"" + CALLER_USER_ID + "\",\"allStages\":false}";
        String otherMsgIdJson      = "{\"id\":\"" + OTHER_USER_ID + "\",\"allStages\":false}";
        // start_order base fixture (customerId="cust-id" does not exist — domain-rejects early, no security gate)
        String startOrderJson      = "{\"customerId\":\"cust-id\",\"customerConfirmed\":true," +
                                     "\"idempotencyKey\":\"key-1\",\"shopId\":\"shop-1\"," +
                                     "\"description\":\"order\",\"orderType\":\"INSTORE\"," +
                                     "\"basketJson\":\"{\\\"items\\\":[]}\",\"shippingDataJson\":null}";
        // start_order CUSTOMER-own fixture: customerId=CALLER_USER_ID, exists in stub → reaches validateCallerOwnsId
        String startOrderOwnCustJson = "{\"customerId\":\"" + CALLER_USER_ID + "\",\"customerConfirmed\":true," +
                                       "\"idempotencyKey\":\"key-oc1\",\"shopId\":\"shop-1\"," +
                                       "\"description\":\"order\",\"orderType\":\"INSTORE\"," +
                                       "\"basketJson\":\"{\\\"items\\\":[]}\",\"shippingDataJson\":null}";
        // start_order CUSTOMER-other fixture: customerId=OTHER_USER_ID, exists in stub → validateCallerOwnsId fires → REJECTED
        String startOrderOtherCustJson = "{\"customerId\":\"" + OTHER_USER_ID + "\",\"customerConfirmed\":true," +
                                         "\"idempotencyKey\":\"key-oc2\",\"shopId\":\"shop-1\"," +
                                         "\"description\":\"order\",\"orderType\":\"INSTORE\"," +
                                         "\"basketJson\":\"{\\\"items\\\":[]}\",\"shippingDataJson\":null}";
        // start_order STORE-own-shop fixture: shopId==permittedStoreId — ADR F4-3: not shop-checked → ALLOW
        String startOrderOwnShopJson = "{\"customerId\":\"cust-id\",\"customerConfirmed\":true," +
                                       "\"idempotencyKey\":\"key-os1\",\"shopId\":\"" + PERMITTED_STORE_ID + "\"," +
                                       "\"description\":\"order\",\"orderType\":\"INSTORE\"," +
                                       "\"basketJson\":\"{\\\"items\\\":[]}\",\"shippingDataJson\":null}";
        String storeIdJson         = "{\"id\":\"" + PERMITTED_STORE_ID + "\"}";
        // find_store_or_shops_by_id — a storeId that is NOT the permitted scope
        String otherStoreIdJson    = "{\"id\":\"other-store-id\"}";
        String ownerIdJson         = "{\"ownerId\":\"" + OWNER_ID + "\"}";
        String wrongOwnerJson      = "{\"ownerId\":\"" + WRONG_OWNER_ID + "\"}";
        String findStoresJson      = "{}";
        String payoutsOwnJson      = "{\"payoutType\":\"MESSENGER\",\"from\":1704067200000," +
                                     "\"toDate\":1762212800000,\"toId\":\"" + CALLER_USER_ID + "\"}";
        String payoutsOtherJson    = "{\"payoutType\":\"MESSENGER\",\"from\":1704067200000," +
                                     "\"toDate\":1762212800000,\"toId\":\"" + OTHER_USER_ID + "\"}";

        return Stream.of(
            // ---- find_user_by_phone (11 rows) ----
            // null audience: rejectTokenlessMcp() throws immediately — BEFORE the catch that calls
            // recordViolation(). So no audit call is expected for the tokenless row.
            // callerPhone=null: production truth — StoreScopeValidationFilter only sets callerPhone
            // when a valid scope token is present; tokenless calls have callerPhone=null.
            row("find_user_by_phone", null,       null,         null,               otherPhoneJson,  DENY,       false, UNAUTHENTICATED_MSG),
            row("find_user_by_phone", "DRIVER",   CALLER_PHONE, null,               ownPhoneJson,    ALLOW,      false, null),
            row("find_user_by_phone", "DRIVER",   CALLER_PHONE, null,               otherPhoneJson,  DENY,       true,  VIOLATION_MSG),
            row("find_user_by_phone", "CUSTOMER", CALLER_PHONE, null,               ownPhoneJson,    ALLOW,      false, null),
            row("find_user_by_phone", "CUSTOMER", CALLER_PHONE, null,               otherPhoneJson,  DENY,       true,  VIOLATION_MSG),
            row("find_user_by_phone", "STORE",    CALLER_PHONE, PERMITTED_STORE_ID, otherPhoneJson,  ALLOW,      false, null),
            row("find_user_by_phone", "STORE",    CALLER_PHONE, PERMITTED_STORE_ID, ownPhoneJson,    ALLOW,      false, null),
            row("find_user_by_phone", "ADMIN",    CALLER_PHONE, null,               otherPhoneJson,  ALLOW,      false, null),
            row("find_user_by_phone", "ADMIN",    CALLER_PHONE, null,               ownPhoneJson,    ALLOW,      false, null),
            row("find_user_by_phone", "OTP",      CALLER_PHONE, null,               otherPhoneJson,  ALLOW,      false, null),
            row("find_user_by_phone", "OTP",      CALLER_PHONE, null,               ownPhoneJson,    ALLOW,      false, null),

            // ---- find_users (6 rows) — ADMIN-only tool ----
            row("find_users", null,       null, null, findUsersJson, DENY,  true,  UNAUTHENTICATED_MSG),
            row("find_users", "DRIVER",   CALLER_PHONE, null, findUsersJson, DENY,  true,  VIOLATION_MSG),
            row("find_users", "CUSTOMER", CALLER_PHONE, null, findUsersJson, DENY,  true,  VIOLATION_MSG),
            row("find_users", "STORE",    CALLER_PHONE, PERMITTED_STORE_ID, findUsersJson, DENY,  true,  VIOLATION_MSG),
            row("find_users", "ADMIN",    CALLER_PHONE, null,               findUsersJson, ALLOW, false, null),
            row("find_users", "OTP",      CALLER_PHONE, null, findUsersJson, DENY,  true,  VIOLATION_MSG),

            // ---- get_missing_fields_by_phone (11 rows) ----
            // null audience: delegates to findUserByPhone which calls rejectTokenlessMcp() first — no audit call.
            row("get_missing_fields_by_phone", null,       null, null, otherMobileJson, DENY,  false, UNAUTHENTICATED_MSG),
            row("get_missing_fields_by_phone", "DRIVER",   CALLER_PHONE, null, ownMobileJson,   ALLOW, false, null),
            row("get_missing_fields_by_phone", "DRIVER",   CALLER_PHONE, null, otherMobileJson, DENY,  true,  VIOLATION_MSG),
            row("get_missing_fields_by_phone", "CUSTOMER", CALLER_PHONE, null, ownMobileJson,   ALLOW, false, null),
            row("get_missing_fields_by_phone", "CUSTOMER", CALLER_PHONE, null, otherMobileJson, DENY,  true,  VIOLATION_MSG),
            row("get_missing_fields_by_phone", "STORE",    CALLER_PHONE, PERMITTED_STORE_ID, otherMobileJson, ALLOW, false, null),
            row("get_missing_fields_by_phone", "STORE",    CALLER_PHONE, PERMITTED_STORE_ID, ownMobileJson,   ALLOW, false, null),
            row("get_missing_fields_by_phone", "ADMIN",    CALLER_PHONE, null,               otherMobileJson, ALLOW, false, null),
            row("get_missing_fields_by_phone", "ADMIN",    CALLER_PHONE, null,               ownMobileJson,   ALLOW, false, null),
            row("get_missing_fields_by_phone", "OTP",      CALLER_PHONE, null,               otherMobileJson, ALLOW, false, null),
            row("get_missing_fields_by_phone", "OTP",      CALLER_PHONE, null,               ownMobileJson,   ALLOW, false, null),

            // ---- get_required_documents_for_user (11 rows) ----
            // callerPhone=null: tokenless — StoreScopeValidationFilter does not set callerPhone without token.
            // validateCallerPhone(otherPhone) sees null callerPhone → normalizeLast9(null)=null ≠ other → DENY with audit.
            row("get_required_documents_for_user", null,       null, null, otherPhoneJson, DENY,  true,  UNAUTHENTICATED_MSG),
            row("get_required_documents_for_user", "DRIVER",   CALLER_PHONE, null, ownPhoneJson,   ALLOW, false, null),
            row("get_required_documents_for_user", "DRIVER",   CALLER_PHONE, null, otherPhoneJson, DENY,  true,  VIOLATION_MSG),
            row("get_required_documents_for_user", "CUSTOMER", CALLER_PHONE, null, ownPhoneJson,   ALLOW, false, null),
            row("get_required_documents_for_user", "CUSTOMER", CALLER_PHONE, null, otherPhoneJson, DENY,  true,  VIOLATION_MSG),
            row("get_required_documents_for_user", "STORE",    CALLER_PHONE, PERMITTED_STORE_ID, otherPhoneJson, ALLOW, false, null),
            row("get_required_documents_for_user", "STORE",    CALLER_PHONE, PERMITTED_STORE_ID, ownPhoneJson,   ALLOW, false, null),
            row("get_required_documents_for_user", "ADMIN",    CALLER_PHONE, null,               otherPhoneJson, ALLOW, false, null),
            row("get_required_documents_for_user", "ADMIN",    CALLER_PHONE, null,               ownPhoneJson,   ALLOW, false, null),
            row("get_required_documents_for_user", "OTP",      CALLER_PHONE, null,               otherPhoneJson, ALLOW, false, null),
            row("get_required_documents_for_user", "OTP",      CALLER_PHONE, null,               ownPhoneJson,   ALLOW, false, null),

            // ---- create_document_upload_session (11 rows) ----
            row("create_document_upload_session", null,       null, null, docSessionJson(OTHER_PHONE), DENY,  true,  UNAUTHENTICATED_MSG),
            row("create_document_upload_session", "DRIVER",   CALLER_PHONE, null, docSessionJson(CALLER_PHONE), ALLOW, false, null),
            row("create_document_upload_session", "DRIVER",   CALLER_PHONE, null, docSessionJson(OTHER_PHONE), DENY,  true,  VIOLATION_MSG),
            row("create_document_upload_session", "CUSTOMER", CALLER_PHONE, null, docSessionJson(CALLER_PHONE), ALLOW, false, null),
            row("create_document_upload_session", "CUSTOMER", CALLER_PHONE, null, docSessionJson(OTHER_PHONE), DENY,  true,  VIOLATION_MSG),
            row("create_document_upload_session", "STORE",    CALLER_PHONE, PERMITTED_STORE_ID, docSessionJson(OTHER_PHONE),   ALLOW, false, null),
            row("create_document_upload_session", "STORE",    CALLER_PHONE, PERMITTED_STORE_ID, docSessionJson(CALLER_PHONE),  ALLOW, false, null),
            row("create_document_upload_session", "ADMIN",    CALLER_PHONE, null,               docSessionJson(OTHER_PHONE),   ALLOW, false, null),
            row("create_document_upload_session", "ADMIN",    CALLER_PHONE, null,               docSessionJson(CALLER_PHONE),  ALLOW, false, null),
            row("create_document_upload_session", "OTP",      CALLER_PHONE, null,               docSessionJson(OTHER_PHONE),   ALLOW, false, null),
            row("create_document_upload_session", "OTP",      CALLER_PHONE, null,               docSessionJson(CALLER_PHONE),  ALLOW, false, null),

            // ---- attach_uploaded_document_to_user_tag (11 rows) ----
            row("attach_uploaded_document_to_user_tag", null,       null, null, attachJson(OTHER_PHONE), DENY,  true,  UNAUTHENTICATED_MSG),
            row("attach_uploaded_document_to_user_tag", "DRIVER",   CALLER_PHONE, null, attachJson(CALLER_PHONE), ALLOW, false, null),
            row("attach_uploaded_document_to_user_tag", "DRIVER",   CALLER_PHONE, null, attachJson(OTHER_PHONE), DENY,  true,  VIOLATION_MSG),
            row("attach_uploaded_document_to_user_tag", "CUSTOMER", CALLER_PHONE, null, attachJson(CALLER_PHONE), ALLOW, false, null),
            row("attach_uploaded_document_to_user_tag", "CUSTOMER", CALLER_PHONE, null, attachJson(OTHER_PHONE), DENY,  true,  VIOLATION_MSG),
            row("attach_uploaded_document_to_user_tag", "STORE",    CALLER_PHONE, PERMITTED_STORE_ID, attachJson(OTHER_PHONE),   ALLOW, false, null),
            row("attach_uploaded_document_to_user_tag", "STORE",    CALLER_PHONE, PERMITTED_STORE_ID, attachJson(CALLER_PHONE),  ALLOW, false, null),
            row("attach_uploaded_document_to_user_tag", "ADMIN",    CALLER_PHONE, null,               attachJson(OTHER_PHONE),   ALLOW, false, null),
            row("attach_uploaded_document_to_user_tag", "ADMIN",    CALLER_PHONE, null,               attachJson(CALLER_PHONE),  ALLOW, false, null),
            row("attach_uploaded_document_to_user_tag", "OTP",      CALLER_PHONE, null,               attachJson(OTHER_PHONE),   ALLOW, false, null),
            row("attach_uploaded_document_to_user_tag", "OTP",      CALLER_PHONE, null,               attachJson(CALLER_PHONE),  ALLOW, false, null),

            // ---- recheck_required_documents (11 rows) ----
            row("recheck_required_documents", null,       null, null, otherPhoneJson, DENY,  true,  UNAUTHENTICATED_MSG),
            row("recheck_required_documents", "DRIVER",   CALLER_PHONE, null, ownPhoneJson,   ALLOW, false, null),
            row("recheck_required_documents", "DRIVER",   CALLER_PHONE, null, otherPhoneJson, DENY,  true,  VIOLATION_MSG),
            row("recheck_required_documents", "CUSTOMER", CALLER_PHONE, null, ownPhoneJson,   ALLOW, false, null),
            row("recheck_required_documents", "CUSTOMER", CALLER_PHONE, null, otherPhoneJson, DENY,  true,  VIOLATION_MSG),
            row("recheck_required_documents", "STORE",    CALLER_PHONE, PERMITTED_STORE_ID, otherPhoneJson, ALLOW, false, null),
            row("recheck_required_documents", "STORE",    CALLER_PHONE, PERMITTED_STORE_ID, ownPhoneJson,   ALLOW, false, null),
            row("recheck_required_documents", "ADMIN",    CALLER_PHONE, null,               otherPhoneJson, ALLOW, false, null),
            row("recheck_required_documents", "ADMIN",    CALLER_PHONE, null,               ownPhoneJson,   ALLOW, false, null),
            row("recheck_required_documents", "OTP",      CALLER_PHONE, null,               otherPhoneJson, ALLOW, false, null),
            row("recheck_required_documents", "OTP",      CALLER_PHONE, null,               ownPhoneJson,   ALLOW, false, null),

            // ---- find_order_by_id (11 rows) ----
            row("find_order_by_id", null,       null, null, anyOrderJson,    DENY,  true,  UNAUTHENTICATED_MSG),
            row("find_order_by_id", "DRIVER",   CALLER_PHONE, null, callerOrderJson, ALLOW, false, null),
            row("find_order_by_id", "DRIVER",   CALLER_PHONE, null, otherOrderJson,  DENY,  true,  VIOLATION_MSG),
            row("find_order_by_id", "CUSTOMER", CALLER_PHONE, null, callerOrderJson, ALLOW, false, null),
            row("find_order_by_id", "CUSTOMER", CALLER_PHONE, null, otherOrderJson,  DENY,  true,  VIOLATION_MSG),
            row("find_order_by_id", "STORE",    CALLER_PHONE, PERMITTED_STORE_ID, anyOrderJson,    ALLOW, false, null),
            row("find_order_by_id", "STORE",    CALLER_PHONE, PERMITTED_STORE_ID, callerOrderJson, ALLOW, false, null),
            row("find_order_by_id", "ADMIN",    CALLER_PHONE, null,               anyOrderJson,    ALLOW, false, null),
            row("find_order_by_id", "ADMIN",    CALLER_PHONE, null,               callerOrderJson, ALLOW, false, null),
            row("find_order_by_id", "OTP",      CALLER_PHONE, null,               anyOrderJson,    ALLOW, false, null),
            row("find_order_by_id", "OTP",      CALLER_PHONE, null,               callerOrderJson, ALLOW, false, null),

            // ---- find_orders_by_user_id (11 rows) ----
            row("find_orders_by_user_id", null,       null, null, otherUserIdJson,  DENY,  true,  UNAUTHENTICATED_MSG),
            row("find_orders_by_user_id", "DRIVER",   CALLER_PHONE, null, callerUserIdJson, ALLOW, false, null),
            row("find_orders_by_user_id", "DRIVER",   CALLER_PHONE, null, otherUserIdJson,  DENY,  true,  VIOLATION_MSG),
            row("find_orders_by_user_id", "CUSTOMER", CALLER_PHONE, null, callerUserIdJson, ALLOW, false, null),
            row("find_orders_by_user_id", "CUSTOMER", CALLER_PHONE, null, otherUserIdJson,  DENY,  true,  VIOLATION_MSG),
            row("find_orders_by_user_id", "STORE",    CALLER_PHONE, PERMITTED_STORE_ID, otherUserIdJson,  ALLOW, false, null),
            row("find_orders_by_user_id", "STORE",    CALLER_PHONE, PERMITTED_STORE_ID, callerUserIdJson, ALLOW, false, null),
            row("find_orders_by_user_id", "ADMIN",    CALLER_PHONE, null,               otherUserIdJson,  ALLOW, false, null),
            row("find_orders_by_user_id", "ADMIN",    CALLER_PHONE, null,               callerUserIdJson, ALLOW, false, null),
            row("find_orders_by_user_id", "OTP",      CALLER_PHONE, null,               otherUserIdJson,  ALLOW, false, null),
            row("find_orders_by_user_id", "OTP",      CALLER_PHONE, null,               callerUserIdJson, ALLOW, false, null),

            // ---- find_order_for_messenger (11 rows) ----
            row("find_order_for_messenger", null,       null, null, anyOrderJson,    DENY,  true,  UNAUTHENTICATED_MSG),
            row("find_order_for_messenger", "DRIVER",   CALLER_PHONE, null, callerOrderJson, ALLOW, false, null),
            row("find_order_for_messenger", "DRIVER",   CALLER_PHONE, null, otherOrderJson,  DENY,  true,  VIOLATION_MSG),
            row("find_order_for_messenger", "CUSTOMER", CALLER_PHONE, null, callerOrderJson, ALLOW, false, null),
            row("find_order_for_messenger", "CUSTOMER", CALLER_PHONE, null, otherOrderJson,  DENY,  true,  VIOLATION_MSG),
            row("find_order_for_messenger", "STORE",    CALLER_PHONE, PERMITTED_STORE_ID, anyOrderJson,    ALLOW, false, null),
            row("find_order_for_messenger", "STORE",    CALLER_PHONE, PERMITTED_STORE_ID, callerOrderJson, ALLOW, false, null),
            row("find_order_for_messenger", "ADMIN",    CALLER_PHONE, null,               anyOrderJson,    ALLOW, false, null),
            row("find_order_for_messenger", "ADMIN",    CALLER_PHONE, null,               callerOrderJson, ALLOW, false, null),
            row("find_order_for_messenger", "OTP",      CALLER_PHONE, null,               anyOrderJson,    ALLOW, false, null),
            row("find_order_for_messenger", "OTP",      CALLER_PHONE, null,               callerOrderJson, ALLOW, false, null),

            // ---- find_orders_by_phone_number (11 rows) ----
            row("find_orders_by_phone_number", null,       null, null, otherPhoneJson, DENY,  true,  UNAUTHENTICATED_MSG),
            row("find_orders_by_phone_number", "DRIVER",   CALLER_PHONE, null, ownPhoneJson,   ALLOW, false, null),
            row("find_orders_by_phone_number", "DRIVER",   CALLER_PHONE, null, otherPhoneJson, DENY,  true,  VIOLATION_MSG),
            row("find_orders_by_phone_number", "CUSTOMER", CALLER_PHONE, null, ownPhoneJson,   ALLOW, false, null),
            row("find_orders_by_phone_number", "CUSTOMER", CALLER_PHONE, null, otherPhoneJson, DENY,  true,  VIOLATION_MSG),
            row("find_orders_by_phone_number", "STORE",    CALLER_PHONE, PERMITTED_STORE_ID, ownPhoneJson,   ALLOW, false, null),
            row("find_orders_by_phone_number", "STORE",    CALLER_PHONE, PERMITTED_STORE_ID, otherPhoneJson, ALLOW, false, null),
            row("find_orders_by_phone_number", "ADMIN",    CALLER_PHONE, null,               otherPhoneJson, ALLOW, false, null),
            row("find_orders_by_phone_number", "ADMIN",    CALLER_PHONE, null,               ownPhoneJson,   ALLOW, false, null),
            row("find_orders_by_phone_number", "OTP",      CALLER_PHONE, null,               ownPhoneJson,   ALLOW, false, null),
            row("find_orders_by_phone_number", "OTP",      CALLER_PHONE, null,               otherPhoneJson, ALLOW, false, null),

            // ---- find_orders_by_messenger_id (9 rows) ----
            row("find_orders_by_messenger_id", null,       null, null, otherMsgIdJson,  DENY,  true,  UNAUTHENTICATED_MSG),
            row("find_orders_by_messenger_id", "DRIVER",   CALLER_PHONE, null, callerMsgIdJson, ALLOW, false, null),
            row("find_orders_by_messenger_id", "DRIVER",   CALLER_PHONE, null, otherMsgIdJson,  DENY,  true,  VIOLATION_MSG),
            row("find_orders_by_messenger_id", "CUSTOMER", CALLER_PHONE, null, callerMsgIdJson, DENY,  true,  VIOLATION_MSG),
            row("find_orders_by_messenger_id", "STORE",    CALLER_PHONE, PERMITTED_STORE_ID, callerMsgIdJson, DENY,  true,  VIOLATION_MSG),
            row("find_orders_by_messenger_id", "ADMIN",    CALLER_PHONE, null,               otherMsgIdJson,  ALLOW, false, null),
            row("find_orders_by_messenger_id", "ADMIN",    CALLER_PHONE, null,               callerMsgIdJson, ALLOW, false, null),
            row("find_orders_by_messenger_id", "OTP",      CALLER_PHONE, null, callerMsgIdJson, DENY,  true,  VIOLATION_MSG),

            // ---- start_order (11 rows) — REJECTED_PAYLOAD for tokenless/DRIVER/OTP ----
            // Source: McpOrderService.java:109-126 — audience gate; OrderServiceImpl.java:163 — validateCallerOwnsId
            row("start_order", null,       null, null,               startOrderJson,         REJECTED, false, UNAUTHENTICATED_MSG),
            row("start_order", "DRIVER",   CALLER_PHONE, null,               startOrderJson,         REJECTED, false, VIOLATION_MSG),
            // CHARACTERISATION ONLY (not a security gate exercise): cust-id doesn't exist →
            // domain-rejects via userProfileRepo.existsById before validateCallerOwnsId.
            // This row pins the early-exit behaviour, NOT security-gate passage. expectedMsg=null.
            row("start_order", "CUSTOMER", CALLER_PHONE, null,               startOrderJson,         ALLOW,    false, null),
            // CUSTOMER-own: CALLER_USER_ID exists → reaches validateCallerOwnsId → CALLER_USER_ID matches → ALLOW
            row("start_order", "CUSTOMER", CALLER_PHONE, null,               startOrderOwnCustJson,  ALLOW,    false, null),
            // CUSTOMER-other: OTHER_USER_ID exists → validateCallerOwnsId fires → REJECTED (IDENTITY_REFUSAL in payload)
            // AND: OrderServiceImpl.startOrder line 168 calls agentIdentityAuditService.recordViolation() → expectAuditCall=true.
            row("start_order", "CUSTOMER", CALLER_PHONE, null,               startOrderOtherCustJson, REJECTED, true, VIOLATION_MSG),
            // STORE base (shopId=shop-1 ≠ PERMITTED_STORE_ID): ADR F4-3 — startOrder does NOT check shopId vs permittedStoreId → ALLOW
            row("start_order", "STORE",    CALLER_PHONE, PERMITTED_STORE_ID, startOrderJson,         ALLOW,    false, null),
            // STORE own-shop (shopId==PERMITTED_STORE_ID): same absence of shopId scope check → also ALLOW (pins F4-3)
            row("start_order", "STORE",    CALLER_PHONE, PERMITTED_STORE_ID, startOrderOwnShopJson,  ALLOW,    false, null),
            row("start_order", "ADMIN",    CALLER_PHONE, null,               startOrderJson,         ALLOW,    false, null),
            // ADMIN-own: ADMIN bypasses validateCallerOwnsId — customerId=CALLER_USER_ID pinned as ALLOW (pins REQ-A7)
            row("start_order", "ADMIN",    CALLER_PHONE, null,               startOrderOwnCustJson,  ALLOW,    false, null),
            row("start_order", "OTP",      CALLER_PHONE, null,               startOrderJson,         REJECTED, false, VIOLATION_MSG),

            // ---- find_store_or_shops_by_id (9 rows) — AudienceViolationException for null/OTP ----
            // Source: StoreMcpController.java — Gate 1: StoreScopeValidator.validate(id); Gate 2: validateAudience
            row("find_store_or_shops_by_id", null,       null, null,               storeIdJson,     DENY_AUDIENCE, false, AUDIENCE_NULL_MSG),
            row("find_store_or_shops_by_id", "DRIVER",   CALLER_PHONE, null,               storeIdJson,     ALLOW,         false, null),
            row("find_store_or_shops_by_id", "CUSTOMER", CALLER_PHONE, null,               storeIdJson,     ALLOW,         false, null),
            // STORE-own: id == permittedStoreId → StoreScopeValidator.validate passes → ALLOW
            row("find_store_or_shops_by_id", "STORE",    CALLER_PHONE, PERMITTED_STORE_ID, storeIdJson,     ALLOW,         false, null),
            // STORE-other: id != permittedStoreId → StoreScopeViolationException (recordViolation NOT called)
            row("find_store_or_shops_by_id", "STORE",    CALLER_PHONE, PERMITTED_STORE_ID, otherStoreIdJson, DENY_STORE,   false, STORE_SCOPE_OTHER_MSG),
            row("find_store_or_shops_by_id", "ADMIN",    CALLER_PHONE, null,               storeIdJson,     ALLOW,         false, null),
            row("find_store_or_shops_by_id", "ADMIN",    CALLER_PHONE, null,               otherStoreIdJson, ALLOW,        false, null),
            row("find_store_or_shops_by_id", "OTP",      CALLER_PHONE, null,               storeIdJson,     DENY_AUDIENCE, false, FIND_STORE_BY_ID_OTP_MSG),

            // ---- find_stores_by_owner (9 rows) ----
            row("find_stores_by_owner", null,       null, null,               ownerIdJson,   DENY,  true,  UNAUTHENTICATED_MSG),
            row("find_stores_by_owner", "DRIVER",   CALLER_PHONE, null,               ownerIdJson,   DENY,  true,  VIOLATION_MSG),
            row("find_stores_by_owner", "CUSTOMER", CALLER_PHONE, null,               ownerIdJson,   DENY,  true,  VIOLATION_MSG),
            row("find_stores_by_owner", "STORE",    CALLER_PHONE, PERMITTED_STORE_ID, ownerIdJson,   ALLOW, false, null),
            row("find_stores_by_owner", "STORE",    CALLER_PHONE, PERMITTED_STORE_ID, wrongOwnerJson, DENY, true,  VIOLATION_MSG),
            row("find_stores_by_owner", "ADMIN",    CALLER_PHONE, null,               ownerIdJson,   ALLOW, false, null),
            row("find_stores_by_owner", "ADMIN",    CALLER_PHONE, null,               wrongOwnerJson, ALLOW, false, null),
            row("find_stores_by_owner", "OTP",      CALLER_PHONE, null,               ownerIdJson,   DENY,  true,  VIOLATION_MSG),

            // ---- find_stores (6 rows) — AudienceViolationException for null/OTP ----
            row("find_stores", null,       null, null, findStoresJson, DENY_AUDIENCE, false, AUDIENCE_NULL_MSG),
            row("find_stores", "DRIVER",   CALLER_PHONE, null, findStoresJson, ALLOW,         false, null),
            row("find_stores", "CUSTOMER", CALLER_PHONE, null, findStoresJson, ALLOW,         false, null),
            row("find_stores", "STORE",    CALLER_PHONE, PERMITTED_STORE_ID, findStoresJson, ALLOW,         false, null),
            row("find_stores", "ADMIN",    CALLER_PHONE, null,               findStoresJson, ALLOW,         false, null),
            row("find_stores", "OTP",      CALLER_PHONE, null, findStoresJson, DENY_AUDIENCE, false, FIND_STORES_OTP_MSG),

            // ---- get_payouts_for_user (11 rows) ----
            row("get_payouts_for_user", null,       null, null, payoutsOtherJson, DENY,  true,  UNAUTHENTICATED_MSG),
            row("get_payouts_for_user", "DRIVER",   CALLER_PHONE, null, payoutsOwnJson,   ALLOW, false, null),
            row("get_payouts_for_user", "DRIVER",   CALLER_PHONE, null, payoutsOtherJson, DENY,  true,  VIOLATION_MSG),
            row("get_payouts_for_user", "CUSTOMER", CALLER_PHONE, null, payoutsOwnJson,   ALLOW, false, null),
            row("get_payouts_for_user", "CUSTOMER", CALLER_PHONE, null, payoutsOtherJson, DENY,  true,  VIOLATION_MSG),
            row("get_payouts_for_user", "STORE",    CALLER_PHONE, PERMITTED_STORE_ID, payoutsOtherJson, ALLOW, false, null),
            row("get_payouts_for_user", "STORE",    CALLER_PHONE, PERMITTED_STORE_ID, payoutsOwnJson,   ALLOW, false, null),
            row("get_payouts_for_user", "ADMIN",    CALLER_PHONE, null,               payoutsOtherJson, ALLOW, false, null),
            row("get_payouts_for_user", "ADMIN",    CALLER_PHONE, null,               payoutsOwnJson,   ALLOW, false, null),
            row("get_payouts_for_user", "OTP",      CALLER_PHONE, null,               payoutsOtherJson, ALLOW, false, null),
            row("get_payouts_for_user", "OTP",      CALLER_PHONE, null,               payoutsOwnJson,   ALLOW, false, null)
        );
    }

    /**
     * Main matrix test — drives every row through the ToolCallback.call(json) path.
     *
     * <p>For DENY cells: asserts the correct exception type AND the FULL exact refusal message
     * by equality (REQ-05 round 2). For ALLOW cells: asserts no security exception is thrown
     * (domain exceptions are OK) AND verifies a downstream collaborator was actually invoked
     * (non-vacuity requirement). For REJECTED_PAYLOAD cells: asserts the returned JSON has
     * success=false with the exact expected message.
     *
     * <p>FIX-03: Every row verifies agentIdentityAuditService.recordViolation() was
     * invoked (expectAuditCall=true) or not invoked (expectAuditCall=false).
     */
    @ParameterizedTest(name = "[{index}] {0} | aud={1} | expected={5}")
    @MethodSource("matrixRows")
    void matrix(String toolName, String audienceStr, String callerPhone,
                String permittedStoreId, String json,
                OutcomeType expected, boolean expectAuditCall, String expectedMsg) throws Exception {
        ToolCallback tc = toolCallbacks.get(toolName);
        assertThat(tc).as("ToolCallback must exist for tool: " + toolName).isNotNull();

        // Set per-request context exactly as StoreScopeValidationFilter does
        setMcpContext(audienceStr, callerPhone, permittedStoreId);

        // Execute through the callback path.
        // Spring AI's MethodToolCallback.call() wraps ALL service exceptions in
        // ToolExecutionException — the original exception is accessible via getCause().
        // Three refusal shapes (ADR-026 F3, frozen by OQ-4 deferral):
        //   Shape 1: CallerIdentityViolationException — service throws it; ToolExecutionException wraps it;
        //            cause.getMessage() == IDENTITY_REFUSAL: prefix text
        //   Shape 2: AudienceViolationException — same wrapping; check cause type and message
        //   Shape 3: start_order returns JSON {"success":false, "message":"IDENTITY_REFUSAL:..."}
        switch (expected) {
            case DENY_IDENTITY_REFUSAL -> assertThatThrownBy(() -> tc.call(json))
                    .isInstanceOf(ToolExecutionException.class)
                    .satisfies(ex -> {
                        ToolExecutionException tee = (ToolExecutionException) ex;
                        assertThat(tee.getCause())
                                .as("ToolExecutionException cause must be CallerIdentityViolationException for tool=%s aud=%s",
                                        toolName, audienceStr)
                                .isInstanceOf(CallerIdentityViolationException.class);
                        // REQ-05 ROUND-3: expectedMsg is REQUIRED for every DENY_IDENTITY_REFUSAL row.
                        // A null here means the matrix row is missing its exact message literal — fix the row.
                        assertThat(expectedMsg)
                                .as("expectedMsg must not be null for DENY_IDENTITY_REFUSAL row tool=%s aud=%s",
                                        toolName, audienceStr)
                                .isNotNull();
                        assertThat(tee.getCause().getMessage())
                                .as("Exact refusal message for tool=%s aud=%s", toolName, audienceStr)
                                .isEqualTo(expectedMsg);
                    });

            case DENY_AUDIENCE -> assertThatThrownBy(() -> tc.call(json))
                    .isInstanceOf(ToolExecutionException.class)
                    .satisfies(ex -> {
                        ToolExecutionException tee = (ToolExecutionException) ex;
                        assertThat(tee.getCause())
                                .as("ToolExecutionException cause must be AudienceViolationException for tool=%s aud=%s",
                                        toolName, audienceStr)
                                .isInstanceOf(AudienceViolationException.class);
                        // REQ-05 ROUND-3: expectedMsg is REQUIRED for every DENY_AUDIENCE row.
                        assertThat(expectedMsg)
                                .as("expectedMsg must not be null for DENY_AUDIENCE row tool=%s aud=%s",
                                        toolName, audienceStr)
                                .isNotNull();
                        assertThat(tee.getCause().getMessage())
                                .as("Exact audience violation message for tool=%s aud=%s", toolName, audienceStr)
                                .isEqualTo(expectedMsg);
                    });

            case DENY_STORE_SCOPE -> assertThatThrownBy(() -> tc.call(json))
                    .isInstanceOf(ToolExecutionException.class)
                    .satisfies(ex -> {
                        ToolExecutionException tee = (ToolExecutionException) ex;
                        assertThat(tee.getCause())
                                .as("ToolExecutionException cause must be StoreScopeViolationException for tool=%s aud=%s",
                                        toolName, audienceStr)
                                .isInstanceOf(StoreScopeViolationException.class);
                        // REQ-05 ROUND-3: expectedMsg is REQUIRED for every DENY_STORE_SCOPE row.
                        assertThat(expectedMsg)
                                .as("expectedMsg must not be null for DENY_STORE_SCOPE row tool=%s aud=%s",
                                        toolName, audienceStr)
                                .isNotNull();
                        assertThat(tee.getCause().getMessage())
                                .as("Exact store scope violation message for tool=%s aud=%s", toolName, audienceStr)
                                .isEqualTo(expectedMsg);
                    });

            case REJECTED_PAYLOAD_IDENTITY -> {
                // start_order returns a JSON response — no exception is thrown.
                // REQ-05 ROUND-3: expectedMsg is REQUIRED for every REJECTED_PAYLOAD_IDENTITY row.
                assertThat(expectedMsg)
                        .as("expectedMsg must not be null for REJECTED_PAYLOAD_IDENTITY row tool=%s aud=%s",
                                toolName, audienceStr)
                        .isNotNull();
                String rejectedResult = tc.call(json);
                assertThat(rejectedResult).as("start_order must return a JSON payload").isNotNull();
                JsonNode node = new ObjectMapper().readTree(rejectedResult);
                // Pin ALL fields of a rejected StartOrderResponse by exact value (round-3).
                assertThat(node.path("success").asBoolean())
                        .as("start_order IDENTITY_REFUSAL must have success=false for aud=%s", audienceStr)
                        .isFalse();
                assertThat(node.path("message").asText())
                        .as("Exact IDENTITY_REFUSAL message in start_order payload for aud=%s", audienceStr)
                        .isEqualTo(expectedMsg);
                assertThat(node.path("orderId").isNull())
                        .as("start_order IDENTITY_REFUSAL must have null orderId for aud=%s", audienceStr)
                        .isTrue();
                assertThat(node.path("orderStatus").isNull())
                        .as("start_order IDENTITY_REFUSAL must have null orderStatus for aud=%s", audienceStr)
                        .isTrue();
                assertThat(node.path("estimatedTotal").asDouble())
                        .as("start_order IDENTITY_REFUSAL must have estimatedTotal=0.0 for aud=%s", audienceStr)
                        .isEqualTo(0.0);
                assertThat(node.path("idempotent").asBoolean())
                        .as("start_order IDENTITY_REFUSAL must have idempotent=false for aud=%s", audienceStr)
                        .isFalse();
            }

            case ALLOW -> {
                // Security gate must be passed — any domain exception wrapped in ToolExecutionException
                // is acceptable as long as its cause is NOT a security exception.
                // ROUND-3 (item 5): capture the result for downstream result-based assertions.
                // The RuntimeException swallow that existed in round-2 is removed: the only rows
                // that hit it were find_order_by_id ALLOW rows where Order.basket was uninitialised,
                // causing Spring AI JSON serialisation to fail. buildCallerOwnedOrder() now initialises
                // basket, so those rows return normally. Any remaining RuntimeException that is NOT
                // a security violation is now a test failure — it means a stub is missing or broken.
                String allowResult = null;
                try {
                    allowResult = tc.call(json);
                } catch (ToolExecutionException e) {
                    if (e.getCause() instanceof CallerIdentityViolationException) {
                        throw new AssertionError(
                            "ALLOW row for tool=" + toolName + " aud=" + audienceStr +
                            " must not throw CallerIdentityViolationException but got: " + e.getMessage(), e);
                    }
                    if (e.getCause() instanceof AudienceViolationException) {
                        throw new AssertionError(
                            "ALLOW row for tool=" + toolName + " aud=" + audienceStr +
                            " must not throw AudienceViolationException but got: " + e.getMessage(), e);
                    }
                    if (e.getCause() instanceof StoreScopeViolationException) {
                        throw new AssertionError(
                            "ALLOW row for tool=" + toolName + " aud=" + audienceStr +
                            " must not throw StoreScopeViolationException but got: " + e.getMessage(), e);
                    }
                    // Domain exception wrapped in ToolExecutionException — security gate was passed.
                    // allowResult stays null; verifyDownstreamInteractionFor handles null result.
                }
                // Non-vacuity (round-3): security gate passed → verify a downstream collaborator OR
                // assert the concrete returned result proves domain code ran past the gate.
                verifyDownstreamInteractionFor(toolName, allowResult);
            }
        }

        // FIX-03: pin audit-service call expectations for every matrix cell.
        // Kotlin default params (agentName, conversationId) do NOT generate Java overloads without
        // @JvmOverloads, so verify must use the full 7-parameter signature.
        if (expectAuditCall) {
            verify(agentIdentityAuditService, atLeastOnce())
                    .recordViolation(any(), any(), any(), anyString(), anyString(), any(), any());
        } else {
            verify(agentIdentityAuditService, never())
                    .recordViolation(any(), any(), any(), anyString(), anyString(), any(), any());
        }
    }

    // ==================================================================================
    // ADR-026 Phase 2b: create_user absence assertion
    // ==================================================================================

    /**
     * ADR-026 Phase 2b (Lindani 2026-11-05): create_user must not appear in the tool callback
     * map. The tool was removed from the MCP surface entirely; user creation is REST/internal only.
     */
    @org.junit.jupiter.api.Test
    void createUserToolIsAbsentFromMcpSurface() {
        assertThat(toolCallbacks.get("create_user"))
                .as("create_user must NOT be registered as an MCP tool callback " +
                    "(ADR-026 Phase 2b, Lindani 2026-11-05)")
                .isNull();
    }

    // ==================================================================================
    // Helper methods
    // ==================================================================================

    private static final OutcomeType DENY          = OutcomeType.DENY_IDENTITY_REFUSAL;
    private static final OutcomeType ALLOW         = OutcomeType.ALLOW;
    private static final OutcomeType REJECTED      = OutcomeType.REJECTED_PAYLOAD_IDENTITY;
    private static final OutcomeType DENY_AUDIENCE = OutcomeType.DENY_AUDIENCE;
    /** StoreScopeViolationException — STORE requesting a storeId outside its permitted scope. */
    private static final OutcomeType DENY_STORE    = OutcomeType.DENY_STORE_SCOPE;

    /**
     * Builds a matrix row as JUnit 5 Arguments with 8 elements.
     *
     * @param expectedMsg the EXACT exception/payload message for DENY/REJECTED rows; null for ALLOW rows.
     */
    private static Arguments row(String toolName, String audienceStr, String callerPhone,
                                 String permittedStoreId, String json,
                                 OutcomeType expected, boolean expectAuditCall, String expectedMsg) {
        return Arguments.of(toolName, audienceStr, callerPhone, permittedStoreId,
                json, expected, expectAuditCall, expectedMsg);
    }

    /**
     * Verifies that at least one downstream collaborator beyond the security gate was invoked,
     * or asserts the concrete returned result proves domain code ran past the gate.
     *
     * <p>Called only for ALLOW rows. Proves the test is non-vacuous: the tool call actually
     * reached domain code, not just the security gate.
     *
     * <p>ROUND-3 (item 4): For tools where the first collaborator call runs BEFORE the ownership
     * gate, we cannot use that collaborator as the proof. Instead we assert the returned JSON
     * result does not contain "IDENTITY_REFUSAL:" — proving the gate did not reject the call.
     * Gate positions are cited by file:line in each case comment.
     *
     * <p>ROUND-3 (item 3): The {@code default} branch now fails the test rather than silently
     * doing nothing, so any new tool added to the matrix without a downstream verifier is caught.
     *
     * @param toolName    the MCP tool name from the matrix row
     * @param allowResult the JSON string returned by {@link org.springframework.ai.tool.ToolCallback#call},
     *                    or {@code null} if the call threw a domain-level ToolExecutionException
     */
    private void verifyDownstreamInteractionFor(String toolName, String allowResult) {
        switch (toolName) {
            // ---- phone / profile tools: findByMobileNumber is called AFTER validateCallerPhone ----
            // Gate: CallerIdentityValidator.validateCallerPhone (UserProfileService.kt:60,
            //       OrderServiceImpl.java:778). findByMobileNumber called post-gate
            //       (UserProfileService.kt:73, OrderServiceImpl.java:793).
            case "find_user_by_phone":
            case "get_missing_fields_by_phone":
            case "get_required_documents_for_user":
            case "create_document_upload_session":
            case "attach_uploaded_document_to_user_tag":
            case "recheck_required_documents":
            case "find_orders_by_phone_number":
                verify(userProfileRepo, atLeastOnce()).findByMobileNumber(anyString());
                break;

            case "find_users":
                // Gate: CallerIdentityValidator.validateCallerOwnsId (UserProfileService.kt — ADMIN-only).
                // findByRoleAndServiceType... is the main query, called post-gate.
                verify(userProfileRepo, atLeastOnce())
                        .findByRoleAndServiceTypeAndLatitudeBetweenAndLongitudeBetween(
                                any(), any(), anyDouble(), anyDouble(), anyDouble(), anyDouble());
                break;

            case "find_order_by_id":
                // ROUND-3 (item 4): orderRepo.findById runs BEFORE validateCallerOwnsId
                // (OrderServiceImpl.java:572 before :578). Cannot use findById as proof.
                // The returned JSON (or null for order-not-found rows) proves domain code ran;
                // absence of "IDENTITY_REFUSAL:" proves the security gate did not reject.
                if (allowResult != null) {
                    assertThat(allowResult)
                            .as("find_order_by_id ALLOW result must not be an IDENTITY_REFUSAL " +
                                "(gate: OrderServiceImpl.java:578)")
                            .doesNotContain("IDENTITY_REFUSAL:");
                }
                break;

            case "find_order_for_messenger":
                // ROUND-3 (item 4): orderRepo.findById runs BEFORE validateCallerOwnsId
                // (OrderServiceImpl.java:742 before :747/760). Cannot use findById as proof.
                if (allowResult != null) {
                    assertThat(allowResult)
                            .as("find_order_for_messenger ALLOW result must not be an IDENTITY_REFUSAL " +
                                "(gate: OrderServiceImpl.java:747/760)")
                            .doesNotContain("IDENTITY_REFUSAL:");
                }
                break;

            case "find_orders_by_user_id":
                // Gate: StoreScopeValidator.validateCallerOwnsId (OrderServiceImpl.java:711, first line).
                // findByCustomerId at :721 is called post-gate.
                verify(orderRepo, atLeastOnce()).findByCustomerId(anyString());
                break;

            case "find_orders_by_messenger_id":
                // Gate: audience check + validateCallerOwnsId (OrderServiceImpl.java:812-834).
                // findByShippingDataMessengerIdAndStageNot at :852 is called post-gate.
                verify(orderRepo, atLeastOnce())
                        .findByShippingDataMessengerIdAndStageNot(anyString(), any());
                break;

            case "start_order":
                // ROUND-3 (item 4): userProfileRepo.existsById runs BEFORE validateCallerOwnsId
                // in BOTH McpOrderService.java:141 AND OrderServiceImpl.java:157 before :164.
                // Cannot use existsById as proof.
                // All start_order ALLOW rows return a StartOrderResponse JSON — assert it is
                // not an IDENTITY_REFUSAL, proving the audience gate (McpOrderService.java:109-126)
                // and the ownership gate (OrderServiceImpl.java:164) did not reject.
                assertThat(allowResult)
                        .as("start_order ALLOW result must not contain IDENTITY_REFUSAL: " +
                            "(audience gate: McpOrderService.java:109, ownership gate: OrderServiceImpl.java:164)")
                        .doesNotContain("IDENTITY_REFUSAL:");
                break;

            case "find_store_or_shops_by_id":
                // Gate: StoreScopeValidator.validate + validateAudience (StoreMcpController.java).
                // storeRepository.findById is called post-gate via storeService.find(id).
                verify(storeRepository, atLeastOnce()).findById(anyString());
                break;

            case "find_stores_by_owner":
                // Gate: audience switch (StoreMcpController.java).
                // findByOwnerId is called post-gate via storeService.findByOwner(ownerId).
                verify(storeRepository, atLeastOnce()).findByOwnerId(anyString());
                break;

            case "find_stores":
                // Gate: validateAudience (StoreMcpController.java).
                // findAll is called post-gate via storeService.findStores(...).
                verify(storeRepository, atLeastOnce()).findAll();
                break;

            case "get_payouts_for_user":
                // Gate: StoreScopeValidator.validateCallerOwnsId (PayoutMcpController, Phase 5).
                // findByModifiedDateBetweenAndToId is called post-gate on the ungated ReconServiceImpl.
                verify(messengerPayoutRepository, atLeastOnce())
                        .findByModifiedDateBetweenAndToId(any(), any(), anyString());
                break;

            default:
                // ROUND-3 (item 3): every tool in the matrix must have a downstream verifier.
                // If this branch is reached, the matrix has a new tool with no verifier — fix it.
                fail("verifyDownstreamInteractionFor: no downstream verifier registered for tool='"
                        + toolName + "'. Add a case with a post-gate collaborator verify or result assertion.");
        }
    }

    /**
     * Sets per-request context exactly as {@link io.curiousoft.izinga.ordermanagement.security.StoreScopeValidationFilter}
     * does on a real {@code /mcp} request.
     */
    private void setMcpContext(String audienceStr, String callerPhone, String permittedStoreId) {
        // SEC-MCP-01: StoreScopeValidationFilter calls setMcpRequest(true) UNCONDITIONALLY before
        // token parsing. All /mcp requests (including tokenless ones) have isMcpRequest=true.
        CallerIdentityContext.setMcpRequest(true);

        Audience audience = audienceStr != null ? Audience.valueOf(audienceStr) : null;
        StoreScopeContext.setAudience(audience);
        StoreScopeContext.setCallerPhone(callerPhone);
        if (permittedStoreId != null) {
            StoreScopeContext.setPermittedStoreId(permittedStoreId);
        }
    }

    private UserProfile buildCallerProfile() {
        UserProfile p = new UserProfile(
                "Caller", UserProfile.SignUpReason.BUY,
                "1 Main St", "https://img.test/1.png",
                CALLER_PHONE, ProfileRoles.CUSTOMER);
        p.setId(CALLER_USER_ID);
        return p;
    }

    private Order buildCallerOwnedOrder() {
        Order o = new Order();
        o.setId(CALLER_ORDER_ID);
        o.setCustomerId(CALLER_USER_ID);
        o.setShopId("shop-1");
        ShippingData sd = new ShippingData("From", "To", ShippingData.ShippingType.DELIVERY);
        sd.setMessengerId(CALLER_USER_ID);
        o.setShippingData(sd);
        // ROUND-3 (item 5): Order.kt declares `lateinit var basket: Basket`. Spring AI wraps
        // the uninitialized-property access as IllegalStateException("Conversion from Object to
        // JSON failed") when the tool callback tries to serialise the returned Order. Initialise
        // basket here so find_order_by_id ALLOW rows can return a serialisable Order.
        o.setBasket(new Basket());
        return o;
    }

    private Order buildOtherOwnedOrder() {
        Order o = new Order();
        o.setId(OTHER_ORDER_ID);
        o.setCustomerId(OTHER_USER_ID);
        o.setShopId("shop-2");
        ShippingData sd = new ShippingData("From2", "To2", ShippingData.ShippingType.DELIVERY);
        sd.setMessengerId(OTHER_USER_ID);
        o.setShippingData(sd);
        // ROUND-3 (item 5): same basket initialisation as buildCallerOwnedOrder().
        o.setBasket(new Basket());
        return o;
    }

    private StoreProfile buildStoreOwnedBy(String ownerId) {
        StoreProfile sp = new StoreProfile(
                StoreType.FOOD, "Test Store", "1 Store St",
                "https://img.test/store.png", "test-store",
                "0812345678",
                new ArrayList<>(), new ArrayList<>(),
                ownerId, new Bank());
        sp.setId(PERMITTED_STORE_ID);
        return sp;
    }

    // ---- JSON input helpers ----

    private static String docSessionJson(String phone) {
        return "{\"phone\":\"" + phone + "\",\"fieldName\":\"identityDocument\"," +
               "\"originalFileName\":\"id.jpg\",\"mimeType\":\"image/jpeg\"}";
    }

    private static String attachJson(String phone) {
        return "{\"phone\":\"" + phone + "\",\"fieldName\":\"identityDocument\"," +
               "\"fileUrl\":\"https://lookaside.fbsbx.com/file.pdf\"}";
    }
}
