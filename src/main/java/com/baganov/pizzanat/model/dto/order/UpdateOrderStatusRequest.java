/**
 * @file: UpdateOrderStatusRequest.java
 * @description: DTO для обновления статуса заказа
 * @dependencies: Jakarta Validation
 * @created: 2025-05-31
 */
package com.baganov.pizzanat.model.dto.order;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UpdateOrderStatusRequest {

    @NotBlank(message = "Статус заказа обязателен")
    @Pattern(regexp = "^(PENDING|CONFIRMED|PREPARING|READY|DELIVERING|DELIVERED|CANCELLED|CREATED|PAID)$", message = "Недопустимый статус заказа")
    private String statusName;
}