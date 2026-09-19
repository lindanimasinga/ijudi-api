package io.curiousoft.izinga.messaging.whatsapp.lines;

import io.curiousoft.izinga.messaging.whatsapp.WhatsappConfig;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Optional;

/**
 * Resolves the phoneNumberId to use when sending a WhatsApp outbound message.
 *
 * Resolution order (flag ON):
 *  1. store+audience match (storeId != null)
 *  2. audience match (storeId = null)
 *  3. isDefault
 *  4. WARN + legacy fallback
 *
 * Flag OFF (multiLineEnabled = false): always returns legacy whatsapp.cloud.phoneId
 * for byte-identical legacy behavior. REQ-05 / REQ-07
 */
@Component
public class WhatsappSenderResolver {

    private static final Logger LOG = LoggerFactory.getLogger(WhatsappSenderResolver.class);

    private final WhatsappConfig whatsappConfig;
    private final WhatsappLineRepository repository;
    private final boolean multiLineEnabled;

    public WhatsappSenderResolver(WhatsappConfig whatsappConfig,
                                  WhatsappLineRepository repository) {
        this.whatsappConfig = whatsappConfig;
        this.repository = repository;
        this.multiLineEnabled = whatsappConfig.multiLineEnabled() != null && whatsappConfig.multiLineEnabled();
    }

    /**
     * Resolve the phoneNumberId to use for the given audience and optional storeId.
     *
     * @param audience the target audience
     * @param storeId  optional store scope (null for non-store-specific sends)
     * @return the phoneNumberId string to pass to the WhatsApp send API
     */
    public String resolve(Audience audience, @Nullable String storeId) {
        if (!multiLineEnabled) {
            return whatsappConfig.phoneId();
        }

        // 1. Store + audience match
        if (storeId != null) {
            Optional<WhatsappLine> storeLine = repository.findByAudienceAndStoreIdAndActiveTrue(audience, storeId);
            if (storeLine.isPresent()) {
                LOG.debug("REQ-23: outbound send phoneNumberId={} toAudience={} storeId={}",
                        storeLine.get().getPhoneNumberId(), audience, storeId);
                return storeLine.get().getPhoneNumberId();
            }
        }

        // 2. Audience match
        List<WhatsappLine> audienceLines = repository.findByAudienceAndActiveTrue(audience);
        if (!audienceLines.isEmpty()) {
            String phoneNumberId = audienceLines.get(0).getPhoneNumberId();
            LOG.debug("REQ-23: outbound send phoneNumberId={} toAudience={}", phoneNumberId, audience);
            return phoneNumberId;
        }

        // 3. Default line
        Optional<WhatsappLine> defaultLine = repository.findByIsDefaultTrue();
        if (defaultLine.isPresent()) {
            LOG.debug("REQ-23: outbound send phoneNumberId={} (default fallback) toAudience={}",
                    defaultLine.get().getPhoneNumberId(), audience);
            return defaultLine.get().getPhoneNumberId();
        }

        // 4. WARN + legacy fallback
        LOG.warn("No WhatsappLine found for audience={} storeId={} — falling back to legacy phoneId", audience, storeId);
        return whatsappConfig.phoneId();
    }
}
