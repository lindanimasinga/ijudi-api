package io.curiousoft.izinga.messaging.whatsapp.lines;

import io.curiousoft.izinga.messaging.whatsapp.WhatsappConfig;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class WhatsappSenderResolverTest {

    @Mock WhatsappLineRepository repository;

    private static final String LEGACY_PHONE_ID = "legacy-123";
    private static final String CUSTOMER_PHONE_ID = "customer-456";
    private static final String DRIVER_PHONE_ID = "driver-789";

    private WhatsappConfig configFlagOff;
    private WhatsappConfig configFlagOn;

    @BeforeEach
    void setUp() {
        configFlagOff = new WhatsappConfig(LEGACY_PHONE_ID, null, null, null, null, false, "secret");
        configFlagOn  = new WhatsappConfig(LEGACY_PHONE_ID, null, null, null, DRIVER_PHONE_ID, true, "secret");
    }

    @Test
    void flagOff_returnsLegacyPhoneId_forCustomerAudience() {
        var resolver = new WhatsappSenderResolver(configFlagOff, repository);
        assertThat(resolver.resolve(Audience.CUSTOMER, null)).isEqualTo(LEGACY_PHONE_ID);
        verifyNoInteractions(repository);
    }

    @Test
    void flagOff_returnsLegacyPhoneId_forDriverAudience() {
        var resolver = new WhatsappSenderResolver(configFlagOff, repository);
        assertThat(resolver.resolve(Audience.DRIVER, null)).isEqualTo(LEGACY_PHONE_ID);
        verifyNoInteractions(repository);
    }

    @Test
    void flagOn_audienceMatch_returnsAudienceLine() {
        var line = makeLine(CUSTOMER_PHONE_ID, Audience.CUSTOMER, null, true, false);
        when(repository.findByAudienceAndActiveTrue(Audience.CUSTOMER)).thenReturn(List.of(line));

        var resolver = new WhatsappSenderResolver(configFlagOn, repository);
        assertThat(resolver.resolve(Audience.CUSTOMER, null)).isEqualTo(CUSTOMER_PHONE_ID);
    }

    @Test
    void flagOn_isDefault_fallback() {
        var defaultLine = makeLine(LEGACY_PHONE_ID, Audience.CUSTOMER, null, true, true);
        when(repository.findByAudienceAndActiveTrue(Audience.CUSTOMER)).thenReturn(List.of());
        when(repository.findByIsDefaultTrue()).thenReturn(Optional.of(defaultLine));

        var resolver = new WhatsappSenderResolver(configFlagOn, repository);
        assertThat(resolver.resolve(Audience.CUSTOMER, null)).isEqualTo(LEGACY_PHONE_ID);
    }

    @Test
    void flagOn_unknownAudience_returnsLegacyFallback() {
        when(repository.findByAudienceAndActiveTrue(Audience.OTP)).thenReturn(List.of());
        when(repository.findByIsDefaultTrue()).thenReturn(Optional.empty());

        var resolver = new WhatsappSenderResolver(configFlagOn, repository);
        // No default configured → falls back to legacy phoneId
        assertThat(resolver.resolve(Audience.OTP, null)).isEqualTo(LEGACY_PHONE_ID);
    }

    @Test
    void flagOn_storeMatch_returnsStoreLine() {
        // Phase 2 path: store-scoped line resolution
        var storeLine = makeLine("store-line-id", Audience.STORE, "store-abc", true, false);
        when(repository.findByAudienceAndStoreIdAndActiveTrue(Audience.STORE, "store-abc"))
                .thenReturn(Optional.of(storeLine));

        var resolver = new WhatsappSenderResolver(configFlagOn, repository);
        assertThat(resolver.resolve(Audience.STORE, "store-abc")).isEqualTo("store-line-id");
    }

    private WhatsappLine makeLine(String phoneNumberId, Audience audience, String storeId,
                                   boolean active, boolean isDefault) {
        var line = new WhatsappLine();
        line.setPhoneNumberId(phoneNumberId);
        line.setAudience(audience);
        line.setStoreId(storeId);
        line.setActive(active);
        line.setDefault(isDefault);
        return line;
    }
}
