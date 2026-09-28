package io.curiousoft.izinga.messaging.whatsapp.lines;

import io.curiousoft.izinga.messaging.aiAgent.StoreAiAgent;
import io.curiousoft.izinga.messaging.aiAgent.config.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.List;
import java.util.Objects;

/**
 * T-05/T-06/T-07: provisioning service for per-store WhatsApp lines.
 *
 * Responsibilities:
 * - Provision: create WhatsappLine with STORE audience + storeId; clone store_support_default
 *   into store_support_<storeId> agent config; write audit record.
 * - Deprovision: deactivate the line; deactivate its agent config; write audit record.
 * - Get: return active lines for a given storeId.
 *
 * This is a pure ADMIN manual action — no billing gate, no plan check.
 */
@Service
public class StoreLineProvisioningService {

    private static final Logger LOG = LoggerFactory.getLogger(StoreLineProvisioningService.class);

    private final WhatsappLineRepository lineRepository;
    private final WhatsappLineAuditRepository auditRepository;
    private final AiAgentConfigRepository agentConfigRepository;

    public StoreLineProvisioningService(WhatsappLineRepository lineRepository,
                                        WhatsappLineAuditRepository auditRepository,
                                        AiAgentConfigRepository agentConfigRepository) {
        this.lineRepository = lineRepository;
        this.auditRepository = auditRepository;
        this.agentConfigRepository = agentConfigRepository;
    }

    /**
     * T-05: Provision a new WhatsApp line for a store.
     * Idempotent: returns existing inactive line if one exists for this phoneNumberId,
     * activates it and updates storeId.
     *
     * @param phoneNumberId Meta phone_number_id for this line
     * @param displayNumber human-readable display number (e.g. "+27 81 234 5678")
     * @param storeId       the store to scope this line to
     * @param operatorUid   Firebase UID of the ADMIN performing the action
     * @return the created or updated WhatsappLine
     * @throws IllegalArgumentException if storeId or phoneNumberId is blank
     */
    public WhatsappLine provisionStoreLine(String phoneNumberId, String displayNumber,
                                           String storeId, String operatorUid) {
        Objects.requireNonNull(phoneNumberId, "phoneNumberId must not be null");
        Objects.requireNonNull(storeId, "storeId must not be null");
        if (phoneNumberId.isBlank()) throw new IllegalArgumentException("phoneNumberId must not be blank");
        if (storeId.isBlank()) throw new IllegalArgumentException("storeId must not be blank");

        // Check for existing line on this phoneNumberId
        var existing = lineRepository.findByPhoneNumberId(phoneNumberId);
        WhatsappLine line;
        Boolean stateBefore = null;
        if (existing.isPresent()) {
            line = existing.get();
            stateBefore = line.isActive();
            line.setActive(true);
            line.setAudience(Audience.STORE);
            line.setStoreId(storeId);
            if (displayNumber != null) line.setDisplayNumber(displayNumber);
            // Ensure agentName follows the naming convention
            if (line.getAgentName() == null || line.getAgentName().isBlank()) {
                line.setAgentName(StoreAiAgent.AGENT_NAME_PREFIX + storeId);
            }
        } else {
            line = new WhatsappLine();
            line.setPhoneNumberId(phoneNumberId);
            line.setDisplayNumber(displayNumber);
            line.setAudience(Audience.STORE);
            line.setStoreId(storeId);
            line.setActive(true);
            line.setDefault(false);
            line.setAgentName(StoreAiAgent.AGENT_NAME_PREFIX + storeId);
        }

        WhatsappLine saved = lineRepository.save(line);
        LOG.info("T-05: provisioned store line phoneNumberId={} storeId={} by operator={}", phoneNumberId, storeId, operatorUid);

        // Clone store_support_default → store_support_<storeId> if not already present
        cloneDefaultAgentConfigForStore(storeId);

        // Write audit record
        writeAuditRecord(operatorUid, "PROVISION_STORE_LINE", phoneNumberId, storeId, stateBefore, true);
        return saved;
    }

    /**
     * T-06: Deactivate the WhatsApp line for a given storeId.
     * Also deactivates the store-specific agent config.
     *
     * @param storeId     the store whose line should be deactivated
     * @param operatorUid Firebase UID of the ADMIN performing the action
     * @return number of lines deactivated (0 if none found)
     */
    public int deprovisionStoreLine(String storeId, String operatorUid) {
        Objects.requireNonNull(storeId, "storeId must not be null");
        List<WhatsappLine> lines = lineRepository.findByAudienceAndActiveTrue(Audience.STORE)
                .stream().filter(l -> storeId.equals(l.getStoreId())).toList();
        if (lines.isEmpty()) {
            LOG.warn("T-06: no active STORE lines found for storeId={}", storeId);
            return 0;
        }
        for (WhatsappLine line : lines) {
            line.setActive(false);
            lineRepository.save(line);
            writeAuditRecord(operatorUid, "DEPROVISION_STORE_LINE", line.getPhoneNumberId(), storeId, true, false);
            LOG.info("T-06: deprovisioned store line phoneNumberId={} storeId={} by operator={}", line.getPhoneNumberId(), storeId, operatorUid);
        }
        // Deactivate the store-specific agent config
        deactivateStoreAgentConfig(storeId);
        return lines.size();
    }

    /**
     * T-07: Get active WhatsApp lines for a store.
     *
     * @param storeId the store to query
     * @return list of active WhatsappLine records for that store
     */
    public List<WhatsappLine> getStoreLinesForStore(String storeId) {
        Objects.requireNonNull(storeId, "storeId must not be null");
        return lineRepository.findByAudienceAndActiveTrue(Audience.STORE)
                .stream().filter(l -> storeId.equals(l.getStoreId())).toList();
    }

    // ---- private helpers ----

    /**
     * Clone store_support_default into store_support_<storeId> if that config doesn't exist yet.
     * The clone is active=true and scoped to the given storeId.
     */
    private void cloneDefaultAgentConfigForStore(String storeId) {
        String agentName = StoreAiAgent.AGENT_NAME_PREFIX + storeId;
        if (agentConfigRepository.findByAgentName(agentName).isPresent()) {
            LOG.debug("Agent config '{}' already exists — skipping clone", agentName);
            return;
        }
        var defaultConfig = agentConfigRepository.findByAgentName(StoreAiAgent.DEFAULT_STORE_AGENT);
        if (defaultConfig.isEmpty()) {
            LOG.warn("store_support_default not found — creating minimal store agent config for storeId={}", storeId);
            // Create a minimal active config without system prompt
            AiAgentConfig config = AiAgentConfig.builder()
                    .agentName(agentName)
                    .systemPrompt("")
                    .description("Store agent for storeId=" + storeId)
                    .active(true)
                    .audience(Audience.STORE)
                    .storeId(storeId)
                    .useTools(true)
                    .build();
            agentConfigRepository.save(config);
        } else {
            AiAgentConfig template = defaultConfig.get();
            AiAgentConfig clone = AiAgentConfig.builder()
                    .agentName(agentName)
                    .systemPrompt(template.getSystemPrompt())
                    .description("Store agent for storeId=" + storeId)
                    .active(true)
                    .audience(Audience.STORE)
                    .storeId(storeId)
                    .mcpServers(template.getMcpServers() != null ? List.copyOf(template.getMcpServers()) : List.of())
                    .allowedTools(template.getAllowedTools() != null ? List.copyOf(template.getAllowedTools()) : List.of())
                    .useTools(template.isUseTools())
                    .build();
            agentConfigRepository.save(clone);
            LOG.info("Cloned store_support_default → {} for storeId={}", agentName, storeId);
        }
    }

    private void deactivateStoreAgentConfig(String storeId) {
        String agentName = StoreAiAgent.AGENT_NAME_PREFIX + storeId;
        agentConfigRepository.findByAgentName(agentName).ifPresent(config -> {
            config.setActive(false);
            config.setUpdatedAt(Instant.now());
            config.setVersion(config.getVersion() + 1);
            agentConfigRepository.save(config);
            LOG.info("T-06: deactivated agent config '{}'", agentName);
        });
    }

    private void writeAuditRecord(String operatorUid, String action, String phoneNumberId,
                                   String storeId, Boolean stateBefore, Boolean stateAfter) {
        try {
            var record = new WhatsappLineAuditRecord(operatorUid, action, phoneNumberId, storeId, stateBefore, stateAfter);
            auditRepository.save(record);
        } catch (Exception e) {
            // Audit writes are best-effort and must not fail the business operation
            LOG.error("Failed to write audit record for action={} phoneNumberId={}: {}", action, phoneNumberId, e.getMessage());
        }
    }
}
