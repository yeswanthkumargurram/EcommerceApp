package com.example.order.service;

import com.example.order.client.ProductClient;
import com.example.order.kafka.OrderPublisher;
import com.example.order.model.Order;
import com.example.order.model.OrderItem;
import com.example.order.model.OrderStatus;
import com.example.order.repository.OrderRepository;
import com.example.order.web.dto.PlaceOrderRequest;
import com.fasterxml.jackson.databind.JsonNode;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

import static org.springframework.http.HttpStatus.FORBIDDEN;
import static org.springframework.http.HttpStatus.NOT_FOUND;

@Service
@RequiredArgsConstructor
public class OrderService {
    private final OrderRepository repo;
    private final OrderPublisher publisher;
    private final ProductClient productClient;

    public Order placeOrder(Long userId, PlaceOrderRequest request) {
        BigDecimal total = BigDecimal.ZERO;
        List<OrderItem> items = new ArrayList<>();
        for (PlaceOrderRequest.OrderItemRequest itemRequest : request.getItems()) {
            ProductClient.ProductView product = productClient.getProduct(itemRequest.getProductId());
            BigDecimal lineTotal = BigDecimal.valueOf(product.getPrice()).multiply(BigDecimal.valueOf(itemRequest.getQuantity()));
            total = total.add(lineTotal);
            items.add(new OrderItem(null, itemRequest.getProductId(), itemRequest.getQuantity()));
        }

        Order order = Order.builder()
                .userId(userId)
                .items(items)
                .totalAmount(total)
                .deliveryAddress(request.getDeliveryAddress())
                .status(OrderStatus.PENDING)
                .build();
        Order saved = repo.save(order);
        publisher.publishOrderCreated(saved);
        return saved;
    }

    public List<Order> getOrdersForUser(Long userId) {
        return repo.findByUserId(userId);
    }

    public Order getOrderForUser(Long orderId, Long userId) {
        Order order = repo.findById(orderId)
                .orElseThrow(() -> new ResponseStatusException(NOT_FOUND, "order not found"));
        if (!order.getUserId().equals(userId)) {
            throw new ResponseStatusException(FORBIDDEN, "not your order");
        }
        return order;
    }

    public void updateStatus(Long orderId, OrderStatus status) {
        repo.findById(orderId).ifPresent(order -> {
            order.setStatus(status);
            repo.save(order);
        });
    }

    /** Async path: Cart Service already snapshotted prices, so no product-service round trip needed here. */
    public Order placeOrderFromCheckoutEvent(JsonNode event) {
        Long userId = event.get("userId").asLong();
        BigDecimal total = new BigDecimal(event.get("totalAmount").asText());
        String deliveryAddress = event.hasNonNull("deliveryAddress") ? event.get("deliveryAddress").asText() : null;

        List<OrderItem> items = new ArrayList<>();
        for (JsonNode item : event.get("items")) {
            items.add(new OrderItem(null, item.get("productId").asLong(), item.get("quantity").asInt()));
        }

        Order order = Order.builder()
                .userId(userId)
                .items(items)
                .totalAmount(total)
                .deliveryAddress(deliveryAddress)
                .status(OrderStatus.PENDING)
                .build();
        Order saved = repo.save(order);
        publisher.publishOrderCreated(saved);
        return saved;
    }
}
