/**
 * @file: TelegramRateLimitConfig.java
 * @description: Конфигурация лимитов отправки сообщений для Telegram ботов
 * @dependencies: Spring Configuration
 * @created: 2025-01-15
 */
package com.baganov.pizzanat.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Data
@Configuration
@ConfigurationProperties(prefix = "telegram.rate-limit")
public class TelegramRateLimitConfig {

    /**
     * Максимальное количество сообщений в секунду для ботов
     * Telegram официально поддерживает до 30 сообщений в секунду,
     * но для безопасности используем более консервативное значение
     */
    private int messagesPerSecond = 20;

    /**
     * Максимальное количество сообщений в минуту
     */
    private int messagesPerMinute = 1200; // 20 * 60

    /**
     * Задержка между отправкой сообщений в миллисекундах
     * Рассчитывается как 1000 / messagesPerSecond
     */
    private long delayBetweenMessages = 50; // 1000 / 20

    /**
     * Размер пакета для пакетной обработки
     */
    private int batchSize = 10;

    /**
     * Задержка между пакетами в миллисекундах
     */
    private long delayBetweenBatches = 1000; // 1 секунда

    /**
     * Максимальное количество попыток повторной отправки
     */
    private int maxRetryAttempts = 3;

    /**
     * Задержка перед повторной попыткой в миллисекундах
     */
    private long retryDelay = 2000; // 2 секунды

    /**
     * Максимальное количество пользователей для массовой рассылки за раз
     */
    private int maxBroadcastUsers = 1000;

    /**
     * Включить ли детальное логирование процесса рассылки
     */
    private boolean enableDetailedLogging = true;

    /**
     * Автоматически рассчитать задержку на основе количества сообщений в секунду
     */
    public void recalculateDelay() {
        if (messagesPerSecond > 0) {
            this.delayBetweenMessages = 1000L / messagesPerSecond;
        }
    }
}
