package io.curiousoft.izinga.ordermanagement.service.whatsapp;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.curiousoft.izinga.commons.model.Order;
import io.curiousoft.izinga.commons.model.Profile;
import io.curiousoft.izinga.commons.model.StoreProfile;
import io.curiousoft.izinga.commons.model.UserProfile;
import io.curiousoft.izinga.messaging.whatsapp.WhatsAppService;
import io.curiousoft.izinga.messaging.whatsapp.WhatsappTemplateRequest;
import io.curiousoft.izinga.ordermanagement.service.AdminOnlyNotificationService;
import io.curiousoft.izinga.ordermanagement.shoppinglist.ShoppingList;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import javax.validation.constraints.NotNull;
import java.io.IOException;
import java.math.BigDecimal;
import java.math.RoundingMode;

@Service
public class WhatsappNotificationService implements AdminOnlyNotificationService {

    private static final Logger LOGGER = LoggerFactory.getLogger(WhatsappNotificationService.class);
    private final WhatsAppService whatsAppService;
    private final WhatsappConfig whatsappConfig;
    private final ObjectMapper mapper;

    public WhatsappNotificationService(WhatsAppService whatsAppService,
                                       WhatsappConfig whatsappConfig, ObjectMapper mapper) {
        this.whatsAppService = whatsAppService;
        this.whatsappConfig = whatsappConfig;
        this.mapper = mapper;
    }

    @Override
    public void sendMessage(String mobileNumber, String message) {

    }

    @Override
    public void notifyShopOrderPlaced(@NotNull Order order, StoreProfile store) throws IOException {
        // Request
        WhatsappTemplateRequest request = new WhatsappTemplateRequest();
        request.setTo(store.getMobileNumber().startsWith("0")
                ? store.getMobileNumber().replaceFirst("0", "+27")
                : store.getMobileNumber());
        var requestStr = """
                {
                  "name": "order_management_1",
                  "language": { "code": "en_US" },
                  "components": [
                    {
                      "type": "BODY",
                      "parameters": [
                        { "type": "TEXT", "text": "#name" },
                        { "type": "TEXT", "text": "#orderId" }
                      ]
                    }
                  ]
                }
                """
                .replaceAll("#orderId", order.getId())
                .replaceAll("#name", store.getName());
        var template = mapper.readValue(requestStr, WhatsappTemplateRequest.Template.class);
        request.setTemplate(template);
        whatsAppService.sendMessage(whatsappConfig.phoneId(), request)
                .execute();
    }

    @Override
    public void notifyMessengerOrderPlaced(Order order, UserProfile userProfile, StoreProfile shop) throws IOException {
        // Request
        WhatsappTemplateRequest request = new WhatsappTemplateRequest();
        request.setTo(userProfile.getMobileNumber().startsWith("0")
                ? userProfile.getMobileNumber().replaceFirst("0", "+27")
                : userProfile.getMobileNumber());
        var requestStr = """
                {
                  "name": "order_management_messenger",
                  "language": { "code": "en_US" },
                  "components": [
                    {
                      "type": "BODY",
                      "parameters": [
                        { "type": "TEXT", "text": "#messenger" },
                        { "type": "TEXT", "text": "#shop" },
                        { "type": "TEXT", "text": "#orderId" }
                      ]
                    }
                  ]
                }
                """
                .replaceAll("#orderId", order.getId())
                .replaceAll("#messenger", userProfile.getName())
                .replaceAll("#shop", shop.getName());
        var template = mapper.readValue(requestStr, WhatsappTemplateRequest.Template.class);
        request.setTemplate(template);
        whatsAppService.sendMessage(whatsappConfig.phoneId(), request)
                .execute();
    }

    @Override
    public void sendTipReceivedMessage(String mobileNumber, BigDecimal tip, BigDecimal payoutTotal) throws IOException {
        // Request
        WhatsappTemplateRequest request = new WhatsappTemplateRequest();
        request.setTo(mobileNumber.startsWith("0")
                ? mobileNumber.replaceFirst("0", "+27")
                : mobileNumber);
        var requestStr = """
                {
                  "name": "tip_message",
                  "language": { "code": "en" },
                  "components": [
                    {
                      "type": "BODY",
                      "parameters": [
                        { "type": "TEXT", "text": "#tip" },
                        { "type": "TEXT", "text": "#balance" }
                      ]
                    }
                  ]
                }
                """
                .replaceAll("#tip", tip.setScale(2, RoundingMode.HALF_EVEN).toString())
                .replaceAll("#balance", payoutTotal.setScale(2, RoundingMode.HALF_EVEN).toString());
        var template = mapper.readValue(requestStr, WhatsappTemplateRequest.Template.class);
        request.setTemplate(template);
        whatsAppService.sendMessage(whatsappConfig.phoneId(), request)
                .execute();
    }

    @Override
    public void sendTipReceivedMessageWithReward(String mobileNumber, BigDecimal tip, BigDecimal reward, BigDecimal payoutTotal) throws IOException {
        // Request
        WhatsappTemplateRequest request = new WhatsappTemplateRequest();
        request.setTo(mobileNumber.startsWith("0")
                ? mobileNumber.replaceFirst("0", "+27")
                : mobileNumber);
        var requestStr = """
                {
                   "name": "tip_with_reward_message",
                   "language": { "code": "en" },
                   "components": [
                     {
                       "type": "BODY",
                       "parameters": [
                         { "type": "TEXT", "text": "#tip" },
                         { "type": "TEXT", "text": "#reward" },
                         { "type": "TEXT", "text": "#balance" }
                       ]
                     }
                   ]
                 }
                """
                .replaceAll("#tip", tip.setScale(2, RoundingMode.HALF_EVEN).toString())
                .replaceAll("#reward", payoutTotal.setScale(2, RoundingMode.HALF_EVEN).toString())
                .replaceAll("#balance", payoutTotal.setScale(2, RoundingMode.HALF_EVEN).toString());
        var template = mapper.readValue(requestStr, WhatsappTemplateRequest.Template.class);
        request.setTemplate(template);
        whatsAppService.sendMessage(whatsappConfig.phoneId(), request)
                .execute();
    }

    @Override
    public void notifyOrderPlaced(Order order, Profile userProfile) throws IOException {
        // Request
        WhatsappTemplateRequest request = new WhatsappTemplateRequest();
        request.setTo(userProfile.getMobileNumber().startsWith("0")
                        ? userProfile.getMobileNumber().replaceFirst("0", "+27")
                        : userProfile.getMobileNumber());
        var requestStr = """
                {
                  "name": "order_confirmed",
                  "language": { "code": "en_US" },
                  "components": [
                    {
                      "type": "BODY",
                      "parameters": [
                        { "type": "TEXT", "text": "#name" },
                        { "type": "TEXT", "text": "#orderId" }
                      ]
                    },
                    {
                      "type": "BUTTON",
                      "sub_type": "URL",
                      "index": "0",
                      "parameters": [
                        { "type": "TEXT", "text": "#orderId" }
                      ]
                    }
                  ]
                }
                """
                .replaceAll("#orderId", order.getId())
                .replaceAll("#name", userProfile.getName());
        var template = mapper.readValue(requestStr, WhatsappTemplateRequest.Template.class);
        request.setTemplate(template);
        whatsAppService.sendMessage(whatsappConfig.phoneId(), request)
                .execute();
    }

    public void notifyShoppingListRun(UserProfile customer, StoreProfile shop, ShoppingList shoppingList) throws IOException {
        WhatsappTemplateRequest request = new WhatsappTemplateRequest();
        request.setTo(customer.getMobileNumber().startsWith("0")
                ? customer.getMobileNumber().replaceFirst("0", "+27")
                : customer.getMobileNumber());
        var requestStr = """
                {
                    "messaging_product": "whatsapp",
                    "to": "27831234567",
                    "type": "template",
                    "template": {
                      "name": "scheduled_groceries_purchuse_due",
                      "language": {
                        "code": "en"
                      },
                      "components": [
                        {
                          "type": "BODY",
                          "parameters": [
                            { "type": "TEXT", "text": "#customerName" },
                            { "type": "TEXT", "text": "#shoppingListName" },
                            { "type": "TEXT", "text": "1500.00" }
                          ]
                        },
                        {
                          "type": "BUTTON",
                          "sub_type": "URL",
                          "index": "0",
                          "parameters": [
                            { "type": "TEXT", "text": "#shoppingListId" }
                          ]
                        }
                      ]
                    }
                  }
                """;
        requestStr = requestStr
                .replaceAll("#customerName", customer.getName())
                .replaceAll("#shoppingListName", shoppingList.getName())
                .replaceAll("#amount", shoppingList.getItems().get(0).getPriceRage())
                .replaceAll("#shoppingListId", shoppingList.getId());
        var template = mapper.readValue(requestStr, WhatsappTemplateRequest.Template.class);
        request.setTemplate(template);
        whatsAppService.sendMessage(whatsappConfig.phoneId(), request).execute();
    }
}
