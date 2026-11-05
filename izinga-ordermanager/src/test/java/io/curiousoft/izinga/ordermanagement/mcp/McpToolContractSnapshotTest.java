package io.curiousoft.izinga.ordermanagement.mcp;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import io.curiousoft.izinga.commons.model.Bank;
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
import io.curiousoft.izinga.messaging.AdminOnlyNotificationService;
import io.curiousoft.izinga.messaging.firebase.FirebaseNotificationService;
import io.curiousoft.izinga.ordermanagement.McpConfig;
import io.curiousoft.izinga.ordermanagement.leads.LeadService;
import io.curiousoft.izinga.ordermanagement.mcp.DocumentUploadMcpController;
import io.curiousoft.izinga.ordermanagement.mcp.McpCaller;
import io.curiousoft.izinga.ordermanagement.mcp.OrderCreationMcpController;
import io.curiousoft.izinga.ordermanagement.notification.EmailNotificationService;
import io.curiousoft.izinga.ordermanagement.orders.McpOrderService;
import io.curiousoft.izinga.ordermanagement.orders.OrderServiceImpl;
import io.curiousoft.izinga.ordermanagement.orders.RestrictedRegionService;
import io.curiousoft.izinga.ordermanagement.orders.quote.OrderQuoteRepository;
import io.curiousoft.izinga.ordermanagement.promocodes.PromoCodeClient;
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
import io.curiousoft.izinga.usermanagement.users.DocumentUploadMcpService;
import io.curiousoft.izinga.usermanagement.users.DocumentUploadSessionRepo;
import io.curiousoft.izinga.usermanagement.users.UserProfileService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.ToolCallbackProvider;
import org.springframework.ai.tool.definition.ToolDefinition;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.test.util.ReflectionTestUtils;

import java.io.IOException;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Fail.fail;
import static org.mockito.Mockito.when;

/**
 * ADR-026 D5 REQ-04: Contract snapshot test for all 18 MCP tool definitions.
 *
 * <p><strong>Golden file:</strong> {@code src/test/resources/mcp/tool-contract-snapshot.json}.
 * <br>To regenerate: {@code ./mvnw -pl izinga-ordermanager -am test -Dmcp.contract.update=true}</p>
 *
 * <p>The golden file is sorted by tool name. Any byte difference in name, description,
 * inputSchema, or inputSchemaRaw fails the build. This ensures tool contract stability for
 * external AI agents and the WhatsApp integration.</p>
 *
 * <p>The test uses the SAME {@link McpConfig} factory as production — so any class-level
 * or annotation-level contract change that changes the generated schema is caught.</p>
 *
 * <p><strong>REQ-04 (round 2):</strong> The snapshot also captures {@code inputSchemaRaw}:
 * the raw string exactly as {@link ToolDefinition#inputSchema()} returns it, byte-for-byte.
 * This catches any whitespace or key-ordering change in the schema string that the parsed
 * JSON node would absorb silently.</p>
 *
 * <p><strong>Item 6 (round 2):</strong> The provider is built from REAL service instances
 * with mocked collaborators — not from mocked services. This ensures that any change to a
 * service's constructor or its {@code @Tool}-annotated methods is caught here.</p>
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class McpToolContractSnapshotTest {

    private static final String GOLDEN_FILE_RESOURCE = "/mcp/tool-contract-snapshot.json";
    private static final String UPDATE_FLAG = "mcp.contract.update";

    private static final int EXPECTED_TOOL_COUNT = 18;

    // ---- All 18 expected tool names (alphabetical) ----
    private static final List<String> EXPECTED_TOOL_NAMES = Arrays.asList(
            "attach_uploaded_document_to_user_tag",
            "create_document_upload_session",
            "create_user",
            "find_order_by_id",
            "find_order_for_messenger",
            "find_orders_by_messenger_id",
            "find_orders_by_phone_number",
            "find_orders_by_user_id",
            "find_store_or_shops_by_id",
            "find_stores",
            "find_stores_by_owner",
            "find_user_by_phone",
            "find_users",
            "get_missing_fields_by_phone",
            "get_payouts_for_user",
            "get_required_documents_for_user",
            "recheck_required_documents",
            "start_order"
    );

    // ---- UserProfileService collaborators (all @Mock) ------------------------------------
    @Mock private UserProfileRepo            userProfileRepo;
    @Mock private ApplicationEventPublisher  eventPublisher;
    @Mock private UserConfigService          userConfig;
    @Mock private IcaAcceptanceLogRepo       icaAcceptanceLogRepo;
    @Mock private ReferralCodeService        referralCodeService;
    @Mock private AgentIdentityAuditService  agentIdentityAuditService;
    @Mock private DocumentUploadSessionRepo  documentUploadSessionRepo;

    // ---- OrderServiceImpl collaborators ---------------------------------------------------
    @Mock private OrderRepository            orderRepo;
    @Mock private StoreRepository            storeRepository;
    @Mock private PaymentService             paymentService;
    @Mock private DeviceRepository           deviceRepo;
    @Mock private FirebaseNotificationService pushNotificationService;
    @Mock private AdminOnlyNotificationService smsNotification;
    @Mock private EmailNotificationService   emailNotificationService;
    @Mock private PromoCodeClient            promoCodeClient;
    @Mock private RestrictedRegionService    restrictedRegionService;
    @Mock private OrderQuoteRepository       orderQuoteRepository;
    @Mock private LeadService                leadService;
    @Mock private DeliveryCalculationService deliveryCalculationService;

    // ---- StoreService collaborators -------------------------------------------------------
    @Mock private StoreAgreementAuditRepository  storeAgreementAuditRepository;
    @Mock private StoreTierChangeAuditRepository storeTierChangeAuditRepository;
    @Mock private com.google.firebase.auth.FirebaseAuth firebaseAuth;
    @Mock private ApplicationContext applicationContext;

    // ---- ReconServiceImpl collaborators ---------------------------------------------------
    @Mock private ShopPayoutRepository                shopPayoutRepo;
    @Mock private MessengerPayoutRepository           messengerPayoutRepository;
    @Mock private AmbassadorPayoutRepository          ambassadorPayoutRepository;
    @Mock private ReferralPartnerPayoutRepository     referralPartnerPayoutRepository;
    @Mock private PayoutRepository                    payoutBundleRepository;
    @Mock private FoodCustomerReferralCommissionRepo  foodCustomerCommissionRepo;
    @Mock private FurnitureCustomerReferralCommissionRepo furnitureCustomerCommissionRepo;
    @Mock private StorePartnerStage1CommissionRepo    storeStage1CommissionRepo;
    @Mock private StorePartnerStage2CommissionRepo    storeStage2CommissionRepo;

    // ---- Lazily built provider (reset per test via @BeforeEach) --------------------------
    private ToolCallbackProvider provider;

    @BeforeEach
    void buildProvider() {
        // Build REAL service instances — this is the core of Item 6.
        // Any change to a service constructor, or to @Tool annotations on its methods,
        // will cause this setup to break or produce different tool definitions.
        UserProfileService userProfileService = new UserProfileService(
                userProfileRepo, eventPublisher, userConfig,
                icaAcceptanceLogRepo, referralCodeService, agentIdentityAuditService);

        // ADR-026 Phase 1: 3-param constructor (agentIdentityAuditService removed from service)
        DocumentUploadMcpService documentUploadMcpService = new DocumentUploadMcpService(
                userProfileService, userConfig, documentUploadSessionRepo);

        OrderServiceImpl orderService = new OrderServiceImpl(
                15.0, 3.0, 5.0, 0.025, 0.10, 5L,
                Collections.singletonList("0812815707"), "test-maps-key",
                orderRepo, storeRepository, userProfileRepo,
                paymentService, deviceRepo, pushNotificationService,
                smsNotification, emailNotificationService,
                promoCodeClient, eventPublisher,
                restrictedRegionService, orderQuoteRepository, leadService);
        ReflectionTestUtils.setField(orderService, "agentIdentityAuditService", agentIdentityAuditService);
        ReflectionTestUtils.setField(orderService, "deliveryCalculationService", deliveryCalculationService);

        McpOrderService mcpOrderService = new McpOrderService(
                orderService, userProfileRepo, orderRepo,
                new ObjectMapper(), 24L);

        StoreService storeService = new StoreService(
                storeRepository, userProfileRepo,
                "main-pay-account", 0.05,
                eventPublisher, referralCodeService,
                storeAgreementAuditRepository, storeTierChangeAuditRepository,
                firebaseAuth, agentIdentityAuditService);

        ReconServiceImpl reconService = new ReconServiceImpl(
                storeRepository, userProfileRepo, shopPayoutRepo,
                messengerPayoutRepository, ambassadorPayoutRepository,
                referralPartnerPayoutRepository, payoutBundleRepository,
                foodCustomerCommissionRepo, furnitureCustomerCommissionRepo,
                storeStage1CommissionRepo, storeStage2CommissionRepo,
                eventPublisher, new AmbassadorProperties(), agentIdentityAuditService);

        // ADR-026 Phase 1: wire real controllers so their tool definitions appear in the snapshot.
        McpCaller mcpCaller = new McpCaller(userProfileRepo);
        OrderCreationMcpController orderCreationController =
                new OrderCreationMcpController(mcpOrderService, mcpCaller, agentIdentityAuditService);
        DocumentUploadMcpController documentUploadController =
                new DocumentUploadMcpController(documentUploadMcpService, agentIdentityAuditService);

        java.util.Map<String, Object> controllerBeans = new java.util.HashMap<>();
        controllerBeans.put("orderCreationMcpController", orderCreationController);
        controllerBeans.put("documentUploadMcpController", documentUploadController);
        when(applicationContext.getBeansWithAnnotation(McpToolController.class))
                .thenReturn(controllerBeans);

        McpConfig mcpConfig = new McpConfig();
        provider = mcpConfig.userProfileToolCallbackProvider(
                userProfileService,
                orderService,
                reconService,
                storeService,
                applicationContext
        );
    }

    // ---- Main snapshot assertion ---------------------------------------------------------

    @Test
    void toolContractIsUnchanged() throws Exception {
        String actualJson = serialiseToolDefinitions(provider);

        boolean updateMode = "true".equalsIgnoreCase(System.getProperty(UPDATE_FLAG));

        if (updateMode) {
            writeGoldenFile(actualJson);
            fail("Golden file updated at src/test/resources/mcp/tool-contract-snapshot.json — " +
                    "commit it and re-run without -D" + UPDATE_FLAG);
        }

        String goldenJson = readGoldenFile();
        if (!actualJson.equals(goldenJson)) {
            fail("MCP tool contract has changed — this means tool definitions visible to AI agents " +
                    "will change. This is intentional only when moving tools to @McpToolController " +
                    "in later ADR-026 phases, or when a tool description/schema is explicitly updated.\n\n" +
                    "EXPECTED (golden):\n" + goldenJson + "\n\n" +
                    "ACTUAL:\n" + actualJson + "\n\n" +
                    "If the change is intentional, regenerate with: " +
                    "./mvnw -pl izinga-ordermanager -am test -D" + UPDATE_FLAG + "=true");
        }
    }

    // ---- Structural assertions (independent of golden file) -----------------------------

    @Test
    void providerHasExactly18Tools() {
        ToolCallback[] tools = provider.getToolCallbacks();
        assertThat(tools)
                .as("Expected exactly %d MCP tools", EXPECTED_TOOL_COUNT)
                .hasSize(EXPECTED_TOOL_COUNT);
    }

    @Test
    void allExpectedToolNamesArePresent() {
        List<String> actualNames = Arrays.stream(provider.getToolCallbacks())
                .map(tc -> tc.getToolDefinition().name())
                .sorted()
                .collect(Collectors.toList());

        assertThat(actualNames)
                .as("Tool names visible to AI agents must match expected set exactly")
                .containsExactlyInAnyOrderElementsOf(EXPECTED_TOOL_NAMES);
    }

    @Test
    void noToolHasNullOrEmptyDescription() {
        for (ToolCallback tc : provider.getToolCallbacks()) {
            ToolDefinition def = tc.getToolDefinition();
            assertThat(def.description())
                    .as("Tool %s must have a non-null, non-empty description", def.name())
                    .isNotNull()
                    .isNotBlank();
        }
    }

    @Test
    void noToolHasNullOrEmptyInputSchema() {
        for (ToolCallback tc : provider.getToolCallbacks()) {
            ToolDefinition def = tc.getToolDefinition();
            assertThat(def.inputSchema())
                    .as("Tool %s must have a non-null, non-empty inputSchema", def.name())
                    .isNotNull()
                    .isNotBlank();
        }
    }

    // ---- Serialization helpers ----------------------------------------------------------

    /**
     * Serialises all ToolDefinitions from the provider to canonical pretty-printed JSON,
     * sorted alphabetically by tool name.
     *
     * <p>REQ-04 (round 2): Each entry carries two schema fields:
     * <ul>
     *   <li>{@code inputSchema}: the schema parsed as a JSON node — human-readable diffs.</li>
     *   <li>{@code inputSchemaRaw}: the raw string exactly as {@link ToolDefinition#inputSchema()}
     *       returns it. Any whitespace or key-ordering change that the parsed node absorbs
     *       silently is caught here.</li>
     * </ul>
     */
    private String serialiseToolDefinitions(ToolCallbackProvider prov) throws Exception {
        ObjectMapper mapper = new ObjectMapper().enable(SerializationFeature.INDENT_OUTPUT);

        List<ToolCallback> sorted = Arrays.stream(prov.getToolCallbacks())
                .sorted(Comparator.comparing(tc -> tc.getToolDefinition().name()))
                .collect(Collectors.toList());

        ArrayNode array = mapper.createArrayNode();
        for (ToolCallback tc : sorted) {
            ToolDefinition def = tc.getToolDefinition();
            ObjectNode entry = mapper.createObjectNode();
            entry.put("name", def.name());
            entry.put("description", def.description());
            // Embed inputSchema as a JSON node (not a string) for human-readable diffs
            JsonNode schemaNode = mapper.readTree(def.inputSchema());
            entry.set("inputSchema", schemaNode);
            // REQ-04 (round 2): also record the raw schema string byte-for-byte
            entry.put("inputSchemaRaw", def.inputSchema());
            array.add(entry);
        }
        return mapper.writeValueAsString(array) + "\n";
    }

    private String readGoldenFile() throws Exception {
        URL resource = getClass().getResource(GOLDEN_FILE_RESOURCE);
        if (resource == null) {
            fail("Golden file is MISSING: src/test/resources/mcp/tool-contract-snapshot.json — " +
                    "generate it with: ./mvnw -pl izinga-ordermanager -am test -D" + UPDATE_FLAG + "=true");
            return null; // unreachable
        }
        return new String(resource.openStream().readAllBytes(), java.nio.charset.StandardCharsets.UTF_8);
    }

    private void writeGoldenFile(String json) throws IOException {
        // Write to src/test/resources, not target — the file must be committed to VCS
        URL codeSource = getClass().getProtectionDomain().getCodeSource().getLocation();
        // codeSource points to target/test-classes; go up two levels to find the module root
        Path moduleRoot;
        try {
            moduleRoot = Paths.get(codeSource.toURI()).getParent().getParent();
        } catch (java.net.URISyntaxException e) {
            throw new IOException("Could not resolve module root from code source location: " + codeSource, e);
        }
        Path goldenDir = moduleRoot.resolve("src/test/resources/mcp");
        Files.createDirectories(goldenDir);
        Path goldenFile = goldenDir.resolve("tool-contract-snapshot.json");
        Files.writeString(goldenFile, json, java.nio.charset.StandardCharsets.UTF_8);
        System.out.println("[McpToolContractSnapshotTest] Golden file written to: " + goldenFile.toAbsolutePath());
    }
}
