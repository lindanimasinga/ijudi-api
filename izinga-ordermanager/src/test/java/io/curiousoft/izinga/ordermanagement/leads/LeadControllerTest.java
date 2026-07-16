package io.curiousoft.izinga.ordermanagement.leads;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.curiousoft.izinga.commons.model.StoreType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class LeadControllerTest {

    @Mock private LeadService leadService;

    private LeadController controller;
    private MockMvc mockMvc;
    private final ObjectMapper objectMapper = new ObjectMapper();

    @BeforeEach
    void setUp() {
        controller = new LeadController(leadService);
        mockMvc = MockMvcBuilders.standaloneSetup(controller).build();
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

    @Test
    void getLeads_v2Path_returnsOk() throws Exception {
        when(leadService.getLeads(StoreType.MOVERS)).thenReturn(List.of());

        mockMvc.perform(get("/v2/leads")
                        .param("storeType", "MOVERS")
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk());

        verify(leadService).getLeads(StoreType.MOVERS);
    }

    @Test
    void getLeads_v1Path_returnsOk() throws Exception {
        when(leadService.getLeads(StoreType.MOVERS)).thenReturn(List.of());

        mockMvc.perform(get("/leads")
                        .param("storeType", "MOVERS")
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk());

        verify(leadService).getLeads(StoreType.MOVERS);
    }
}
