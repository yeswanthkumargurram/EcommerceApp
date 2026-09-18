package com.example.user.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/** Dev/default implementation: logs the reset link instead of sending a real email. */
@Service
@Slf4j
public class LoggingEmailService implements EmailService {
    @Override
    public void sendPasswordResetEmail(String toEmail, String resetLink) {
        log.info("Password reset requested for email={}. Reset link: {}", toEmail, resetLink);
    }
}
