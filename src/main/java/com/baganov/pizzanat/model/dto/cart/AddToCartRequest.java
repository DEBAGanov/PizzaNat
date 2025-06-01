/**
 * @file: AddToCartRequest.java
 * @description: DTO для добавления товара в корзину
 * @dependencies: Jakarta Validation, Jackson
 * @created: 2025-05-31
 */
package com.baganov.pizzanat.model.dto.cart;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Map;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AddToCartRequest {

    @NotNull(message = "ID продукта обязателен")
    @Positive(message = "ID продукта должен быть положительным")
    private Integer productId;

    @NotNull(message = "Количество обязательно")
    @Positive(message = "Количество должно быть положительным")
    private Integer quantity;

    /**
     * Опции продукта для Android интеграции
     * Пример: {"size": "large", "extraCheese": true}
     */
    private Map<String, Object> selectedOptions;
}