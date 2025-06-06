package com.baganov.pizzanat.model.dto.auth;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * DTO для запроса верификации SMS кода.
 * Следует принципу Interface Segregation из SOLID.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "Запрос на верификацию SMS кода")
public class VerifySmsCodeRequest {

    @NotBlank(message = "Номер телефона обязателен")
    @Pattern(regexp = "^(\\+7|8|7)?[0-9]{10}$", message = "Номер телефона должен быть в российском формате")
    @Schema(description = "Номер телефона в российском формате", example = "+79123456789", pattern = "^(\\+7|8|7)?[0-9]{10}$")
    private String phoneNumber;

    @NotBlank(message = "SMS код обязателен")
    @Pattern(regexp = "^\\d{4}$", message = "SMS код должен состоять из 4 цифр")
    @Schema(description = "4-значный SMS код", example = "1234", pattern = "^\\d{4}$")
    private String code;
}