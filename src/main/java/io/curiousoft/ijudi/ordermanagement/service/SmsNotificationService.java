package io.curiousoft.ijudi.ordermanagement.service;


public interface SmsNotificationService {


    void sendMessage(String mobileNumber, String message) throws Exception;
}
