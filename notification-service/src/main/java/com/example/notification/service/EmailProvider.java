package com.example.notification.service;

/** Swap this for a real Amazon SES/SendGrid adapter in production (PRD: "Integrates with Amazon SES"). */
public interface EmailProvider {
    void send(String subject, String body);
}
