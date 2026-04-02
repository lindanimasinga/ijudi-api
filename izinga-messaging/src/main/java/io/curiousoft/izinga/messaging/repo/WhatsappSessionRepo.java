package io.curiousoft.izinga.messaging.repo;

import io.curiousoft.izinga.commons.model.WhatsappSession;
import org.springframework.data.mongodb.repository.MongoRepository;
import java.util.Optional;

public interface WhatsappSessionRepo extends MongoRepository<WhatsappSession, String> {
    Optional<WhatsappSession> findByFrom(String from);
}

