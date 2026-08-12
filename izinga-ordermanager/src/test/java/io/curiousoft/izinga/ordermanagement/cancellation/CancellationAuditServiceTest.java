package io.curiousoft.izinga.ordermanagement.cancellation;

import io.curiousoft.izinga.commons.model.OrderStage;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class CancellationAuditServiceTest {

    @Mock
    private CancellationAuditRepository repository;

    private CancellationAuditService sut;

    @BeforeEach
    void setUp() {
        sut = new CancellationAuditService(repository);
    }

    private CancellationFeeResult feeResult(double feeZAR) {
        Map<String, Object> factors = Map.of("noticePeriodMinutes", 30L, "rawFeeZAR", feeZAR);
        return new CancellationFeeResult(feeZAR, factors, OrderStage.STAGE_1_WAITING_STORE_CONFIRM);
    }

    private CancellationPaymentResult paymentResult() {
        return new CancellationPaymentResult(
                CancellationPaymentResult.Status.PENDING_MANUAL_RECONCILIATION,
                ManualReconciliationPaymentHandler.HANDLER_NAME,
                "notes");
    }

    // -------------------------------------------------------------------------
    // Happy path: document is inserted with correct fields
    // -------------------------------------------------------------------------

    @Test
    void insert_happyPath_savesDocument() {
        CancellationAuditLog saved = new CancellationAuditLog();
        saved.setId("audit-001");
        when(repository.save(any(CancellationAuditLog.class))).thenReturn(saved);

        CancellationAuditLog result = sut.insert(
                "order-1", "customer-1",
                50.0, 150.0,
                feeResult(50.0),
                "raw-token-value",
                "YOCO",
                paymentResult());

        assertEquals("audit-001", result.getId());

        ArgumentCaptor<CancellationAuditLog> captor = ArgumentCaptor.forClass(CancellationAuditLog.class);
        verify(repository, times(1)).save(captor.capture());

        CancellationAuditLog captured = captor.getValue();
        assertEquals("order-1", captured.getOrderId());
        assertEquals("customer-1", captured.getCustomerId());
        assertEquals(50.0, captured.getCalculatedFeeZAR(), 0.0001);
        assertEquals(150.0, captured.getNetRefundDueZAR(), 0.0001);
        assertEquals(OrderStage.STAGE_1_WAITING_STORE_CONFIRM, captured.getAllocationStageAtCalculation());
        assertEquals("YOCO", captured.getPaymentType());
        assertEquals(ManualReconciliationPaymentHandler.HANDLER_NAME, captured.getPaymentHandlerUsed());
        assertEquals(CancellationPaymentResult.Status.PENDING_MANUAL_RECONCILIATION, captured.getPaymentStatus());
        assertNotNull(captured.getConfirmationTimestamp());
        assertNotNull(captured.getFeeTokenHash());
        // Token must be hashed — not stored raw
        assertNotEquals("raw-token-value", captured.getFeeTokenHash());
    }

    // -------------------------------------------------------------------------
    // R0 fee is also persisted correctly
    // -------------------------------------------------------------------------

    @Test
    void insert_zeroFee_persistsCorrectly() {
        CancellationAuditLog saved = new CancellationAuditLog();
        saved.setId("audit-002");
        when(repository.save(any())).thenReturn(saved);

        sut.insert("order-2", "customer-2",
                0.0, 200.0,
                feeResult(0.0),
                "token-abc",
                "CASH",
                paymentResult());

        ArgumentCaptor<CancellationAuditLog> captor = ArgumentCaptor.forClass(CancellationAuditLog.class);
        verify(repository).save(captor.capture());
        assertEquals(0.0, captor.getValue().getCalculatedFeeZAR(), 0.0);
    }

    // -------------------------------------------------------------------------
    // hashToken: same input → same hash; different input → different hash
    // -------------------------------------------------------------------------

    @Test
    void hashToken_deterministicAndNotRaw() {
        String h1 = CancellationAuditService.hashToken("token-abc");
        String h2 = CancellationAuditService.hashToken("token-abc");
        String h3 = CancellationAuditService.hashToken("token-xyz");

        assertEquals(h1, h2);
        assertNotEquals(h1, h3);
        assertNotEquals("token-abc", h1);
        assertFalse(h1.isEmpty());
    }

    // -------------------------------------------------------------------------
    // Repository failure propagates — do not swallow
    // -------------------------------------------------------------------------

    @Test
    void insert_repositoryThrows_propagatesException() {
        when(repository.save(any())).thenThrow(new RuntimeException("Mongo is down"));

        assertThrows(RuntimeException.class, () ->
                sut.insert("order-3", "customer-3",
                        10.0, 90.0,
                        feeResult(10.0),
                        "some-token",
                        "YOCO",
                        paymentResult()));
    }
}
