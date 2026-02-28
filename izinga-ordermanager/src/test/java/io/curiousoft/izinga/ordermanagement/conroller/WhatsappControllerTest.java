package io.curiousoft.izinga.ordermanagement.conroller;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.curiousoft.izinga.messaging.whatsapp.WhatsAppWebhookController;
import io.curiousoft.izinga.messaging.whatsapp.WhatsappWebhookPayload;
import io.curiousoft.izinga.ordermanagement.IjudiApplication;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.test.context.junit4.SpringRunner;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

@Disabled
@RunWith(SpringRunner.class)
@SpringBootTest(classes = IjudiApplication.class,
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
public class WhatsappControllerTest {

    @Autowired
    private WhatsAppWebhookController mockMvc;

    @MockBean
    private ApplicationEventPublisher eventPublisher;

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    void whenTextMessageWebhook_thenPublishesEventAndReturns200() throws Exception {
        var payload = objectMapper.readValue("{\n" +
                "  \"object\": \"whatsapp_business_account\",\n" +
                "  \"entry\": [\n" +
                "    {\n" +
                "      \"id\": \"WHATSAPP_BUSINESS_ACCOUNT_ID\",\n" +
                "      \"changes\": [\n" +
                "        {\n" +
                "          \"value\": {\n" +
                "            \"messaging_product\": \"whatsapp\",\n" +
                "            \"metadata\": {\n" +
                "              \"display_phone_number\": \"BUSINESS_DISPLAY_PHONE_NUMBER\",\n" +
                "              \"phone_number_id\": \"BUSINESS_PHONE_NUMBER_ID\"\n" +
                "            },\n" +
                "            \"contacts\": [\n" +
                "              {\n" +
                "                \"profile\": {\n" +
                "                  \"name\": \"John Doe\"\n" +
                "                },\n" +
                "                \"wa_id\": \"27731234567\"\n" +
                "              }\n" +
                "            ],\n" +
                "            \"messages\": [\n" +
                "              {\n" +
                "                \"from\": \"27731234567\",\n" +
                "                \"id\": \"wamid.HBgLN...\",\n" +
                "                \"timestamp\": \"1615924992\",\n" +
                "                \"type\": \"text\",\n" +
                "                \"text\": {\n" +
                "                  \"body\": \"Hello, I would like to place an order\"\n" +
                "                }\n" +
                "              }\n" +
                "            ]\n" +
                "          },\n" +
                "          \"field\": \"messages\"\n" +
                "        }\n" +
                "      ]\n" +
                "    }\n" +
                "  ]\n" +
                "}", WhatsappWebhookPayload.class);

        mockMvc.receiveWebhook(payload);

        // verify that the controller published an inbound event for other components to consume
        verify(eventPublisher, times(1)).publishEvent(any());
    }
}