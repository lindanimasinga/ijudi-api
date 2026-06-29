package io.curiousoft.izinga.messaging.whatsapp.webhooks;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

@Service
public class WhatsappMediaDownloader {

    private static final Logger LOG = LoggerFactory.getLogger(WhatsappMediaDownloader.class);

    private final RestTemplate restTemplate;
    private final ObjectMapper objectMapper;
    private final String whatsappToken;

    public WhatsappMediaDownloader(RestTemplate restTemplate,
                                   ObjectMapper objectMapper,
                                   @Value("${whatsapp.cloud.api.key}") String whatsappToken) {
        this.restTemplate = restTemplate;
        this.objectMapper = objectMapper;
        this.whatsappToken = whatsappToken;
    }

    public DownloadedWhatsappMedia downloadImage(WhatsappWebhookPayload.Value.Message.Image image) {
        if (image == null) {
            throw new IllegalArgumentException("Image payload is required");
        }
        String mediaId = image.getId();
        String mimeType = image.getMimeType() != null ? image.getMimeType() : "image/jpeg";
        String downloadUrl = image.getUrl();

        if ((downloadUrl == null || downloadUrl.isBlank()) && mediaId != null && !mediaId.isBlank()) {
            downloadUrl = fetchMediaUrl(mediaId);
        }
        if (downloadUrl == null || downloadUrl.isBlank()) {
            throw new IllegalArgumentException("No media URL available for WhatsApp image");
        }

        HttpHeaders headers = authHeaders();
        ResponseEntity<byte[]> response = restTemplate.exchange(
                downloadUrl,
                HttpMethod.GET,
                new HttpEntity<>(headers),
                byte[].class
        );
        if (!response.getStatusCode().is2xxSuccessful() || response.getBody() == null || response.getBody().length == 0) {
            throw new IllegalStateException("Failed to download WhatsApp image bytes");
        }

        LOG.info("Downloaded WhatsApp media id={} bytes={}", mediaId, response.getBody().length);
        return new DownloadedWhatsappMedia(mediaId, mimeType, response.getBody());
    }

    private String fetchMediaUrl(String mediaId) {
        String mediaLookupUrl = "https://graph.facebook.com/v23.0/" + mediaId;
        ResponseEntity<String> response = restTemplate.exchange(
                mediaLookupUrl,
                HttpMethod.GET,
                new HttpEntity<>(authHeaders()),
                String.class
        );
        if (!response.getStatusCode().is2xxSuccessful() || response.getBody() == null) {
            throw new IllegalStateException("Unable to resolve WhatsApp media URL for mediaId=" + mediaId);
        }

        try {
            JsonNode node = objectMapper.readTree(response.getBody());
            JsonNode urlNode = node.get("url");
            return urlNode != null ? urlNode.asText(null) : null;
        } catch (Exception e) {
            throw new IllegalStateException("Invalid WhatsApp media metadata response", e);
        }
    }

    private HttpHeaders authHeaders() {
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(whatsappToken);
        headers.setAccept(java.util.List.of(MediaType.APPLICATION_JSON, MediaType.APPLICATION_OCTET_STREAM));
        return headers;
    }

    public record DownloadedWhatsappMedia(String mediaId, String mimeType, byte[] bytes) {
    }
}
