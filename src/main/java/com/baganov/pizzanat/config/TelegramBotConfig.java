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
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

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

    private static final int MAX_RETRIES = 10;
    private static final long RETRY_DELAY_SECONDS = 30;
    private volatile boolean mainBotRegistered = false;
    private volatile boolean adminBotRegistered = false;

    @PostConstruct
    public void registerBotsAsync() {
        if (!isTelegramEnabled()) {
            log.info("🚫 Telegram боты отключены в конфигурации");
            return;
        }

        log.info("🔄 Запуск асинхронной регистрации Telegram ботов (поток daemon)");

        ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "telegram-bot-registration");
            t.setDaemon(true);
            return t;
        });

        scheduler.scheduleWithFixedDelay(() -> {
            try {
                TelegramBotsApi botsApi = telegramBotsApi();

                if (isMainBotEnabled() && pizzaNatTelegramBot != null && !mainBotRegistered) {
                    botsApi.registerBot(pizzaNatTelegramBot);
                    mainBotRegistered = true;
                    log.info("✅ Основной Telegram бот успешно зарегистрирован: @{}",
                            pizzaNatTelegramBot.getBotUsername());
                }

                if (isAdminBotEnabled() && pizzaNatAdminBot != null && !adminBotRegistered) {
                    botsApi.registerBot(pizzaNatAdminBot);
                    adminBotRegistered = true;
                    log.info("✅ Админский Telegram бот успешно зарегистрирован: @{}",
                            pizzaNatAdminBot.getBotUsername());
                }

                // Все боты зарегистрированы — останавливаем retry
                if (mainBotRegistered && adminBotRegistered) {
                    scheduler.shutdown();
                    log.info("✅ Все Telegram боты зарегистрированы, retry планировщик остановлен");
                } else if (mainBotRegistered || adminBotRegistered) {
                    // Частичная регистрация — если один готов, ждём второй
                    log.info("⏳ Частичная регистрация: основной={}, админ={}", mainBotRegistered, adminBotRegistered);
                }

            } catch (TelegramApiException e) {
                log.warn("⚠️ Telegram API недоступен, повторная попытка через {} сек: {}", RETRY_DELAY_SECONDS, e.getMessage());
            } catch (Exception e) {
                log.warn("⚠️ Ошибка регистрации Telegram ботов, повторная попытка через {} сек: {}", RETRY_DELAY_SECONDS, e.getMessage());
            }
        }, 0, RETRY_DELAY_SECONDS, TimeUnit.SECONDS);

        // Ограничение максимального числа попыток
        scheduler.schedule(() -> {
            if (!scheduler.isShutdown()) {
                log.error("❌ Превышено время ожидания ({} попыток по {} сек) подключения к Telegram API. Боты не запущены.",
                        MAX_RETRIES, RETRY_DELAY_SECONDS);
                scheduler.shutdown();
            }
        }, (long) MAX_RETRIES * RETRY_DELAY_SECONDS, TimeUnit.SECONDS);
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