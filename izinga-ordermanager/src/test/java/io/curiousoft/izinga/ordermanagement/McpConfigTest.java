package io.curiousoft.izinga.ordermanagement;

import io.curiousoft.izinga.commons.repo.IcaAcceptanceLogRepo;
import io.curiousoft.izinga.commons.repo.UserProfileRepo;
import io.curiousoft.izinga.commons.security.AgentIdentityAuditService;
import io.curiousoft.izinga.ordermanagement.mcp.GuardedToolCallback;
import io.curiousoft.izinga.ordermanagement.mcp.McpToolController;
import io.curiousoft.izinga.ordermanagement.orders.McpOrderService;
import io.curiousoft.izinga.ordermanagement.orders.OrderServiceImpl;
import io.curiousoft.izinga.ordermanagement.stores.StoreService;
import io.curiousoft.izinga.recon.ReconServiceImpl;
import io.curiousoft.izinga.usermanagement.referral.ReferralCodeService;
import io.curiousoft.izinga.usermanagement.userconfig.UserConfigService;
import io.curiousoft.izinga.usermanagement.users.DocumentUploadMcpService;
import io.curiousoft.izinga.usermanagement.users.UserProfileService;
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
 *   <li>Zero controllers (Phase 0): exactly 18 tools, none wrapped in {@link GuardedToolCallback}.</li>
 *   <li>One controller bean: its callback IS wrapped in {@link GuardedToolCallback}; legacy callbacks are not.</li>
 *   <li>Duplicate tool name: {@link IllegalStateException} thrown by Spring AI.</li>
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
                icaAcceptanceLogRepo, referralCodeService, agentIdentityAuditService);
        mcpConfig = new McpConfig();
    }

    // ---- Phase 0: zero controllers → 18 definitions, none wrapped ---------------------

    @Test
    void zeroControllers_returns18Tools_noneGuarded() {
        when(applicationContext.getBeansWithAnnotation(McpToolController.class))
                .thenReturn(Collections.emptyMap());

        ToolCallbackProvider provider = mcpConfig.userProfileToolCallbackProvider(
                userProfileService, orderService, reconService,
                storeService, documentUploadMcpService, mcpOrderService,
                applicationContext);

        ToolCallback[] callbacks = provider.getToolCallbacks();
        assertThat(callbacks).hasSize(18);

        for (ToolCallback tc : callbacks) {
            assertThat(tc)
                    .as("Phase 0: no callback should be wrapped in GuardedToolCallback (tool: %s)",
                            tc.getToolDefinition().name())
                    .isNotInstanceOf(GuardedToolCallback.class);
        }
    }

    // ---- Phase 1+: controller bean → its callback IS GuardedToolCallback, legacy not --

    /**
     * Minimal in-memory {@link McpToolController} bean with one tool named
     * {@code test_controller_tool}. Must use a name that does NOT clash with any of the 18
     * legacy tools.
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
                userProfileService, orderService, reconService,
                storeService, documentUploadMcpService, mcpOrderService,
                applicationContext);

        ToolCallback[] callbacks = provider.getToolCallbacks();
        // 18 legacy + 1 controller = 19
        assertThat(callbacks).hasSize(19);

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
     * Controller that declares a tool whose name clashes with an existing legacy tool.
     */
    @McpToolController
    static class DuplicateNameController {
        @Tool(name = "find_user_by_phone", description = "duplicate of a legacy tool")
        public String findUserByPhone(String phone) {
            return phone;
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
                        userProfileService, orderService, reconService,
                        storeService, documentUploadMcpService, mcpOrderService,
                        applicationContext)
        ).isInstanceOf(IllegalStateException.class);
    }
}
