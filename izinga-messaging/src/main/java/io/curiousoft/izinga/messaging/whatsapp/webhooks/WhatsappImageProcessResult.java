package io.curiousoft.izinga.messaging.whatsapp.webhooks;

public record WhatsappImageProcessResult(
        String mediaId,
        String uploadedUrl,
        String tagField,
        String mimeType,
        String fileName
) {
}
