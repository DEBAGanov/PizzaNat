/**
 * @file: TelegramBotConfig.java
 * @description: Конфигурация для автоматической регистрации Telegram бота
 * @dependencies: TelegramBots API, Spring Boot
 * @created: 2025-01-11
 */
package com.baganov.pizzanat.config;

import com.baganov.pizzanat.service.PizzaNatTelegramBot;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.telegram.telegrambots.meta.TelegramBotsApi;
import org.telegram.telegrambots.meta.exceptions.TelegramApiException;
import org.telegram.telegrambots.updatesreceivers.DefaultBotSession;

@Slf4j
@Configuration
@ConditionalOnProperty(name = "telegram.longpolling.enabled", havingValue = "true", matchIfMissing = false)
public class TelegramBotConfig {

    @Bean
    public TelegramBotsApi telegramBotsApi(PizzaNatTelegramBot pizzaNatTelegramBot) {
        try {
            log.info("Инициализация Telegram Bots API для Long Polling...");
            TelegramBotsApi botsApi = new TelegramBotsApi(DefaultBotSession.class);
            botsApi.registerBot(pizzaNatTelegramBot);
            log.info("✅ Telegram бот успешно зарегистрирован для Long Polling");
            return botsApi;
        } catch (TelegramApiException e) {
            log.error("❌ Ошибка регистрации Telegram бота: {}", e.getMessage());
            throw new RuntimeException("Не удалось зарегистрировать Telegram бота", e);
        }
    }
}