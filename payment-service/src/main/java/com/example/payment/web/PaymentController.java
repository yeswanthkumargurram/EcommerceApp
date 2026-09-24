package com.example.payment.web;

import com.example.payment.model.Payment;
import com.example.payment.repository.PaymentRepository;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;

/** Read-only: payments are only ever created by the InventoryEventListener saga step. */
@RestController
@RequestMapping("/api/payments")
@RequiredArgsConstructor
public class PaymentController {
    private final PaymentRepository repo;

    @GetMapping
    public List<Payment> myPayments(HttpServletRequest request) {
        return repo.findByUserId(requireUserId(request));
    }

    @GetMapping("/order/{orderId}")
    public Payment getByOrder(@PathVariable Long orderId, HttpServletRequest request) {
        Payment payment = repo.findByOrderId(orderId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "no payment for order " + orderId));
        Long userId = requireUserId(request);
        if (!payment.getUserId().equals(userId)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "not your payment");
        }
        return payment;
    }

    private Long requireUserId(HttpServletRequest request) {
        Object userId = request.getAttribute("userId");
        if (userId == null) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "token missing userId claim");
        }
        return (Long) userId;
    }
}
