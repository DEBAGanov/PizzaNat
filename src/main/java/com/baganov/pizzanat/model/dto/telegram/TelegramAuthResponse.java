package com.baganov.pizzanat.model.dto.telegram;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

/**
 * DTO для ответа инициализации Telegram аутентификации.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "Ответ инициализации Telegram аутентификации")
public class TelegramAuthResponse {

    @Schema(description = "Успешность операции", example = "true")
    private boolean success;

    @Schema(description = "Токен аутентификации", example = "tg_auth_abc123xyz789")
    private String authToken;

    @Schema(description = "URL для перехода в Telegram бот", example = "https://t.me/pizzanat_auth_bot?start=tg_auth_abc123xyz789")
    private String telegramBotUrl;

    @Schema(description = "Время истечения токена", example = "2025-01-15T14:30:00")
    private String expiresAt;

    @Schema(description = "Сообщение для пользователя", example = "Перейдите по ссылке для подтверждения")
    private String message;

    /**
     * Создает успешный ответ
     */
    public static TelegramAuthResponse success(String authToken, String telegramBotUrl, LocalDateTime expiresAt) {
        return TelegramAuthResponse.builder()
                .success(true)
                .authToken(authToken)
                .telegramBotUrl(telegramBotUrl)
                .expiresAt(expiresAt.format(DateTimeFormatter.ISO_LOCAL_DATE_TIME))
                .message("Перейдите по ссылке для подтверждения аутентификации в Telegram")
                .build();
    }

    /**
     * Создает ответ об ошибке
     */
    public static TelegramAuthResponse error(String message) {
        return TelegramAuthResponse.builder()
                .success(false)
                .message(message)
                .build();
    }
}