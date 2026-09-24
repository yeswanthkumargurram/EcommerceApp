package com.example.order.web;

import com.example.order.model.Order;
import com.example.order.service.OrderService;
import com.example.order.web.dto.PlaceOrderRequest;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/orders")
@RequiredArgsConstructor
public class OrderController {
    private final OrderService orderService;

    @PostMapping
    public ResponseEntity<Order> placeOrder(@Valid @RequestBody PlaceOrderRequest request, HttpServletRequest httpRequest) {
        Long userId = requireUserId(httpRequest);
        return ResponseEntity.ok(orderService.placeOrder(userId, request));
    }

    /** Order history for the caller (PRD: "view their past orders"). */
    @GetMapping
    public List<Order> myOrders(HttpServletRequest httpRequest) {
        return orderService.getOrdersForUser(requireUserId(httpRequest));
    }

    /** Order detail / tracking (PRD: "track their order's delivery status"). Ownership-checked. */
    @GetMapping("/{id}")
    public Order getOrder(@PathVariable Long id, HttpServletRequest httpRequest) {
        return orderService.getOrderForUser(id, requireUserId(httpRequest));
    }

    private Long requireUserId(HttpServletRequest request) {
        Object userId = request.getAttribute("userId");
        if (userId == null) {
            throw new org.springframework.web.server.ResponseStatusException(
                    org.springframework.http.HttpStatus.UNAUTHORIZED, "token missing userId claim");
        }
        return (Long) userId;
    }
}
