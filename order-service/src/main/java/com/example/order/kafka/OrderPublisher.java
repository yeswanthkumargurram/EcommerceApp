package com.example.order.kafka;

import com.example.order.model.Order;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import java.util.Map;
import java.util.stream.Collectors;

/** Publishes the OrderCreated event that kicks off the Order/Inventory/Payment saga. */
@Component
@RequiredArgsConstructor
@Slf4j
public class OrderPublisher {
    private final KafkaTemplate<String, String> kafka;
    private final ObjectMapper mapper = new ObjectMapper();

    public void publishOrderCreated(Order order) {
        Map<String, Object> event = Map.of(
                "orderId", order.getId(),
                "userId", order.getUserId(),
                "totalAmount", order.getTotalAmount(),
                "items", order.getItems().stream()
                        .map(i -> Map.of("productId", i.getProductId(), "quantity", i.getQuantity()))
                        .collect(Collectors.toList())
        );
        publish("orders", event);
    }

    public void publish(String topic, Object event) {
        try {
            String payload = mapper.writeValueAsString(event);
            log.info("[OrderPublisher] publishing topic={} payload={}", topic, payload);
            kafka.send(topic, payload).whenComplete((result, ex) -> {
                if (ex != null) {
                    log.error("[OrderPublisher] failed to publish topic={}", topic, ex);
                } else {
                    if (result != null && result.getRecordMetadata() != null) {
                        log.info("[OrderPublisher] published topic={} partition={} offset={}", topic,
                                result.getRecordMetadata().partition(), result.getRecordMetadata().offset());
                    } else {
                        log.info("[OrderPublisher] published topic={} (no metadata)", topic);
                    }
                }
            });
        } catch (JsonProcessingException e) {
            throw new RuntimeException(e);
        }
    }
}
