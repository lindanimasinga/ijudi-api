package io.curiousoft.ijudi.ordermanagement.service;


import io.curiousoft.ijudi.ordermanagement.model.Order;
import io.curiousoft.ijudi.ordermanagement.model.StoreProfile;
import io.curiousoft.ijudi.ordermanagement.model.UserProfile;

public interface AdminOnlyNotificationService {

    void sendMessage(String mobileNumber, String message) throws Exception;

    void notifyOrderPlaced(StoreProfile store, Order persistedOrder, UserProfile userProfile) throws Exception;
}
