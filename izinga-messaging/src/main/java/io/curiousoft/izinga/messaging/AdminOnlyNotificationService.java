package io.curiousoft.izinga.messaging;

import io.curiousoft.izinga.commons.model.Order;
import io.curiousoft.izinga.commons.model.Profile;
import io.curiousoft.izinga.commons.model.StoreProfile;
import io.curiousoft.izinga.commons.model.UserProfile;
import org.jetbrains.annotations.Nullable;

import javax.validation.constraints.NotBlank;
import java.io.IOException;
import java.math.BigDecimal;

public interface AdminOnlyNotificationService {

    void sendMessage(String mobileNumber, String message);

    // send welcome message to a newly registered driver (optional)
    void sendWelcomeMessageDriver(String mobileNumber, String driverName);

    void sendTipReceivedMessageWithReward(String mobileNumber, BigDecimal tip, BigDecimal reward, BigDecimal payoutTotal) throws IOException;

    void notifyOrderPlaced(Order persistedOrder, Profile userProfile) throws IOException;

    void notifyShopOrderPlaced(Order persistedOrder, StoreProfile userProfile) throws IOException;

    void notifyMessengerOrderPlaced(Order order, UserProfile userProfile, StoreProfile shop) throws IOException;

    void sendTipReceivedMessage(String mobileNumber, BigDecimal tip, BigDecimal payoutTotal) throws IOException;

    // Send the landing options template to a user (positional parameter: name)
    void sendLandingOptions(String mobileNumber, String name, io.curiousoft.izinga.commons.model.UserProfile userProfile);

    void sendCrimnalCheckConsent(@NotBlank(message = "profile mobile not format is not valid. Please put like +27812815577 or 27812815577") @Nullable String mobileNumber, @NotBlank(message = "profile name not valid") @Nullable String name);
}
