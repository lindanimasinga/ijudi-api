package io.curiousoft.izinga.messaging.whatsapp.lines;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.Instant;

/**
 * Immutable audit record written on every admin change to a WhatsappLine.
 * SEC-06: never updated, only inserted.
 */
@Document(collection = "whatsapp_line_audit")
public class WhatsappLineAuditRecord {

    @Id
    private String id;

    /** Firebase UID of the admin who made the change. */
    private String operatorUid;

    /** Action performed: ACTIVATE or DEACTIVATE. */
    private String action;

    /** The phoneNumberId of the line that was changed. */
    private String phoneNumberId;

    /** Serialized before-state (active flag). */
    private Boolean stateBefore;

    /** Serialized after-state (active flag). */
    private Boolean stateAfter;

    private Instant timestamp;

    public WhatsappLineAuditRecord() {}

    public WhatsappLineAuditRecord(String operatorUid, String action, String phoneNumberId,
                                   Boolean stateBefore, Boolean stateAfter) {
        this.operatorUid = operatorUid;
        this.action = action;
        this.phoneNumberId = phoneNumberId;
        this.stateBefore = stateBefore;
        this.stateAfter = stateAfter;
        this.timestamp = Instant.now();
    }

    public String getId() { return id; }
    public String getOperatorUid() { return operatorUid; }
    public String getAction() { return action; }
    public String getPhoneNumberId() { return phoneNumberId; }
    public Boolean getStateBefore() { return stateBefore; }
    public Boolean getStateAfter() { return stateAfter; }
    public Instant getTimestamp() { return timestamp; }
}
