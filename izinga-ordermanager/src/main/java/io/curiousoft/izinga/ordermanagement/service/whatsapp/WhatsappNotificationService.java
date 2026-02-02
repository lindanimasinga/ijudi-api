package io.curiousoft.izinga.ordermanagement.service.whatsapp;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.curiousoft.izinga.commons.model.*;
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
import java.text.SimpleDateFormat;
import java.util.List;

import static java.util.Optional.ofNullable;

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

    public void notifyQuoteAcceptedToCustomer(Order order, Profile customer) throws IOException {
        WhatsappTemplateRequest request = new WhatsappTemplateRequest();
        request.setTo(customer.getMobileNumber().startsWith("0")
                ? customer.getMobileNumber().replaceFirst("0", "+27")
                : customer.getMobileNumber());

        // Use a customer-facing template with TEXT parameters to request payment completion
        var requestStr = """
                {
                  "name": "quote_accepted_customer",
                  "language": { "code": "en_US" },
                  "components": [
                    {
                      "type": "BODY",
                      "parameters": [
                        { "type": "TEXT", "text": "#customerName" },
                        { "type": "TEXT", "text": "#orderId" },
                        { "type": "TEXT", "text": "#amount" }
                      ]
                    },
                    {
                      "type": "BUTTON",
                      "sub_type": "URL",
                      "index": "0",
                      "parameters": [
                        { "type": "TEXT", "text": "#paymentLink" }
                      ]
                    }
                  ]
                }
                """
                .replaceAll("#customerName", customer.getName() != null ? customer.getName() : "Customer")
                .replaceAll("#orderId", order.getId())
                .replaceAll("#amount", String.format("%.2f", order.getTotalAmount()))
                .replaceAll("#paymentLink", "https://pay.izinga.co.za/order/" + order.getId());

        var template = mapper.readValue(requestStr, WhatsappTemplateRequest.Template.class);
        request.setTemplate(template);
        whatsAppService.sendMessage(whatsappConfig.phoneId(), request).execute();
    }

    public void notifyShoppingListRun(UserProfile customer, StoreProfile shop, ShoppingList shoppingList) throws IOException {
        WhatsappTemplateRequest request = new WhatsappTemplateRequest();
        request.setTo(customer.getMobileNumber().startsWith("0")
                ? customer.getMobileNumber().replaceFirst("0", "+27")
                : customer.getMobileNumber());
        var requestStr = """
                {
                  "name": "scheduled_groceries_purchuse_due3",
                  "language": {
                    "code": "en_US"
                  },
                  "components": [
                    {
                            "type": "HEADER",
                            "parameters": [
                              {
                                "type": "IMAGE",
                                "image": {
                                  "link": "#headerImage"
                                }
                              }
                            ]
                          },
                    {
                      "type": "BODY",
                      "parameters": [
                        { "type": "TEXT", "text": "#customerName" },
                        { "type": "TEXT", "text": "#shoppingListName" },
                        { "type": "TEXT", "text": "#amount" }
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
                """;
        requestStr = requestStr
                .replaceAll("#customerName", customer.getName() != null ? customer.getName() : "Customer")
                .replaceAll("#shoppingListName", shoppingList.getName())
                .replaceAll("#amount", ""+shoppingList.getTotalAmount().setScale(2, RoundingMode.HALF_EVEN))
                .replaceAll("#shoppingListId", shoppingList.getId())
                .replaceAll("#headerImage", shop.getImageUrl());
        var template = mapper.readValue(requestStr, WhatsappTemplateRequest.Template.class);
        request.setTemplate(template);
        whatsAppService.sendMessage(whatsappConfig.phoneId(), request).execute();
    }

    // java
    public void notifyMessengerQuoteAvailable(Order order, StoreProfile store, UserProfile messenger) {
        WhatsappTemplateRequest request = new WhatsappTemplateRequest();
        // normalize phone safely
        String to = (messenger != null && messenger.getMobileNumber() != null && messenger.getMobileNumber().startsWith("0"))
                ? messenger.getMobileNumber().replaceFirst("0", "+27")
                : (messenger != null ? messenger.getMobileNumber() : null);
        request.setTo(to);

        // scheduled date (fix year token and use 24h hour)
        String scheduledDate = new SimpleDateFormat("yyyy-MM-dd 'at' HH:mm")
                .format(ofNullable(order.getShippingData())
                        .map(ShippingData::getPickUpTime)
                        .orElse(order.getCreatedDate())
                );

        // Build Template programmatically to ensure types match (HEADER expects LOCATION)
        WhatsappTemplateRequest.Template template = new WhatsappTemplateRequest.Template();
        template.setName("quote_request_for_acceptance2");
        var lang = new WhatsappTemplateRequest.Language();
        lang.setCode("en"); // matches template language
        template.setLanguage(lang);

        // HEADER component with LOCATION parameter
        WhatsappTemplateRequest.Component header = new WhatsappTemplateRequest.Component();
        header.setType(WhatsappTemplateRequest.ComponentType.HEADER);
        var headerParam = new WhatsappTemplateRequest.Parameter();
        headerParam.setType(WhatsappTemplateRequest.ParameterType.LOCATION);
        var loc = new WhatsappTemplateRequest.Location();
        var shiipingGeoPoints = order.getShippingData().getShippingDataGeoData().getFromGeoPoint();
        loc.setLatitude(shiipingGeoPoints.getLatitude());
        loc.setLongitude(shiipingGeoPoints.getLongitude());
        loc.setName("Delivery Location");
        loc.setAddress(order.getShippingData().getToAddress());
        headerParam.setLocation(loc);
        header.setParameters(List.of(headerParam));

        // BODY component with positional TEXT parameters: messenger name, quoteId, scheduledDate
        WhatsappTemplateRequest.Component body = new WhatsappTemplateRequest.Component();
        body.setType(WhatsappTemplateRequest.ComponentType.BODY);
        var p1 = new WhatsappTemplateRequest.Parameter(); p1.setType(WhatsappTemplateRequest.ParameterType.TEXT);
        p1.setText(messenger != null && messenger.getName() != null ? messenger.getName() : "Messenger");
        var p2 = new WhatsappTemplateRequest.Parameter(); p2.setType(WhatsappTemplateRequest.ParameterType.TEXT);
        p2.setText(order.getId() != null ? order.getId() : "");
        var p3 = new WhatsappTemplateRequest.Parameter(); p3.setType(WhatsappTemplateRequest.ParameterType.TEXT);
        p3.setText(scheduledDate);
        body.setParameters(List.of(p1, p2, p3));

        // BUTTON component (URL) — give the parameter expected by the template's URL (example uses shop id)
        WhatsappTemplateRequest.Component button = new WhatsappTemplateRequest.Component();
        button.setType(WhatsappTemplateRequest.ComponentType.BUTTON);
        button.setSub_type(WhatsappTemplateRequest.ButtonSubType.URL);
        button.setIndex(0);
        var bp = new WhatsappTemplateRequest.Parameter();
        bp.setType(WhatsappTemplateRequest.ParameterType.TEXT);
        bp.setText(order.getId());
        button.setParameters(List.of(bp));

        template.setComponents(List.of(header, body, button));
        request.setTemplate(template);

        try {
            whatsAppService.sendMessage(whatsappConfig.phoneId(), request).execute();
        } catch (IOException e) {
            LOGGER.error("Failed to send messenger quote available notification for order {} to {}",
                    order.getId(), (messenger != null ? messenger.getMobileNumber() : null), e);
        }
     }

 }
