package io.curiousoft.izinga.messaging.whatsapp.lines;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.security.oauth2.jwt.Jwt;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class WhatsappLineAdminControllerTest {

    @Mock WhatsappLineRepository lineRepository;
    @Mock WhatsappLineAuditRepository auditRepository;
    @Mock Jwt jwt;

    @InjectMocks
    WhatsappLineAdminController controller;

    @BeforeEach
    void setUp() {
        lenient().when(jwt.getSubject()).thenReturn("admin-uid-123");
    }

    @Test
    void setActive_writesAuditRecord() {
        var line = new WhatsappLine();
        line.setPhoneNumberId("123456789012345");
        line.setActive(false);
        when(lineRepository.findByPhoneNumberId("123456789012345")).thenReturn(Optional.of(line));
        when(lineRepository.save(any())).thenReturn(line);
        when(auditRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        var response = controller.setActive("123456789012345", true, jwt);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        ArgumentCaptor<WhatsappLineAuditRecord> auditCaptor = ArgumentCaptor.forClass(WhatsappLineAuditRecord.class);
        verify(auditRepository).save(auditCaptor.capture());
        assertThat(auditCaptor.getValue().getOperatorUid()).isEqualTo("admin-uid-123");
        assertThat(auditCaptor.getValue().getAction()).isEqualTo("ACTIVATE");
        assertThat(auditCaptor.getValue().getStateBefore()).isFalse();
        assertThat(auditCaptor.getValue().getStateAfter()).isTrue();
    }

    @Test
    void invalidPhoneNumberId_nonNumeric_returns400() {
        var response = controller.setActive("abc123", true, jwt);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        verifyNoInteractions(lineRepository);
    }

    @Test
    void invalidPhoneNumberId_tooLong_returns400() {
        // 16 digits — exceeds max of 15
        var response = controller.setActive("1234567890123456", true, jwt);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        verifyNoInteractions(lineRepository);
    }

    @Test
    void lineNotFound_returns404() {
        when(lineRepository.findByPhoneNumberId("999999999999999")).thenReturn(Optional.empty());
        var response = controller.setActive("999999999999999", true, jwt);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    }
}
