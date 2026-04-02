package io.curiousoft.izinga.messaging.whatsapp.templates;

import lombok.Data;

import java.util.Map;

@Data
public class WhatsappTextResponse {
    private Map<String, Object> result;
}

