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
public class PaymentEventListener {
    private final NotificationService notificationService;
    private final ObjectMapper mapper = new ObjectMapper();

    @KafkaListener(topics = "payment-events", groupId = "notification-group")
    public void onPaymentEvent(String payload) throws Exception {
        log.info("[PaymentEventListener] received payload={}", payload);
        JsonNode root = mapper.readTree(payload);
        String type = root.get("type").asText();
        Long orderId = root.get("orderId").asLong();
        Long userId = root.hasNonNull("userId") ? root.get("userId").asLong() : null;
        if ("PaymentSucceeded".equals(type)) {
            log.info("[PaymentEventListener] payment succeeded for orderId={} userId={}", orderId, userId);
            notificationService.sendPaymentReceipt(userId, orderId, root.get("amount").asText());
        } else if ("PaymentFailed".equals(type)) {
            log.warn("[PaymentEventListener] payment failed for orderId={} userId={} reason={}", orderId, userId, root.path("reason").asText("payment declined"));
            notificationService.sendOrderCancelled(userId, orderId, root.path("reason").asText("payment declined"));
        }
    }
}
