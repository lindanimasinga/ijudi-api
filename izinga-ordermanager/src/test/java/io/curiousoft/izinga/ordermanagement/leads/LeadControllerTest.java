package io.curiousoft.izinga.ordermanagement.leads;

import io.curiousoft.izinga.commons.model.ProfileRoles;
import io.curiousoft.izinga.commons.model.StoreType;
import io.curiousoft.izinga.commons.model.UserProfile;
import io.curiousoft.izinga.commons.model.UserProfile.SignUpReason;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class LeadControllerTest {

    @Mock private LeadService leadService;
    @Mock private io.curiousoft.izinga.usermanagement.users.UserProfileService userProfileService;

    private LeadController controller;

    @BeforeEach
    void setUp() {
        controller = new LeadController(leadService, userProfileService);
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken("test-uid", null, List.of()));
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void getLeads_adminUser_returnsLeads() {
        UserProfile admin = new UserProfile("admin", SignUpReason.BUY, "addr", "https://img", "0810000000", ProfileRoles.ADMIN);
        when(userProfileService.find("test-uid")).thenReturn(admin);
        when(leadService.getLeads(StoreType.MOVERS)).thenReturn(List.of());

        ResponseEntity<List<Lead>> response = controller.getLeads(StoreType.MOVERS);

        assertEquals(200, response.getStatusCode().value());
        verify(leadService).getLeads(StoreType.MOVERS);
    }

    @Test
    void getLeads_nonAdminUser_returns403() {
        UserProfile customer = new UserProfile("customer", SignUpReason.BUY, "addr", "https://img", "0820000000", ProfileRoles.CUSTOMER);
        when(userProfileService.find("test-uid")).thenReturn(customer);

        ResponseEntity<List<Lead>> response = controller.getLeads(StoreType.MOVERS);

        assertEquals(403, response.getStatusCode().value());
        verifyNoInteractions(leadService);
    }
}
