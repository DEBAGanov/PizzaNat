package com.baganov.pizzanat.model.dto.telegram;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import jakarta.validation.constraints.Size;

/**
 * DTO для запроса инициализации Telegram аутентификации.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "Запрос инициализации Telegram аутентификации")
public class InitTelegramAuthRequest {

    @Schema(description = "ID устройства (опционально)", example = "android_device_123")
    @Size(max = 100, message = "ID устройства не должен превышать 100 символов")
    private String deviceId;
}