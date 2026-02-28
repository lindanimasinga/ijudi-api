package io.curiousoft.izinga.messaging.whatsapp;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.event.EventListener;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service
public class WhatsappTemplateReplyEventHandler {

    private static final Logger LOG = LoggerFactory.getLogger(WhatsappTemplateReplyEventHandler.class);

    private final RestTemplate restTemplate;
    private final String orderManagerBaseUrl;

    public WhatsappTemplateReplyEventHandler(RestTemplate restTemplate,
                                             @Value("${ordermanager.base-url:http://localhost:8080}") String orderManagerBaseUrl) {
        this.restTemplate = restTemplate;
        this.orderManagerBaseUrl = orderManagerBaseUrl;
    }

    @EventListener
    public void handleTemplateReply(WhatsappTemplateReplyEvent event) {
        try {
            String from = event.getFrom();
            String id = event.getId();
            String title = event.getTitle();
            var message = event.getRawMessage();

            LOG.info("Handling template reply from {} id={} title={}", from, id, title);

            // try to find an order id in title, id or text content
            String orderId = findOrderId(id, title, message);
            if (orderId == null) {
                LOG.warn("Unable to find orderId in template reply (from={}, id={}, title={})", from, id, title);
                return;
            }

            // Build QouteApproval JSON
            String quoteApproval = String.format("{\"approved\":true,\"reason\":null,\"orderId\":\"%s\",\"messengerId\":\"%s\"}", orderId, from);

            String url = String.format("%s/order/%s/quote", orderManagerBaseUrl, orderId);
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            HttpEntity<String> request = new HttpEntity<>(quoteApproval, headers);

            LOG.info("Calling order manager to accept quote: {}", url);
            ResponseEntity<String> resp = restTemplate.exchange(url, HttpMethod.PATCH, request, String.class);
            LOG.info("Order manager responded: {}", resp);
        } catch (Exception e) {
            LOG.error("Error handling WhatsappTemplateReplyEvent", e);
        }
    }

    private String findOrderId(String id, String title, WhatsappWebhookPayload.Value.Message message) {
        // check id and title first
        String candidate = pickFirstNonNull(id, title);
        String found = findToken(candidate);
        if (found != null) return found;

        // check text body
        if (message != null && message.getText() != null && message.getText().getBody() != null) {
            found = findToken(message.getText().getBody());
            if (found != null) return found;
        }

        // check interactive fields if present
        if (message != null && message.getInteractive() != null) {
            var interactive = message.getInteractive();
            if (interactive.getButtonReply() != null) {
                var br = interactive.getButtonReply();
                String payload = pickFirstNonNull(br.getId(), br.getTitle());
                found = findToken(payload);
                if (found != null) return found;
            }
            if (interactive.getListReply() != null) {
                var lr = interactive.getListReply();
                String payload = pickFirstNonNull(lr.getId(), lr.getTitle());
                found = findToken(payload);
                if (found != null) return found;
            }
        }

        // check context or other fields
        if (message != null) {
            String dumped = message.toString();
            found = findToken(dumped);
            if (found != null) return found;
        }

        return null;
    }

    private String pickFirstNonNull(String... s) {
        for (String t : s) if (t != null && !t.isEmpty()) return t;
        return null;
    }

    private String findToken(String text) {
        if (text == null) return null;
        // look for typical order id patterns: sequences of alphanumeric chars length >=4
        Pattern p = Pattern.compile("([A-Za-z0-9\\-]{4,})");
        Matcher m = p.matcher(text);
        while (m.find()) {
            String tok = m.group(1);
            // optionally filter out purely numeric short tokens; accept tokens with letters or length >=6
            if (tok.length() >= 6 || tok.matches(".*[A-Za-z].*")) return tok;
            if (tok.matches("\\d{4,}")) return tok; // accept digit sequences of length>=4
        }
        return null;
    }
}
