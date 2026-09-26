package io.curiousoft.izinga.messaging.whatsapp.lines;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * T-05/T-06/T-07: ADMIN-only REST endpoints for per-store WhatsApp line provisioning.
 *
 * All provisioning is a pure manual ADMIN action — no billing gate, no plan/tier check.
 *
 * POST   /admin/whatsapp-lines/store             — provision a new store line (T-05)
 * DELETE /admin/whatsapp-lines/store/{storeId}   — deprovision a store line (T-06)
 * GET    /admin/whatsapp-lines/store/{storeId}   — get active lines for a store (T-07)
 */
@RestController
@RequestMapping("/admin/whatsapp-lines/store")
@PreAuthorize("hasRole('ADMIN')")
public class StoreLineProvisioningController {

    private static final Logger LOG = LoggerFactory.getLogger(StoreLineProvisioningController.class);

    private final StoreLineProvisioningService provisioningService;

    public StoreLineProvisioningController(StoreLineProvisioningService provisioningService) {
        this.provisioningService = provisioningService;
    }

    /**
     * T-05: Provision a new WhatsApp line for a store.
     *
     * Request body (JSON):
     * {
     *   "phoneNumberId": "1234567890",
     *   "displayNumber": "+27 81 234 5678",
     *   "storeId": "abc-store-id"
     * }
     *
     * Returns 201 Created with the saved WhatsappLine, or 400 for invalid input.
     */
    @PostMapping(consumes = MediaType.APPLICATION_JSON_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<Object> provisionStoreLine(@RequestBody ProvisionStoreLineRequest request,
                                                      Authentication authentication) {
        try {
            String operatorUid = authentication != null ? authentication.getName() : "unknown";
            if (request.getPhoneNumberId() == null || request.getPhoneNumberId().isBlank()) {
                return ResponseEntity.badRequest().body(Map.of("error", "phoneNumberId is required"));
            }
            if (request.getStoreId() == null || request.getStoreId().isBlank()) {
                return ResponseEntity.badRequest().body(Map.of("error", "storeId is required"));
            }
            WhatsappLine line = provisioningService.provisionStoreLine(
                    request.getPhoneNumberId(), request.getDisplayNumber(),
                    request.getStoreId(), operatorUid);
            return ResponseEntity.status(HttpStatus.CREATED).body(line);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        } catch (Exception e) {
            LOG.error("Error provisioning store line", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(Map.of("error", "internal error"));
        }
    }

    /**
     * T-06: Deactivate all WhatsApp lines for a store.
     *
     * Returns 200 with count of deactivated lines, or 404 if no active lines were found.
     */
    @DeleteMapping(value = "/{storeId}", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<Object> deprovisionStoreLine(@PathVariable String storeId,
                                                        Authentication authentication) {
        try {
            String operatorUid = authentication != null ? authentication.getName() : "unknown";
            int count = provisioningService.deprovisionStoreLine(storeId, operatorUid);
            if (count == 0) {
                return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("error", "no active lines found for storeId"));
            }
            return ResponseEntity.ok(Map.of("deactivated", count));
        } catch (Exception e) {
            LOG.error("Error deprovisioning store line for storeId={}", storeId, e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(Map.of("error", "internal error"));
        }
    }

    /**
     * T-07: Get active WhatsApp lines for a store.
     *
     * Returns 200 with list (may be empty).
     */
    @GetMapping(value = "/{storeId}", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<List<WhatsappLine>> getStoreLines(@PathVariable String storeId) {
        try {
            List<WhatsappLine> lines = provisioningService.getStoreLinesForStore(storeId);
            return ResponseEntity.ok(lines);
        } catch (Exception e) {
            LOG.error("Error fetching store lines for storeId={}", storeId, e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    /** DTO for the POST /store provisioning request. */
    public static class ProvisionStoreLineRequest {
        private String phoneNumberId;
        private String displayNumber;
        private String storeId;

        public String getPhoneNumberId() { return phoneNumberId; }
        public void setPhoneNumberId(String phoneNumberId) { this.phoneNumberId = phoneNumberId; }
        public String getDisplayNumber() { return displayNumber; }
        public void setDisplayNumber(String displayNumber) { this.displayNumber = displayNumber; }
        public String getStoreId() { return storeId; }
        public void setStoreId(String storeId) { this.storeId = storeId; }
    }
}
