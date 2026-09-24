package com.example.inventory.kafka;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.Map;

@Component
@RequiredArgsConstructor
@Slf4j
public class InventoryEventPublisher {
    private final KafkaTemplate<String, String> kafka;
    private final ObjectMapper mapper = new ObjectMapper();

    public void publishReserved(Long orderId, JsonNode userId, JsonNode totalAmount, JsonNode items) {
        Map<String, Object> event = new LinkedHashMap<>();
        event.put("type", "InventoryReserved");
        event.put("orderId", orderId);
        event.put("userId", userId);
        event.put("totalAmount", totalAmount);
        event.put("items", items);
        publish(event, "inventory-events");
    }

    public void publishReservationFailed(Long orderId, String reason) {
        Map<String, Object> event = Map.of(
                "type", "InventoryReservationFailed",
                "orderId", orderId,
                "reason", reason
        );
        publish(event, "inventory-events");
    }

    private void publish(Object event, String topic) {
        try {
            String payload = mapper.writeValueAsString(event);
            log.info("[InventoryEventPublisher] publishing topic={} payload={}", topic, payload);
            kafka.send(topic, payload).whenComplete((result, ex) -> {
                if (ex != null) {
                    log.error("[InventoryEventPublisher] failed to publish topic={}", topic, ex);
                } else {
                    if (result != null && result.getRecordMetadata() != null) {
                        log.info("[InventoryEventPublisher] published topic={} partition={} offset={}", topic,
                                result.getRecordMetadata().partition(), result.getRecordMetadata().offset());
                    } else {
                        log.info("[InventoryEventPublisher] published topic={} (no metadata)", topic);
                    }
                }
            });
        } catch (JsonProcessingException e) {
            throw new RuntimeException(e);
        }
    }
}
