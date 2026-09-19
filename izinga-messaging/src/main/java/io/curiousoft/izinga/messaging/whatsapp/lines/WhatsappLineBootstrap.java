package io.curiousoft.izinga.messaging.whatsapp.lines;

import io.curiousoft.izinga.messaging.whatsapp.WhatsappConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.annotation.Profile;
import org.springframework.context.event.EventListener;
import org.springframework.core.annotation.Order;
import org.springframework.core.Ordered;
import org.springframework.stereotype.Component;

/**
 * Idempotent seed of default WhatsappLine rows on application startup.
 *
 * Creates:
 *  - CUSTOMER default line from whatsapp.cloud.phoneId
 *  - DRIVER line from whatsapp.cloud.driverPhoneId (skipped if blank/null)
 *
 * Uses find-then-save-if-absent per SA-R2.
 * Runs after Spring Data init via @Order(Ordered.LOWEST_PRECEDENCE).
 *
 * REQ-02 / SA-R2
 */
@Component
@Order(Ordered.LOWEST_PRECEDENCE)
public class WhatsappLineBootstrap {

    private static final Logger LOG = LoggerFactory.getLogger(WhatsappLineBootstrap.class);

    private final WhatsappLineRepository repository;
    private final WhatsappConfig config;

    public WhatsappLineBootstrap(WhatsappLineRepository repository, WhatsappConfig config) {
        this.repository = repository;
        this.config = config;
    }

    @EventListener(ApplicationReadyEvent.class)
    public void onApplicationReady() {
        // SEC-01 startup guard: app secret must be present in non-test environments
        if (config.appSecret() == null || config.appSecret().isBlank()) {
            LOG.error("whatsapp.cloud.appSecret is missing — inbound webhook signature verification WILL REJECT all requests. " +
                      "Set this value before going to production.");
        }

        seedCustomerLine();
        seedDriverLine();
    }

    private void seedCustomerLine() {
        String phoneId = config.phoneId();
        if (phoneId == null || phoneId.isBlank()) {
            LOG.warn("whatsapp.cloud.phoneId is blank — skipping CUSTOMER line seed");
            return;
        }
        var existing = repository.findByPhoneNumberId(phoneId);
        if (existing.isPresent()) {
            LOG.debug("CUSTOMER WhatsappLine already exists for phoneNumberId={}", phoneId);
            return;
        }
        var line = new WhatsappLine();
        line.setPhoneNumberId(phoneId);
        line.setDisplayNumber(phoneId);
        line.setAudience(Audience.CUSTOMER);
        line.setAgentName("customer_support");
        line.setActive(true);
        line.setDefault(true);
        repository.save(line);
        LOG.info("Seeded default CUSTOMER WhatsappLine phoneNumberId={}", phoneId);
    }

    private void seedDriverLine() {
        String driverPhoneId = config.driverPhoneId();
        if (driverPhoneId == null || driverPhoneId.isBlank()) {
            LOG.debug("whatsapp.cloud.driverPhoneId is blank — skipping DRIVER line seed");
            return;
        }
        var existing = repository.findByPhoneNumberId(driverPhoneId);
        if (existing.isPresent()) {
            LOG.debug("DRIVER WhatsappLine already exists for phoneNumberId={}", driverPhoneId);
            return;
        }
        var line = new WhatsappLine();
        line.setPhoneNumberId(driverPhoneId);
        line.setDisplayNumber(driverPhoneId);
        line.setAudience(Audience.DRIVER);
        line.setAgentName("driver_support");
        line.setActive(true);
        line.setDefault(false);
        repository.save(line);
        LOG.info("Seeded DRIVER WhatsappLine phoneNumberId={}", driverPhoneId);
    }
}
