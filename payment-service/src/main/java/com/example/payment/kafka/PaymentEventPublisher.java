package com.example.payment.kafka;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.Map;

@Component
@RequiredArgsConstructor
@Slf4j
public class PaymentEventPublisher {
    private final KafkaTemplate<String, String> kafka;
    private final ObjectMapper mapper = new ObjectMapper();

    public void publishSucceeded(Long orderId, Long paymentId, Long userId, BigDecimal amount) {
        Map<String, Object> event = new LinkedHashMap<>();
        event.put("type", "PaymentSucceeded");
        event.put("orderId", orderId);
        event.put("paymentId", paymentId);
        event.put("userId", userId);
        event.put("amount", amount);
        publish(event, "payment-events");
    }

    /** Includes the original items so Inventory Service can release the reservation (compensating step). */
    public void publishFailed(Long orderId, Long paymentId, Long userId, BigDecimal amount, String reason, JsonNode items) {
        Map<String, Object> event = new LinkedHashMap<>();
        event.put("type", "PaymentFailed");
        event.put("orderId", orderId);
        event.put("paymentId", paymentId);
        event.put("userId", userId);
        event.put("amount", amount);
        event.put("reason", reason);
        event.put("items", items);
        publish(event, "payment-events");
    }

    private void publish(Object event, String topic) {
        try {
            String payload = mapper.writeValueAsString(event);
            log.info("[PaymentEventPublisher] publishing topic={} payload={}", topic, payload);
            kafka.send(topic, payload).whenComplete((result, ex) -> {
                if (ex != null) {
                    log.error("[PaymentEventPublisher] failed to publish topic={}", topic, ex);
                } else {
                    if (result != null && result.getRecordMetadata() != null) {
                        log.info("[PaymentEventPublisher] published topic={} partition={} offset={}", topic,
                                result.getRecordMetadata().partition(), result.getRecordMetadata().offset());
                    } else {
                        log.info("[PaymentEventPublisher] published topic={} (no metadata)", topic);
                    }
                }
            });
        } catch (JsonProcessingException e) {
            throw new RuntimeException(e);
        }
    }
}
