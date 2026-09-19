package io.curiousoft.izinga.messaging.whatsapp.lines;

import org.springframework.data.mongodb.repository.MongoRepository;

import java.util.List;
import java.util.Optional;

/**
 * Spring Data MongoDB repository for WhatsappLine documents.
 * REQ-01 / SA-1
 */
public interface WhatsappLineRepository extends MongoRepository<WhatsappLine, String> {

    Optional<WhatsappLine> findByPhoneNumberId(String phoneNumberId);

    List<WhatsappLine> findByAudienceAndActiveTrue(Audience audience);

    Optional<WhatsappLine> findByAudienceAndStoreIdAndActiveTrue(Audience audience, String storeId);

    Optional<WhatsappLine> findByIsDefaultTrue();
}
