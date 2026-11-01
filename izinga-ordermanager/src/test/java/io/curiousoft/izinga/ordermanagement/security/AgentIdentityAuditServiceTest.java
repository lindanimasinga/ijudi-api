package io.curiousoft.izinga.ordermanagement.security;

import io.curiousoft.izinga.commons.security.AgentIdentityAuditLog;
import io.curiousoft.izinga.commons.security.AgentIdentityAuditLogRepository;
import io.curiousoft.izinga.commons.security.AgentIdentityAuditService;
import io.curiousoft.izinga.messaging.whatsapp.lines.Audience;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;

import static org.junit.Assert.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * ADR-024: Tests for AgentIdentityAuditService (canonical class in izinga-commons).
 *
 * Key invariant: a failure in the audit-write MUST NOT suppress the real
 * CallerIdentityViolationException.  This is verified by the failing-repo test.
 *
 * Note: because recordViolation() is @Async, the tests call it directly
 * (without a Spring context) to verify the write and failure-handling logic.
 */
@RunWith(MockitoJUnitRunner.class)
public class AgentIdentityAuditServiceTest {

    @Mock
    private AgentIdentityAuditLogRepository repository;

    @InjectMocks
    private AgentIdentityAuditService service;

    @Before
    public void before() {
        StoreScopeContext.clear();
    }

    @After
    public void after() {
        StoreScopeContext.clear();
    }

    @Test
    public void recordViolation_savesRecordToRepository() {
        // FIX-04: audience is now passed explicitly; context set for documentation only
        StoreScopeContext.setAudience(Audience.CUSTOMER);
        when(repository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        service.recordViolation(
                "+27812345678",
                "+27811111111",
                AgentIdentityAuditLog.ViolationType.PHONE_MISMATCH,
                "find_user_by_phone",
                "CUSTOMER",
                "driver_support",
                null
        );

        ArgumentCaptor<AgentIdentityAuditLog> captor = ArgumentCaptor.forClass(AgentIdentityAuditLog.class);
        verify(repository).save(captor.capture());
        AgentIdentityAuditLog record = captor.getValue();

        assertNotNull("record id must be set", record.getId());
        assertNotNull("timestamp must be set", record.getTimestamp());
        assertEquals(AgentIdentityAuditLog.ViolationType.PHONE_MISMATCH, record.getViolationType());
        assertEquals("find_user_by_phone", record.getToolName());
        assertEquals("driver_support", record.getAgentName());
        assertEquals("CUSTOMER", record.getAudience());
    }

    @Test
    public void recordViolation_repositoryThrows_doesNotPropagate() {
        // FIX-04: audience is now passed explicitly; context set for documentation only
        StoreScopeContext.setAudience(Audience.DRIVER);
        doThrow(new RuntimeException("Mongo is down")).when(repository).save(any());

        // Must not throw — audit failure must be swallowed by the service
        try {
            service.recordViolation(
                    "+27812345678",
                    "+27811111111",
                    AgentIdentityAuditLog.ViolationType.PHONE_MISMATCH,
                    "find_user_by_phone",
                    "DRIVER",
                    "driver_support",
                    null
            );
        } catch (RuntimeException e) {
            fail("recordViolation must swallow write exceptions, not propagate: " + e.getMessage());
        }
    }

    @Test
    public void recordViolation_nullConversationId_recordSavedSuccessfully() {
        // FIX-04: audience is now passed explicitly; context set for documentation only
        StoreScopeContext.setAudience(Audience.CUSTOMER);
        when(repository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        service.recordViolation(
                "+27812345678",
                "some-order-id",
                AgentIdentityAuditLog.ViolationType.ORDER_NOT_OWNED,
                "find_order_by_id",
                "CUSTOMER",
                "driver_support",
                null
        );

        verify(repository).save(any(AgentIdentityAuditLog.class));
    }

    @Test
    public void recordViolation_unknownAudience_audienceFieldIsUnknown() {
        // FIX-04: caller passes "UNKNOWN" explicitly when context is absent (no audience JWT)
        when(repository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        service.recordViolation(
                null,
                "+27811111111",
                AgentIdentityAuditLog.ViolationType.THIRD_PARTY_REGISTRATION,
                "create_user",
                "UNKNOWN",
                "driver_support",
                "conv-123"
        );

        ArgumentCaptor<AgentIdentityAuditLog> captor = ArgumentCaptor.forClass(AgentIdentityAuditLog.class);
        verify(repository).save(captor.capture());
        assertEquals("UNKNOWN", captor.getValue().getAudience());
    }
}
