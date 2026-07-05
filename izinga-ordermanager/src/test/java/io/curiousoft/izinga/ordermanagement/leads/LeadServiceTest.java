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
        assertEquals(500.0, result.getEstimatedPrice(), 0.001);
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
}
