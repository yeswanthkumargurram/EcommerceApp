package com.example.payment.model;

/** Mirrors PaymentSucceeded/Failed events published to Kafka. */
public enum PaymentStatus {
    PENDING,
    SUCCEEDED,
    FAILED
}
