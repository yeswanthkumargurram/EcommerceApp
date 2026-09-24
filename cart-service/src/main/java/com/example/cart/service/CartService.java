package com.example.cart.service;

import com.example.cart.client.ProductCatalogClient;
import com.example.cart.kafka.CartEventPublisher;
import com.example.cart.model.Cart;
import com.example.cart.model.CartItem;
import com.example.cart.repository.CartRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.Optional;

import static org.springframework.http.HttpStatus.BAD_REQUEST;

/**
 * Cache-aside (a.k.a. lazy-loading) pattern:
 *  - reads try Redis first, fall back to the DB on a miss, then repopulate Redis.
 *  - writes go to the DB first (source of truth), then overwrite the cache entry.
 * See docs/REDIS_CACHING.md for why this pattern (vs write-through) was chosen.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class CartService {
    private static final String KEY_PREFIX = "cart:";

    private final CartRepository repo;
    private final RedisTemplate<String, Cart> redis;
    private final ProductCatalogClient productClient;
    private final CartEventPublisher events;

    @Value("${cart.ttl-minutes:30}")
    private long ttlMinutes;

    public Cart getCart(Long userId) {
        String key = KEY_PREFIX + userId;
        Cart cached = redis.opsForValue().get(key);
        if (cached != null) {
            log.info("[getCart] cache HIT for user={} key={}", userId, key);
            return cached;
        }
        log.info("[getCart] cache MISS for user={} key={} - falling back to DB", userId, key);
        Cart cart = loadOrCreate("getCart", userId);
        cacheWrite(cart);
        return cart;
    }

    public Cart addItem(Long userId, Long productId, int quantity) {
        Cart cart = loadOrCreate("addItem", userId);
        ProductCatalogClient.ProductView product = productClient.getProduct(productId);

        Optional<CartItem> existing = cart.getItems().stream()
                .filter(i -> i.getProductId().equals(productId))
                .findFirst();
        if (existing.isPresent()) {
            existing.get().setQuantity(existing.get().getQuantity() + quantity);
        } else {
            // cart.addItem (not cart.getItems().add) so the owning-side back-reference is set -
            // items is mappedBy="cart", so Hibernate derives the cart_id FK from CartItem.cart.
            cart.addItem(CartItem.builder()
                    .productId(productId)
                    .productName(product.getName())
                    .unitPrice(BigDecimal.valueOf(product.getPrice()))
                    .quantity(quantity)
                    .build());
        }
        return save("addItem", cart);
    }

    public Cart updateQuantity(Long userId, Long productId, int quantity) {
        Cart cart = loadOrCreate("updateQuantity", userId);
        if (quantity == 0) {
            cart.getItems().removeIf(i -> i.getProductId().equals(productId));
        } else {
            CartItem item = cart.getItems().stream()
                    .filter(i -> i.getProductId().equals(productId))
                    .findFirst()
                    .orElseThrow(() -> new ResponseStatusException(BAD_REQUEST, "product not in cart: " + productId));
            item.setQuantity(quantity);
        }
        return save("updateQuantity", cart);
    }

    public Cart removeItem(Long userId, Long productId) {
        Cart cart = loadOrCreate("removeItem", userId);
        cart.getItems().removeIf(i -> i.getProductId().equals(productId));
        return save("removeItem", cart);
    }

    public void checkout(Long userId, String deliveryAddress) {
        Cart cart = loadOrCreate("checkout", userId);
        if (cart.getItems().isEmpty()) {
            throw new ResponseStatusException(BAD_REQUEST, "cart is empty");
        }
        events.publishCheckoutRequested(cart, deliveryAddress);
        cart.getItems().clear();
        save("checkout", cart);
    }

    private Cart loadOrCreate(String operation, Long userId) {
        Optional<Cart> existing = repo.findByUserId(userId);
        if (existing.isPresent()) {
            log.info("[{}] DB HIT - loaded cart id={} for user={}", operation, existing.get().getId(), userId);
            return existing.get();
        }
        log.info("[{}] DB MISS - no cart row for user={}, creating new in-memory cart", operation, userId);
        return Cart.builder().userId(userId).build();
    }

    private Cart save(String operation, Cart cart) {
        cart.setUpdatedAt(Instant.now());
        Cart saved = repo.save(cart);
        log.info("[{}] DB WRITE - persisted cart id={} for user={}", operation, saved.getId(), saved.getUserId());
        cacheWrite(saved);
        return saved;
    }

    private void cacheWrite(Cart cart) {
        String key = KEY_PREFIX + cart.getUserId();
        redis.opsForValue().set(key, cart, Duration.ofMinutes(ttlMinutes));
        log.info("cache WRITE key={} ttl={}m", key, ttlMinutes);
    }
}
