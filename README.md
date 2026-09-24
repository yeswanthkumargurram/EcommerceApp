# EcommerceApp — Interview Learning Project (Java 21)

EcommerceApp is a multi-module Java 21 Spring Boot sample designed to illustrate microservice architecture, integration patterns, and implementation choices commonly discussed in senior Java interviews. The repository contains modular services (product, cart, order, inventory, payment, user, notification), an API gateway, and shared libraries.

Highlights
- Lightweight, modular Spring Boot services using Java 21.
- Example infra for local development with MySQL, Kafka, Redis, and Elasticsearch via Docker Compose.
- Focused documentation in `/docs` and architecture diagrams in `/diagrams`.

Quick start (run a single service)

To run the `product-service` (uses H2 by default):

```bash
mvn -T 1C -pl :product-service -am spring-boot:run
```

Run full infrastructure locally (MySQL + Kafka + other infra)

```bash
docker-compose up -d
# Start services with the MySQL profile:
mvn -T 1C -pl :product-service -am spring-boot:run -Dspring.profiles.active=mysql
```

Repository layout (top-level)
- `api-gateway/` - gateway service and routing configuration.
- `product-service/`, `cart-service/`, `order-service/`, `inventory-service/`, `payment-service/`, `notification-service/`, `user-service/` - service modules.
- `common/` - shared utilities, DTOs, and configs.
- `docs/` - design notes and service-specific guides.
- `diagrams/` - architecture and UML diagrams.

Key development notes
- Lombok: This project uses Lombok for model boilerplate. If your IDE doesn't recognize generated members, install the Lombok plugin and enable annotation processing.
- Java & Build: Requires Java 21 and Maven 3.8+. Services are standard Spring Boot apps; use the `-pl` and `-am` Maven flags to start a single module with its dependencies.
- Profiles: Default profiles use in-memory or embedded stores (H2). Use `-Dspring.profiles.active=mysql` to switch to MySQL-backed configuration.

Useful commands
- Run all tests across modules:

```bash
mvn test
```
- Build a single module (example `product-service`):

```bash
mvn -pl :product-service -am package
```

Documentation and design
- Service guides: see `docs/` for per-service notes such as `CART_SERVICE.md` and `PAYMENT_SERVICE.md`.
- Architecture diagrams: view detailed UML and DB diagrams in `diagrams/LLD/`.

Contributing
- If you'd like a particular service implemented or expanded, open an issue or request it here and I can scaffold or implement it next.

Next steps I can help with
- Scaffold or implement a specific service (API, persistence, tests).
- Add run scripts, health checks, or Dockerfiles for missing modules.
- Generate per-service README files with start instructions.

Enjoy exploring the code — tell me which service you'd like implemented next.

