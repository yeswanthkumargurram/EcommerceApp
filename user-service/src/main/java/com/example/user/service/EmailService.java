package com.example.user.service;

/** Abstraction so the reset-link delivery mechanism can be swapped for a real SMTP/SES provider later. */
public interface EmailService {
    void sendPasswordResetEmail(String toEmail, String resetLink);
}
