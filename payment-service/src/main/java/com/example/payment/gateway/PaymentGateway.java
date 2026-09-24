package com.example.payment.gateway;

import java.math.BigDecimal;

/** Swap this for a real Stripe/PayPal/Braintree adapter in production. */
public interface PaymentGateway {
    GatewayResult authorize(BigDecimal amount);

    record GatewayResult(boolean approved, String reason) {}
}
