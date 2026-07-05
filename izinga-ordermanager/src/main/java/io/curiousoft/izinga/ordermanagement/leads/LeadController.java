package io.curiousoft.izinga.ordermanagement.leads;

import io.curiousoft.izinga.commons.model.StoreType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/leads")
public class LeadController {

    private final LeadService leadService;

    public LeadController(LeadService leadService) {
        this.leadService = leadService;
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
        List<Lead> leads = leadService.getLeads(storeType);
        return ResponseEntity.ok(leads);
    }

    /**
     * PATCH /v2/leads/{id}/status — admin only.
     */
    @PatchMapping(value = "/{id}/status", consumes = "application/json", produces = "application/json")
    public ResponseEntity<Lead> updateStatus(@PathVariable String id,
                                             @RequestBody LeadStatusUpdateRequest statusRequest) {
        Lead updated = leadService.updateLeadStatus(id, statusRequest.getStatus());
        return ResponseEntity.ok(updated);
    }
}
