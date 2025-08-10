package com.cosmetology.service;

import com.twilio.Twilio;
import com.twilio.rest.api.v2010.account.Message;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Service
public class TwilioService {

    @Value("${twilio.account.sid}")
    private String accountSid;

    @Value("${twilio.auth.token}")
    private String authToken;

    @Value("${twilio.phone.number}")
    private String fromNumber;

    public void sendOtp(String to, String otp) {
        if (accountSid == null || accountSid.isBlank() || authToken == null || authToken.isBlank()) {
            System.out.println("Twilio not configured; OTP: " + otp + " to " + to);
            return;
        }
        Twilio.init(accountSid, authToken);
        String toNumber = to.startsWith("+") ? to : ("+91" + to); // adjust as needed
        Message.creator(
                new com.twilio.type.PhoneNumber(toNumber),
                new com.twilio.type.PhoneNumber(fromNumber),
                "Your Cosmetology OTP is: " + otp
        ).create();
    }
}
