package com.example.cart.model;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/**
 * Cart is intentionally a normal relational entity (MySQL/H2), not MongoDB -
 * see docs/CART_SERVICE.md "Why not MongoDB?" for the rationale. Redis sits in
 * front of it as a cache, matching the PRD's "Redis for fast, in-memory access".
 */
@Entity
@Table(name = "carts")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Cart implements Serializable {
    @Id
    @GeneratedValue(strategy = GenerationType.AUTO)
    private Long id;

    @Column(unique = true, nullable = false)
    private Long userId;

    @OneToMany(mappedBy = "cart", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.EAGER)
    @Builder.Default
    private List<CartItem> items = new ArrayList<>();

    @Builder.Default
    private Instant updatedAt = Instant.now();

    // Derived, not persisted/settable - include when serializing (API responses, cache writes) but
    // don't require it back when deserializing (e.g. reading this Cart out of the Redis cache).
    @Transient
    @JsonProperty(access = JsonProperty.Access.READ_ONLY)
    public BigDecimal getTotal() {
        return items.stream()
                .map(i -> i.getUnitPrice().multiply(BigDecimal.valueOf(i.getQuantity())))
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    public void addItem(CartItem item) {
        items.add(item);
        item.setCart(this);
    }

    public void removeItem(CartItem item) {
        items.remove(item);
        item.setCart(null);
    }
}
