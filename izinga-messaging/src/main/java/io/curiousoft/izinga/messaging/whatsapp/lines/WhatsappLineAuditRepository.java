package io.curiousoft.izinga.messaging.whatsapp.lines;

import org.springframework.data.mongodb.repository.MongoRepository;

public interface WhatsappLineAuditRepository extends MongoRepository<WhatsappLineAuditRecord, String> {}
