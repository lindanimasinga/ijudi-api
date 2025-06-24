package io.curiousoft.izinga.ordermanagement.service;


import io.curiousoft.izinga.commons.model.Order;
import io.curiousoft.izinga.commons.model.Profile;
import io.curiousoft.izinga.commons.model.StoreProfile;
import io.curiousoft.izinga.commons.model.UserProfile;

import java.io.IOException;

public interface AdminOnlyNotificationService {

    void sendMessage(String mobileNumber, String message);

    void notifyOrderPlaced(Order persistedOrder, Profile userProfile) throws IOException;

    void notifyShopOrderPlaced(Order persistedOrder, StoreProfile userProfile) throws IOException;
}
