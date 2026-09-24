package com.example.inventory.kafka;

import com.example.inventory.repository.InventoryRepository;
import com.example.inventory.model.Inventory;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;
import lombok.RequiredArgsConstructor;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Consumes OrderCreated and attempts to reserve stock for every line item.
 * Reservation is all-or-nothing: if any item is short, nothing is decremented
 * and an InventoryReservationFailed event is published instead (saga step, see
 * docs/ORDER_SAGA_FLOW.md).
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class OrderListener {
    private final InventoryRepository repo;
    private final InventoryEventPublisher events;
    private final ObjectMapper mapper = new ObjectMapper();

    @KafkaListener(topics = "orders", groupId = "inventory-group")
    public void onOrderCreated(String payload) throws Exception {
        log.info("[OrderListener] received payload={}", payload);
        JsonNode root = mapper.readTree(payload);
        Long orderId = root.get("orderId").asLong();
        JsonNode itemsNode = root.get("items");
        List<Map.Entry<Long, Integer>> items = new ArrayList<>();
        if (itemsNode != null && itemsNode.isArray()) {
            for (JsonNode it : itemsNode) {
                items.add(Map.entry(it.get("productId").asLong(), it.get("quantity").asInt()));
            }
        }

        boolean allAvailable = items.stream().allMatch(item ->
                repo.findByProductId(item.getKey()).map(inv -> inv.getQuantity() >= item.getValue()).orElse(false));

        if (!allAvailable) {
            log.warn("[OrderListener] insufficient stock for orderId={}", orderId);
            events.publishReservationFailed(orderId, "insufficient stock");
            return;
        }

        for (Map.Entry<Long, Integer> item : items) {
            Inventory inv = repo.findByProductId(item.getKey()).orElseThrow();
            inv.setQuantity(inv.getQuantity() - item.getValue());
            repo.save(inv);
        }
        log.info("[OrderListener] reserved stock for orderId={}", orderId);
        events.publishReserved(orderId, root.get("userId"), root.get("totalAmount"), itemsNode);
    }
}
