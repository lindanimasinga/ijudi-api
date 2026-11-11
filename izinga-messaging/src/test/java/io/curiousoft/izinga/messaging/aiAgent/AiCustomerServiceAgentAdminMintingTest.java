package io.curiousoft.izinga.messaging.aiAgent;

import io.curiousoft.izinga.commons.model.ProfileRoles;
import io.curiousoft.izinga.commons.model.UserProfile;
import io.curiousoft.izinga.commons.repo.UserProfileRepo;
import io.curiousoft.izinga.messaging.aiAgent.config.AiAgentConfig;
import io.curiousoft.izinga.messaging.aiAgent.config.AiAgentConfigService;
import io.curiousoft.izinga.messaging.aiAgent.config.McpServerConfig;
import io.curiousoft.izinga.messaging.aiAgent.conversation.ConversationHistoryService;
import io.curiousoft.izinga.messaging.security.StoreScopeJwtService;
import io.curiousoft.izinga.messaging.whatsapp.WhatsappConfig;
import io.curiousoft.izinga.messaging.whatsapp.lines.Audience;
import org.springframework.retry.support.RetryTemplate;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.web.client.RestTemplate;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * AMD-01: Unit tests for ADMIN audience minting in AiCustomerServiceAgent.buildScopedMcpTools().
 *
 * Covers:
 * - REQ-A2: admin caller + secret configured → ADMIN JWT minted
 * - REQ-A3: admin caller + blank secret → configured audience, repo never called
 * - REQ-A5: non-admin role → configured audience
 * - REQ-A5: unknown sender → configured audience
 * - REQ-A4: config audience ADMIN → never ADMIN for non-admin sender
 * - REQ-A2: phone prefix variants (27/+27/0) resolve admin
 * - Architect fail-closed: lookup exception → configured audience, URL still has scope token
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class AiCustomerServiceAgentAdminMintingTest {

    @Mock private RestTemplate restTemplate;
    @Mock private ConversationHistoryService conversationHistoryService;
    @Mock private AiAgentConfigService agentConfigService;
    @Mock private StoreScopeJwtService storeScopeJwtService;
    @Mock private UserProfileRepo userProfileRepo;

    /** Secret configured — verification is enabled. */
    private WhatsappConfig configWithSecret;
    /** Blank secret — verification is disabled (production bypass state). */
    private WhatsappConfig configBlankSecret;

    private static final String AGENT_NAME = "driver_support";
    private static final String STORE_ID = "store-123";
    private static final String ADMIN_PHONE = "+27812345678";
    private static final String CUSTOMER_PHONE = "+27800000001";
    private static final String BASE_URL = "https://mcp.izinga.co.za/mcp";

    private McpServerConfig mcpServer;
    private AiAgentConfig agentConfigWithCustomerAudience;
    private AiAgentConfig agentConfigWithDriverAudience;
    private AiAgentConfig agentConfigWithAdminAudience;
    private UserProfile adminProfile;
    private UserProfile customerProfile;

    @BeforeEach
    void setUp() {
        configWithSecret = new WhatsappConfig(
                "phone-id", "cust-tmpl", "shop-tmpl", "msg-tmpl",
                "driver-phone-id", true, "real-app-secret-value");
        configBlankSecret = new WhatsappConfig(
                "phone-id", "cust-tmpl", "shop-tmpl", "msg-tmpl",
                "driver-phone-id", true, "");

        mcpServer = new McpServerConfig("mcp", "izinga-mcp", "iZinga MCP", BASE_URL, "never", null);

        agentConfigWithCustomerAudience = new AiAgentConfig();
        agentConfigWithCustomerAudience.setAudience(Audience.CUSTOMER);

        agentConfigWithDriverAudience = new AiAgentConfig();
        agentConfigWithDriverAudience.setAudience(Audience.DRIVER);

        agentConfigWithAdminAudience = new AiAgentConfig();
        agentConfigWithAdminAudience.setAudience(Audience.ADMIN);

        adminProfile = mock(UserProfile.class);
        when(adminProfile.getRole()).thenReturn(ProfileRoles.ADMIN);

        customerProfile = mock(UserProfile.class);
        when(customerProfile.getRole()).thenReturn(ProfileRoles.CUSTOMER);
    }

    private AiCustomerServiceAgent agentWith(WhatsappConfig cfg) {
        // ADR-031: single-attempt no-op retry for unit tests that don't exercise retry behaviour
        RetryTemplate noOpRetry = RetryTemplate.builder().maxAttempts(1).fixedBackoff(1).build();
        return new AiCustomerServiceAgent(
                true, "sk-test", "gpt-4.1-mini", false,
                restTemplate, conversationHistoryService, agentConfigService,
                storeScopeJwtService, cfg, userProfileRepo, noOpRetry);
    }

    // ── REQ-A2: admin caller + secret configured → ADMIN audience in JWT ─────

    @Test
    void buildScopedMcpTools_adminCallerAndSecretSet_mintsAdminAudience() {
        when(agentConfigService.getMcpToolsForAgent(AGENT_NAME)).thenReturn(List.of(mcpServer));
        when(agentConfigService.getActiveAgentConfig(AGENT_NAME)).thenReturn(agentConfigWithCustomerAudience);
        when(userProfileRepo.findByMobileNumber(anyString())).thenReturn(null);
        // the "+27" prefix matches
        when(userProfileRepo.findByMobileNumber("+27812345678")).thenReturn(adminProfile);
        when(storeScopeJwtService.buildScopedUrl(eq(BASE_URL), isNull(), eq(Audience.ADMIN), eq(ADMIN_PHONE)))
                .thenReturn(BASE_URL + "?scope=admin-token");

        var sut = agentWith(configWithSecret);
        var result = sut.buildScopedMcpTools(AGENT_NAME, STORE_ID, ADMIN_PHONE);

        assertEquals(1, result.size());
        assertTrue(result.get(0).getServerUrl().contains("admin-token"),
                "URL must contain an ADMIN-audience scope token");

        // Verify storeId was passed as null (ADMIN scope is not store-scoped)
        verify(storeScopeJwtService).buildScopedUrl(eq(BASE_URL), isNull(), eq(Audience.ADMIN), eq(ADMIN_PHONE));
    }

    // ── REQ-A3: blank secret → configured audience, repo never called ────────

    @Test
    void buildScopedMcpTools_adminCallerAndSecretBlank_mintsConfiguredAudience_repoNeverCalled() {
        when(agentConfigService.getMcpToolsForAgent(AGENT_NAME)).thenReturn(List.of(mcpServer));
        when(agentConfigService.getActiveAgentConfig(AGENT_NAME)).thenReturn(agentConfigWithCustomerAudience);
        when(storeScopeJwtService.buildScopedUrl(eq(BASE_URL), eq(STORE_ID), eq(Audience.CUSTOMER), eq(ADMIN_PHONE)))
                .thenReturn(BASE_URL + "?scope=customer-token");

        var sut = agentWith(configBlankSecret);
        var result = sut.buildScopedMcpTools(AGENT_NAME, STORE_ID, ADMIN_PHONE);

        assertEquals(1, result.size());
        // repo must never be called when verification is disabled
        verify(userProfileRepo, never()).findByMobileNumber(anyString());
        // configured CUSTOMER audience was minted unchanged
        verify(storeScopeJwtService).buildScopedUrl(eq(BASE_URL), eq(STORE_ID), eq(Audience.CUSTOMER), eq(ADMIN_PHONE));
    }

    // ── REQ-A5: non-admin role → configured audience ─────────────────────────

    @Test
    void buildScopedMcpTools_nonAdminRole_mintsConfiguredAudience() {
        when(agentConfigService.getMcpToolsForAgent(AGENT_NAME)).thenReturn(List.of(mcpServer));
        when(agentConfigService.getActiveAgentConfig(AGENT_NAME)).thenReturn(agentConfigWithDriverAudience);
        // all prefix lookups return the customer profile (non-admin)
        when(userProfileRepo.findByMobileNumber(anyString())).thenReturn(customerProfile);
        when(storeScopeJwtService.buildScopedUrl(eq(BASE_URL), eq(STORE_ID), eq(Audience.DRIVER), eq(CUSTOMER_PHONE)))
                .thenReturn(BASE_URL + "?scope=driver-token");

        var sut = agentWith(configWithSecret);
        var result = sut.buildScopedMcpTools(AGENT_NAME, STORE_ID, CUSTOMER_PHONE);

        assertEquals(1, result.size());
        // DRIVER audience — not elevated to ADMIN
        verify(storeScopeJwtService).buildScopedUrl(eq(BASE_URL), eq(STORE_ID), eq(Audience.DRIVER), eq(CUSTOMER_PHONE));
        verify(storeScopeJwtService, never()).buildScopedUrl(any(), any(), eq(Audience.ADMIN), any());
    }

    // ── REQ-A5: unknown sender → configured audience ─────────────────────────

    @Test
    void buildScopedMcpTools_unknownSender_mintsConfiguredAudience() {
        when(agentConfigService.getMcpToolsForAgent(AGENT_NAME)).thenReturn(List.of(mcpServer));
        when(agentConfigService.getActiveAgentConfig(AGENT_NAME)).thenReturn(agentConfigWithCustomerAudience);
        // no profile found for any prefix
        when(userProfileRepo.findByMobileNumber(anyString())).thenReturn(null);
        when(storeScopeJwtService.buildScopedUrl(eq(BASE_URL), eq(STORE_ID), eq(Audience.CUSTOMER), eq(CUSTOMER_PHONE)))
                .thenReturn(BASE_URL + "?scope=customer-token");

        var sut = agentWith(configWithSecret);
        var result = sut.buildScopedMcpTools(AGENT_NAME, STORE_ID, CUSTOMER_PHONE);

        assertEquals(1, result.size());
        verify(storeScopeJwtService, never()).buildScopedUrl(any(), any(), eq(Audience.ADMIN), any());
    }

    // ── REQ-A4: config audience ADMIN → never ADMIN for non-admin sender ─────

    @Test
    void buildScopedMcpTools_configuredAudienceAdmin_neverMintsAdminForNonAdminSender() {
        when(agentConfigService.getMcpToolsForAgent(AGENT_NAME)).thenReturn(List.of(mcpServer));
        // AiAgentConfig has ADMIN audience — should be demoted to CUSTOMER per REQ-A4
        when(agentConfigService.getActiveAgentConfig(AGENT_NAME)).thenReturn(agentConfigWithAdminAudience);
        // caller resolves to a CUSTOMER profile (non-admin)
        when(userProfileRepo.findByMobileNumber(anyString())).thenReturn(customerProfile);
        // after REQ-A4 demotion, CUSTOMER audience is used; non-admin lookup yields CUSTOMER
        when(storeScopeJwtService.buildScopedUrl(eq(BASE_URL), eq(STORE_ID), eq(Audience.CUSTOMER), eq(CUSTOMER_PHONE)))
                .thenReturn(BASE_URL + "?scope=customer-token");

        var sut = agentWith(configWithSecret);
        var result = sut.buildScopedMcpTools(AGENT_NAME, STORE_ID, CUSTOMER_PHONE);

        assertEquals(1, result.size());
        // Must never mint ADMIN for a non-admin sender, even if config says ADMIN
        verify(storeScopeJwtService, never()).buildScopedUrl(any(), any(), eq(Audience.ADMIN), any());
        // The demoted CUSTOMER audience was used
        verify(storeScopeJwtService).buildScopedUrl(eq(BASE_URL), eq(STORE_ID), eq(Audience.CUSTOMER), eq(CUSTOMER_PHONE));
    }

    // ── REQ-A2: phone prefix variants (27/+27/0) ─────────────────────────────

    @Test
    void buildScopedMcpTools_adminCallerPhoneVariants() {
        // Test each phone prefix variant resolving the admin
        String[] phones = {"+27812345678", "27812345678", "0812345678"};
        for (String phone : phones) {
            reset(agentConfigService, userProfileRepo, storeScopeJwtService);
            when(agentConfigService.getMcpToolsForAgent(AGENT_NAME)).thenReturn(List.of(mcpServer));
            when(agentConfigService.getActiveAgentConfig(AGENT_NAME)).thenReturn(agentConfigWithCustomerAudience);

            // Only one of the prefix lookups returns admin; simulate using a lenient stub
            when(userProfileRepo.findByMobileNumber(anyString())).thenReturn(null);
            // Determine which prefixed variant to stub based on the input phone's last 9 digits
            String last9 = phone.substring(phone.length() - 9);
            when(userProfileRepo.findByMobileNumber("+27" + last9)).thenReturn(adminProfile);
            when(storeScopeJwtService.buildScopedUrl(eq(BASE_URL), isNull(), eq(Audience.ADMIN), eq(phone)))
                    .thenReturn(BASE_URL + "?scope=admin-token");

            var sut = agentWith(configWithSecret);
            var result = sut.buildScopedMcpTools(AGENT_NAME, STORE_ID, phone);

            assertFalse(result.isEmpty(), "Expected non-empty result for phone=" + phone);
            verify(storeScopeJwtService, description("phone=" + phone + " must mint ADMIN"))
                    .buildScopedUrl(eq(BASE_URL), isNull(), eq(Audience.ADMIN), eq(phone));
        }
    }

    // ── Architect fail-closed: lookup exception → configured audience + token ─

    @Test
    void buildScopedMcpTools_adminLookupThrows_fallsBackToConfiguredAudienceWithToken() {
        when(agentConfigService.getMcpToolsForAgent(AGENT_NAME)).thenReturn(List.of(mcpServer));
        when(agentConfigService.getActiveAgentConfig(AGENT_NAME)).thenReturn(agentConfigWithCustomerAudience);
        // Simulate DB failure on admin lookup
        when(userProfileRepo.findByMobileNumber(anyString())).thenThrow(new RuntimeException("DB unreachable"));
        // Fallback: configured CUSTOMER audience is used; URL must still carry a scope token
        when(storeScopeJwtService.buildScopedUrl(eq(BASE_URL), eq(STORE_ID), eq(Audience.CUSTOMER), eq(ADMIN_PHONE)))
                .thenReturn(BASE_URL + "?scope=customer-fallback-token");

        var sut = agentWith(configWithSecret);
        var result = sut.buildScopedMcpTools(AGENT_NAME, STORE_ID, ADMIN_PHONE);

        // URL must still carry a token — the exception must NOT drop the token and return the base URL
        assertEquals(1, result.size());
        assertTrue(result.get(0).getServerUrl().contains("scope="),
                "Exception during admin lookup must not drop the scope token from the URL");
        verify(storeScopeJwtService).buildScopedUrl(eq(BASE_URL), eq(STORE_ID), eq(Audience.CUSTOMER), eq(ADMIN_PHONE));
        verify(storeScopeJwtService, never()).buildScopedUrl(any(), any(), eq(Audience.ADMIN), any());
    }
}
