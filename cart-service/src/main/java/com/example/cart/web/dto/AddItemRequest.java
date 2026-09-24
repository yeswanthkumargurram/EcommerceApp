package com.example.cart.web.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class AddItemRequest {
    @NotNull
    private Long productId;

    @Min(1)
    private int quantity = 1;
}
