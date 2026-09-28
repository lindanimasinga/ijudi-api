package io.curiousoft.izinga.messaging.whatsapp;

import java.util.List;
import java.util.regex.Pattern;

/**
 * Sanitizes store-sourced content (stock items, business names, custom prompts) before
 * they are injected into AI agent system prompts.
 *
 * SEC-WA02-04-A (start gate — BINDING): injection keywords are detected as BOTH prefix
 * AND substring (case-insensitive), unlike {@link HumanCorrectionSanitizer} which only
 * detects them at the start of a string (WA-LINES-01 SEC-04 Finding 15 gap).
 *
 * Limits:
 * - Individual store-sourced field: max 200 characters
 * - Full constructed store context block: max 500 characters
 */
public final class StoreContentSanitizer {

    private StoreContentSanitizer() {}

    /** Maximum length for a single store-sourced field value (e.g. a stock item name). */
    public static final int MAX_FIELD_LENGTH = 200;
    /**
     * Maximum length for a product description field.
     * NOTE-05 (SEC-WA02-04-A): descriptions are capped at 500 chars (vs 200 for names)
     * because descriptions are free-form and legitimately longer — a 200-char cap would
     * truncate common product descriptions. The cap still limits context size and injection surface.
     */
    public static final int MAX_DESCRIPTION_LENGTH = 500;
    /** Maximum length for the full assembled store context block. */
    public static final int MAX_CONTEXT_LENGTH = 500;

    /**
     * Injection keywords detected as SUBSTRING (case-insensitive) — not just prefix.
     * SEC-WA02-04-A: this is the key difference from HumanCorrectionSanitizer.
     *
     * NOTE-03 (SEC-WA02-04-A — binding): three additional keywords added:
     * - {@code [inst]} — LLaMA-style instruction delimiter commonly used in prompt injection
     * - {@code assistant:} — conversation-role header that can redirect model behaviour
     * - {@code user:} — conversation-role header with same risk profile
     */
    private static final List<Pattern> INJECTION_PATTERNS = List.of(
            // Classic prompt-injection preambles
            Pattern.compile("(?i)ignore\\s+previous"),
            Pattern.compile("(?i)you\\s+are\\s+now"),
            Pattern.compile("(?i)new\\s+instructions"),
            Pattern.compile("(?i)disregard\\s+(all\\s+)?previous"),
            Pattern.compile("(?i)forget\\s+(everything|all|previous)"),
            Pattern.compile("(?i)\\bsystem\\s*:"),
            Pattern.compile("(?i)act\\s+as"),
            Pattern.compile("(?i)pretend\\s+you"),
            // Structural injection tokens
            Pattern.compile("(?i)===.*begin"),
            Pattern.compile("(?i)===.*end"),
            Pattern.compile("(?i)<\\s*system\\s*>"),
            Pattern.compile("(?i)<\\s*/\\s*system\\s*>"),
            // Role override attempts
            Pattern.compile("(?i)from\\s+now\\s+on"),
            Pattern.compile("(?i)your\\s+new\\s+(role|instructions|prompt)"),
            // NOTE-03 additions: LLaMA-style and conversation-role headers
            Pattern.compile("(?i)\\[inst\\]"),
            Pattern.compile("(?i)assistant\\s*:"),
            Pattern.compile("(?i)\\buser\\s*:")
    );

    /**
     * Sanitize a single store-sourced field value (stock name, store name, etc.).
     *
     * @param raw the raw field value from the store's MongoDB document
     * @return sanitized value (trimmed, max 200 chars), or "[removed]" if injection is detected
     */
    public static String sanitizeField(String raw) {
        if (raw == null || raw.isBlank()) {
            return "";
        }
        String text = raw.trim();
        // SEC-WA02-04-A: substring (not just prefix) injection detection
        for (Pattern pattern : INJECTION_PATTERNS) {
            if (pattern.matcher(text).find()) {
                return "[removed]";
            }
        }
        if (text.length() > MAX_FIELD_LENGTH) {
            text = text.substring(0, MAX_FIELD_LENGTH);
        }
        return text;
    }

    /**
     * Sanitize a product description field.
     *
     * NOTE-05 (SEC-WA02-04-A): product descriptions are capped at {@value #MAX_DESCRIPTION_LENGTH}
     * characters (vs {@value #MAX_FIELD_LENGTH} for names). Descriptions are legitimately longer
     * than product names; a 200-char cap would truncate common descriptions. Both apply the same
     * injection detection logic.
     *
     * @param raw the raw description value from the store's MongoDB document
     * @return sanitized description (trimmed, max 500 chars), or "[removed]" if injection is detected
     */
    public static String sanitizeDescription(String raw) {
        if (raw == null || raw.isBlank()) {
            return "";
        }
        String text = raw.trim();
        for (Pattern pattern : INJECTION_PATTERNS) {
            if (pattern.matcher(text).find()) {
                return "[removed]";
            }
        }
        if (text.length() > MAX_DESCRIPTION_LENGTH) {
            text = text.substring(0, MAX_DESCRIPTION_LENGTH);
        }
        return text;
    }

    /**
     * Sanitize the assembled store context block.
     * Called after all fields are assembled but before the block is injected into the prompt.
     *
     * @param contextBlock the assembled store context string
     * @return sanitized block, capped at 500 chars; "[context removed]" if injection is detected
     */
    public static String sanitizeContextBlock(String contextBlock) {
        if (contextBlock == null || contextBlock.isBlank()) {
            return "";
        }
        String text = contextBlock.trim();
        // Full-block scan for injection patterns
        for (Pattern pattern : INJECTION_PATTERNS) {
            if (pattern.matcher(text).find()) {
                return "[context removed]";
            }
        }
        if (text.length() > MAX_CONTEXT_LENGTH) {
            text = text.substring(0, MAX_CONTEXT_LENGTH);
        }
        return text;
    }
}
