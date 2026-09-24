package com.example.notification.service;

import com.example.notification.model.Notification;
import com.example.notification.repository.NotificationRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class NotificationService {
    private final NotificationRepository repo;
    private final EmailProvider emailProvider;

    public void sendOrderConfirmation(Long userId, Long orderId) {
        record("ORDER_PLACED", userId, orderId, "Your order #" + orderId + " has been placed.");
    }

    public void sendPaymentReceipt(Long userId, Long orderId, String amount) {
        record("PAYMENT_RECEIPT", userId, orderId, "Payment of " + amount + " received for order #" + orderId + ". Your order is confirmed.");
    }

    public void sendOrderCancelled(Long userId, Long orderId, String reason) {
        record("ORDER_CANCELLED", userId, orderId, "Order #" + orderId + " was cancelled: " + reason);
    }

    private void record(String type, Long userId, Long orderId, String message) {
        emailProvider.send(type, message);
        repo.save(Notification.builder()
                .userId(userId)
                .orderId(orderId)
                .type(type)
                .message(message)
                .build());
    }
}
