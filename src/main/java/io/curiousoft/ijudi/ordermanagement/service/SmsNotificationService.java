package io.curiousoft.ijudi.ordermanagement.service;

import org.springframework.stereotype.Service;


public interface SmsNotificationService {


    void sendMessage(String mobileNumber, String message) throws Exception;
}
