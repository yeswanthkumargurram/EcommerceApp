package com.example.payment.gateway;

import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * Deterministic simulator (no real gateway/sandbox integration in this sample).
 * Declines whenever the amount's fractional cents equal .13, so demos/tests can
 * force a decline reproducibly (e.g. price a product at 19.13) and observe the
 * saga rollback (order CANCELLED, inventory released) described in
 * docs/ORDER_SAGA_FLOW.md.
 */
@Component
public class MockPaymentGateway implements PaymentGateway {
    private static final BigDecimal UNLUCKY_CENTS = new BigDecimal("0.13");

    @Override
    public GatewayResult authorize(BigDecimal amount) {
        if (amount == null || amount.compareTo(BigDecimal.ZERO) <= 0) {
            return new GatewayResult(false, "invalid amount");
        }
        BigDecimal cents = amount.remainder(BigDecimal.ONE).abs().setScale(2, RoundingMode.HALF_UP);
        if (cents.compareTo(UNLUCKY_CENTS) == 0) {
            return new GatewayResult(false, "card declined by issuer");
        }
        return new GatewayResult(true, "approved");
    }
}
