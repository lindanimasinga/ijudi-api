package io.curiousoft.izinga.messaging.whatsapp.lines;

import io.curiousoft.izinga.messaging.whatsapp.WhatsappConfig;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class WhatsappLineBootstrapTest {

    @Mock WhatsappLineRepository repository;

    @Test
    void idempotent_twiceProducesTwoNotFourDocuments() {
        var config = new WhatsappConfig("phone-001", null, null, null, "driver-002", true, "secret");
        // First call: both lines absent
        when(repository.findByPhoneNumberId("phone-001")).thenReturn(Optional.empty());
        when(repository.findByPhoneNumberId("driver-002")).thenReturn(Optional.empty());
        when(repository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        var bootstrap = new WhatsappLineBootstrap(repository, config);
        bootstrap.onApplicationReady();
        // Second call: both lines already present
        var customerLine = new WhatsappLine(); customerLine.setPhoneNumberId("phone-001");
        var driverLine   = new WhatsappLine(); driverLine.setPhoneNumberId("driver-002");
        when(repository.findByPhoneNumberId("phone-001")).thenReturn(Optional.of(customerLine));
        when(repository.findByPhoneNumberId("driver-002")).thenReturn(Optional.of(driverLine));
        bootstrap.onApplicationReady();

        // save() should have been called exactly twice total (one per line on the first run only)
        verify(repository, times(2)).save(any());
    }

    @Test
    void driverPhoneIdMissing_skipsDriverRow() {
        var config = new WhatsappConfig("phone-001", null, null, null, null, true, "secret");
        when(repository.findByPhoneNumberId("phone-001")).thenReturn(Optional.empty());
        when(repository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        var bootstrap = new WhatsappLineBootstrap(repository, config);
        bootstrap.onApplicationReady();

        ArgumentCaptor<WhatsappLine> captor = ArgumentCaptor.forClass(WhatsappLine.class);
        verify(repository, times(1)).save(captor.capture());
        assertThat(captor.getValue().getAudience()).isEqualTo(Audience.CUSTOMER);
    }
}
