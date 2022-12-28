package io.curiousoft.izinga.ordermanagement.service.zoomsms;

import io.curiousoft.izinga.ordermanagement.service.AdminOnlyNotificationService;
import org.junit.Ignore;
import org.junit.Test;

public class ZoomSmsNotificationServiceTest {

    @Ignore
    @Test
    public void sendMessage() throws Exception {
        AdminOnlyNotificationService zoomSmsNotificationService = new ZoomSmsNotificationService("https://www.zoomconnect.com/app/api/rest/v1/sms/send",
                "lindanimasinga@gmail.com", "c0217d42-dd53-4d6b-811c-a79b584e5177");
        zoomSmsNotificationService.sendMessage("0812815707", "iZinga test message");
    }
}