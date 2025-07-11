package io.curiousoft.izinga.ordermanagement.service;


import com.fasterxml.jackson.core.JsonProcessingException;
import io.curiousoft.izinga.commons.model.Order;
import io.curiousoft.izinga.commons.model.Profile;
import io.curiousoft.izinga.commons.model.StoreProfile;
import io.curiousoft.izinga.commons.model.UserProfile;

import java.io.IOException;
import java.math.BigDecimal;

public interface AdminOnlyNotificationService {

    void sendMessage(String mobileNumber, String message);

    void sendTipReceivedMessageWithReward(String mobileNumber, BigDecimal tip, BigDecimal reward, BigDecimal payoutTotal) throws IOException;

    void notifyOrderPlaced(Order persistedOrder, Profile userProfile) throws IOException;

    void notifyShopOrderPlaced(Order persistedOrder, StoreProfile userProfile) throws IOException;

    void notifyMessengerOrderPlaced(Order order, UserProfile userProfile, StoreProfile shop) throws IOException;

    void sendTipReceivedMessage(String mobileNumber, BigDecimal tip, BigDecimal payoutTotal) throws IOException;
}
