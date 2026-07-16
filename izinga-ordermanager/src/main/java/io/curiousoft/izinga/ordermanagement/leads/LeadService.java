package io.curiousoft.izinga.ordermanagement.leads;

import io.curiousoft.izinga.commons.model.StoreType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.Date;
import java.util.List;
import java.util.Optional;

@Service
public class LeadService {

    private static final Logger LOG = LoggerFactory.getLogger(LeadService.class);

    private final LeadRepository leadRepository;

    public LeadService(LeadRepository leadRepository) {
        this.leadRepository = leadRepository;
    }

    /**
     * Upsert a lead. If phone is non-null and a lead already exists for that phone,
     * update items/addresses/price/modifiedDate. Otherwise insert a new doc.
     * Anonymous leads (null phone) always insert a new record.
     */
    public Lead upsertLead(LeadRequest request) {
        boolean hasPhone = StringUtils.hasText(request.getPhone());

        if (hasPhone) {
            Optional<Lead> existing = leadRepository.findByPhone(request.getPhone());
            if (existing.isPresent()) {
                Lead lead = existing.get();
                lead.setItems(request.getItems());
                lead.setFromAddress(request.getFromAddress());
                lead.setToAddress(request.getToAddress());
                Double reqFeeUpdate = request.getEstimatedDeliveryFee();
                lead.setEstimatedDeliveryFee(reqFeeUpdate != null && reqFeeUpdate > 0
                        ? reqFeeUpdate
                        : request.getEstimatedPrice());
                lead.setCategory(request.getCategory());
                lead.setDistanceKm(request.getDistanceKm());
                lead.setStandardFee(request.getStandardFee());
                lead.setStandardKm(request.getStandardKm());
                lead.setRatePerKm(request.getRatePerKm());
                lead.setModifiedDate(new Date());
                lead.setConsentGiven(request.isConsentGiven());
                lead.setConsentTimestamp(request.getConsentTimestamp());
                if (request.getStoreId() != null) {
                    lead.setStoreId(request.getStoreId());
                }
                return leadRepository.save(lead);
            }
        }

        Lead lead = new Lead();
        lead.setPhone(hasPhone ? request.getPhone() : null);
        lead.setAnonymous(!hasPhone);
        lead.setItems(request.getItems());
        lead.setFromAddress(request.getFromAddress());
        lead.setToAddress(request.getToAddress());
        Double reqFeeNew = request.getEstimatedDeliveryFee();
        lead.setEstimatedDeliveryFee(reqFeeNew != null && reqFeeNew > 0
                ? reqFeeNew
                : request.getEstimatedPrice());
        lead.setCategory(request.getCategory());
        lead.setDistanceKm(request.getDistanceKm());
        lead.setStandardFee(request.getStandardFee());
        lead.setStandardKm(request.getStandardKm());
        lead.setRatePerKm(request.getRatePerKm());
        lead.setStoreType(request.getStoreType());
        lead.setStoreId(request.getStoreId());
        lead.setStatus(LeadStatus.CAPTURED);
        lead.setConsentGiven(request.isConsentGiven());
        lead.setConsentTimestamp(request.getConsentTimestamp());
        return leadRepository.save(lead);
    }

    /**
     * Return all leads for a given storeType, ordered by createdDate desc.
     */
    public List<Lead> getLeads(StoreType storeType) {
        return leadRepository.findByStoreTypeOrderByCreatedDateDesc(storeType);
    }

    /**
     * Update lead status. Allowed transitions:
     * CAPTURED -> CONTACTED
     * CONTACTED -> CLOSED
     * All other manual transitions (including CONVERTED) are rejected.
     * CONVERTED is set only via convertLeadByPhone.
     */
    public Lead updateLeadStatus(String id, LeadStatus newStatus) {
        Lead lead = leadRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Lead with id " + id + " not found"));

        LeadStatus current = lead.getStatus();
        boolean allowed = (current == LeadStatus.CAPTURED && newStatus == LeadStatus.CONTACTED)
                || (current == LeadStatus.CONTACTED && newStatus == LeadStatus.CLOSED);

        if (!allowed) {
            throw new IllegalArgumentException(
                    "Transition from " + current + " to " + newStatus + " is not allowed");
        }

        lead.setStatus(newStatus);
        lead.setModifiedDate(new Date());
        return leadRepository.save(lead);
    }

    /**
     * Marks the lead as CONVERTED when a matching order is paid.
     * Swallows all exceptions — this is a best-effort conversion hook.
     */
    public void convertLeadByPhone(String phone) {
        try {
            if (!StringUtils.hasText(phone)) {
                return;
            }
            Optional<Lead> leadOpt = leadRepository.findByPhone(phone);
            if (leadOpt.isEmpty()) {
                return;
            }
            Lead lead = leadOpt.get();
            if (lead.getStatus() == LeadStatus.CONVERTED) {
                return;
            }
            lead.setStatus(LeadStatus.CONVERTED);
            lead.setModifiedDate(new Date());
            leadRepository.save(lead);
        } catch (Exception e) {
            LOG.warn("convertLeadByPhone failed for phone — non-critical", e);
        }
    }
}
