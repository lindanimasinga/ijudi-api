package io.curiousoft.izinga.messaging.aiAgent;

import io.curiousoft.izinga.commons.model.BusinessHours;
import io.curiousoft.izinga.commons.model.Stock;
import io.curiousoft.izinga.commons.model.StoreProfile;
import io.curiousoft.izinga.messaging.whatsapp.StoreContentSanitizer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.text.SimpleDateFormat;
import java.util.*;
import java.util.Locale;
import java.util.stream.Collectors;

/**
 * Builds the ephemeral store context block that is injected into a store AI agent's
 * system prompt at inference time.
 *
 * T-09: creates the store context block containing:
 * - Store name and address (sanitized)
 * - Business hours (formatted)
 * - Stock menu items sorted by position ASC, capped at {@code storeContextMaxItems}
 *
 * The block is EPHEMERAL — it is never persisted; it is assembled fresh each call.
 * SEC-WA02-04-A: all store-sourced field values are sanitized via {@link StoreContentSanitizer}
 * before assembly.
 */
@Component
public class StoreContextResolver {

    private static final Logger LOG = LoggerFactory.getLogger(StoreContextResolver.class);
    private static final SimpleDateFormat TIME_FMT = new SimpleDateFormat("HH:mm");

    /**
     * SA-021-7 / NOTE-04: cap on menu items injected per prompt.
     * Configurable via {@code ai.agent.store.productCap} in application.properties
     * (renamed from the original {@code store.context.max-items} to match ADR-021 SA-021-7).
     */
    private final int maxItems;

    public StoreContextResolver(
            @Value("${ai.agent.store.productCap:50}") int maxItems) {
        this.maxItems = maxItems;
    }

    /**
     * Build the ephemeral store context block for injection into the agent system prompt.
     *
     * @param store the StoreProfile loaded from MongoDB
     * @return a sanitized, capped context block string
     */
    public String buildContextBlock(StoreProfile store) {
        if (store == null) {
            LOG.warn("StoreContextResolver: null store — returning empty context");
            return "";
        }
        StringBuilder sb = new StringBuilder();
        // Store name
        String name = StoreContentSanitizer.sanitizeField(store.getName());
        sb.append("Store: ").append(name).append("\n");

        // Address
        String address = store.getAddress() != null
                ? StoreContentSanitizer.sanitizeField(store.getAddress())
                : "";
        sb.append("Location: ").append(address).append("\n");

        // Business hours
        sb.append("Hours:\n");
        List<BusinessHours> hours = store.getBusinessHours();
        if (hours != null && !hours.isEmpty()) {
            for (BusinessHours bh : hours) {
                String open = bh.getOpen() != null ? TIME_FMT.format(bh.getOpen()) : "?";
                String close = bh.getClose() != null ? TIME_FMT.format(bh.getClose()) : "?";
                sb.append("  ").append(bh.getDay()).append(": ").append(open).append(" - ").append(close).append("\n");
            }
        } else {
            sb.append("  Not specified\n");
        }

        // Menu — sorted by position ASC, capped at maxItems
        sb.append("Menu:\n");
        Set<Stock> stockSet = store.getStockList();
        if (stockSet != null && !stockSet.isEmpty()) {
            List<Stock> eligible = stockSet.stream()
                    .filter(s -> s != null && s.getName() != null)
                    .collect(Collectors.toList());
            // REQ-16: log when the cap is applied so omissions are traceable in CloudWatch
            if (eligible.size() > maxItems) {
                LOG.debug("StoreContextResolver: product cap applied — store={} eligible={} showing={} omitted={}",
                        store.getId(), eligible.size(), maxItems, eligible.size() - maxItems);
            }
            List<Stock> sorted = eligible.stream()
                    .sorted(Comparator.comparingInt(s -> {
                        // Stock.position defaults to 10000 (from Kotlin default)
                        int pos = s.getPosition();
                        return pos;
                    }))
                    .limit(maxItems)
                    .collect(Collectors.toList());
            for (Stock s : sorted) {
                String itemName = StoreContentSanitizer.sanitizeField(s.getName());
                // NOTE-05: descriptions use sanitizeDescription() capped at 500 chars, not
                // sanitizeField() capped at 200 chars — descriptions are legitimately longer.
                String desc = s.getDescription() != null
                        ? StoreContentSanitizer.sanitizeDescription(s.getDescription())
                        : "";
                double price = s.getPrice();
                sb.append("  - ").append(itemName);
                if (!desc.isEmpty()) {
                    sb.append(": ").append(desc);
                }
                // Use Locale.US to ensure period decimal separator regardless of server locale
                sb.append(" (R").append(String.format(Locale.US, "%.2f", price)).append(")\n");
            }
        } else {
            sb.append("  No items listed\n");
        }

        // Sanitize the full assembled block
        return StoreContentSanitizer.sanitizeContextBlock(sb.toString());
    }
}
