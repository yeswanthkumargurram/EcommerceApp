package com.example.order.kafka;

import com.example.order.service.OrderService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

/** Async alternative to POST /api/orders - triggered by Cart Service's checkout endpoint. */
@Component
@RequiredArgsConstructor
@Slf4j
public class CheckoutRequestedListener {
    private final OrderService orderService;
    private final ObjectMapper mapper = new ObjectMapper();

    @KafkaListener(topics = "checkout-requested", groupId = "order-group")
    public void onCheckoutRequested(String payload) throws Exception {
        log.info("[CheckoutRequestedListener] received payload={}", payload);
        JsonNode event = mapper.readTree(payload);
        Long userId = event.has("userId") ? event.get("userId").asLong() : null;
        int items = event.has("items") && event.get("items").isArray() ? event.get("items").size() : 0;
        log.info("[CheckoutRequestedListener] processing checkout for userId={} items={}", userId, items);
        var order = orderService.placeOrderFromCheckoutEvent(event);
        log.info("[CheckoutRequestedListener] Order {} created from checkout event for userId={}", order.getId(), order.getUserId());
    }
}
