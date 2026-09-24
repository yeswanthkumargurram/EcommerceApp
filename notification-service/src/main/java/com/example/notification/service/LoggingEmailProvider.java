package com.example.notification.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/** Dev/default implementation: logs the "email" instead of sending a real one. */
@Service
@Slf4j
public class LoggingEmailProvider implements EmailProvider {
    @Override
    public void send(String subject, String body) {
        log.info("Sending email - subject='{}', body='{}'", subject, body);
    }
}
