package com.baganov.pizzanat.model.dto.cart;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AddToCartRequest {
    @NotNull(message = "ID продукта не может быть пустым")
    private Integer productId;

    @NotNull(message = "Количество товара не может быть пустым")
    @Min(value = 1, message = "Минимальное количество товара - 1")
    private Integer quantity;
}