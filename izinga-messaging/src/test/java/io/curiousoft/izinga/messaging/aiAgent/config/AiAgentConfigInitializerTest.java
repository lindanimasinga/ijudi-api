package io.curiousoft.izinga.messaging.aiAgent.config;

import io.curiousoft.izinga.messaging.whatsapp.lines.Audience;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Tests for {@link AiAgentConfigInitializer}.
 *
 * Key invariants under test:
 * 1. Fresh-seeded agent configs have useTools=true.
 *    @Builder.Default now makes the builder produce useTools=true; the initializer's
 *    explicit setUseTools(true) call is a belt-and-suspenders guard for paths that
 *    bypass the builder.
 * 2. Existing documents with useTools=false (persisted before the @Builder.Default fix)
 *    are backfilled to useTools=true on startup (Cause B fix).
 */
@ExtendWith(MockitoExtension.class)
class AiAgentConfigInitializerTest {

    @Mock
    private AiAgentConfigService configService;

    @Mock
    private AiAgentConfigRepository repository;

    private AiAgentConfigInitializer initializer;

    @BeforeEach
    void setUp() {
        initializer = new AiAgentConfigInitializer(configService, repository);
    }

    // ---- driver_support fresh seed ----

    @Test
    void run_driverSupport_freshSeed_setsUseToolsTrue() throws Exception {
        // Arrange: no existing driver_support document
        when(repository.findByAgentName("driver_support")).thenReturn(Optional.empty());

        AiAgentConfig fromService = AiAgentConfig.builder()
                .id("d1").agentName("driver_support").active(true).build();
        // @Builder.Default now makes the builder produce useTools=true; verify the builder is working
        assertTrue(fromService.isUseTools(), "pre-condition: @Builder.Default makes builder produce useTools=true");

        when(configService.saveAgentConfig(eq("driver_support"), anyString(), anyString()))
                .thenReturn(fromService);

        // Arrange: customer_support and store_support_default already exist so they short-circuit
        when(repository.findByAgentName("customer_support")).thenReturn(Optional.of(existingDoc("customer_support")));
        when(repository.findByAgentName("store_support_default")).thenReturn(Optional.of(existingDoc("store_support_default")));

        // Act
        initializer.run();

        // Assert: the object saved to repository must have useTools=true
        ArgumentCaptor<AiAgentConfig> captor = ArgumentCaptor.forClass(AiAgentConfig.class);
        verify(repository).save(captor.capture());
        AiAgentConfig persisted = captor.getValue();
        assertTrue(persisted.isUseTools(), "driver_support fresh seed must set useTools=true");
        assertEquals(Audience.DRIVER, persisted.getAudience());
        assertNotNull(persisted.getMcpServers());
        assertFalse(persisted.getMcpServers().isEmpty());
    }

    // ---- customer_support fresh seed ----

    @Test
    void run_customerSupport_freshSeed_setsUseToolsTrue() throws Exception {
        // Arrange: no existing customer_support document
        when(repository.findByAgentName("customer_support")).thenReturn(Optional.empty());

        AiAgentConfig fromService = AiAgentConfig.builder()
                .id("c1").agentName("customer_support").active(true).build();
        // @Builder.Default now makes the builder produce useTools=true; verify the builder is working
        assertTrue(fromService.isUseTools(), "pre-condition: @Builder.Default makes builder produce useTools=true");

        when(configService.saveAgentConfig(eq("customer_support"), anyString(), anyString()))
                .thenReturn(fromService);

        // Arrange: driver_support and store_support_default already exist so they short-circuit
        when(repository.findByAgentName("driver_support")).thenReturn(Optional.of(existingDoc("driver_support")));
        when(repository.findByAgentName("store_support_default")).thenReturn(Optional.of(existingDoc("store_support_default")));

        // Act
        initializer.run();

        // Assert: the object saved to repository must have useTools=true
        ArgumentCaptor<AiAgentConfig> captor = ArgumentCaptor.forClass(AiAgentConfig.class);
        verify(repository).save(captor.capture());
        AiAgentConfig persisted = captor.getValue();
        assertTrue(persisted.isUseTools(), "customer_support fresh seed must set useTools=true");
        assertEquals(Audience.CUSTOMER, persisted.getAudience());
        assertNotNull(persisted.getMcpServers());
        assertFalse(persisted.getMcpServers().isEmpty());
    }

    // ---- existing customer_support document is NOT touched by the fresh-creation path ----

    @Test
    void run_customerSupport_existingDoc_doesNotCallSaveAgentConfig() throws Exception {
        // Arrange: all three agents already exist — idempotency check must short-circuit everything
        AiAgentConfig existing = existingDoc("customer_support");
        existing.setAudience(Audience.CUSTOMER);
        existing.setMcpServers(List.of(AiAgentConfigService.DEFAULT_MCP_SERVER));
        when(repository.findByAgentName("customer_support")).thenReturn(Optional.of(existing));
        when(repository.findByAgentName("driver_support")).thenReturn(Optional.of(existingDoc("driver_support")));
        when(repository.findByAgentName("store_support_default")).thenReturn(Optional.of(existingDoc("store_support_default")));

        // Act
        initializer.run();

        // Assert: saveAgentConfig was never called for customer_support
        verify(configService, never()).saveAgentConfig(eq("customer_support"), anyString(), anyString());
        // repository.save was never called (audience + mcpServers already present, dirty=false)
        verify(repository, never()).save(any());
    }

    // ---- existing driver_support document is NOT touched by the fresh-creation path ----

    @Test
    void run_driverSupport_existingDoc_doesNotCallSaveAgentConfig() throws Exception {
        AiAgentConfig existing = existingDoc("driver_support");
        existing.setAudience(Audience.DRIVER);
        existing.setMcpServers(List.of(AiAgentConfigService.DEFAULT_MCP_SERVER));
        when(repository.findByAgentName("driver_support")).thenReturn(Optional.of(existing));
        when(repository.findByAgentName("customer_support")).thenReturn(Optional.of(existingDoc("customer_support")));
        when(repository.findByAgentName("store_support_default")).thenReturn(Optional.of(existingDoc("store_support_default")));

        initializer.run();

        verify(configService, never()).saveAgentConfig(eq("driver_support"), anyString(), anyString());
        verify(repository, never()).save(any());
    }

    // ---- Cause B backfill: existing docs with useTools=false must be fixed on startup ----

    @Test
    void run_driverSupport_existingDocWithUseToolsFalse_backfillsUseToolsTrue() throws Exception {
        // Simulate a document persisted before the @Builder.Default fix: useTools=false
        AiAgentConfig existing = AiAgentConfig.builder()
                .id("old-driver").agentName("driver_support").active(true)
                .audience(Audience.DRIVER)
                .mcpServers(List.of(AiAgentConfigService.DEFAULT_MCP_SERVER))
                .build();
        existing.setUseTools(false); // force the pre-fix state

        when(repository.findByAgentName("driver_support")).thenReturn(Optional.of(existing));
        when(repository.findByAgentName("customer_support")).thenReturn(Optional.of(existingDoc("customer_support")));
        when(repository.findByAgentName("store_support_default")).thenReturn(Optional.of(existingDoc("store_support_default")));

        initializer.run();

        // The backfill block must call repository.save with useTools=true
        ArgumentCaptor<AiAgentConfig> captor = ArgumentCaptor.forClass(AiAgentConfig.class);
        verify(repository).save(captor.capture());
        assertTrue(captor.getValue().isUseTools(),
                "driver_support backfill must set useTools=true on existing doc");
    }

    @Test
    void run_customerSupport_existingDocWithUseToolsFalse_backfillsUseToolsTrue() throws Exception {
        // Simulate a document persisted before the @Builder.Default fix: useTools=false
        AiAgentConfig existing = AiAgentConfig.builder()
                .id("old-customer").agentName("customer_support").active(true)
                .audience(Audience.CUSTOMER)
                .mcpServers(List.of(AiAgentConfigService.DEFAULT_MCP_SERVER))
                .build();
        existing.setUseTools(false); // force the pre-fix state

        when(repository.findByAgentName("customer_support")).thenReturn(Optional.of(existing));
        when(repository.findByAgentName("driver_support")).thenReturn(Optional.of(existingDoc("driver_support")));
        when(repository.findByAgentName("store_support_default")).thenReturn(Optional.of(existingDoc("store_support_default")));

        initializer.run();

        // The backfill block must call repository.save with useTools=true
        ArgumentCaptor<AiAgentConfig> captor = ArgumentCaptor.forClass(AiAgentConfig.class);
        verify(repository).save(captor.capture());
        assertTrue(captor.getValue().isUseTools(),
                "customer_support backfill must set useTools=true on existing doc");
    }

    // ---- store_support_default fresh seed preserves useTools=true via builder ----

    @Test
    void run_storeSupportDefault_freshSeed_setsUseToolsTrue() throws Exception {
        // Arrange: all conversation agents already exist; only store_support_default is new
        when(repository.findByAgentName("driver_support")).thenReturn(Optional.of(existingDoc("driver_support")));
        when(repository.findByAgentName("customer_support")).thenReturn(Optional.of(existingDoc("customer_support")));
        when(repository.findByAgentName("store_support_default")).thenReturn(Optional.empty());

        // Act
        initializer.run();

        // Assert: repository.save was called once for store_support_default
        ArgumentCaptor<AiAgentConfig> captor = ArgumentCaptor.forClass(AiAgentConfig.class);
        verify(repository).save(captor.capture());
        AiAgentConfig persisted = captor.getValue();
        assertEquals("store_support_default", persisted.getAgentName());
        assertTrue(persisted.isUseTools(), "store_support_default fresh seed must set useTools=true");
        assertEquals(Audience.STORE, persisted.getAudience());
        assertFalse(persisted.getActive(), "store_support_default must be seeded as active=false (template)");
    }

    // ---- helper ----

    /**
     * Builds a minimal pre-existing document that passes ALL backfill checks without dirtying:
     * audience is non-null, mcpServers is non-empty, and useTools=true (@Builder.Default).
     */
    private AiAgentConfig existingDoc(String agentName) {
        return AiAgentConfig.builder()
                .id("existing-" + agentName)
                .agentName(agentName)
                .active(true)
                .audience(Audience.DRIVER) // any non-null audience suppresses the audience dirty flag
                .mcpServers(List.of(AiAgentConfigService.DEFAULT_MCP_SERVER))
                // useTools defaults to true via @Builder.Default — no setUseTools needed here
                .build();
    }
}
