package com.baganov.pizzanat.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/**
 * Конфигурация MAX ботов
 *
 * MAX - российский мессенджер, альтернатива Telegram
 * Документация: https://dev.max.ru/docs/webapps/introduction
 *
 * Боты:
 * - ДИМБО (пользовательский): https://max.ru/id121603899498_bot
 * - ДИМБО Админ (административный): https://max.ru/id121603899498_1_bot
 */
@Data
@Configuration
@ConfigurationProperties(prefix = "max.bot")
public class MaxBotConfig {

    /**
     * Токен пользовательского бота (ДИМБО)
     * Используется для:
     * - Валидации initData от MAX Mini App
     * - Авторизации пользователей
     */
    private String userBotToken;

    /**
     * Токен административного бота (ДИМБО Админ)
     * Используется для:
     * - Отправки уведомлений о новых заказах
     * - Уведомлений об оплатах
     * - Уведомлений об изменении статусов заказов
     */
    private String adminBotToken;

    /**
     * Username пользовательского бота
     */
    private String userBotUsername = "id121603899498_bot";

    /**
     * Username административного бота
     */
    private String adminBotUsername = "id121603899498_1_bot";

    /**
     * URL MAX API для отправки сообщений
     */
    private String apiUrl = "https://platform-api.max.ru";

    /**
     * Включена ли интеграция с MAX
     */
    private boolean enabled = true;

    /**
     * Включены ли административные уведомления
     */
    private boolean adminEnabled = true;

    /**
     * Chat ID для административных уведомлений
     * Можно указать конкретный чат или пользователя
     */
    private String adminChatId;

    /**
     * Webhook URL для Admin Bot
     * MAX API будет отправлять события на этот URL
     * Формат: https://your-domain.com/max-admin/
     */
    private String adminWebhookUrl;

    /**
     * Таймаут для API запросов (мс)
     */
    private int timeout = 30000;

    /**
     * Максимальное количество повторных попыток
     */
    private int maxRetries = 3;

    /**
     * Bot Properties для внутреннего использования
     */
    @Data
    public static class BotProperties {
        private String token;
        private String username;
        private boolean enabled;
    }

    /**
     * Получить свойства пользовательского бота
     */
    public BotProperties getUserBotProperties() {
        BotProperties props = new BotProperties();
        props.setToken(userBotToken);
        props.setUsername(userBotUsername);
        props.setEnabled(enabled);
        return props;
    }

    /**
     * Получить свойства административного бота
     */
    public BotProperties getAdminBotProperties() {
        BotProperties props = new BotProperties();
        props.setToken(adminBotToken);
        props.setUsername(adminBotUsername);
        props.setEnabled(adminEnabled);
        return props;
    }
}
