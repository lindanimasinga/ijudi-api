package io.curiousoft.izinga.messaging.whatsapp.lines;

/**
 * Audience classification for a WhatsApp phone line.
 * Determines which type of user the line serves and which AI agent is active.
 */
public enum Audience {
    DRIVER,
    CUSTOMER,
    STORE,
    OTP
}
