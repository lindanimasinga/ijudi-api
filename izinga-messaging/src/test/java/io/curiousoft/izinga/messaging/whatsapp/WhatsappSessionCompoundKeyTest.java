package io.curiousoft.izinga.messaging.whatsapp;

import io.curiousoft.izinga.commons.model.WhatsappSession;
import io.curiousoft.izinga.messaging.repo.WhatsappSessionRepo;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

/**
 * Unit tests covering compound-key lookup semantics for WhatsappSession.
 *
 * These tests verify that:
 * 1. Two sessions with the same `from` but different `phoneNumberId` are
 *    treated as independent documents (compound-key isolation — REQ-12).
 * 2. A legacy session without `phoneNumberId` is returned via the single-key
 *    lookup so that the inbound handler can adopt and stamp it (REQ-14).
 */
@ExtendWith(MockitoExtension.class)
class WhatsappSessionCompoundKeyTest {

    @Mock
    private WhatsappSessionRepo sessionRepo;

    private static final String FROM = "27821234567";
    private static final String PHONE_ID_A = "phone-line-A";
    private static final String PHONE_ID_B = "phone-line-B";

    /**
     * REQ-12: same `from`, two different phoneNumberIds — repo must return
     * distinct Optional results for each compound key.
     */
    @Test
    void sameFrom_twoPhoneNumberIds_twoDocuments() {
        WhatsappSession sessionA = buildSession(FROM, PHONE_ID_A);
        WhatsappSession sessionB = buildSession(FROM, PHONE_ID_B);

        when(sessionRepo.findByFromAndPhoneNumberId(FROM, PHONE_ID_A)).thenReturn(Optional.of(sessionA));
        when(sessionRepo.findByFromAndPhoneNumberId(FROM, PHONE_ID_B)).thenReturn(Optional.of(sessionB));

        Optional<WhatsappSession> resultA = sessionRepo.findByFromAndPhoneNumberId(FROM, PHONE_ID_A);
        Optional<WhatsappSession> resultB = sessionRepo.findByFromAndPhoneNumberId(FROM, PHONE_ID_B);

        assertThat(resultA).isPresent();
        assertThat(resultB).isPresent();
        assertThat(resultA.get().getPhoneNumberId()).isEqualTo(PHONE_ID_A);
        assertThat(resultB.get().getPhoneNumberId()).isEqualTo(PHONE_ID_B);
        assertThat(resultA.get()).isNotSameAs(resultB.get());

        verify(sessionRepo).findByFromAndPhoneNumberId(FROM, PHONE_ID_A);
        verify(sessionRepo).findByFromAndPhoneNumberId(FROM, PHONE_ID_B);
    }

    /**
     * REQ-14: a legacy session that has no phoneNumberId is returned via the
     * single-key findByFrom lookup so the inbound handler can adopt and stamp it
     * with the current phoneNumberId.
     */
    @Test
    void legacyDoc_noPhoneNumberId_isAdoptedAndStamped() {
        // Legacy session: phoneNumberId is null
        WhatsappSession legacy = buildSession(FROM, null);
        when(sessionRepo.findByFrom(FROM)).thenReturn(Optional.of(legacy));

        Optional<WhatsappSession> found = sessionRepo.findByFrom(FROM);

        assertThat(found).isPresent();
        assertThat(found.get().getPhoneNumberId()).isNull();

        // Simulate the adoption/stamp step: inbound handler sets phoneNumberId then saves
        found.get().setPhoneNumberId(PHONE_ID_A);
        when(sessionRepo.save(found.get())).thenReturn(found.get());
        WhatsappSession stamped = sessionRepo.save(found.get());

        assertThat(stamped.getPhoneNumberId()).isEqualTo(PHONE_ID_A);
        verify(sessionRepo).save(found.get());
    }

    // ── helpers ────────────────────────────────────────────────────────────────

    private WhatsappSession buildSession(String from, String phoneNumberId) {
        WhatsappSession s = new WhatsappSession(from);
        s.setPhoneNumberId(phoneNumberId);
        s.setLastMessageDate(Instant.now());
        return s;
    }
}
