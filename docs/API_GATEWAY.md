# API Gateway — Concepts, Design Decisions, and Repo Mapping

Goal: understand what an API Gateway is for, why the PRD's "Kong" couldn't be meaningfully represented as application code, and how Spring Cloud Gateway demonstrates the same concepts.

## 1) What an API Gateway does (and why the PRD calls for one)

Without a gateway, every client (web app, mobile app, Postman) needs to know every service's host/port and call them directly — painful to change, and every service must independently implement cross-cutting concerns (rate limiting, auth, logging, CORS). A gateway is a single entry point that:

- **Routes** requests to the right backend service by path (`/api/products/**` → product-service, etc.)
- **Centralizes cross-cutting concerns** — rate limiting, request logging, auth pre-checks, response caching
- Can do **protocol translation**, request/response transformation, circuit breaking, retries

The PRD names **Kong** (a widely-used, plugin-based gateway usually run as its own process with its own admin API/database for route config).

## 2) Why this sample uses Spring Cloud Gateway instead of Kong

Kong is configured via its own admin API/declarative YAML and typically deployed as a separate piece of infrastructure (often with its own Postgres). There's no meaningful way to "write Kong" as a Java class in this repo — you'd just be writing Kong's config file, which doesn't teach you anything about how a gateway *works*. Spring Cloud Gateway is a real, production-grade gateway that happens to be a Spring Boot app, so:

- It lives in this repo as actual Java/YAML you can read and modify (`api-gateway` module).
- It demonstrates the same concepts (path routing, filters, rate limiting) with code you can step through in a debugger.
- In a real system, choosing Kong vs. Spring Cloud Gateway vs. AWS API Gateway is an infra/ops decision (which team owns it, Lua/plugin ecosystem vs. Java/Spring ecosystem, managed vs. self-hosted) — not a functionality difference worth re-implementing here.

## 3) What's implemented

See [`api-gateway/src/main/resources/application.yml`](../api-gateway/src/main/resources/application.yml):

| Route | Target | Notes |
|---|---|---|
| `/api/auth/**`, `/api/users/**` | `user-service` (8080) | |
| `/api/products/**` | `product-service` (8081) | Rate-limited (see below) |
| `/api/orders/**` | `order-service` (8082) | |
| `/api/inventory/**` | `inventory-service` (8083) | |
| `/api/cart/**` | `cart-service` (8085) | |
| `/api/payments/**` | `payment-service` (8086) | |
| `/api/notifications/**` | `notification-service` (8087) | |

Plus:
- **`RequestRateLimiter`** (Redis-backed token bucket) on the product-browsing route — the PRD explicitly says the gateway "handles rate limiting". Product browsing is public/anonymous, so [`RateLimiterConfig`](../api-gateway/src/main/java/com/example/gateway/config/RateLimiterConfig.java) keys the bucket by client IP (`KeyResolver`) rather than a JWT subject.
- **`RequestLoggingFilter`** — a `GlobalFilter` that logs method/path/status/latency for every routed request, showing the "one place for cross-cutting concerns" idea concretely.

## 4) What's deliberately NOT re-implemented at the gateway

- **JWT validation** — each service still validates its own JWT (via `common`'s `JwtProvider`) rather than the gateway stripping/verifying it and forwarding a trusted identity header. This is a legitimate alternative design (see "Interview talking points"), but duplicating the entire `JwtFilter` pattern into the gateway added complexity without much additional learning value here, since JWT validation is already covered per-service (see [`docs/JWT-Interview-Prep.md`](JWT-Interview-Prep.md)).
- **Service discovery** (Eureka/Consul) — routes use hardcoded `localhost:PORT` URIs instead of a service registry, matching how every other service in this repo finds its dependencies (see `services.product.base-url` in `order-service`/`cart-service`).

## 5) Try it yourself

1. Start `redis` (for the rate limiter), then `api-gateway` plus at least `product-service`.
2. `curl http://localhost:8888/api/products` — routed through the gateway to product-service on 8081.
3. Hit `curl http://localhost:8888/api/products` in a tight loop (>20 requests quickly) — eventually you should see `429 Too Many Requests` once the token bucket (burst capacity 20, refill rate 10/sec) is exhausted.
4. Watch the gateway logs for the `RequestLoggingFilter` line on every call.

## 6) Interview talking points

- **Gateway-level auth vs. service-level auth**: gateway-level (strip/verify JWT once, forward a trusted `X-User-Id` header) reduces duplicate work and centralizes token logic, but makes the gateway a hard dependency for *every* request and a single point of failure/attack surface. Service-level (this repo) means each service is independently securable/testable without the gateway running, at the cost of duplicated validation logic. Many real systems do both: gateway does coarse-grained checks (is there a token at all?) and services do fine-grained authorization (is this token allowed to touch *this* resource?).
- **Rate limiting algorithms**: token bucket (used here, via Spring Cloud Gateway's Redis-backed limiter) allows bursts up to a capacity while enforcing a steady refill rate — different from a fixed window counter (simpler but allows 2x burst at window boundaries) or sliding window log (most accurate, most memory).
- **BFF (Backend-for-Frontend) pattern**: a gateway can also aggregate multiple service calls into one response for a specific client (e.g. a mobile app's home screen) — not implemented here, but a natural next evolution of this gateway layer.
