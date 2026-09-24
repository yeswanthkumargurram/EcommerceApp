package com.example.cart.kafka;

import com.example.cart.model.Cart;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.stream.Collectors;

/** Publishes CheckoutRequested so Order Service can create the order asynchronously. */
@Component
@Slf4j
@RequiredArgsConstructor
public class CartEventPublisher {
    private final KafkaTemplate<String, String> kafka;
    private final ObjectMapper mapper = new ObjectMapper();

    public void publishCheckoutRequested(Cart cart, String deliveryAddress) {
        Map<String, Object> event = Map.of(
                "userId", cart.getUserId(),
                "deliveryAddress", deliveryAddress,
                "totalAmount", cart.getTotal(),
                "items", cart.getItems().stream()
                        .map(i -> Map.of("productId", i.getProductId(), "quantity", i.getQuantity()))
                        .collect(Collectors.toList())
        );
        try {
            String payload = mapper.writeValueAsString(event);
            log.info("[CartEventPublisher] publishing topic=checkout-requested userId={} items={} payload={}", cart.getUserId(), cart.getItems().size(), payload);
            // send and add callback to log success/failure with metadata
            kafka.send("checkout-requested", payload).whenComplete((result, ex) -> {
                if (ex != null) {
                    log.error("[CartEventPublisher] failed to publish checkout-requested for userId={}", cart.getUserId(), ex);
                    return;
                }
                if (result != null && result.getRecordMetadata() != null) {
                    var md = result.getRecordMetadata();
                    log.info("[CartEventPublisher] published topic={} partition={} offset={} for userId={}", md.topic(), md.partition(), md.offset(), cart.getUserId());
                } else {
                    log.info("[CartEventPublisher] published (no metadata) for userId={}", cart.getUserId());
                }
            });
        } catch (JsonProcessingException e) {
            log.error("[CartEventPublisher] failed to serialize checkout event for userId={}", cart.getUserId(), e);
            throw new RuntimeException(e);
        }
    }
}
