package io.curiousoft.izinga.messaging.whatsapp;

import java.util.List;
import java.util.regex.Pattern;

/**
 * Sanitizes human-correction text before it is appended to AI agent prompts.
 *
 * SEC-04: strips instruction-injection preambles, caps at 500 characters.
 */
public final class HumanCorrectionSanitizer {

    private HumanCorrectionSanitizer() {}

    private static final int MAX_LENGTH = 500;

    /**
     * Patterns that indicate prompt-injection attempts.
     * Case-insensitive prefix match at start of trimmed text.
     */
    private static final List<Pattern> INJECTION_PATTERNS = List.of(
            Pattern.compile("(?i)^ignore\\s+previous.*"),
            Pattern.compile("(?i)^you\\s+are\\s+now.*"),
            Pattern.compile("(?i)^new\\s+instructions.*"),
            Pattern.compile("(?i)^disregard\\s+(all\\s+)?previous.*"),
            Pattern.compile("(?i)^forget\\s+(everything|all|previous).*"),
            Pattern.compile("(?i)^system\\s*:.*"),
            Pattern.compile("(?i)^act\\s+as.*"),
            Pattern.compile("(?i)^pretend\\s+you.*")
    );

    /**
     * Sanitize a human-correction string.
     *
     * @param raw the raw text as supplied by the human operator
     * @return sanitized text, or null if the input is null/blank after stripping
     */
    public static String sanitize(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        String text = raw.trim();

        // Strip instruction-injection preambles
        for (Pattern pattern : INJECTION_PATTERNS) {
            if (pattern.matcher(text).matches()) {
                return null;
            }
        }

        // Cap at 500 chars
        if (text.length() > MAX_LENGTH) {
            text = text.substring(0, MAX_LENGTH);
        }

        return text;
    }
}
