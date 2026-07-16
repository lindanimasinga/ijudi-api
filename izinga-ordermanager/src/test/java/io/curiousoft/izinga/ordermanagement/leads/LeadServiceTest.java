package io.curiousoft.izinga.ordermanagement.leads;

import io.curiousoft.izinga.commons.model.StoreType;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
public class LeadServiceTest {

    @Mock
    private LeadRepository leadRepository;

    @InjectMocks
    private LeadService leadService;

    @Test
    public void upsertLead_phonePresent_existingLead_updatesFields() {
        LeadRequest request = new LeadRequest();
        request.setPhone("+27821234567");
        request.setFromAddress("Cape Town");
        request.setToAddress("Johannesburg");
        request.setEstimatedPrice(500.0);
        request.setItems(List.of());

        Lead existing = new Lead();
        existing.setId("lead-1");
        existing.setPhone("+27821234567");
        existing.setStatus(LeadStatus.CAPTURED);
        existing.setFromAddress("Old Address");

        when(leadRepository.findByPhone("+27821234567")).thenReturn(Optional.of(existing));
        when(leadRepository.save(any(Lead.class))).thenAnswer(inv -> inv.getArgument(0));

        Lead result = leadService.upsertLead(request);

        assertEquals("Cape Town", result.getFromAddress());
        assertEquals("Johannesburg", result.getToAddress());
        assertEquals(500.0, result.getEstimatedDeliveryFee(), 0.001);
        verify(leadRepository).findByPhone("+27821234567");
        verify(leadRepository).save(existing);
    }

    @Test
    public void upsertLead_phonePresent_noExistingLead_insertsWithCaptured() {
        LeadRequest request = new LeadRequest();
        request.setPhone("+27821234567");
        request.setFromAddress("Cape Town");
        request.setToAddress("JHB");
        request.setStoreType(StoreType.MOVERS);

        when(leadRepository.findByPhone("+27821234567")).thenReturn(Optional.empty());
        when(leadRepository.save(any(Lead.class))).thenAnswer(inv -> inv.getArgument(0));

        Lead result = leadService.upsertLead(request);

        assertEquals(LeadStatus.CAPTURED, result.getStatus());
        assertFalse(result.isAnonymous());
        assertEquals("+27821234567", result.getPhone());
        verify(leadRepository).findByPhone("+27821234567");

        ArgumentCaptor<Lead> captor = ArgumentCaptor.forClass(Lead.class);
        verify(leadRepository).save(captor.capture());
        assertEquals(LeadStatus.CAPTURED, captor.getValue().getStatus());
    }

    @Test
    public void upsertLead_phoneNull_insertsAnonymousWithoutQuery() {
        LeadRequest request = new LeadRequest();
        request.setPhone(null);
        request.setFromAddress("Cape Town");
        request.setStoreType(StoreType.MOVERS);

        when(leadRepository.save(any(Lead.class))).thenAnswer(inv -> inv.getArgument(0));

        Lead result = leadService.upsertLead(request);

        assertTrue(result.isAnonymous());
        assertNull(result.getPhone());
        assertEquals(LeadStatus.CAPTURED, result.getStatus());
        verify(leadRepository, never()).findByPhone(any());
    }

    @Test
    public void upsertLead_phoneBlank_insertsAnonymousWithoutQuery() {
        LeadRequest request = new LeadRequest();
        request.setPhone("  ");

        when(leadRepository.save(any(Lead.class))).thenAnswer(inv -> inv.getArgument(0));

        Lead result = leadService.upsertLead(request);

        assertTrue(result.isAnonymous());
        verify(leadRepository, never()).findByPhone(any());
    }

    @Test
    public void getLeads_returnsListFromRepo() {
        Lead lead = new Lead();
        lead.setStoreType(StoreType.MOVERS);
        when(leadRepository.findByStoreTypeOrderByCreatedDateDesc(StoreType.MOVERS))
                .thenReturn(List.of(lead));

        List<Lead> result = leadService.getLeads(StoreType.MOVERS);

        assertEquals(1, result.size());
        verify(leadRepository).findByStoreTypeOrderByCreatedDateDesc(StoreType.MOVERS);
    }

    @Test
    public void updateLeadStatus_capturedToContacted_allowed() {
        Lead lead = new Lead();
        lead.setId("lead-1");
        lead.setStatus(LeadStatus.CAPTURED);

        when(leadRepository.findById("lead-1")).thenReturn(Optional.of(lead));
        when(leadRepository.save(any(Lead.class))).thenAnswer(inv -> inv.getArgument(0));

        Lead result = leadService.updateLeadStatus("lead-1", LeadStatus.CONTACTED);

        assertEquals(LeadStatus.CONTACTED, result.getStatus());
        verify(leadRepository).save(lead);
    }

    @Test
    public void updateLeadStatus_contactedToClosed_allowed() {
        Lead lead = new Lead();
        lead.setId("lead-1");
        lead.setStatus(LeadStatus.CONTACTED);

        when(leadRepository.findById("lead-1")).thenReturn(Optional.of(lead));
        when(leadRepository.save(any(Lead.class))).thenAnswer(inv -> inv.getArgument(0));

        Lead result = leadService.updateLeadStatus("lead-1", LeadStatus.CLOSED);

        assertEquals(LeadStatus.CLOSED, result.getStatus());
    }

    @Test
    public void updateLeadStatus_contactedToConverted_rejected() {
        Lead lead = new Lead();
        lead.setId("lead-1");
        lead.setStatus(LeadStatus.CONTACTED);

        when(leadRepository.findById("lead-1")).thenReturn(Optional.of(lead));

        assertThrows(IllegalArgumentException.class,
                () -> leadService.updateLeadStatus("lead-1", LeadStatus.CONVERTED));
    }

    @Test
    public void updateLeadStatus_capturedToConverted_rejected() {
        Lead lead = new Lead();
        lead.setId("lead-1");
        lead.setStatus(LeadStatus.CAPTURED);

        when(leadRepository.findById("lead-1")).thenReturn(Optional.of(lead));

        assertThrows(IllegalArgumentException.class,
                () -> leadService.updateLeadStatus("lead-1", LeadStatus.CONVERTED));
    }

    @Test
    public void updateLeadStatus_capturedToClosed_rejected() {
        Lead lead = new Lead();
        lead.setId("lead-1");
        lead.setStatus(LeadStatus.CAPTURED);

        when(leadRepository.findById("lead-1")).thenReturn(Optional.of(lead));

        assertThrows(IllegalArgumentException.class,
                () -> leadService.updateLeadStatus("lead-1", LeadStatus.CLOSED));
    }

    @Test
    public void updateLeadStatus_idNotFound_throws() {
        when(leadRepository.findById("missing")).thenReturn(Optional.empty());

        assertThrows(IllegalArgumentException.class,
                () -> leadService.updateLeadStatus("missing", LeadStatus.CONTACTED));
    }

    @Test
    public void convertLeadByPhone_leadFound_notConverted_setsConverted() {
        Lead lead = new Lead();
        lead.setId("lead-1");
        lead.setStatus(LeadStatus.CONTACTED);

        when(leadRepository.findByPhone("+27821234567")).thenReturn(Optional.of(lead));
        when(leadRepository.save(any(Lead.class))).thenAnswer(inv -> inv.getArgument(0));

        leadService.convertLeadByPhone("+27821234567");

        verify(leadRepository).save(lead);
        assertEquals(LeadStatus.CONVERTED, lead.getStatus());
    }

    @Test
    public void convertLeadByPhone_leadAlreadyConverted_noSave() {
        Lead lead = new Lead();
        lead.setId("lead-1");
        lead.setStatus(LeadStatus.CONVERTED);

        when(leadRepository.findByPhone("+27821234567")).thenReturn(Optional.of(lead));

        leadService.convertLeadByPhone("+27821234567");

        verify(leadRepository, never()).save(any());
    }

    @Test
    public void convertLeadByPhone_leadNotFound_noOp() {
        when(leadRepository.findByPhone("+27829999999")).thenReturn(Optional.empty());

        leadService.convertLeadByPhone("+27829999999");

        verify(leadRepository, never()).save(any());
    }

    @Test
    public void convertLeadByPhone_repoThrows_swallowsException() {
        when(leadRepository.findByPhone("+27821234567")).thenThrow(new RuntimeException("DB error"));

        assertDoesNotThrow(() -> leadService.convertLeadByPhone("+27821234567"));
    }

    @Test
    public void convertLeadByPhone_nullPhone_noQuery() {
        leadService.convertLeadByPhone(null);

        verify(leadRepository, never()).findByPhone(any());
    }

    // --- ADR-018 Step 1: new fields round-trip tests ---

    @Test
    public void upsertLead_newFields_newLead_allMapped() {
        LeadRequest request = new LeadRequest();
        request.setPhone("+27821111111");
        request.setCategory("Bakkie Delivery Driver");
        request.setDistanceKm(12.5);
        request.setStandardFee(85.0);
        request.setStandardKm(5.0);
        request.setRatePerKm(10.0);
        request.setEstimatedDeliveryFee(200.0);

        when(leadRepository.findByPhone("+27821111111")).thenReturn(Optional.empty());
        when(leadRepository.save(any(Lead.class))).thenAnswer(inv -> inv.getArgument(0));

        Lead result = leadService.upsertLead(request);

        assertEquals("Bakkie Delivery Driver", result.getCategory());
        assertEquals(12.5, result.getDistanceKm(), 0.001);
        assertEquals(85.0, result.getStandardFee(), 0.001);
        assertEquals(5.0, result.getStandardKm(), 0.001);
        assertEquals(10.0, result.getRatePerKm(), 0.001);
        assertEquals(200.0, result.getEstimatedDeliveryFee(), 0.001);
    }

    @Test
    public void upsertLead_newFields_existingLead_allMapped() {
        LeadRequest request = new LeadRequest();
        request.setPhone("+27822222222");
        request.setCategory("Bike Delivery Driver");
        request.setDistanceKm(3.0);
        request.setStandardFee(50.0);
        request.setStandardKm(2.0);
        request.setRatePerKm(8.0);
        request.setEstimatedDeliveryFee(75.0);

        Lead existing = new Lead();
        existing.setId("lead-x");
        existing.setPhone("+27822222222");
        existing.setStatus(LeadStatus.CAPTURED);

        when(leadRepository.findByPhone("+27822222222")).thenReturn(Optional.of(existing));
        when(leadRepository.save(any(Lead.class))).thenAnswer(inv -> inv.getArgument(0));

        Lead result = leadService.upsertLead(request);

        assertEquals("Bike Delivery Driver", result.getCategory());
        assertEquals(3.0, result.getDistanceKm(), 0.001);
        assertEquals(50.0, result.getStandardFee(), 0.001);
        assertEquals(2.0, result.getStandardKm(), 0.001);
        assertEquals(8.0, result.getRatePerKm(), 0.001);
        assertEquals(75.0, result.getEstimatedDeliveryFee(), 0.001);
    }

    @Test
    public void upsertLead_estimatedDeliveryFeeZero_fallsBackToEstimatedPrice_newLead() {
        LeadRequest request = new LeadRequest();
        request.setEstimatedPrice(350.0);
        request.setEstimatedDeliveryFee(0.0);  // zero — use legacy field

        when(leadRepository.save(any(Lead.class))).thenAnswer(inv -> inv.getArgument(0));

        Lead result = leadService.upsertLead(request);

        assertEquals(350.0, result.getEstimatedDeliveryFee(), 0.001);
    }

    @Test
    public void upsertLead_estimatedDeliveryFeeZero_fallsBackToEstimatedPrice_existingLead() {
        LeadRequest request = new LeadRequest();
        request.setPhone("+27823333333");
        request.setEstimatedPrice(420.0);
        request.setEstimatedDeliveryFee(0.0);

        Lead existing = new Lead();
        existing.setId("lead-y");
        existing.setPhone("+27823333333");
        existing.setStatus(LeadStatus.CAPTURED);

        when(leadRepository.findByPhone("+27823333333")).thenReturn(Optional.of(existing));
        when(leadRepository.save(any(Lead.class))).thenAnswer(inv -> inv.getArgument(0));

        Lead result = leadService.upsertLead(request);

        assertEquals(420.0, result.getEstimatedDeliveryFee(), 0.001);
    }

    @Test
    public void upsertLead_estimatedDeliveryFeePositive_doesNotFallBack_newLead() {
        LeadRequest request = new LeadRequest();
        request.setEstimatedPrice(100.0);
        request.setEstimatedDeliveryFee(250.0);  // explicit value wins

        when(leadRepository.save(any(Lead.class))).thenAnswer(inv -> inv.getArgument(0));

        Lead result = leadService.upsertLead(request);

        assertEquals(250.0, result.getEstimatedDeliveryFee(), 0.001);
    }

    // --- Boxed-null propagation tests (fix for primitive double boxing bug) ---

    @Test
    public void upsertLead_enrichmentFieldsAbsent_newLead_entityFieldsAreNull() {
        // Simulates a request that does not set any enrichment fields.
        // With boxed Double in LeadRequest, absent fields are null → entity gets null →
        // NON_NULL serialisation omits them (frontend *ngIf != null guards work correctly).
        LeadRequest request = new LeadRequest();
        request.setPhone("+27829000001");
        request.setStoreType(StoreType.MOVERS);
        // distanceKm, estimatedDeliveryFee, ratePerKm, standardFee, standardKm intentionally NOT set

        when(leadRepository.findByPhone("+27829000001")).thenReturn(Optional.empty());
        when(leadRepository.save(any(Lead.class))).thenAnswer(inv -> inv.getArgument(0));

        Lead result = leadService.upsertLead(request);

        assertNull(result.getDistanceKm(), "distanceKm must be null, not 0.0, for legacy leads");
        assertNull(result.getRatePerKm(), "ratePerKm must be null, not 0.0, for legacy leads");
        assertNull(result.getStandardFee(), "standardFee must be null, not 0.0, for legacy leads");
        assertNull(result.getStandardKm(), "standardKm must be null, not 0.0, for legacy leads");
    }

    @Test
    public void upsertLead_enrichmentFieldsAbsent_existingLead_entityFieldsAreNull() {
        // Simulates updating an existing lead without providing enrichment fields.
        LeadRequest request = new LeadRequest();
        request.setPhone("+27829000002");
        // distanceKm etc. intentionally NOT set — all remain null

        Lead existing = new Lead();
        existing.setId("lead-legacy");
        existing.setPhone("+27829000002");
        existing.setStatus(LeadStatus.CAPTURED);

        when(leadRepository.findByPhone("+27829000002")).thenReturn(Optional.of(existing));
        when(leadRepository.save(any(Lead.class))).thenAnswer(inv -> inv.getArgument(0));

        Lead result = leadService.upsertLead(request);

        assertNull(result.getDistanceKm(), "distanceKm must be null, not 0.0, on legacy update path");
        assertNull(result.getRatePerKm(), "ratePerKm must be null, not 0.0, on legacy update path");
        assertNull(result.getStandardFee(), "standardFee must be null, not 0.0, on legacy update path");
        assertNull(result.getStandardKm(), "standardKm must be null, not 0.0, on legacy update path");
    }

    @Test
    public void upsertLead_estimatedDeliveryFeeNull_fallsBackToEstimatedPrice_newLead() {
        // estimatedDeliveryFee absent from request (null) — must fall back to estimatedPrice.
        LeadRequest request = new LeadRequest();
        request.setEstimatedPrice(320.0);
        // estimatedDeliveryFee intentionally NOT set — null

        when(leadRepository.save(any(Lead.class))).thenAnswer(inv -> inv.getArgument(0));

        Lead result = leadService.upsertLead(request);

        // null fee → fallback to estimatedPrice
        assertEquals(320.0, result.getEstimatedDeliveryFee(), 0.001);
    }

    @Test
    public void upsertLead_estimatedDeliveryFeeNull_fallsBackToEstimatedPrice_existingLead() {
        LeadRequest request = new LeadRequest();
        request.setPhone("+27829000003");
        request.setEstimatedPrice(480.0);
        // estimatedDeliveryFee intentionally NOT set — null

        Lead existing = new Lead();
        existing.setId("lead-z");
        existing.setPhone("+27829000003");
        existing.setStatus(LeadStatus.CAPTURED);

        when(leadRepository.findByPhone("+27829000003")).thenReturn(Optional.of(existing));
        when(leadRepository.save(any(Lead.class))).thenAnswer(inv -> inv.getArgument(0));

        Lead result = leadService.upsertLead(request);

        assertEquals(480.0, result.getEstimatedDeliveryFee(), 0.001);
    }
}
