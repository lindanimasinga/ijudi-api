package io.curiousoft.izinga.ordermanagement.leads;

import io.curiousoft.izinga.commons.model.ProfileRoles;
import io.curiousoft.izinga.commons.model.StoreType;
import io.curiousoft.izinga.commons.model.UserProfile;
import io.curiousoft.izinga.usermanagement.users.UserProfileService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/leads")
public class LeadController {

    private static final Logger log = LoggerFactory.getLogger(LeadController.class);

    private final LeadService leadService;
    private final UserProfileService userProfileService;

    public LeadController(LeadService leadService, UserProfileService userProfileService) {
        this.leadService = leadService;
        this.userProfileService = userProfileService;
    }

    /**
     * POST /v2/leads — permitAll (customer is unauthenticated).
     */
    @PostMapping(consumes = "application/json", produces = "application/json")
    public ResponseEntity<Lead> upsertLead(@RequestBody LeadRequest request) {
        Lead saved = leadService.upsertLead(request);
        return ResponseEntity.ok(saved);
    }

    /**
     * GET /v2/leads?storeType=MOVERS — admin only.
     */
    @GetMapping(produces = "application/json")
    public ResponseEntity<List<Lead>> getLeads(@RequestParam StoreType storeType) {
        if (!isAdmin()) return ResponseEntity.status(403).build();
        List<Lead> leads = leadService.getLeads(storeType);
        return ResponseEntity.ok(leads);
    }

    /**
     * PATCH /v2/leads/{id}/status — admin only.
     */
    @PatchMapping(value = "/{id}/status", consumes = "application/json", produces = "application/json")
    public ResponseEntity<Lead> updateStatus(@PathVariable String id,
                                             @RequestBody LeadStatusUpdateRequest statusRequest) {
        if (!isAdmin()) return ResponseEntity.status(403).build();
        Lead updated = leadService.updateLeadStatus(id, statusRequest.getStatus());
        return ResponseEntity.ok(updated);
    }

    private boolean isAdmin() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        log.info("[leads] auth={} class={}", auth, auth != null ? auth.getClass().getSimpleName() : "null");
        if (auth == null) {
            log.warn("[leads] no authentication in SecurityContext — returning 403");
            return false;
        }
        String uid = auth.getName();
        log.info("[leads] uid={} authenticated={} authorities={}", uid, auth.isAuthenticated(), auth.getAuthorities());
        UserProfile caller = userProfileService.find(uid);
        log.info("[leads] userProfile={} role={}", caller != null ? caller.getId() : "NOT_FOUND", caller != null ? caller.getRole() : "null");
        boolean admin = caller != null && caller.getRole() == ProfileRoles.ADMIN;
        log.info("[leads] isAdmin={}", admin);
        return admin;
    }
}
