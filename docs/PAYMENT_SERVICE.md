# Payment Service — Concepts, Design Decisions, and Repo Mapping

Goal: understand how a payment service typically fits into an order flow, why it's modeled as an event-driven saga participant rather than a synchronous call, and how to safely simulate "a payment gateway" for learning without real credentials.

## 1) Why Payment is a separate service, and why it's event-driven here

Payment logic (talking to a gateway, PCI-scope isolation, retries, receipts) is naturally its own bounded context — you want to change/scale/audit it independently of order-taking. In this repo, `payment-service` never receives a direct HTTP call to "charge this order" — it's purely a Kafka consumer/producer:

- **Consumes** `inventory-events` (`InventoryReserved`) — only after stock is confirmed does it make sense to charge the customer.
- **Produces** `payment-events` (`PaymentSucceeded` / `PaymentFailed`) — for `order-service` (update status), `inventory-service` (release stock on failure) and `notification-service` (receipt/cancellation email) to react to.

This keeps the services decoupled: `order-service` never needs to know payment-service's API shape or even that it exists — it just reacts to events on a topic. See [`docs/ORDER_SAGA_FLOW.md`](ORDER_SAGA_FLOW.md) for the full sequence.

## 2) Data model

```
Payment (1) --- (1..*) PaymentAttempt
  orderId, userId, amount, status      outcome, reason, attemptedAt
```

- `Payment` is the aggregate the API exposes (`GET /api/payments`, `GET /api/payments/order/{orderId}`).
- `PaymentAttempt` records each authorization attempt — a real system uses this for retry/backoff logic and dispute investigation. This sample only ever makes one attempt per order, but the model leaves room for retries.

## 3) `PaymentGateway` — the swappable seam

```java
public interface PaymentGateway {
    GatewayResult authorize(BigDecimal amount);
}
```

[`MockPaymentGateway`](../payment-service/src/main/java/com/example/payment/gateway/MockPaymentGateway.java) is the only implementation — a **deterministic simulator**, not a real Stripe/Braintree/PayPal integration (no sandbox credentials needed to run this repo). It declines whenever the amount's fractional cents equal `.13` — e.g. price a product at `19.13` — so you can reproducibly demo the decline → saga-rollback path without relying on flaky randomness. Everything else approves.

**Interview framing**: this is the Strategy/Ports-and-Adapters pattern — `PaymentService`/the Kafka listener depends only on the `PaymentGateway` interface, so swapping in a real Stripe adapter later is a one-class change, not a rewrite. The same pattern shows up for `EmailProvider` in `notification-service`.

## 4) What a production version would add (and why it's out of scope here)

| Missing here | Why it matters in production |
|---|---|
| Real gateway integration (Stripe/Braintree/etc.) | Actual money movement, 3-D Secure, webhooks |
| Idempotency keys on `PaymentAttempt` | Retrying a network-timeout-but-actually-succeeded charge must not double-charge the customer |
| PCI-DSS scope isolation | Card data must never touch services outside a tightly audited boundary |
| Refunds/partial refunds | `PaymentService.refund(...)` exists in the LLD class diagram but has no controller/logic here |
| Receipts (PDF/URL) | `payment_receipts` table exists in `LLD.md`'s target schema, not in this sample |

These are called out explicitly rather than silently ignored — see [`docs/SCOPE_DEVIATIONS.md`](SCOPE_DEVIATIONS.md).

## 5) Try it yourself

1. Run `inventory-service`, `payment-service`, `order-service`, `notification-service` plus Kafka.
2. Place an order for a normally-priced product → `GET /api/payments/order/{orderId}` should show `SUCCEEDED`.
3. Create/price a product at `X.13` and place an order for it → the payment record shows `FAILED`, the order ends up `CANCELLED`, and the inventory reservation is released (check the inventory quantity went back up).
4. Try `GET /api/payments/order/{orderId}` with a JWT for a *different* user than the order's owner → expect `403 Forbidden` (ownership check via the JWT `userId` claim, not a client-supplied one).

## 6) Interview talking points

- Why payment must never be attempted before stock is confirmed (data integrity: don't charge for something you can't ship).
- BigDecimal for money (never `float`/`double`) — see how `Payment.amount` and `Order.totalAmount` use `BigDecimal`, while the older `Product.price` field is still a `double` (a known, called-out inconsistency — see `docs/SCOPE_DEVIATIONS.md`).
- Outbox pattern (not implemented): in production you'd worry about a payment being saved to the DB but the Kafka publish failing (or vice versa) — the transactional outbox pattern solves that "dual write" problem by writing the event to an outbox table in the same DB transaction and having a separate poller publish it.
