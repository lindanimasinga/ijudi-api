package io.curiousoft.izinga.messaging.whatsapp.lines;

import org.jetbrains.annotations.Nullable;

/**
 * Immutable value object threading inbound-line context through the inbound message pipeline.
 *
 * SA-3: explicit parameter — NEVER @RequestScope. Passed from processInboundMessage
 * → upsertSession → handlePreDispatchFlows → dispatchMessageByType → AiCustomerServiceAgent.
 *
 * REQ-10
 */
public record LineContext(
        WhatsappLine line,
        String agentName,
        Audience audience,
        @Nullable String storeId
) {}
