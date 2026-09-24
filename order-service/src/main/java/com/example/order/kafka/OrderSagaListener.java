package com.example.order.kafka;

import com.example.order.model.OrderStatus;
import com.example.order.service.OrderService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

/**
 * Reacts to the choreographed saga (see docs/ORDER_SAGA_FLOW.md):
 * - inventory-events: InventoryReservationFailed -> cancel the order early.
 * - payment-events: PaymentSucceeded -> confirm; PaymentFailed -> cancel.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class OrderSagaListener {
    private final OrderService orderService;
    private final ObjectMapper mapper = new ObjectMapper();

    @KafkaListener(topics = "inventory-events", groupId = "order-group")
    public void onInventoryEvent(String payload) throws Exception {
        JsonNode root = mapper.readTree(payload);
        String type = root.get("type").asText();
        Long orderId = root.get("orderId").asLong();
        if ("InventoryReservationFailed".equals(type)) {
            log.warn("Inventory reservation failed for orderId={}, cancelling order", orderId);
            orderService.updateStatus(orderId, OrderStatus.CANCELLED);
        }
    }

    @KafkaListener(topics = "payment-events", groupId = "order-group")
    public void onPaymentEvent(String payload) throws Exception {
        JsonNode root = mapper.readTree(payload);
        String type = root.get("type").asText();
        Long orderId = root.get("orderId").asLong();
        if ("PaymentSucceeded".equals(type)) {
            log.info("Payment succeeded for orderId={}, confirming order", orderId);
            orderService.updateStatus(orderId, OrderStatus.CONFIRMED);
        } else if ("PaymentFailed".equals(type)) {
            log.warn("Payment failed for orderId={}, cancelling order", orderId);
            orderService.updateStatus(orderId, OrderStatus.CANCELLED);
        }
    }
}
