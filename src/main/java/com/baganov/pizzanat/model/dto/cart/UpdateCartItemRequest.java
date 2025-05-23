/**
 * @file: UpdateCartItemRequest.java
 * @description: DTO для обновления количества товара в корзине
 * @dependencies: Валидация Jakarta
 * @created: 2025-05-23
 */
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
public class UpdateCartItemRequest {
    @NotNull(message = "Количество товара не может быть пустым")
    @Min(value = 1, message = "Минимальное количество товара - 1")
    private Integer quantity;
}