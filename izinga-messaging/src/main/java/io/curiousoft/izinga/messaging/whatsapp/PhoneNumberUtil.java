package io.curiousoft.izinga.messaging.whatsapp;

/**
 * Static utility for normalizing South African mobile numbers.
 * REQ-09: consolidates phone normalization across the messaging module.
 *
 * Normalizes to +27XXXXXXXXX using the last 9 digits of the input.
 * Guards against short input (< 9 chars after stripping non-digits).
 */
public final class PhoneNumberUtil {

    private PhoneNumberUtil() {}

    /**
     * Normalizes a raw phone number to E.164 format with +27 prefix.
     *
     * @param phone raw phone (e.g. "0821234567", "+27821234567", "27821234567")
     * @return "+27XXXXXXXXX" or the original value if it is null/blank/too short
     */
    public static String normalize0ToE164(String phone) {
        if (phone == null || phone.isBlank()) {
            return phone;
        }
        // Strip non-digit characters
        String digits = phone.replaceAll("[^\\d]", "");
        if (digits.length() < 9) {
            // Too short — return as-is to avoid a substring exception
            return phone;
        }
        String last9 = digits.substring(digits.length() - 9);
        return "+27" + last9;
    }

    /**
     * Legacy single-step normalization for the common case where a number starts with "0".
     * Kept for backward compatibility in simple call sites.
     */
    public static String normalizeLeadingZero(String phone) {
        if (phone == null) return null;
        return phone.startsWith("0") ? phone.replaceFirst("0", "+27") : phone;
    }
}
