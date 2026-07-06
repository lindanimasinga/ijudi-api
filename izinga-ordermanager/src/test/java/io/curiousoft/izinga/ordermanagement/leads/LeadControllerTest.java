package io.curiousoft.izinga.ordermanagement.leads;

import io.curiousoft.izinga.commons.model.StoreType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.ResponseEntity;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class LeadControllerTest {

    @Mock private LeadService leadService;

    private LeadController controller;

    @BeforeEach
    void setUp() {
        controller = new LeadController(leadService);
    }

    @Test
    void getLeads_returnsLeads() {
        when(leadService.getLeads(StoreType.MOVERS)).thenReturn(List.of());

        ResponseEntity<List<Lead>> response = controller.getLeads(StoreType.MOVERS);

        assertEquals(200, response.getStatusCode().value());
        verify(leadService).getLeads(StoreType.MOVERS);
    }

    @Test
    void upsertLead_returnsCreatedLead() {
        LeadRequest request = new LeadRequest();
        Lead lead = new Lead();
        when(leadService.upsertLead(request)).thenReturn(lead);

        ResponseEntity<Lead> response = controller.upsertLead(request);

        assertEquals(200, response.getStatusCode().value());
        verify(leadService).upsertLead(request);
    }
}
