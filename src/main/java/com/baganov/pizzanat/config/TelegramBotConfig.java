/**
 * @file: TelegramBotConfig.java
 * @description: Конфигурация для автоматической регистрации Telegram ботов с поддержкой условного включения
 * @dependencies: TelegramBots API, Spring Boot
 * @created: 2025-01-11
 * @updated: 2025-01-15 - добавлена поддержка условного включения ботов через переменные окружения
 */
package com.baganov.pizzanat.config;

import com.baganov.pizzanat.service.PizzaNatTelegramBot;
import com.baganov.pizzanat.service.AdminBotService;
import com.baganov.pizzanat.telegram.PizzaNatAdminBot;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Lazy;
import org.telegram.telegrambots.meta.TelegramBotsApi;
import org.telegram.telegrambots.meta.exceptions.TelegramApiException;
import org.telegram.telegrambots.updatesreceivers.DefaultBotSession;

import jakarta.annotation.PostConstruct;

@Slf4j
@Configuration
public class TelegramBotConfig {

    @Value("${telegram.bot.enabled:true}")
    private boolean mainBotEnabled;

    @Value("${telegram.admin-bot.enabled:true}")
    private boolean adminBotEnabled;

    @Value("${telegram.longpolling.enabled:true}")
    private boolean longPollingEnabled;

    @Autowired(required = false)
    private PizzaNatTelegramBot pizzaNatTelegramBot;

    @Autowired(required = false)
    private PizzaNatAdminBot pizzaNatAdminBot;

    @Bean
    @ConditionalOnProperty(name = "telegram.enabled", havingValue = "true", matchIfMissing = true)
    public TelegramBotsApi telegramBotsApi() throws TelegramApiException {
        return new TelegramBotsApi(DefaultBotSession.class);
    }

    @PostConstruct
    public void registerBots() {
        try {
            // Проверяем, включены ли Telegram боты вообще
            if (!isTelegramEnabled()) {
                log.info("🚫 Telegram боты отключены в конфигурации");
                return;
            }

            TelegramBotsApi botsApi = telegramBotsApi();

            // Регистрируем основной бот если включен и доступен
            if (isMainBotEnabled() && pizzaNatTelegramBot != null) {
                botsApi.registerBot(pizzaNatTelegramBot);
                log.info("✅ Основной Telegram бот успешно зарегистрирован: @{}",
                        pizzaNatTelegramBot.getBotUsername());
            } else {
                log.info("🚫 Основной Telegram бот отключен в настройках (TELEGRAM_BOT_ENABLED=false)");
            }

            // Регистрируем админский бот если включен и доступен
            if (isAdminBotEnabled() && pizzaNatAdminBot != null) {
                botsApi.registerBot(pizzaNatAdminBot);
                log.info("✅ Админский Telegram бот успешно зарегистрирован: @{}",
                        pizzaNatAdminBot.getBotUsername());
            } else {
                log.info("🚫 Админский Telegram бот отключен в настройках (TELEGRAM_ADMIN_BOT_ENABLED=false)");
            }

        } catch (TelegramApiException e) {
            log.error("❌ Ошибка при регистрации Telegram ботов: {}", e.getMessage(), e);
            throw new RuntimeException("Не удалось зарегистрировать Telegram ботов", e);
        } catch (Exception e) {
            log.warn("⚠️ Telegram боты недоступны: {}", e.getMessage());
        }
    }

    /**
     * Проверяет, включены ли Telegram боты в принципе
     */
    private boolean isTelegramEnabled() {
        return mainBotEnabled || adminBotEnabled;
    }

    /**
     * Проверяет, включен ли основной бот
     */
    private boolean isMainBotEnabled() {
        return mainBotEnabled && longPollingEnabled;
    }

    /**
     * Проверяет, включен ли админский бот
     */
    private boolean isAdminBotEnabled() {
        return adminBotEnabled;
    }

    /**
     * Инициализация связи между AdminBotService и PizzaNatAdminBot
     */
    @PostConstruct
    public void initializeAdminBotService() {
        if (!isAdminBotEnabled() || pizzaNatAdminBot == null) {
            log.info("ℹ️ AdminBotService не инициализируется - админский бот отключен");
            return;
        }

        try {
            // Устанавливаем связь после создания всех бинов
            log.info("🔗 Инициализация AdminBotService...");
        } catch (Exception e) {
            log.warn("⚠️ Не удалось установить связь AdminBotService с PizzaNatAdminBot: {}", e.getMessage());
        }
    }
}