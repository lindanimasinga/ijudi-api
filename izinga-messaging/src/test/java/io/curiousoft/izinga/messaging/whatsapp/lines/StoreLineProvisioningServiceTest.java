package io.curiousoft.izinga.messaging.whatsapp.lines;

import io.curiousoft.izinga.messaging.aiAgent.config.AiAgentConfig;
import io.curiousoft.izinga.messaging.aiAgent.config.AiAgentConfigRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class StoreLineProvisioningServiceTest {

    @Mock
    private WhatsappLineRepository lineRepository;

    @Mock
    private WhatsappLineAuditRepository auditRepository;

    @Mock
    private AiAgentConfigRepository agentConfigRepository;

    private StoreLineProvisioningService service;

    private static final String PHONE_ID = "11223344";
    private static final String DISPLAY = "+27 81 000 1234";
    private static final String STORE_ID = "store-abc";
    private static final String OPERATOR = "admin-uid-1";

    @BeforeEach
    void setUp() {
        service = new StoreLineProvisioningService(lineRepository, auditRepository, agentConfigRepository);
    }

    // ---- T-05 provisionStoreLine ----

    @Test
    void provisionStoreLine_createsNewLine_whenPhoneIdUnknown() {
        when(lineRepository.findByPhoneNumberId(PHONE_ID)).thenReturn(Optional.empty());
        when(agentConfigRepository.findByAgentName(anyString())).thenReturn(Optional.empty());
        WhatsappLine saved = lineWithId("line-1", PHONE_ID, STORE_ID);
        when(lineRepository.save(any())).thenReturn(saved);
        when(auditRepository.save(any())).thenReturn(null);

        WhatsappLine result = service.provisionStoreLine(PHONE_ID, DISPLAY, STORE_ID, OPERATOR);

        assertNotNull(result);
        verify(lineRepository).save(argThat(l ->
                PHONE_ID.equals(l.getPhoneNumberId()) &&
                STORE_ID.equals(l.getStoreId()) &&
                l.getAudience() == Audience.STORE &&
                l.isActive()
        ));
        verify(auditRepository).save(any(WhatsappLineAuditRecord.class));
    }

    @Test
    void provisionStoreLine_activatesExistingInactiveLine() {
        WhatsappLine existing = lineWithId("line-1", PHONE_ID, STORE_ID);
        existing.setActive(false);
        when(lineRepository.findByPhoneNumberId(PHONE_ID)).thenReturn(Optional.of(existing));
        when(agentConfigRepository.findByAgentName(anyString())).thenReturn(Optional.empty());
        when(lineRepository.save(any())).thenReturn(existing);
        when(auditRepository.save(any())).thenReturn(null);

        WhatsappLine result = service.provisionStoreLine(PHONE_ID, DISPLAY, STORE_ID, OPERATOR);

        assertNotNull(result);
        assertTrue(existing.isActive());
        verify(lineRepository).save(existing);
    }

    @Test
    void provisionStoreLine_clonesDefaultAgentConfig_whenNotPresent() {
        when(lineRepository.findByPhoneNumberId(PHONE_ID)).thenReturn(Optional.empty());
        when(lineRepository.save(any())).thenReturn(lineWithId("line-1", PHONE_ID, STORE_ID));
        when(auditRepository.save(any())).thenReturn(null);

        // No existing store-specific config
        String storeAgentName = "store_support_" + STORE_ID;
        when(agentConfigRepository.findByAgentName(storeAgentName)).thenReturn(Optional.empty());

        // Default template exists
        AiAgentConfig template = AiAgentConfig.builder()
                .agentName("store_support_default")
                .systemPrompt("Default prompt")
                .active(false)
                .useTools(true)
                .build();
        when(agentConfigRepository.findByAgentName("store_support_default")).thenReturn(Optional.of(template));
        when(agentConfigRepository.save(any())).thenReturn(null);

        service.provisionStoreLine(PHONE_ID, DISPLAY, STORE_ID, OPERATOR);

        // Verify a clone was saved
        ArgumentCaptor<AiAgentConfig> captor = ArgumentCaptor.forClass(AiAgentConfig.class);
        verify(agentConfigRepository).save(captor.capture());
        AiAgentConfig clone = captor.getValue();
        assertEquals(storeAgentName, clone.getAgentName());
        assertEquals(STORE_ID, clone.getStoreId());
        assertTrue(clone.getActive());
        assertEquals(Audience.STORE, clone.getAudience());
    }

    @Test
    void provisionStoreLine_skipsClone_whenStoreConfigAlreadyExists() {
        when(lineRepository.findByPhoneNumberId(PHONE_ID)).thenReturn(Optional.empty());
        when(lineRepository.save(any())).thenReturn(lineWithId("line-1", PHONE_ID, STORE_ID));
        when(auditRepository.save(any())).thenReturn(null);

        String storeAgentName = "store_support_" + STORE_ID;
        AiAgentConfig existingConfig = AiAgentConfig.builder().agentName(storeAgentName).active(true).build();
        when(agentConfigRepository.findByAgentName(storeAgentName)).thenReturn(Optional.of(existingConfig));

        service.provisionStoreLine(PHONE_ID, DISPLAY, STORE_ID, OPERATOR);

        verify(agentConfigRepository, never()).save(any());
    }

    @Test
    void provisionStoreLine_throwsIllegalArgument_whenPhoneIdBlank() {
        assertThrows(IllegalArgumentException.class,
                () -> service.provisionStoreLine("", DISPLAY, STORE_ID, OPERATOR));
    }

    @Test
    void provisionStoreLine_throwsIllegalArgument_whenStoreIdBlank() {
        assertThrows(IllegalArgumentException.class,
                () -> service.provisionStoreLine(PHONE_ID, DISPLAY, "", OPERATOR));
    }

    // ---- T-06 deprovisionStoreLine ----

    @Test
    void deprovisionStoreLine_deactivatesLines_returnsCount() {
        WhatsappLine line = lineWithId("line-1", PHONE_ID, STORE_ID);
        line.setActive(true);
        when(lineRepository.findByAudienceAndActiveTrue(Audience.STORE)).thenReturn(List.of(line));
        when(lineRepository.save(any())).thenReturn(line);
        when(auditRepository.save(any())).thenReturn(null);
        // No agent config to deactivate
        when(agentConfigRepository.findByAgentName("store_support_" + STORE_ID)).thenReturn(Optional.empty());

        int count = service.deprovisionStoreLine(STORE_ID, OPERATOR);

        assertEquals(1, count);
        assertFalse(line.isActive());
        verify(lineRepository).save(line);
        verify(auditRepository).save(any());
    }

    @Test
    void deprovisionStoreLine_returns0_whenNoActiveLines() {
        when(lineRepository.findByAudienceAndActiveTrue(Audience.STORE)).thenReturn(List.of());

        int count = service.deprovisionStoreLine(STORE_ID, OPERATOR);

        assertEquals(0, count);
        verify(lineRepository, never()).save(any());
    }

    @Test
    void deprovisionStoreLine_deactivatesAgentConfig_whenPresent() {
        WhatsappLine line = lineWithId("line-1", PHONE_ID, STORE_ID);
        line.setActive(true);
        when(lineRepository.findByAudienceAndActiveTrue(Audience.STORE)).thenReturn(List.of(line));
        when(lineRepository.save(any())).thenReturn(line);
        when(auditRepository.save(any())).thenReturn(null);

        AiAgentConfig config = AiAgentConfig.builder()
                .agentName("store_support_" + STORE_ID).active(true).version(1).build();
        when(agentConfigRepository.findByAgentName("store_support_" + STORE_ID)).thenReturn(Optional.of(config));
        when(agentConfigRepository.save(any())).thenReturn(config);

        service.deprovisionStoreLine(STORE_ID, OPERATOR);

        assertFalse(config.getActive());
        verify(agentConfigRepository).save(config);
    }

    // ---- T-07 getStoreLinesForStore ----

    @Test
    void getStoreLinesForStore_returnsActiveLines() {
        WhatsappLine line = lineWithId("line-1", PHONE_ID, STORE_ID);
        when(lineRepository.findByAudienceAndActiveTrue(Audience.STORE)).thenReturn(List.of(line));

        List<WhatsappLine> result = service.getStoreLinesForStore(STORE_ID);

        assertEquals(1, result.size());
        assertEquals(STORE_ID, result.get(0).getStoreId());
    }

    @Test
    void getStoreLinesForStore_returnsEmpty_whenNoLines() {
        when(lineRepository.findByAudienceAndActiveTrue(Audience.STORE)).thenReturn(List.of());

        List<WhatsappLine> result = service.getStoreLinesForStore(STORE_ID);

        assertTrue(result.isEmpty());
    }

    @Test
    void getStoreLinesForStore_filtersOutOtherStoreLines() {
        WhatsappLine lineForOurStore = lineWithId("line-1", PHONE_ID, STORE_ID);
        WhatsappLine lineForOtherStore = lineWithId("line-2", "99887766", "other-store");
        when(lineRepository.findByAudienceAndActiveTrue(Audience.STORE))
                .thenReturn(List.of(lineForOurStore, lineForOtherStore));

        List<WhatsappLine> result = service.getStoreLinesForStore(STORE_ID);

        assertEquals(1, result.size());
        assertEquals(STORE_ID, result.get(0).getStoreId());
    }

    // ---- helper ----
    private static WhatsappLine lineWithId(String id, String phoneId, String storeId) {
        WhatsappLine line = new WhatsappLine();
        line.setId(id);
        line.setPhoneNumberId(phoneId);
        line.setAudience(Audience.STORE);
        line.setStoreId(storeId);
        line.setActive(true);
        return line;
    }
}
