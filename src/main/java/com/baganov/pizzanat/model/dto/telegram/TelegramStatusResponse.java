package com.baganov.pizzanat.model.dto.telegram;

import com.baganov.pizzanat.entity.TelegramAuthToken.TokenStatus;
import com.baganov.pizzanat.model.dto.auth.AuthResponse;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * DTO для ответа статуса Telegram аутентификации.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "Статус Telegram аутентификации")
public class TelegramStatusResponse {

    @Schema(description = "Успешность операции", example = "true")
    private boolean success;

    @Schema(description = "Статус токена", example = "CONFIRMED")
    private TokenStatus status;

    @Schema(description = "Сообщение для пользователя", example = "Аутентификация подтверждена")
    private String message;

    @Schema(description = "Данные аутентификации (если подтверждено)")
    private AuthResponse authData;

    /**
     * Создает ответ для статуса PENDING
     */
    public static TelegramStatusResponse pending() {
        return TelegramStatusResponse.builder()
                .success(true)
                .status(TokenStatus.PENDING)
                .message("Ожидание подтверждения в Telegram")
                .build();
    }

    /**
     * Создает ответ для статуса CONFIRMED
     */
    public static TelegramStatusResponse confirmed(AuthResponse authData) {
        return TelegramStatusResponse.builder()
                .success(true)
                .status(TokenStatus.CONFIRMED)
                .message("Аутентификация успешно подтверждена")
                .authData(authData)
                .build();
    }

    /**
     * Создает ответ для статуса EXPIRED
     */
    public static TelegramStatusResponse expired() {
        return TelegramStatusResponse.builder()
                .success(false)
                .status(TokenStatus.EXPIRED)
                .message("Токен аутентификации истек. Попробуйте снова")
                .build();
    }

    /**
     * Создает ответ об ошибке
     */
    public static TelegramStatusResponse error(String message) {
        return TelegramStatusResponse.builder()
                .success(false)
                .message(message)
                .build();
    }
}