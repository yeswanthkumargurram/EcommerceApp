package com.example.order.model;

/** Order lifecycle driven by the choreographed saga (see docs/ORDER_SAGA_FLOW.md). */
public enum OrderStatus {
    PENDING,
    CONFIRMED,
    CANCELLED,
    SHIPPED,
    DELIVERED
}
