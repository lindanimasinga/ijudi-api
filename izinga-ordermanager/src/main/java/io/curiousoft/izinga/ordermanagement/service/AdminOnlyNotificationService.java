package io.curiousoft.izinga.ordermanagement.service;


import io.curiousoft.izinga.commons.model.Order;
import io.curiousoft.izinga.commons.model.StoreProfile;
import io.curiousoft.izinga.commons.model.UserProfile;

public interface AdminOnlyNotificationService {

    void sendMessage(String mobileNumber, String message);

    void notifyOrderPlaced(StoreProfile store, Order persistedOrder, UserProfile userProfile);

    void notifyShopOrderPlaced(StoreProfile store, Order persistedOrder, UserProfile userProfile);
}
