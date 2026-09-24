package com.example.payment.kafka;

import com.example.payment.gateway.PaymentGateway;
import com.example.payment.model.Payment;
import com.example.payment.model.PaymentAttempt;
import com.example.payment.model.PaymentStatus;
import com.example.payment.repository.PaymentRepository;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;

/**
 * Only reacts once Inventory Service confirms stock was reserved - payment
 * should never be attempted for an order that can't be fulfilled.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class InventoryEventListener {
    private final PaymentRepository repo;
    private final PaymentGateway gateway;
    private final PaymentEventPublisher events;
    private final ObjectMapper mapper = new ObjectMapper();

    @KafkaListener(topics = "inventory-events", groupId = "payment-group")
    public void onInventoryEvent(String payload) throws Exception {
        JsonNode root = mapper.readTree(payload);
        if (!"InventoryReserved".equals(root.path("type").asText())) {
            return;
        }
        Long orderId = root.get("orderId").asLong();
        Long userId = root.hasNonNull("userId") ? root.get("userId").asLong() : null;
        BigDecimal amount = root.hasNonNull("totalAmount") ? new BigDecimal(root.get("totalAmount").asText()) : BigDecimal.ZERO;
        JsonNode items = root.get("items");

        PaymentGateway.GatewayResult result = gateway.authorize(amount);
        PaymentStatus status = result.approved() ? PaymentStatus.SUCCEEDED : PaymentStatus.FAILED;

        Payment payment = Payment.builder()
                .orderId(orderId)
                .userId(userId)
                .amount(amount)
                .status(status)
                .build();
        payment.getAttempts().add(PaymentAttempt.builder().outcome(status).reason(result.reason()).build());
        Payment saved = repo.save(payment);

        if (result.approved()) {
            log.info("Payment succeeded for orderId={}, paymentId={}", orderId, saved.getId());
            events.publishSucceeded(orderId, saved.getId(), userId, amount);
        } else {
            log.warn("Payment failed for orderId={}, reason={}", orderId, result.reason());
            events.publishFailed(orderId, saved.getId(), userId, amount, result.reason(), items);
        }
    }
}
