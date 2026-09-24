package com.example.notification.kafka;

import com.example.notification.service.NotificationService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@Slf4j
public class OrderEventListener {
    private final NotificationService notificationService;
    private final ObjectMapper mapper = new ObjectMapper();

    @KafkaListener(topics = "orders", groupId = "notification-group")
    public void onOrderCreated(String payload) throws Exception {
        log.info("[OrderEventListener] received payload={}", payload);
        JsonNode root = mapper.readTree(payload);
        Long userId = root.hasNonNull("userId") ? root.get("userId").asLong() : null;
        Long orderId = root.get("orderId").asLong();
        log.info("[OrderEventListener] sending order confirmation for orderId={} userId={}", orderId, userId);
        notificationService.sendOrderConfirmation(userId, orderId);
    }
}
