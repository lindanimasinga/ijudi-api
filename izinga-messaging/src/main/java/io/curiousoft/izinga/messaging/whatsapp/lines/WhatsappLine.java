package io.curiousoft.izinga.messaging.whatsapp.lines;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

/**
 * Represents a registered WhatsApp Business phone line.
 * Each line is associated with an audience type, an AI agent, and optionally a store.
 *
 * REQ-01 / SA-1: lives in izinga-messaging, not izinga-commons.
 */
@Document(collection = "whatsapp_lines")
public class WhatsappLine {

    @Id
    private String id;

    /** Meta phone_number_id — unique per line. */
    @Indexed(unique = true)
    private String phoneNumberId;

    /** Human-readable display number (e.g. +27 81 234 5678). */
    private String displayNumber;

    /** Audience this line serves. */
    private Audience audience;

    /** Name of the AI agent that handles inbound messages on this line. */
    private String agentName;

    /** Optional store ID if this line is scoped to a specific store. */
    private String storeId;

    /** Whether this line is active and should receive/send messages. */
    private boolean active;

    /** Whether this is the default fallback line when no other line matches. */
    private boolean isDefault;

    public WhatsappLine() {}

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }

    public String getPhoneNumberId() { return phoneNumberId; }
    public void setPhoneNumberId(String phoneNumberId) { this.phoneNumberId = phoneNumberId; }

    public String getDisplayNumber() { return displayNumber; }
    public void setDisplayNumber(String displayNumber) { this.displayNumber = displayNumber; }

    public Audience getAudience() { return audience; }
    public void setAudience(Audience audience) { this.audience = audience; }

    public String getAgentName() { return agentName; }
    public void setAgentName(String agentName) { this.agentName = agentName; }

    public String getStoreId() { return storeId; }
    public void setStoreId(String storeId) { this.storeId = storeId; }

    public boolean isActive() { return active; }
    public void setActive(boolean active) { this.active = active; }

    public boolean isDefault() { return isDefault; }
    public void setDefault(boolean aDefault) { isDefault = aDefault; }
}
