# Notification Service — Concepts, Design Decisions, and Repo Mapping

Goal: understand the "fan-in consumer" pattern (one service reacting to many event types from many producers) and how to simulate outbound email/SMS without a real provider.

## 1) What it does

`notification-service` has **no controller that creates anything** — it's a pure Kafka consumer that reacts to events other services already publish for their own reasons:

| Topic | Event | Notification sent |
|---|---|---|
| `orders` | `OrderCreated` | "Your order has been placed" |
| `payment-events` | `PaymentSucceeded` | "Payment received, order confirmed" |
| `payment-events` | `PaymentFailed` | "Order cancelled: payment declined" |

This is deliberate: notification-service adds a **new consumer group** (`notification-group`) to topics that already exist for the saga (see [`docs/ORDER_SAGA_FLOW.md`](ORDER_SAGA_FLOW.md)) — it doesn't need `order-service`/`payment-service` to change anything or know it exists. That's the main benefit of Kafka's pub/sub model over direct service-to-service calls: you can add new subscribers to a stream of business events without touching the publisher.

## 2) `EmailProvider` — the swappable seam

```java
public interface EmailProvider {
    void send(String subject, String body);
}
```

[`LoggingEmailProvider`](../notification-service/src/main/java/com/example/notification/service/LoggingEmailProvider.java) just logs the "email" — same pattern as `user-service`'s `LoggingEmailService` for password-reset links, and as `PaymentGateway` in `payment-service`. Swapping in a real Amazon SES/SendGrid adapter later is a one-class change behind this interface.

Every "send" is also persisted as a `Notification` row (`GET /api/notifications` lets you inspect what a user would have received) — useful for demos and for a real system's notification-history/"resend" feature.

## 3) Why a Kafka consumer group per service matters

Each service that needs its own full copy of every message on a topic must use a **distinct consumer group ID**. `inventory-service` (`inventory-group`), `payment-service` (`payment-group`), `order-service` (`order-group`) and `notification-service` (`notification-group`) can all consume the same `orders`/`payment-events` topics independently because Kafka delivers each message once per consumer group, not once per topic overall. If two of these services accidentally shared a group ID, they'd split the messages between them instead of each seeing all of them — a classic Kafka footgun.

## 4) Try it yourself

1. Run `notification-service` alongside `order-service`/`inventory-service`/`payment-service`.
2. Place an order and watch the `notification-service` logs — you should see an "ORDER_PLACED" line immediately, then a "PAYMENT_RECEIPT" or "ORDER_CANCELLED" line once the saga resolves.
3. `GET /api/notifications` (with a JWT) to see the persisted history for that user.

## 5) Interview talking points

- Fan-out/fan-in with pub/sub vs. direct calls: adding a notification feature required **zero changes** to any publisher.
- What's missing vs. production: templated emails, batching/digesting (don't spam a user with 5 emails/minute), delivery-failure retries/dead-letter handling, user notification preferences (opt out of marketing but not order updates), and SMS as a second channel (mentioned in the PRD).
