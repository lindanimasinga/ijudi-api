package io.curiousoft.izinga.ordermanagement.mcp;

import io.curiousoft.izinga.commons.model.ProfileRoles;
import io.curiousoft.izinga.commons.model.UserProfile;
import io.curiousoft.izinga.commons.repo.IcaAcceptanceLogRepo;
import io.curiousoft.izinga.commons.repo.UserProfileRepo;
import io.curiousoft.izinga.ordermanagement.McpConfig;
import io.curiousoft.izinga.ordermanagement.orders.OrderServiceImpl;
import io.curiousoft.izinga.ordermanagement.stores.StoreService;
import io.curiousoft.izinga.recon.ReconServiceImpl;
import io.curiousoft.izinga.usermanagement.referral.ReferralCodeService;
import io.curiousoft.izinga.usermanagement.userconfig.UserConfigService;
import io.curiousoft.izinga.usermanagement.users.DocumentUploadMcpService;
import io.curiousoft.izinga.usermanagement.users.UserProfileService;
import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.ToolCallbackProvider;
import org.springframework.context.ApplicationEventPublisher;

import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * Unit tests for the MCP (Model Context Protocol) server tool registration.
 *
 * <p>These tests verify that:
 * <ul>
 *   <li>The {@link McpConfig} registers the expected {@link ToolCallbackProvider} beans.</li>
 *   <li>All expected tools are present and have the correct names and descriptions.</li>
 *   <li>Tool invocation delegates correctly to the underlying service method.</li>
 * </ul>
 *
 * <h2>How the MCP server works</h2>
 * <p>Spring AI auto-configures an MCP server when {@code spring-ai-starter-mcp-server-webmvc}
 * is on the classpath. It scans all {@link ToolCallbackProvider} beans, collects their tools,
 * and makes them available over a Server-Sent Events (SSE) transport:</p>
 * <ul>
 *   <li>{@code GET /sse} – SSE event stream that VSCode / Claude / Copilot connects to.</li>
 *   <li>{@code POST /mcp/message} – JSON-RPC endpoint for tool-call messages.</li>
 * </ul>
 *
 * <h2>VS Code connection steps</h2>
 * <pre>
 * 1. Install the "GitHub Copilot" extension (v1.220+) or any MCP-compatible extension.
 * 2. Open the Command Palette → "MCP: Add Server".
 * 3. Select "HTTP (SSE)" as the transport type.
 * 4. Enter the server URL: http://localhost:80/sse
 * 5. Save. The extension will connect and list available tools.
 * 6. Ask Copilot Chat: "Find the user with phone 0812345678".
 *    Copilot will automatically invoke the `find_user_by_phone` tool.
 * </pre>
 */
@ExtendWith(MockitoExtension.class)
class McpToolRegistrationTest {

    @Mock
    private UserProfileRepo userProfileRepo;
    @Mock
    OrderServiceImpl orderService;
    @Mock
    ReconServiceImpl reconService;
    @Mock
    StoreService storeService;
    @Mock
    private ApplicationEventPublisher eventPublisher;

    private McpConfig mcpConfig;
    private UserProfileService userProfileService;
    @Mock
    private UserConfigService userConfig;
    @Mock
    private IcaAcceptanceLogRepo icaAcceptanceLogRepo;
    @Mock
    private ReferralCodeService referralCodeService;
    @Mock
    private DocumentUploadMcpService documentUploadMcpService;

    @BeforeEach
    void setUp() {
        userProfileService = new UserProfileService(userProfileRepo, eventPublisher, userConfig, icaAcceptanceLogRepo, referralCodeService);
        mcpConfig = new McpConfig();
    }

    @Test
    void userProfileToolCallbackProvider_isCreated() {
        ToolCallbackProvider provider = mcpConfig.userProfileToolCallbackProvider(
                userProfileService,
                orderService,
                reconService,
                storeService,
                documentUploadMcpService
        );
        assertNotNull(provider, "ToolCallbackProvider must not be null");
    }

    @Test
    void userProfileTools_allExpectedToolsAreRegistered() {
        ToolCallbackProvider provider = mcpConfig.userProfileToolCallbackProvider(
            userProfileService,
            orderService,
            reconService,
            storeService,
            documentUploadMcpService
        );
        ToolCallback[] tools = provider.getToolCallbacks();

        assertNotNull(tools, "Tool callbacks array must not be null");
        assertTrue(tools.length >= 6,
            "At least six tools should be registered including document upload tools, got: " + tools.length);

        List<String> toolNames = Arrays.stream(tools)
                .map(t -> t.getToolDefinition().name())
                .collect(Collectors.toList());

        assertTrue(toolNames.contains("find_user_by_phone"), "Tool 'find_user_by_phone' must be registered");
        assertTrue(toolNames.contains("create_user"), "Tool 'create_user' must be registered");
        assertTrue(toolNames.contains("get_required_documents_for_user"), "Tool 'get_required_documents_for_user' must be registered");
        assertTrue(toolNames.contains("create_document_upload_session"), "Tool 'create_document_upload_session' must be registered");
        assertTrue(toolNames.contains("attach_uploaded_document_to_user_tag"), "Tool 'attach_uploaded_document_to_user_tag' must be registered");
        assertTrue(toolNames.contains("recheck_required_documents"), "Tool 'recheck_required_documents' must be registered");
    }

    @Test
    void findUserByPhone_toolHasDescription() {
        ToolCallbackProvider provider = mcpConfig.userProfileToolCallbackProvider(
            userProfileService,
            orderService,
            reconService,
            storeService,
            documentUploadMcpService
        );
        ToolCallback findUserTool = Arrays.stream(provider.getToolCallbacks())
                .filter(t -> "find_user_by_phone".equals(t.getToolDefinition().name()))
                .findFirst()
                .orElseThrow(() -> new AssertionError("find_user_by_phone tool not found"));

        String description = findUserTool.getToolDefinition().description();
        assertNotNull(description, "Tool description must not be null");
        assertFalse(description.isEmpty(), "Tool description must not be empty");
    }

    @Test
    void createUser_toolHasDescription() {
        ToolCallbackProvider provider = mcpConfig.userProfileToolCallbackProvider(
            userProfileService,
            orderService,
            reconService,
            storeService,
            documentUploadMcpService
        );
        ToolCallback createUserTool = Arrays.stream(provider.getToolCallbacks())
                .filter(t -> "create_user".equals(t.getToolDefinition().name()))
                .findFirst()
                .orElseThrow(() -> new AssertionError("create_user tool not found"));

        String description = createUserTool.getToolDefinition().description();
        assertNotNull(description, "Tool description must not be null");
        assertFalse(description.isEmpty(), "Tool description must not be empty");
    }

    @Test
    void findUserByPhoneTool_delegatesToService() {
        UserProfile expectedProfile = new UserProfile(
                "Test User",
                UserProfile.SignUpReason.BUY,
                "123 Main St",
                "https://example.com/photo.jpg",
                "+27812345678",
                ProfileRoles.CUSTOMER
        );
        // "+27812345678" has last-9 = "812345678"; the service tries "0812345678" first (null)
        // then "+27812345678" (match). Use lenient() so Mockito doesn't flag the unused "0…" call.
        lenient().when(userProfileRepo.findByMobileNumber(anyString())).thenReturn(null);
        lenient().when(userProfileRepo.findByMobileNumber("+27812345678")).thenReturn(expectedProfile);

        UserProfile result = userProfileService.findUserByPhone("+27812345678");
        assertEquals(expectedProfile, result, "Phone lookup must return the expected profile");
    }

    @Test
    void findUserByPhone_triesMultiplePrefixes() {
        // last 9 digits of the phone
        String last9 = "812345678";
        // Use lenient to avoid strict-stubbing errors with intermediate null returns
        lenient().when(userProfileRepo.findByMobileNumber(anyString())).thenReturn(null);

        UserProfile result = userProfileService.findUserByPhone("0" + last9);
        assertNull(result, "Should return null when no user found with any prefix");

        verify(userProfileRepo).findByMobileNumber("0" + last9);
        verify(userProfileRepo).findByMobileNumber("+27" + last9);
        verify(userProfileRepo).findByMobileNumber("27" + last9);
    }
}
