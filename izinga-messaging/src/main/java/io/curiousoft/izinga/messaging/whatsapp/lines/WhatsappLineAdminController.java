package io.curiousoft.izinga.messaging.whatsapp.lines;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Admin REST API for WhatsApp line registry management.
 *
 * REQ-04 / SEC-06: ADMIN only. Validates phoneNumberId (numeric, <=15 chars).
 * Writes immutable audit records on every change.
 */
@RestController
@RequestMapping("/admin/whatsapp-lines")
@PreAuthorize("hasRole('ADMIN')")
public class WhatsappLineAdminController {

    private static final Logger LOG = LoggerFactory.getLogger(WhatsappLineAdminController.class);
    private static final int PHONE_NUMBER_ID_MAX_LENGTH = 15;

    private final WhatsappLineRepository lineRepository;
    private final WhatsappLineAuditRepository auditRepository;

    public WhatsappLineAdminController(WhatsappLineRepository lineRepository,
                                       WhatsappLineAuditRepository auditRepository) {
        this.lineRepository = lineRepository;
        this.auditRepository = auditRepository;
    }

    /**
     * List all registered WhatsApp lines.
     */
    @GetMapping
    public ResponseEntity<List<WhatsappLine>> listLines() {
        return ResponseEntity.ok(lineRepository.findAll());
    }

    /**
     * Update the active flag of a specific line.
     * SEC-06: validates phoneNumberId format, writes audit record.
     */
    @PutMapping("/{phoneNumberId}/active")
    public ResponseEntity<Object> setActive(
            @PathVariable("phoneNumberId") String phoneNumberId,
            @RequestParam("active") boolean active,
            @AuthenticationPrincipal Jwt jwt) {

        // SEC-06: validate phoneNumberId
        if (phoneNumberId == null || phoneNumberId.isBlank()) {
            return ResponseEntity.badRequest().body(Map.of("error", "phoneNumberId must not be blank"));
        }
        if (!phoneNumberId.matches("\\d+")) {
            return ResponseEntity.badRequest().body(Map.of("error", "phoneNumberId must be numeric"));
        }
        if (phoneNumberId.length() > PHONE_NUMBER_ID_MAX_LENGTH) {
            return ResponseEntity.badRequest().body(
                    Map.of("error", "phoneNumberId must be " + PHONE_NUMBER_ID_MAX_LENGTH + " characters or fewer"));
        }

        Optional<WhatsappLine> opt = lineRepository.findByPhoneNumberId(phoneNumberId);
        if (opt.isEmpty()) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(Map.of("error", "line not found: " + phoneNumberId));
        }

        WhatsappLine line = opt.get();
        boolean before = line.isActive();
        line.setActive(active);
        lineRepository.save(line);

        // SEC-06: immutable audit record
        String operatorUid = jwt != null ? jwt.getSubject() : "unknown";
        String action = active ? "ACTIVATE" : "DEACTIVATE";
        auditRepository.save(new WhatsappLineAuditRecord(operatorUid, action, phoneNumberId, before, active));

        LOG.info("Admin {} set phoneNumberId={} active={}", operatorUid, phoneNumberId, active);
        return ResponseEntity.ok(Map.of("phoneNumberId", phoneNumberId, "active", active));
    }
}
