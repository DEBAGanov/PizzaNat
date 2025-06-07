package com.baganov.pizzanat.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/**
 * Конфигурационные свойства для Telegram Gateway API
 * Согласно документации: https://core.telegram.org/gateway/api
 */
@Data
@Configuration
@ConfigurationProperties(prefix = "telegram.gateway")
public class TelegramGatewayProperties {

    /**
     * Включить/выключить Telegram Gateway API
     */
    private boolean enabled = false;

    /**
     * Access token от Telegram Gateway (получается в настройках аккаунта)
     */
    private String accessToken;

    /**
     * TTL сообщения в секундах (30-3600)
     * Если сообщение не доставлено в течение TTL, плата возвращается
     */
    private int messageTtl = 300; // 5 минут

    /**
     * URL для получения отчетов о доставке (callback)
     */
    private String callbackUrl;

    /**
     * Таймаут для HTTP запросов в секундах
     */
    private int timeoutSeconds = 10;

    /**
     * Максимальное количество попыток повтора запроса
     */
    private int maxRetryAttempts = 3;

    /**
     * Проверка валидности конфигурации
     */
    public boolean isValid() {
        return enabled &&
                accessToken != null &&
                !accessToken.trim().isEmpty() &&
                messageTtl >= 30 &&
                messageTtl <= 3600;
    }
}