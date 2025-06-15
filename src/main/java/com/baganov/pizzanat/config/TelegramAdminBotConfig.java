/**
 * @file: TelegramAdminBotConfig.java
 * @description: Конфигурация админского Telegram бота для уведомлений о заказах
 * @dependencies: TelegramConfig, Spring Boot Configuration
 * @created: 2025-06-13
 */
package com.baganov.pizzanat.config;

import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Slf4j
@Data
@Configuration
@ConfigurationProperties(prefix = "telegram.admin-bot")
public class TelegramAdminBotConfig {

    /**
     * Токен админского бота
     */
    private String token;

    /**
     * Имя админского бота
     */
    private String username;

    /**
     * Включен ли админский бот
     */
    private boolean enabled = true;

    /**
     * Максимальное количество попыток отправки сообщения
     */
    private int maxRetries = 3;

    /**
     * Таймаут для HTTP запросов (в секундах)
     */
    private int timeoutSeconds = 30;

    /**
     * Проверка корректности конфигурации
     */
    public boolean isValid() {
        if (!enabled) {
            log.info("Админский Telegram бот отключен в конфигурации");
            return false;
        }

        if (token == null || token.trim().isEmpty()) {
            log.error("Не указан токен для админского Telegram бота");
            return false;
        }

        if (username == null || username.trim().isEmpty()) {
            log.warn("Не указано имя для админского Telegram бота, будет использовано значение по умолчанию");
            username = "PizzaNatOrders_bot";
        }

        log.info("Конфигурация админского Telegram бота корректна: {}", username);
        return true;
    }

    /**
     * Получить очищенный токен
     */
    public String getCleanToken() {
        return token != null ? token.trim() : null;
    }

    /**
     * Получить очищенное имя пользователя
     */
    public String getCleanUsername() {
        return username != null ? username.trim() : "PizzaNatOrders_bot";
    }
}