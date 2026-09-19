package io.curiousoft.izinga.messaging.whatsapp.lines;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.Optional;

/**
 * Service for looking up WhatsappLine records.
 * Falls back to the default line (and logs WARN) when the requested phoneNumberId is unknown.
 *
 * REQ-05, REQ-06
 */
@Service
public class WhatsappLineService {

    private static final Logger LOG = LoggerFactory.getLogger(WhatsappLineService.class);

    private final WhatsappLineRepository repository;

    public WhatsappLineService(WhatsappLineRepository repository) {
        this.repository = repository;
    }

    /**
     * Resolve a line by phoneNumberId.
     * If not found, falls back to the default line and logs WARN.
     * Returns empty if no default is configured either.
     */
    public Optional<WhatsappLine> findByPhoneNumberId(String phoneNumberId) {
        Optional<WhatsappLine> line = repository.findByPhoneNumberId(phoneNumberId);
        if (line.isPresent()) {
            return line;
        }
        LOG.warn("Unknown phoneNumberId '{}' — falling back to default line", phoneNumberId);
        return repository.findByIsDefaultTrue();
    }

    public java.util.List<WhatsappLine> findAll() {
        return repository.findAll();
    }

    public Optional<WhatsappLine> findDefault() {
        return repository.findByIsDefaultTrue();
    }
}
