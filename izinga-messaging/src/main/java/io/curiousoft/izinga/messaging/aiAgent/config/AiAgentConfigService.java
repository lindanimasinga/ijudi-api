package io.curiousoft.izinga.messaging.aiAgent.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import io.curiousoft.izinga.messaging.whatsapp.HumanCorrectionSanitizer;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Service for managing AI agent configurations.
 * Loads system prompts from MongoDB instead of hardcoding them.
 *
 * REQ-18: per-agent cache (Map<String, AiAgentConfig>) replaces single-entry Optional.
 */
@Service
public class AiAgentConfigService {

    private static final Logger LOG = LoggerFactory.getLogger(AiAgentConfigService.class);

    private final AiAgentConfigRepository repository;
    /** REQ-18: keyed by agentName */
    private final Map<String, AiAgentConfig> configCache = new ConcurrentHashMap<>();

    public AiAgentConfigService(AiAgentConfigRepository repository) {
        this.repository = repository;
    }

    /**
     * Get the system prompt for an agent by name
     * @param agentName The name of the agent (e.g., "driver_support")
     * @return The system prompt, or null if not found
     */
    public String getSystemPrompt(String agentName) {
        return getAgentConfig(agentName).map(AiAgentConfig::getSystemPrompt).orElse(null);
    }

    public AiAgentConfig getActiveAgentConfig(String agentName) {
         return getAgentConfig(agentName).orElse(null);
    }

    /**
     * Get full agent config by name.
     * REQ-18: backed by per-agent map cache.
     */
    public Optional<AiAgentConfig> getAgentConfig(String agentName) {
        if (!configCache.containsKey(agentName)) {
            repository.findByAgentNameAndActiveTrue(agentName)
                    .ifPresent(c -> configCache.put(agentName, c));
        }
        return Optional.ofNullable(configCache.get(agentName));
    }

    /**
     * Create or update an agent configuration
     */
    public AiAgentConfig saveAgentConfig(String agentName, String systemPrompt, String description) {
        var existing = repository.findByAgentName(agentName);
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
        AiAgentConfig saved = repository.save(config);
        // REQ-18: invalidate cache entry so next read re-fetches
        configCache.put(agentName, saved);
        return saved;
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

    public List<McpServerConfig> getMcpToolsForAgent() {
        // For simplicity, returning hardcoded tools. In a real implementation, this could be loaded from the database as well.
        return List.of(
                new McpServerConfig("mcp", "order-and-user-management-api", "API for managing orders and users", "https://api.izinga.co.za/mcp", "never")
        );
    }

    /**
     * Append a human-correction entry to the agent's system prompt.
     * Creates the "## Human Correction Log" section if not already present.
     * Safe to call even when no active config exists for the agent.
     */
    public void appendHumanCorrection(String agentName, String phone, String messageText) {
        AiAgentConfig agentConfig = getActiveAgentConfig(agentName);
        if (agentConfig == null) {
            LOG.warn("No active agent config for {} — skipping correction append", agentName);
            return;
        }
        // SEC-04: sanitize before appending to prompt
        String sanitized = HumanCorrectionSanitizer.sanitize(messageText);
        if (sanitized == null) {
            LOG.warn("Human correction for agentName={} phone={} was rejected by sanitizer — skipping", agentName, phone);
            return;
        }
        String currentPrompt = agentConfig.getSystemPrompt() != null ? agentConfig.getSystemPrompt() : "";
        String entry = "\n- [" + Instant.now() + "] Customer " + phone + ": " + sanitized;
        String updatedPrompt = currentPrompt.contains("## Human Correction Log")
                ? currentPrompt + entry
                : currentPrompt
                        + "\n\n## Human Correction Log"
                        + "\nThe following corrections were made by human agents. Apply these as guidance when similar questions arise:"
                        + entry;
        saveAgentConfig(agentName, updatedPrompt, agentConfig.getDescription());
        LOG.info("Appended human correction to agent config: {}", agentName);
    }
}

