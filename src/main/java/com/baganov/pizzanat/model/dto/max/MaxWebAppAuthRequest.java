package com.baganov.pizzanat.model.dto.max;

import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Request DTO для авторизации через MAX WebApp
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MaxWebAppAuthRequest {

    /**
     * Сырая строка initData от MAX WebApp
     * Формат: user=%7B...%7D&auth_date=xxx&hash=yyy
     */
    @NotBlank(message = "initDataRaw is required")
    private String initDataRaw;

    /**
     * Идентификатор устройства
     * Используется для отслеживания сессий
     */
    private String deviceId;

    /**
     * Платформа (max-miniapp, max-web и т.д.)
     */
    private String platform;

    /**
     * User Agent браузера
     */
    private String userAgent;

    /**
     * Номер телефона (опционально)
     * Может быть получен через WebApp.requestPhone()
     */
    private String phoneNumber;
}
