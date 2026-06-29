package io.curiousoft.izinga.messaging.whatsapp.webhooks;

import java.net.URL;

public interface MediaStorageService {
    URL upload(String fileName, byte[] bytes);
}
