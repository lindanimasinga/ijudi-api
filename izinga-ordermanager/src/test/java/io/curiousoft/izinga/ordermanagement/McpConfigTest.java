package io.curiousoft.izinga.ordermanagement;

import io.curiousoft.izinga.commons.repo.IcaAcceptanceLogRepo;
import io.curiousoft.izinga.commons.repo.UserProfileRepo;
import io.curiousoft.izinga.commons.security.AgentIdentityAuditService;
import io.curiousoft.izinga.ordermanagement.mcp.DocumentUploadMcpController;
import io.curiousoft.izinga.ordermanagement.mcp.GuardedToolCallback;
import io.curiousoft.izinga.ordermanagement.mcp.McpCaller;
import io.curiousoft.izinga.ordermanagement.mcp.McpToolController;
import io.curiousoft.izinga.ordermanagement.mcp.OrderCreationMcpController;
import io.curiousoft.izinga.ordermanagement.mcp.OrderMcpController;
import io.curiousoft.izinga.ordermanagement.mcp.PayoutMcpController;
import io.curiousoft.izinga.ordermanagement.mcp.StoreMcpController;
import io.curiousoft.izinga.ordermanagement.mcp.UserMcpController;
import io.curiousoft.izinga.ordermanagement.orders.McpOrderService;
import io.curiousoft.izinga.ordermanagement.orders.OrderServiceImpl;
import io.curiousoft.izinga.ordermanagement.stores.StoreService;
import io.curiousoft.izinga.recon.ReconService;
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
 * ADR-026 Phase 6 (D5 / T-07): Unit tests for {@link McpConfig#userProfileToolCallbackProvider}.
 *
 * <p>Phase 6 lock-down: the legacy tool-object list is gone. ALL 17 tools live on
 * {@link McpToolController} beans (create_user removed from the MCP surface in Phase 2b,
 * Lindani 2026-11-05). The bean method now takes only {@link ApplicationContext};
 * it collects {@code @McpToolController} beans, fails fast on duplicate names, and wraps
 * EVERY callback unconditionally in {@link GuardedToolCallback}.
 *
 * <p>Verifies:
 * <ol>
 *   <li>Zero controllers: 0 tools, empty array — no legacy tools remain.</li>
 *   <li>One controller bean: its callback IS wrapped in {@link GuardedToolCallback}.</li>
 *   <li>Duplicate tool name: {@link IllegalStateException} thrown.</li>
 *   <li>All real controllers: 18 tools total, all wrapped in {@link GuardedToolCallback}.</li>
 * </ol>
 */
@ExtendWith(MockitoExtension.class)
class McpConfigTest {

    // ---- Mocked service collaborators ---------------------------------------------------

    @Mock private UserProfileRepo            userProfileRepo;
    @Mock private ApplicationEventPublisher  eventPublisher;
    @Mock private UserConfigService          userConfig;
    @Mock private IcaAcceptanceLogRepo       icaAcceptanceLogRepo;
    @Mock private ReferralCodeService        referralCodeService;
    @Mock private AgentIdentityAuditService  agentIdentityAuditService;

    @Mock private OrderServiceImpl           orderService;
    @Mock private ReconService               reconService;
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

    // ---- Phase 6: zero controllers → 0 tools, empty array -------------------------------

    @Test
    void zeroControllers_returns0Tools_emptyArray() {
        when(applicationContext.getBeansWithAnnotation(McpToolController.class))
                .thenReturn(Collections.emptyMap());

        ToolCallbackProvider provider = mcpConfig.userProfileToolCallbackProvider(applicationContext);

        ToolCallback[] callbacks = provider.getToolCallbacks();
        // Phase 6: no legacy tools, no controller beans → empty
        assertThat(callbacks).isEmpty();
    }

    // ---- One controller bean → its callback IS GuardedToolCallback ----------------------

    /**
     * Minimal in-memory {@link McpToolController} bean with one tool named
     * {@code test_controller_tool}. Must use a name that does NOT clash with any real tool.
     */
    @McpToolController
    static class TestMcpController {
        @Tool(name = "test_controller_tool", description = "controller tool for testing")
        public String testControllerTool(String input) {
            return input;
        }
    }

    @Test
    void oneControllerBean_itsCallbackIsGuarded() {
        TestMcpController controller = new TestMcpController();
        Map<String, Object> controllerBeans = new HashMap<>();
        controllerBeans.put("testMcpController", controller);
        when(applicationContext.getBeansWithAnnotation(McpToolController.class))
                .thenReturn(controllerBeans);

        ToolCallbackProvider provider = mcpConfig.userProfileToolCallbackProvider(applicationContext);

        ToolCallback[] callbacks = provider.getToolCallbacks();
        // 1 controller tool → wrapped unconditionally in GuardedToolCallback
        assertThat(callbacks).hasSize(1);
        assertThat(callbacks[0])
                .as("Controller tool must be wrapped in GuardedToolCallback")
                .isInstanceOf(GuardedToolCallback.class);
        assertThat(callbacks[0].getToolDefinition().name())
                .isEqualTo("test_controller_tool");
    }

    // ---- Duplicate tool name → IllegalStateException ------------------------------------

    /**
     * Two controllers that each declare the same tool name — the fail-fast guard must fire.
     */
    @McpToolController
    static class AnotherControllerWithDuplicate {
        @Tool(name = "test_controller_tool", description = "duplicate name from another controller")
        public String testControllerTool(String input) {
            return input;
        }
    }

    @Test
    void duplicateToolName_throwsIllegalStateException() {
        Map<String, Object> controllerBeans = new HashMap<>();
        controllerBeans.put("testMcpController", new TestMcpController());
        controllerBeans.put("anotherControllerWithDuplicate", new AnotherControllerWithDuplicate());
        when(applicationContext.getBeansWithAnnotation(McpToolController.class))
                .thenReturn(controllerBeans);

        assertThatThrownBy(() ->
                mcpConfig.userProfileToolCallbackProvider(applicationContext)
        ).isInstanceOf(IllegalStateException.class);
    }

    // ---- Phase 6: all real controllers → 18 tools, ALL guarded -------------------------

    /**
     * Phase 6: wiring all 6 real controllers produces exactly 17 tools, every one wrapped
     * in {@link GuardedToolCallback} — no legacy callbacks remain.
     *
     * <p>Controllers: {@code OrderCreationMcpController} (1), {@code DocumentUploadMcpController} (4),
     * {@code UserMcpController} (3), {@code OrderMcpController} (5), {@code StoreMcpController} (3),
     * {@code PayoutMcpController} (1) = 17 tools, all guarded
     * (create_user removed from UserMcpController in Phase 2b, Lindani 2026-11-05).</p>
     */
    @Test
    void allRealControllers_returns17Tools_allGuarded() {
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

        ToolCallbackProvider provider = mcpConfig.userProfileToolCallbackProvider(applicationContext);

        ToolCallback[] callbacks = provider.getToolCallbacks();
        assertThat(callbacks)
                .as("Phase 6: all 6 controllers together must produce exactly 17 tools (create_user removed Phase 2b)")
                .hasSize(17);

        // Every callback must be GuardedToolCallback — no legacy unguarded callbacks remain
        Set<String> unguardedNames = new java.util.HashSet<>();
        for (ToolCallback tc : callbacks) {
            if (!(tc instanceof GuardedToolCallback)) {
                unguardedNames.add(tc.getToolDefinition().name());
            }
        }
        assertThat(unguardedNames)
                .as("Phase 6: ALL tool callbacks must be GuardedToolCallback — found unguarded: %s", unguardedNames)
                .isEmpty();
    }
}
