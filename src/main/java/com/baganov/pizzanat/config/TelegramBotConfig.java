/**
 * @file: TelegramBotConfig.java
 * @description: Конфигурация для регистрации Telegram бота в Spring Boot
 * @dependencies: TelegramBots API, Spring Boot
 * @created: 2025-01-11
 */
package com.baganov.pizzanat.config;

import com.baganov.pizzanat.service.PizzaNatTelegramBot;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.telegram.telegrambots.meta.TelegramBotsApi;
import org.telegram.telegrambots.meta.exceptions.TelegramApiException;
import org.telegram.telegrambots.updatesreceivers.DefaultBotSession;

@Slf4j
@Configuration
@RequiredArgsConstructor
@ConditionalOnProperty(name = "telegram.enabled", havingValue = "true", matchIfMissing = true)
public class TelegramBotConfig {

    private final PizzaNatTelegramBot pizzaNatTelegramBot;

    /**
     * Регистрация Telegram бота
     */
    @Bean
    public TelegramBotsApi telegramBotsApi() throws TelegramApiException {
        log.info("Инициализация Telegram Bots API");

        TelegramBotsApi botsApi = new TelegramBotsApi(DefaultBotSession.class);

        try {
            botsApi.registerBot(pizzaNatTelegramBot);
            log.info("PizzaNat Telegram Bot успешно зарегистрирован");
        } catch (TelegramApiException e) {
            log.error("Ошибка при регистрации Telegram бота: {}", e.getMessage(), e);
            throw e;
        }

        return botsApi;
    }
}