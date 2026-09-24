# Redis Caching in Cart Service — Concepts and Repo Mapping

Goal: understand cache-aside vs. write-through, TTL-based expiry, and cache serialization tradeoffs, using `cart-service`'s Redis integration as the concrete example.

## 1) Why cache the cart at all?

"Get my cart" is one of the most frequently called reads in an e-commerce app (rendered on nearly every page: header cart icon/count, cart page, checkout page). Hitting the relational DB for every one of those calls is wasteful when the data changes far less often than it's read. Redis (in-memory key-value store) serves these reads in sub-millisecond time instead of a DB round trip.

## 2) Cache-aside (a.k.a. lazy-loading) — what this repo implements

See [`CartService`](../cart-service/src/main/java/com/example/cart/service/CartService.java):

```
read:  check Redis -> miss? -> read DB -> write result into Redis -> return
write: write DB (source of truth) -> overwrite the Redis entry -> return
```

- The **database is always the source of truth**; Redis only ever holds a copy.
- A cache miss (e.g. after Redis restarts, or the TTL expired) transparently falls back to the DB — the cache is an optimization, not a dependency the app breaks without.
- Every write (add/update/remove/checkout) updates the DB **and then** the cache, so reads immediately after a write are consistent (no stale read within this one process).

### Cache-aside vs. write-through

- **Write-through** (not used here): every write goes to the cache first, and the cache itself is responsible for persisting to the DB (synchronously or asynchronously). Reads are always cache hits after the first write. This adds complexity to the caching layer and risks losing writes if the cache crashes before flushing.
- **Cache-aside** (used here): the application is responsible for both the DB and the cache explicitly. Simpler to reason about, and if Redis is unavailable, reads/writes still work (just slower) since the DB path doesn't depend on Redis. This is why cache-aside is the far more common pattern for "read-heavy, DB-is-authoritative" data like a cart.

## 3) TTL (time-to-live)

Every cache write sets an expiry (`cart.ttl-minutes`, default 30 — see `application.yml`), matching the PRD/LLD's notion of an "abandoned cart" concept. Two things to know:

- This TTL only affects the **Redis copy** — the underlying `carts` row is never automatically deleted by this sample (a real system would add a scheduled job to expire/archive abandoned cart rows in the DB too, which `LLD.md`'s `expires_at` column is meant for).
- After the TTL passes, the next `GET /api/cart` call is simply a cache miss — completely transparent to the client, just marginally slower for that one request while it re-hydrates from the DB.

## 4) Serialization

Redis stores bytes/strings, not Java objects, so `Cart`/`CartItem` need a serializer. [`RedisConfig`](../cart-service/src/main/java/com/example/cart/config/RedisConfig.java) uses `GenericJackson2JsonRedisSerializer` (JSON, human-readable in `redis-cli`, includes a `@class` type hint via `activateDefaultTyping` so Jackson can deserialize back to the concrete `Cart` type) rather than Java's native serialization (works but binary/opaque, versioning is brittle, and non-JVM tools can't read it).

## 5) Try it yourself

1. Start `redis` via `docker-compose up redis`.
2. Add an item to your cart, then `redis-cli KEYS "cart:*"` and `redis-cli GET cart:<userId>` — you'll see the JSON blob.
3. `redis-cli TTL cart:<userId>` — watch it count down from ~1800 seconds.
4. `redis-cli FLUSHALL` then `GET /api/cart` again — still works (falls back to the DB), and a fresh cache entry is written.

## 6) Interview talking points

- Cache invalidation is "one of the two hard things in computer science" — this repo sidesteps most of the hard cases (no cross-instance invalidation races) because writes always go through the same service that owns the cache key.
- Cache stampede (many requests missing the cache simultaneously, e.g. right after a TTL expiry, all hitting the DB at once) is not handled here — a production system might add request coalescing or a shorter "soft" TTL with background refresh.
- When *not* to cache: data that changes as often as it's read (no benefit), or where staleness is unacceptable (e.g. payment status) — that's why `payment-service`/`order-service` don't cache their reads in this repo.
