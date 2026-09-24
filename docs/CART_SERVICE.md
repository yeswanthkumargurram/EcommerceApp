# Cart Service — Concepts, Design Decisions, and Repo Mapping

Goal: understand what a "cart service" needs to do, why it's usually its own microservice, the MongoDB-vs-relational tradeoff, and how it fits into checkout.

## 1) Why is Cart its own service?

The cart has different characteristics than the rest of the catalog/order data:
- **High write volume, low durability requirement** — every "change quantity" click is a write, but if you lose an abandoned cart it's not a business disaster (unlike losing an order).
- **Short-lived** — most carts are abandoned or converted within minutes/hours, not kept forever like orders.
- **Read-heavy on a single key** (`userId`) — "get my cart" is called on nearly every page load.

Those characteristics are why the PRD's HLD proposes MongoDB (flexible/document-shaped, cheap writes) + Redis (fast reads) for this service specifically, while Product/Order/Payment stay on MySQL.

## 2) Why this sample uses MySQL/H2 instead of MongoDB

This is a deliberate deviation from the PRD's HLD — see [`Cart` entity](../cart-service/src/main/java/com/example/cart/model/Cart.java):

- A cart's shape **never actually varies**: it's always "a list of `{productId, name, price, quantity}`". MongoDB's schema flexibility earns its keep when different documents in the same collection have genuinely different shapes (e.g. product catalogs with wildly different attributes per category) — that's not the case here.
- Running a MongoDB cluster is another piece of infrastructure to operate, monitor, and back up. For a fixed-shape entity, a relational table is simpler and gets you foreign keys/joins/tooling for free.
- **Interview framing**: "Would I always pick MongoDB for a cart?" — no. The right question is "does my data have variable structure, or do I need document-level atomicity/no-join-across-shards?" If not, relational is usually the simpler default. Reach for a document store when you actually need its properties (e.g. arbitrary nested attributes, no schema migrations).

Redis is still used, exactly as the PRD specifies — see [`docs/REDIS_CACHING.md`](REDIS_CACHING.md).

## 3) Design

```
Cart (1) --- (0..*) CartItem
  userId (unique)         productId, productName, unitPrice, quantity
```

- `CartItem.productName` / `unitPrice` are **snapshots** taken at add-to-cart time (from `product-service`), the same "don't own another service's rows" principle used by `OrderItem`. If the product's price changes later, the cart shows the price the user saw when they added it — until they re-add or the cart is rebuilt.
- One cart per user (`userId` is unique) — no cart ID needs to be known by the client; `GET /api/cart` always resolves "my" cart from the JWT.

## 4) Endpoints

| Method & Path | Purpose |
|---|---|
| `GET /api/cart` | Get (or lazily create) the caller's cart |
| `POST /api/cart/items` | Add a product (validates it exists via `product-service`, merges quantity if already present) |
| `PUT /api/cart/items/{productId}` | Set an exact quantity (`0` removes the item) |
| `DELETE /api/cart/items/{productId}` | Remove an item |
| `POST /api/cart/checkout` | Publish `CheckoutRequested` to Kafka and empty the cart |

All endpoints resolve the caller from the JWT's `userId` claim (see [`CartController`](../cart-service/src/main/java/com/example/cart/web/CartController.java)) — never from a client-supplied ID, which would let one user manipulate another's cart (an IDOR vulnerability).

## 5) Checkout: sync validation, async order creation

`checkout()` does **not** call `order-service` over REST. Instead it publishes a `CheckoutRequested` event and clears the cart; `order-service`'s `CheckoutRequestedListener` consumes it and creates the order. This is the same "two ways to start a workflow" idea used elsewhere in this repo (`order-service` also has a direct `POST /api/orders` for a synchronous path) — see [`docs/ORDER_SAGA_FLOW.md`](ORDER_SAGA_FLOW.md).

Why async here specifically: checkout doesn't need to block the user waiting for the full saga (stock reservation + payment) to finish — `202 Accepted` and let the saga run is the right UX (think Amazon's "Order placed" page appearing instantly, with confirmation email later).

## 6) Try it yourself

1. Register/login to get a JWT.
2. `POST /api/cart/items` with a real `productId` from `product-service`.
3. `GET /api/cart` twice — the second call is served from Redis (see [`docs/REDIS_CACHING.md`](REDIS_CACHING.md) for how to observe the cache hit).
4. `PUT /api/cart/items/{productId}` to change the quantity, then `POST /api/cart/checkout` with a `deliveryAddress`.
5. Check `order-service`'s `GET /api/orders` — a new order should appear, created asynchronously from the checkout event.

## 7) Interview talking points

- "Cart doesn't own Product rows" — same external-reference-not-foreign-key principle as Order (see `LLD.md`'s notes on `Cart`/`Order`).
- Cache-aside vs. write-through for the cart cache — see `docs/REDIS_CACHING.md`.
- What happens if `product-service` is down when adding to cart? `ProductCatalogClient` throws a `503` (see the `RestClientException` handling) rather than silently trusting client-supplied price/name — never trust the client for a monetary value.
