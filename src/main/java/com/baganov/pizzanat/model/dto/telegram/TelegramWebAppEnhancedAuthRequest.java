/**
 * @file: TelegramWebAppEnhancedAuthRequest.java
 * @description: DTO для расширенной авторизации через Telegram WebApp с номером телефона
 * @dependencies: javax.validation
 * @created: 2025-01-27
 */
package com.baganov.pizzanat.model.dto.telegram;

import lombok.Data;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import io.swagger.v3.oas.annotations.media.Schema;

@Data
@Schema(description = "Запрос расширенной авторизации через Telegram WebApp с номером телефона")
public class TelegramWebAppEnhancedAuthRequest {

    @NotBlank(message = "initDataRaw не может быть пустым")
    @Schema(description = "Сырые данные initData от Telegram WebApp", 
            example = "auth_date=1234567890&hash=abcdef&user=%7B%22id%22%3A123456%7D")
    private String initDataRaw;

    @Pattern(regexp = "^\\+7\\d{10}$", message = "Номер телефона должен быть в формате +7XXXXXXXXXX")
    @Schema(description = "Номер телефона пользователя", 
            example = "+79161234567")
    private String phoneNumber;

    @Schema(description = "Идентификатор устройства для кросс-платформенной авторизации", 
            example = "device_12345")
    private String deviceId;

    @Schema(description = "Платформа клиента", 
            example = "telegram-miniapp")
    private String platform = "telegram-miniapp";
}
