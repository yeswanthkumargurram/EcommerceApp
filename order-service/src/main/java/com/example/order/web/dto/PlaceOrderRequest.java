package com.example.order.web.dto;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.Valid;
import java.util.List;
import lombok.Data;

/** API contract for placing an order - deliberately separate from the Order JPA entity. */
@Data
public class PlaceOrderRequest {
    @NotEmpty
    private String deliveryAddress;

    @NotEmpty
    @Valid
    private List<OrderItemRequest> items;

    @Data
    public static class OrderItemRequest {
        @NotNull
        private Long productId;

        @jakarta.validation.constraints.Min(1)
        private int quantity;
    }
}
