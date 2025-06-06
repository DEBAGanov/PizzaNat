package com.baganov.pizzanat.model.dto.auth;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * DTO для запроса отправки SMS кода.
 * Следует принципу Interface Segregation из SOLID.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "Запрос на отправку SMS кода для аутентификации")
public class SendSmsCodeRequest {

    @NotBlank(message = "Номер телефона обязателен")
    @Pattern(regexp = "^(\\+7|8|7)?[0-9]{10}$", message = "Номер телефона должен быть в российском формате")
    @Schema(description = "Номер телефона в российском формате", example = "+79123456789", pattern = "^(\\+7|8|7)?[0-9]{10}$")
    private String phoneNumber;
}