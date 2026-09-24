# Architecture Overview

This document summarizes the architecture for the interview-focused e-commerce sample.

- Architecture style: Microservices (multiple Spring Boot services) in a mono-repo for convenience.
- Services: `user-service` (identity: registration/login/password reset/social login, plus profile management), `product-service`, `cart-service`, `order-service`, `payment-service`, `inventory-service`, `notification-service`, `api-gateway`, `common` library.
- Database: Each service uses its own relational schema (MySQL in production). Tests use H2/Testcontainers.
- Note: `auth-service` and `user-service` were originally split but have been merged into a single `user-service`, since they shared one bounded context (identity + profile) with no independent scaling/team boundary justifying the split.
- Messaging: Kafka for async events across four topics: `orders` (OrderCreated), `inventory-events` (InventoryReserved / InventoryReservationFailed), `payment-events` (PaymentSucceeded / PaymentFailed), `checkout-requested` (Cart → Order).
- Caching: Redis, used by `cart-service` (cache-aside for the active cart) and by `api-gateway` (request-rate-limiter counters).
- Search: Elasticsearch, used by `product-service` for keyword search (`GET /api/products/search`), with the existing DB `LIKE` query kept as an automatic fallback if the cluster is unreachable.
- Local dev: `docker-compose.yml` runs MySQL, Kafka/Zookeeper, Redis and Elasticsearch for integration testing.

Data flow (order example - see [docs/ORDER_SAGA_FLOW.md](docs/ORDER_SAGA_FLOW.md) for the full choreographed saga):
1. Client places an order two ways: synchronously via `POST /api/orders` on `order-service`, or asynchronously by adding items to `cart-service` and calling `POST /api/cart/checkout` (publishes `CheckoutRequested`, which `order-service` consumes to create the order).
2. `order-service` persists the order (`PENDING`) and publishes `OrderCreated` to `orders`.
3. `inventory-service` consumes `OrderCreated`, attempts an all-or-nothing stock reservation, and publishes `InventoryReserved` or `InventoryReservationFailed` to `inventory-events`.
4. `payment-service` consumes `InventoryReserved` only (never attempts payment for stock that wasn't reserved), simulates authorization, and publishes `PaymentSucceeded`/`PaymentFailed` to `payment-events`.
5. `order-service` consumes `inventory-events`/`payment-events` to move the order to `CONFIRMED` or `CANCELLED`; `inventory-service` consumes `PaymentFailed` to release the reservation (the saga's compensating transaction); `notification-service` consumes all three topics to record/"send" user notifications.

Security: `user-service` issues JWT tokens (register/login/social-login) carrying a `userId` claim; all services, including `user-service` itself, validate tokens locally with Spring Security filters using the shared `common` `JwtProvider`, and read `userId` off the token (never from the request body) to authorize access to a caller's own orders/cart/payments.

## Deviations from the original HLD (PRD.md)

The PRD's HLD calls out MongoDB, Kong, and unspecified payment/email providers. This sample intentionally substitutes simpler, self-contained equivalents so the whole stack can run locally without external accounts/services. Each substitution is a real architectural decision, documented in more depth in `docs/`:

| PRD/HLD said | This sample uses | Why | Details |
|---|---|---|---|
| MongoDB for Cart Service | MySQL/H2 (relational), same as every other service | The cart's shape never varies (fixed list of line items), so schema flexibility isn't needed; one less datastore/tech to operate | [docs/CART_SERVICE.md](docs/CART_SERVICE.md) |
| Redis for Cart Service | Redis (unchanged) | Matches the PRD directly - cache-aside in front of the relational cart | [docs/REDIS_CACHING.md](docs/REDIS_CACHING.md) |
| Kong API Gateway | Spring Cloud Gateway (`api-gateway` module) | A real Kong deployment needs its own admin API/config store that can't be meaningfully expressed as application code in this repo; Spring Cloud Gateway demonstrates the same routing/rate-limiting/cross-cutting-filter concepts in Java | [docs/API_GATEWAY.md](docs/API_GATEWAY.md) |
| Payment gateway (unspecified, e.g. Stripe) | `MockPaymentGateway` (deterministic simulator) | No real merchant account/sandbox credentials available for a learning repo; the `PaymentGateway` interface is the seam where a real adapter would plug in | [docs/PAYMENT_SERVICE.md](docs/PAYMENT_SERVICE.md) |
| Amazon SES | `LoggingEmailProvider` (logs + persists a `Notification` row) | Same reasoning as payment - `EmailProvider` is the seam for a real SES/SendGrid adapter | [docs/NOTIFICATION_SERVICE.md](docs/NOTIFICATION_SERVICE.md) |
| `UUID` primary keys (LLD.md ER diagram) | `BIGINT`/`Long` auto-increment | The class diagram already used `Long id` everywhere and every existing entity in the codebase does too - the ER diagram's `UUID` was an internal inconsistency in the original LLD, now fixed | LLD.md |

See [docs/SCOPE_DEVIATIONS.md](docs/SCOPE_DEVIATIONS.md) for the full list, including smaller simplifications (no order/payment audit-history tables, no idempotency keys, no product image/spec tables).
