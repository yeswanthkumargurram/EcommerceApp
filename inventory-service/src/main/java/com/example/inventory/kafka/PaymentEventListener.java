package com.example.inventory.kafka;

import com.example.inventory.repository.InventoryRepository;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

/**
 * Compensating transaction: if payment fails after stock was already reserved,
 * release the reserved quantity back to inventory (saga rollback step).
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class PaymentEventListener {
    private final InventoryRepository repo;
    private final ObjectMapper mapper = new ObjectMapper();

    @KafkaListener(topics = "payment-events", groupId = "inventory-group")
    public void onPaymentEvent(String payload) throws Exception {
        JsonNode root = mapper.readTree(payload);
        if (!"PaymentFailed".equals(root.path("type").asText())) {
            return;
        }
        Long orderId = root.get("orderId").asLong();
        JsonNode items = root.get("items");
        if (items == null || !items.isArray()) {
            return;
        }
        for (JsonNode item : items) {
            Long productId = item.get("productId").asLong();
            int quantity = item.get("quantity").asInt();
            repo.findByProductId(productId).ifPresent(inv -> {
                inv.setQuantity(inv.getQuantity() + quantity);
                repo.save(inv);
            });
        }
        log.info("Released reserved stock for orderId={} after payment failure", orderId);
    }
}
