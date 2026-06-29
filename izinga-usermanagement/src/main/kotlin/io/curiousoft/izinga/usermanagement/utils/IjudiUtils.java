package io.curiousoft.izinga.usermanagement.utils;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Java port of the Kotlin utility methods from IjudiUtils.kt
 */
public final class IjudiUtils {

    private static final Logger LOGGER = LoggerFactory.getLogger(IjudiUtils.class);

    private IjudiUtils() { /* utility */ }

    /**
     * Validate South African ID number format and Luhn check.
     */
    public static boolean isIdNumber(String id) {
        if (id == null) return false;
        final String idNumberRegex = "(((\\d{2}((0[13578]|1[02])(0[1-9]|[12]\\d|3[01])|(0[13456789]|1[012])(0[1-9]|[12]\\d|30)|02(0[1-9]|1\\d|2[0-8])))|([02468][048]|[13579][26])0229))(( |-)(\\d{4})( |-)(\\d{3})|(\\d{7}))";
        if (!id.matches(idNumberRegex)) return false;

        // Luhn validation: typical Luhn algorithm on full id
        try {
            int sum = 0;
            boolean alternate = false;
            for (int i = id.length() - 1; i >= 0; i--) {
                char ch = id.charAt(i);
                if (!Character.isDigit(ch)) continue; // skip separators if present
                int n = ch - '0';
                if (alternate) {
                    n *= 2;
                    if (n > 9) n = n - 9;
                }
                sum += n;
                alternate = !alternate;
            }
            return sum % 10 == 0;
        } catch (Exception ex) {
            LOGGER.debug("Error during Luhn validation", ex);
            return false;
        }
    }

    public static boolean isSAMobileNumber(String number) {
        if (number == null) return false;
        final String regex = "(\\+27|27|0)[1-9]\\d{8}";
        return number.matches(regex);
    }
}

