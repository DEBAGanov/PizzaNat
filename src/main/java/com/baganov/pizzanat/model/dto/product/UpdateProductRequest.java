/**
 * @file: UpdateProductRequest.java
 * @description: DTO для обновления продукта
 * @dependencies: Jakarta Validation
 * @created: 2025-05-31
 */
package com.baganov.pizzanat.model.dto.product;

import jakarta.validation.constraints.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UpdateProductRequest {

    @NotBlank(message = "Название продукта не может быть пустым")
    @Size(max = 100, message = "Название продукта не может превышать 100 символов")
    private String name;

    @Size(max = 1000, message = "Описание не может превышать 1000 символов")
    private String description;

    @NotNull(message = "Цена обязательна")
    @DecimalMin(value = "0.01", message = "Цена должна быть больше 0")
    @Digits(integer = 8, fraction = 2, message = "Неверный формат цены")
    private BigDecimal price;

    @DecimalMin(value = "0.01", message = "Цена со скидкой должна быть больше 0")
    @Digits(integer = 8, fraction = 2, message = "Неверный формат цены со скидкой")
    private BigDecimal discountedPrice;

    @NotNull(message = "ID категории обязателен")
    @Positive(message = "ID категории должен быть положительным")
    private Integer categoryId;

    @Size(max = 500, message = "URL изображения не может превышать 500 символов")
    private String imageUrl;

    @Positive(message = "Вес должен быть положительным")
    private Integer weight;

    private Boolean isAvailable;

    private Boolean isSpecialOffer;

    @Min(value = 0, message = "Процент скидки не может быть отрицательным")
    @Max(value = 100, message = "Процент скидки не может превышать 100")
    private Integer discountPercent;
}