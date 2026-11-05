package io.curiousoft.izinga.ordermanagement;

import io.curiousoft.izinga.commons.repo.IcaAcceptanceLogRepo;
import io.curiousoft.izinga.commons.repo.UserProfileRepo;
import io.curiousoft.izinga.commons.security.AgentIdentityAuditService;
import io.curiousoft.izinga.ordermanagement.mcp.DocumentUploadMcpController;
import io.curiousoft.izinga.ordermanagement.mcp.GuardedToolCallback;
import io.curiousoft.izinga.ordermanagement.mcp.McpCaller;
import io.curiousoft.izinga.ordermanagement.mcp.McpToolController;
import io.curiousoft.izinga.ordermanagement.mcp.OrderCreationMcpController;
import io.curiousoft.izinga.ordermanagement.mcp.UserMcpController;
import io.curiousoft.izinga.ordermanagement.orders.McpOrderService;
import io.curiousoft.izinga.ordermanagement.orders.OrderServiceImpl;
import io.curiousoft.izinga.ordermanagement.stores.StoreService;
import io.curiousoft.izinga.recon.ReconServiceImpl;
import io.curiousoft.izinga.usermanagement.referral.ReferralCodeService;
import io.curiousoft.izinga.usermanagement.userconfig.UserConfigService;
import io.curiousoft.izinga.usermanagement.users.DocumentUploadMcpService;
import io.curiousoft.izinga.usermanagement.users.UserProfileService;

import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.ToolCallbackProvider;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationEventPublisher;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

/**
 * ADR-026 D5 Gap-C: Unit tests for {@link McpConfig#userProfileToolCallbackProvider}.
 *
 * <p>Verifies:
 * <ol>
 *   <li>Zero controllers (Phase 1): exactly 13 legacy tools, none wrapped in {@link GuardedToolCallback}.</li>
 *   <li>One controller bean: its callback IS wrapped in {@link GuardedToolCallback}; legacy callbacks are not.</li>
 *   <li>Duplicate tool name: {@link IllegalStateException} thrown by Spring AI.</li>
 *   <li>AC-03 (Phase 1): wiring real controllers produces 18 tools total; 5 controller tools are guarded.</li>
 * </ol>
 */
@ExtendWith(MockitoExtension.class)
class McpConfigTest {

    // ---- Mocked service collaborators (all legacy — never mocked controllers) ------------

    @Mock private UserProfileRepo            userProfileRepo;
    @Mock private ApplicationEventPublisher  eventPublisher;
    @Mock private UserConfigService          userConfig;
    @Mock private IcaAcceptanceLogRepo       icaAcceptanceLogRepo;
    @Mock private ReferralCodeService        referralCodeService;
    @Mock private AgentIdentityAuditService  agentIdentityAuditService;

    @Mock private OrderServiceImpl           orderService;
    @Mock private ReconServiceImpl           reconService;
    @Mock private StoreService               storeService;
    @Mock private DocumentUploadMcpService   documentUploadMcpService;
    @Mock private McpOrderService            mcpOrderService;

    @Mock private ApplicationContext         applicationContext;

    // ---- Class under test ---------------------------------------------------------------

    private McpConfig mcpConfig;
    private UserProfileService userProfileService;

    @BeforeEach
    void setUp() {
        userProfileService = new UserProfileService(
                userProfileRepo, eventPublisher, userConfig,
                icaAcceptanceLogRepo, referralCodeService);
        mcpConfig = new McpConfig();
    }

    // ---- Phase 4: zero controllers → 1 legacy definition (ReconServiceImpl only), none wrapped --

    @Test
    void zeroControllers_returns1Tool_noneGuarded() {
        when(applicationContext.getBeansWithAnnotation(McpToolController.class))
                .thenReturn(Collections.emptyMap());

        ToolCallbackProvider provider = mcpConfig.userProfileToolCallbackProvider(
                reconService, applicationContext);

        ToolCallback[] callbacks = provider.getToolCallbacks();
        // Phase 4 complete: only ReconServiceImpl retains @Tool — 1 legacy tool remains
        assertThat(callbacks).hasSize(1);

        for (ToolCallback tc : callbacks) {
            assertThat(tc)
                    .as("Legacy-only config: no callback should be wrapped in GuardedToolCallback (tool: %s)",
                            tc.getToolDefinition().name())
                    .isNotInstanceOf(GuardedToolCallback.class);
        }
    }

    // ---- Phase 1+: controller bean → its callback IS GuardedToolCallback, legacy not --

    /**
     * Minimal in-memory {@link McpToolController} bean with one tool named
     * {@code test_controller_tool}. Must use a name that does NOT clash with the 1
     * legacy tool (get_payouts_for_user).
     */
    @McpToolController
    static class TestMcpController {
        @Tool(name = "test_controller_tool", description = "controller tool for testing")
        public String testControllerTool(String input) {
            return input;
        }
    }

    @Test
    void oneControllerBean_itsCallbackIsGuarded_legacyCallbacksAreNot() {
        TestMcpController controller = new TestMcpController();
        Map<String, Object> controllerBeans = new HashMap<>();
        controllerBeans.put("testMcpController", controller);
        when(applicationContext.getBeansWithAnnotation(McpToolController.class))
                .thenReturn(controllerBeans);

        ToolCallbackProvider provider = mcpConfig.userProfileToolCallbackProvider(
                reconService, applicationContext);

        ToolCallback[] callbacks = provider.getToolCallbacks();
        // 1 legacy (ReconServiceImpl) + 1 test controller = 2
        assertThat(callbacks).hasSize(2);

        for (ToolCallback tc : callbacks) {
            String name = tc.getToolDefinition().name();
            if ("test_controller_tool".equals(name)) {
                assertThat(tc)
                        .as("Controller tool must be wrapped in GuardedToolCallback")
                        .isInstanceOf(GuardedToolCallback.class);
            } else {
                assertThat(tc)
                        .as("Legacy tool '%s' must NOT be wrapped in GuardedToolCallback", name)
                        .isNotInstanceOf(GuardedToolCallback.class);
            }
        }
    }

    // ---- Duplicate tool name → IllegalStateException from Spring AI -------------------

    /**
     * Controller that declares a tool whose name clashes with the one remaining legacy tool.
     * ADR-026 Phase 4: only ReconServiceImpl retains @Tool, so the only legacy name is
     * get_payouts_for_user.
     */
    @McpToolController
    static class DuplicateNameController {
        @Tool(name = "get_payouts_for_user", description = "duplicate of the remaining legacy tool")
        public String getPayoutsForUser(String userId) {
            return userId;
        }
    }

    @Test
    void duplicateToolName_throwsIllegalStateException() {
        DuplicateNameController controller = new DuplicateNameController();
        Map<String, Object> controllerBeans = new HashMap<>();
        controllerBeans.put("duplicateNameController", controller);
        when(applicationContext.getBeansWithAnnotation(McpToolController.class))
                .thenReturn(controllerBeans);

        assertThatThrownBy(() ->
                mcpConfig.userProfileToolCallbackProvider(
                reconService, applicationContext)
        ).isInstanceOf(IllegalStateException.class);
    }

    // ---- AC-03 (Phase 2): real controllers → 18 tools total, 9 controller tools guarded ----

    /**
     * AC-03: Wiring all Phase 1+2 real controllers (OrderCreationMcpController +
     * DocumentUploadMcpController + UserMcpController) produces exactly 10 tools in total
     * (1 legacy from ReconServiceImpl + 9 migrated), and each of the 9 controller tools is
     * wrapped in {@link GuardedToolCallback} while the legacy tool is not.
     */
    @Test
    void phase2RealControllers_returns10Tools_9controllerToolsGuarded() {
        McpCaller mcpCaller = new McpCaller(userProfileRepo);
        OrderCreationMcpController orderCreationController =
                new OrderCreationMcpController(mcpOrderService, mcpCaller, agentIdentityAuditService);
        DocumentUploadMcpController documentUploadController =
                new DocumentUploadMcpController(documentUploadMcpService, agentIdentityAuditService);
        UserMcpController userMcpController =
                new UserMcpController(userProfileService, agentIdentityAuditService);

        Map<String, Object> controllerBeans = new HashMap<>();
        controllerBeans.put("orderCreationMcpController", orderCreationController);
        controllerBeans.put("documentUploadMcpController", documentUploadController);
        controllerBeans.put("userMcpController", userMcpController);
        when(applicationContext.getBeansWithAnnotation(McpToolController.class))
                .thenReturn(controllerBeans);

        ToolCallbackProvider provider = mcpConfig.userProfileToolCallbackProvider(
                reconService, applicationContext);

        ToolCallback[] callbacks = provider.getToolCallbacks();
        // 1 legacy (ReconServiceImpl) + 9 Phase 1+2 controllers = 10
        assertThat(callbacks).hasSize(10);

        Set<String> controllerToolNames = Set.of(
                // Phase 1
                "start_order",
                "get_required_documents_for_user",
                "create_document_upload_session",
                "attach_uploaded_document_to_user_tag",
                "recheck_required_documents",
                // Phase 2
                "find_user_by_phone",
                "find_users",
                "create_user",
                "get_missing_fields_by_phone");

        for (ToolCallback tc : callbacks) {
            String name = tc.getToolDefinition().name();
            if (controllerToolNames.contains(name)) {
                assertThat(tc)
                        .as("Controller tool '%s' must be wrapped in GuardedToolCallback", name)
                        .isInstanceOf(GuardedToolCallback.class);
            } else {
                assertThat(tc)
                        .as("Legacy tool '%s' must NOT be wrapped in GuardedToolCallback", name)
                        .isNotInstanceOf(GuardedToolCallback.class);
            }
        }
    }
}
