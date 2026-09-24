package com.example.cart.web.dto;

import jakarta.validation.constraints.Min;
import lombok.Data;

@Data
public class UpdateQuantityRequest {
    @Min(0)
    private int quantity;
}
