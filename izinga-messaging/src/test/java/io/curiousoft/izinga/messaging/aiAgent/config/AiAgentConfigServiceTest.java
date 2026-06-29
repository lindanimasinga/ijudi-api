package io.curiousoft.izinga.messaging.aiAgent.config;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
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
}

