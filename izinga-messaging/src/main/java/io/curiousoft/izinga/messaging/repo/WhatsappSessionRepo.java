package io.curiousoft.izinga.messaging.repo;

import io.curiousoft.izinga.commons.model.WhatsappSession;
import org.springframework.data.mongodb.repository.MongoRepository;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

public interface WhatsappSessionRepo extends MongoRepository<WhatsappSession, String> {
    Optional<WhatsappSession> findByFrom(String from);

    /** SEC-02: compound key lookup — session scoped to (from, phoneNumberId). REQ-14 / SEC-02 */
    Optional<WhatsappSession> findByFromAndPhoneNumberId(String from, String phoneNumberId);

    List<WhatsappSession> findByLastMessageDateBetween(LocalDate start, LocalDate end);
}

