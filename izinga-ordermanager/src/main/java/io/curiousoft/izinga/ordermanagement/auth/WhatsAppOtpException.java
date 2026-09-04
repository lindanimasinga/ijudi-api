package io.curiousoft.izinga.ordermanagement.auth;

/** Thrown by WhatsAppOtpService for rate-limit, validation, or code-mismatch failures. */
public class WhatsAppOtpException extends Exception {
    public WhatsAppOtpException(String message) {
        super(message);
    }
}
