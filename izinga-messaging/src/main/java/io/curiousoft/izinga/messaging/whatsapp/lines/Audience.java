package io.curiousoft.izinga.messaging.whatsapp.lines;

/**
 * Audience classification for a WhatsApp phone line.
 * Determines which type of user the line serves and which AI agent is active.
 */
public enum Audience {
    DRIVER,
    CUSTOMER,
    STORE,
    OTP,
    /** AMD-01 (REQ-A1): ADMIN audience — minted at runtime only when caller is ProfileRoles.ADMIN
     *  AND webhook signature verification is active. Never sourced from AiAgentConfig (REQ-A4). */
    ADMIN
}
