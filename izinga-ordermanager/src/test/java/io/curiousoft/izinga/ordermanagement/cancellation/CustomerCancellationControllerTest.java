package io.curiousoft.izinga.ordermanagement.cancellation;

import io.curiousoft.izinga.commons.model.Order;
import io.curiousoft.izinga.commons.model.OrderStage;
import io.curiousoft.izinga.commons.order.CancellationPreviewResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.util.Date;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class CustomerCancellationControllerTest {

    @Mock
    private CustomerCancellationService cancellationService;

    private CustomerCancellationController sut;

    @BeforeEach
    void setUp() {
        sut = new CustomerCancellationController(cancellationService);
    }

    // =========================================================================
    // GET /order/{id}/cancellation-preview
    // =========================================================================

    @Test
    void previewCancellationFee_success_returns200() {
        CancellationPreviewResponse preview = new CancellationPreviewResponse(
                "ord-1", 0.0, 100.0, "noticePeriodMinutes=30;", "signed-token", new Date());
        when(cancellationService.previewCancellationFee("ord-1")).thenReturn(preview);

        ResponseEntity<?> response = sut.previewCancellationFee("ord-1");

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertSame(preview, response.getBody());
    }

    @Test
    void previewCancellationFee_orderNotFound_returns400() {
        when(cancellationService.previewCancellationFee("missing"))
                .thenThrow(new IllegalArgumentException("Order with id missing not found."));

        ResponseEntity<?> response = sut.previewCancellationFee("missing");

        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
        assertTrue(response.getBody() instanceof Map);
    }

    @Test
    void previewCancellationFee_policyNotSeeded_returns503() {
        when(cancellationService.previewCancellationFee("ord-2"))
                .thenThrow(new IllegalStateException("No cancellation_policy document found."));

        ResponseEntity<?> response = sut.previewCancellationFee("ord-2");

        assertEquals(HttpStatus.SERVICE_UNAVAILABLE, response.getStatusCode());
        assertTrue(response.getBody() instanceof Map);
    }

    @Test
    void previewCancellationFee_alreadyCancelled_returns400() {
        when(cancellationService.previewCancellationFee("ord-3"))
                .thenThrow(new IllegalArgumentException("Order ord-3 is already cancelled."));

        ResponseEntity<?> response = sut.previewCancellationFee("ord-3");

        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
    }

    // =========================================================================
    // POST /order/{id}/customer-cancel
    // =========================================================================

    @Test
    void confirmCustomerCancel_success_returns200() {
        Order cancelled = new Order();
        cancelled.setId("ord-4");
        cancelled.setStage(OrderStage.CANCELLED);
        when(cancellationService.confirmCustomerCancelOrder("ord-4", "valid-token"))
                .thenReturn(cancelled);

        ResponseEntity<?> response = sut.confirmCustomerCancel("ord-4",
                Map.of("feeToken", "valid-token"));

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertSame(cancelled, response.getBody());
    }

    @Test
    void confirmCustomerCancel_missingFeeToken_returns400() {
        when(cancellationService.confirmCustomerCancelOrder("ord-5", null))
                .thenThrow(new IllegalArgumentException("feeToken is required."));

        ResponseEntity<?> response = sut.confirmCustomerCancel("ord-5", Map.of());

        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
    }

    @Test
    void confirmCustomerCancel_nullBody_returns400() {
        when(cancellationService.confirmCustomerCancelOrder("ord-6", null))
                .thenThrow(new IllegalArgumentException("feeToken is required."));

        ResponseEntity<?> response = sut.confirmCustomerCancel("ord-6", null);

        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
    }

    @Test
    void confirmCustomerCancel_expiredToken_returns400() {
        when(cancellationService.confirmCustomerCancelOrder("ord-7", "expired-token"))
                .thenThrow(new IllegalArgumentException("feeToken has expired."));

        ResponseEntity<?> response = sut.confirmCustomerCancel("ord-7",
                Map.of("feeToken", "expired-token"));

        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
        @SuppressWarnings("unchecked")
        Map<String, String> body = (Map<String, String>) response.getBody();
        assertNotNull(body);
        assertTrue(body.containsKey("error"));
    }

    @Test
    void confirmCustomerCancel_tamperedToken_returns400() {
        when(cancellationService.confirmCustomerCancelOrder("ord-8", "bad-hmac-token"))
                .thenThrow(new IllegalArgumentException("Invalid feeToken: signature verification failed."));

        ResponseEntity<?> response = sut.confirmCustomerCancel("ord-8",
                Map.of("feeToken", "bad-hmac-token"));

        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
    }

    @Test
    void confirmCustomerCancel_auditFailure_returns500() {
        when(cancellationService.confirmCustomerCancelOrder("ord-9", "good-token"))
                .thenThrow(new IllegalStateException("Cancellation audit log write failed."));

        ResponseEntity<?> response = sut.confirmCustomerCancel("ord-9",
                Map.of("feeToken", "good-token"));

        assertEquals(HttpStatus.INTERNAL_SERVER_ERROR, response.getStatusCode());
    }

    @Test
    void confirmCustomerCancel_feeDrift_returns400() {
        when(cancellationService.confirmCustomerCancelOrder("ord-10", "drift-token"))
                .thenThrow(new IllegalArgumentException(
                        "Cancellation fee has changed since the token was issued."));

        ResponseEntity<?> response = sut.confirmCustomerCancel("ord-10",
                Map.of("feeToken", "drift-token"));

        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
    }
}
