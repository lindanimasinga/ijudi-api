package io.curiousoft.izinga.messaging.aiAgent.config;

import io.curiousoft.izinga.messaging.whatsapp.lines.Audience;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AiAgentConfigServiceTest {

    @Mock
    private AiAgentConfigRepository repository;

    private AiAgentConfigService service;

    private static final String AGENT_NAME = "driver_support";
    private static final String SYSTEM_PROMPT = "You are a support agent";
    private static final String DESCRIPTION = "Driver support agent";

    @BeforeEach
    void setUp() {
        service = new AiAgentConfigService(repository);
    }

    @Test
    void getSystemPrompt_returnsPrompt_whenConfigExists() {
        // Given
        AiAgentConfig config = AiAgentConfig.builder()
            .id("1")
            .agentName(AGENT_NAME)
            .systemPrompt(SYSTEM_PROMPT)
            .active(true)
            .build();

        when(repository.findByAgentNameAndActiveTrue(AGENT_NAME))
            .thenReturn(Optional.of(config));

        // When
        String result = service.getSystemPrompt(AGENT_NAME);

        // Then
        assertEquals(SYSTEM_PROMPT, result);
        verify(repository, times(1)).findByAgentNameAndActiveTrue(AGENT_NAME);
    }

    @Test
    void getSystemPrompt_returnsNull_whenConfigNotFound() {
        // Given
        when(repository.findByAgentNameAndActiveTrue(AGENT_NAME))
            .thenReturn(Optional.empty());

        // When
        String result = service.getSystemPrompt(AGENT_NAME);

        // Then
        assertNull(result);
    }

    @Test
    void getAgentConfig_returnsConfig_whenExists() {
        // Given
        AiAgentConfig config = AiAgentConfig.builder()
            .id("1")
            .agentName(AGENT_NAME)
            .systemPrompt(SYSTEM_PROMPT)
            .active(true)
            .build();

        when(repository.findByAgentNameAndActiveTrue(AGENT_NAME))
            .thenReturn(Optional.of(config));

        // When
        Optional<AiAgentConfig> result = service.getAgentConfig(AGENT_NAME);

        // Then
        assertTrue(result.isPresent());
        assertEquals(SYSTEM_PROMPT, result.get().getSystemPrompt());
    }

    @Test
    void saveAgentConfig_createsNewConfig_whenDoesntExist() {
        // Given
        when(repository.findByAgentName(AGENT_NAME))
            .thenReturn(Optional.empty());

        AiAgentConfig savedConfig = AiAgentConfig.builder()
            .id("1")
            .agentName(AGENT_NAME)
            .systemPrompt(SYSTEM_PROMPT)
            .description(DESCRIPTION)
            .active(true)
            .createdAt(Instant.now())
            .updatedAt(Instant.now())
            .version(1)
            .build();

        when(repository.save(any(AiAgentConfig.class)))
            .thenReturn(savedConfig);

        // When
        AiAgentConfig result = service.saveAgentConfig(AGENT_NAME, SYSTEM_PROMPT, DESCRIPTION);

        // Then
        assertNotNull(result);
        assertEquals(AGENT_NAME, result.getAgentName());
        assertEquals(1, result.getVersion());
        verify(repository, times(1)).save(any(AiAgentConfig.class));
    }

    @Test
    void saveAgentConfig_updatesExisting_whenExists() {
        // Given
        AiAgentConfig existing = AiAgentConfig.builder()
            .id("1")
            .agentName(AGENT_NAME)
            .systemPrompt("Old prompt")
            .active(true)
            .createdAt(Instant.now())
            .updatedAt(Instant.now())
            .version(1)
            .build();

        when(repository.findByAgentName(AGENT_NAME))
            .thenReturn(Optional.of(existing));

        AiAgentConfig updatedConfig = AiAgentConfig.builder()
            .id("1")
            .agentName(AGENT_NAME)
            .systemPrompt(SYSTEM_PROMPT)
            .description(DESCRIPTION)
            .active(true)
            .createdAt(existing.getCreatedAt())
            .updatedAt(Instant.now())
            .version(2)
            .build();

        when(repository.save(any(AiAgentConfig.class)))
            .thenReturn(updatedConfig);

        // When
        AiAgentConfig result = service.saveAgentConfig(AGENT_NAME, SYSTEM_PROMPT, DESCRIPTION);

        // Then
        assertEquals(2, result.getVersion());
        assertEquals(SYSTEM_PROMPT, result.getSystemPrompt());
    }

    @Test
    void deactivateAgent_setsActiveFalse() {
        // Given
        AiAgentConfig config = AiAgentConfig.builder()
            .id("1")
            .agentName(AGENT_NAME)
            .systemPrompt(SYSTEM_PROMPT)
            .active(true)
            .build();

        when(repository.findByAgentName(AGENT_NAME))
            .thenReturn(Optional.of(config));

        // When
        service.deactivateAgent(AGENT_NAME);

        // Then
        assertFalse(config.getActive());
        verify(repository, times(1)).save(config);
    }

    @Test
    void activateAgent_setsActiveTrue() {
        // Given
        AiAgentConfig config = AiAgentConfig.builder()
            .id("1")
            .agentName(AGENT_NAME)
            .systemPrompt(SYSTEM_PROMPT)
            .active(false)
            .build();

        when(repository.findByAgentName(AGENT_NAME))
            .thenReturn(Optional.of(config));

        // When
        service.activateAgent(AGENT_NAME);

        // Then
        assertTrue(config.getActive());
        verify(repository, times(1)).save(config);
    }

    /**
     * Multi-agent cache isolation (REQ-11 / Map<String, AiAgentConfig> cache).
     * Two calls to getAgentConfig with different agent names must return
     * distinct config objects — each agent's config is cached under its own key.
     */
    @Test
    void twoAgents_cacheReturnsDifferentConfigs() {
        String agentA = "driver_support";
        String agentB = "customer_support";

        AiAgentConfig configA = AiAgentConfig.builder()
            .id("a1").agentName(agentA).systemPrompt("Driver prompt").active(true).build();
        AiAgentConfig configB = AiAgentConfig.builder()
            .id("b1").agentName(agentB).systemPrompt("Customer prompt").active(true).build();

        when(repository.findByAgentNameAndActiveTrue(agentA)).thenReturn(Optional.of(configA));
        when(repository.findByAgentNameAndActiveTrue(agentB)).thenReturn(Optional.of(configB));

        Optional<AiAgentConfig> resultA = service.getAgentConfig(agentA);
        Optional<AiAgentConfig> resultB = service.getAgentConfig(agentB);

        assertTrue(resultA.isPresent());
        assertTrue(resultB.isPresent());
        assertEquals("Driver prompt", resultA.get().getSystemPrompt());
        assertEquals("Customer prompt", resultB.get().getSystemPrompt());
        assertNotSame(resultA.get(), resultB.get());
    }

    /**
     * Cache invalidation on save: after saveAgentConfig, the next call to
     * getAgentConfig must return the updated config from the cache, without
     * hitting the repository again.
     */
    @Test
    void cacheInvalidatedOnSave() {
        AiAgentConfig v1 = AiAgentConfig.builder()
            .id("1").agentName(AGENT_NAME).systemPrompt("Old").active(true).version(1).build();
        AiAgentConfig v2 = AiAgentConfig.builder()
            .id("1").agentName(AGENT_NAME).systemPrompt("New").active(true).version(2).build();

        // Initial load
        when(repository.findByAgentNameAndActiveTrue(AGENT_NAME)).thenReturn(Optional.of(v1));
        service.getAgentConfig(AGENT_NAME);  // populates cache

        // Save triggers cache update
        when(repository.findByAgentName(AGENT_NAME)).thenReturn(Optional.of(v1));
        when(repository.save(any())).thenReturn(v2);
        service.saveAgentConfig(AGENT_NAME, "New", DESCRIPTION);

        // Next get must return v2 from cache (repository.findByAgentNameAndActiveTrue NOT called again)
        Optional<AiAgentConfig> result = service.getAgentConfig(AGENT_NAME);
        assertTrue(result.isPresent());
        assertEquals("New", result.get().getSystemPrompt());

        // findByAgentNameAndActiveTrue must have been called exactly once (initial load only)
        verify(repository, times(1)).findByAgentNameAndActiveTrue(AGENT_NAME);
    }

    // ---- SA-021-3: getMcpToolsForAgent(String agentName) tests ----

    @Test
    void getMcpToolsForAgent_returnsMcpServers_whenConfigHasThem() {
        McpServerConfig mcpServer = new McpServerConfig("mcp", "store-api", "Store API",
                "https://api.izinga.co.za/mcp", "never", null);
        AiAgentConfig config = AiAgentConfig.builder()
                .id("s1").agentName("store_support_abc")
                .active(true)
                .audience(Audience.STORE)
                .storeId("abc")
                .mcpServers(List.of(mcpServer))
                .build();

        when(repository.findByAgentNameAndActiveTrue("store_support_abc")).thenReturn(Optional.of(config));
        service.getAgentConfig("store_support_abc"); // prime cache

        List<McpServerConfig> result = service.getMcpToolsForAgent("store_support_abc");

        assertEquals(1, result.size());
        assertEquals("store-api", result.get(0).getServerLabel());
    }

    @Test
    void getMcpToolsForAgent_returnsDefaultServer_whenConfigHasNoMcpServers() {
        AiAgentConfig config = AiAgentConfig.builder()
                .id("d1").agentName(AGENT_NAME)
                .active(true)
                .mcpServers(List.of()) // empty list
                .build();

        when(repository.findByAgentNameAndActiveTrue(AGENT_NAME)).thenReturn(Optional.of(config));

        List<McpServerConfig> result = service.getMcpToolsForAgent(AGENT_NAME);

        assertEquals(1, result.size());
        assertEquals(AiAgentConfigService.DEFAULT_MCP_SERVER.getServerLabel(),
                result.get(0).getServerLabel());
    }

    @Test
    void getMcpToolsForAgent_returnsDefaultServer_whenAgentNotFound() {
        when(repository.findByAgentNameAndActiveTrue("unknown_agent")).thenReturn(Optional.empty());

        List<McpServerConfig> result = service.getMcpToolsForAgent("unknown_agent");

        assertEquals(1, result.size());
        assertEquals(AiAgentConfigService.DEFAULT_MCP_SERVER.getServerLabel(),
                result.get(0).getServerLabel());
    }

    // ---- getAgentConfigAnyStatus — loads inactive (template) agents ----

    @Test
    void getAgentConfigAnyStatus_returnsConfig_whenActiveTrue() {
        AiAgentConfig config = AiAgentConfig.builder()
                .id("1").agentName(AGENT_NAME).systemPrompt(SYSTEM_PROMPT).active(true).build();
        when(repository.findByAgentName(AGENT_NAME)).thenReturn(Optional.of(config));

        Optional<AiAgentConfig> result = service.getAgentConfigAnyStatus(AGENT_NAME);

        assertTrue(result.isPresent());
        assertEquals(SYSTEM_PROMPT, result.get().getSystemPrompt());
    }

    @Test
    void getAgentConfigAnyStatus_returnsConfig_whenActiveFalse() {
        // store_support_default is active=false — must still be found by this method
        AiAgentConfig template = AiAgentConfig.builder()
                .id("t1").agentName("store_support_default").systemPrompt("Template").active(false).build();
        when(repository.findByAgentName("store_support_default")).thenReturn(Optional.of(template));

        Optional<AiAgentConfig> result = service.getAgentConfigAnyStatus("store_support_default");

        assertTrue(result.isPresent());
        assertFalse(result.get().getActive());
        assertEquals("Template", result.get().getSystemPrompt());
    }

    @Test
    void getAgentConfigAnyStatus_returnsEmpty_whenNotFound() {
        when(repository.findByAgentName("no_such_agent")).thenReturn(Optional.empty());

        Optional<AiAgentConfig> result = service.getAgentConfigAnyStatus("no_such_agent");

        assertTrue(result.isEmpty());
    }

    @Test
    void invalidateCache_removesEntryFromCache_soNextGetHitsRepository() {
        AiAgentConfig config = AiAgentConfig.builder()
                .id("1").agentName(AGENT_NAME).systemPrompt(SYSTEM_PROMPT).active(true).build();
        when(repository.findByAgentNameAndActiveTrue(AGENT_NAME)).thenReturn(Optional.of(config));

        // Prime the cache
        service.getAgentConfig(AGENT_NAME);
        verify(repository, times(1)).findByAgentNameAndActiveTrue(AGENT_NAME);

        // Invalidate
        service.invalidateCache(AGENT_NAME);

        // Next get should hit repository again
        service.getAgentConfig(AGENT_NAME);
        verify(repository, times(2)).findByAgentNameAndActiveTrue(AGENT_NAME);
    }
}

