/**
 * @file: TelegramWebAppAuthRequest.java
 * @description: DTO для запроса авторизации через Telegram WebApp
 * @dependencies: TelegramWebAppInitData
 * @created: 2025-01-23
 */
package com.baganov.pizzanat.model.dto.telegram;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class TelegramWebAppAuthRequest {
    
    @NotBlank(message = "initDataRaw не может быть пустым")
    private String initDataRaw;
    
    private String deviceId;
    private String userAgent;
    private String platform;
}
