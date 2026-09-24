package com.example.cart.web;

import com.example.cart.model.Cart;
import com.example.cart.service.CartService;
import com.example.cart.web.dto.AddItemRequest;
import com.example.cart.web.dto.CheckoutRequest;
import com.example.cart.web.dto.UpdateQuantityRequest;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/api/cart")
@RequiredArgsConstructor
public class CartController {
    private final CartService cartService;

    @GetMapping
    public Cart getCart(HttpServletRequest request) {
        return cartService.getCart(requireUserId(request));
    }

    @PostMapping("/items")
    public Cart addItem(@Valid @RequestBody AddItemRequest body, HttpServletRequest request) {
        return cartService.addItem(requireUserId(request), body.getProductId(), body.getQuantity());
    }

    @PutMapping("/items/{productId}")
    public Cart updateQuantity(@PathVariable Long productId, @Valid @RequestBody UpdateQuantityRequest body, HttpServletRequest request) {
        return cartService.updateQuantity(requireUserId(request), productId, body.getQuantity());
    }

    @DeleteMapping("/items/{productId}")
    public Cart removeItem(@PathVariable Long productId, HttpServletRequest request) {
        return cartService.removeItem(requireUserId(request), productId);
    }

    /** Publishes CheckoutRequested; Order Service asynchronously creates the order (see docs/ORDER_SAGA_FLOW.md). */
    @PostMapping("/checkout")
    public ResponseEntity<Void> checkout(@Valid @RequestBody CheckoutRequest body, HttpServletRequest request) {
        cartService.checkout(requireUserId(request), body.getDeliveryAddress());
        return ResponseEntity.accepted().build();
    }

    private Long requireUserId(HttpServletRequest request) {
        Object userId = request.getAttribute("userId");
        if (userId == null) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "token missing userId claim");
        }
        return (Long) userId;
    }
}
