package io.curiousoft.izinga.messaging.aiAgent.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.Optional;

/**
 * Service for managing AI agent configurations.
 * Loads system prompts from MongoDB instead of hardcoding them.
 */
@Service
public class AiAgentConfigService {

    private static final Logger LOG = LoggerFactory.getLogger(AiAgentConfigService.class);

    private final AiAgentConfigRepository repository;

    public AiAgentConfigService(AiAgentConfigRepository repository) {
        this.repository = repository;
    }

    /**
     * Get the system prompt for an agent by name
     * @param agentName The name of the agent (e.g., "driver_support")
     * @return The system prompt, or null if not found
     */
    public String getSystemPrompt(String agentName) {
        Optional<AiAgentConfig> config = repository.findByAgentNameAndActiveTrue(agentName);

        if (config.isPresent()) {
            LOG.info("Loaded system prompt for agent: {}", agentName);
            return config.get().getSystemPrompt();
        } else {
            LOG.warn("No active agent config found for: {}", agentName);
            return null;
        }
    }

    /**
     * Get full agent config by name
     */
    public Optional<AiAgentConfig> getAgentConfig(String agentName) {
        return repository.findByAgentNameAndActiveTrue(agentName);
    }

    /**
     * Create or update an agent configuration
     */
    public AiAgentConfig saveAgentConfig(String agentName, String systemPrompt, String description) {
        Optional<AiAgentConfig> existing = repository.findByAgentName(agentName);

        AiAgentConfig config;
        if (existing.isPresent()) {
            config = existing.get();
            config.setSystemPrompt(systemPrompt);
            config.setDescription(description);
            config.setUpdatedAt(Instant.now());
            config.setVersion(config.getVersion() + 1);
            LOG.info("Updated agent config: {}, version: {}", agentName, config.getVersion());
        } else {
            config = AiAgentConfig.builder()
                .agentName(agentName)
                .systemPrompt(systemPrompt)
                .description(description)
                .active(true)
                .createdAt(Instant.now())
                .updatedAt(Instant.now())
                .version(1)
                .build();
            LOG.info("Created new agent config: {}", agentName);
        }

        return repository.save(config);
    }

    /**
     * Deactivate an agent configuration
     */
    public void deactivateAgent(String agentName) {
        Optional<AiAgentConfig> config = repository.findByAgentName(agentName);
        if (config.isPresent()) {
            config.get().setActive(false);
            repository.save(config.get());
            LOG.info("Deactivated agent config: {}", agentName);
        }
    }

    /**
     * Activate an agent configuration
     */
    public void activateAgent(String agentName) {
        Optional<AiAgentConfig> config = repository.findByAgentName(agentName);
        if (config.isPresent()) {
            config.get().setActive(true);
            repository.save(config.get());
            LOG.info("Activated agent config: {}", agentName);
        }
    }
}

