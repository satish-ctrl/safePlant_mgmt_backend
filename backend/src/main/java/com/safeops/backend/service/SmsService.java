package com.safeops.backend.service;

import com.twilio.Twilio;
import com.twilio.rest.api.v2010.account.Message;
import com.twilio.type.PhoneNumber;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Service
@Slf4j
public class SmsService {

    @Value("${safeops.twilio.account-sid:}")
    private String accountSid;

    @Value("${safeops.twilio.auth-token:}")
    private String authToken;

    @Value("${safeops.twilio.from-number:}")
    private String fromNumber;

    private boolean isInitialized = false;

    @PostConstruct
    public void init() {
        if (accountSid == null || accountSid.isBlank() || accountSid.contains("your_twilio") ||
            authToken == null || authToken.isBlank() || authToken.contains("your_twilio")) {
            log.warn("Twilio SMS service is not configured. Alerts will be logged but not sent via SMS.");
            return;
        }
        try {
            Twilio.init(accountSid, authToken);
            isInitialized = true;
            log.info("Twilio SMS service initialized successfully with From Number: {}", fromNumber);
        } catch (Exception e) {
            log.error("Failed to initialize Twilio SMS service: {}", e.getMessage());
        }
    }

    public void sendSms(String to, String messageBody) {
        if (to == null || to.isBlank()) {
            log.warn("Cannot send SMS: Recipient phone number is empty.");
            return;
        }

        if (!isInitialized) {
            log.info("[SMS Mock Send] To: {}, Body: {}", to, messageBody);
            return;
        }

        try {
            Message message = Message.creator(
                    new PhoneNumber(to),
                    new PhoneNumber(fromNumber),
                    messageBody
            ).create();
            log.info("Twilio SMS sent to {} successfully. SID: {}", to, message.getSid());
        } catch (Exception e) {
            log.error("Failed to send Twilio SMS to {}. Error: {}", to, e.getMessage());
        }
    }
}
