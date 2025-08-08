/**
 * @file: TelegramConfig.java
 * @description: Конфигурация для интеграции с Telegram Bot API
 * @dependencies: Spring Boot, Spring Web
 * @created: 2025-05-31
 */
package com.baganov.pizzanat.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestTemplate;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;

import java.time.Duration;

@Configuration
@EnableConfigurationProperties({
        TelegramConfig.TelegramProperties.class,
        TelegramConfig.TelegramAuthProperties.class,
        TelegramConfig.TelegramBotProperties.class
})
public class TelegramConfig {

    /**
     * RestTemplate для основных Telegram операций (уведомления)
     */
    @Bean("telegramRestTemplate")
    public RestTemplate telegramRestTemplate(RestTemplateBuilder builder) {
        return builder
                .connectTimeout(Duration.ofSeconds(10))
                .readTimeout(Duration.ofSeconds(10))
                .build();
    }

    /**
     * RestTemplate для Telegram аутентификации
     */
    @Bean("telegramAuthRestTemplate")
    public RestTemplate telegramAuthRestTemplate(RestTemplateBuilder builder) {
        return builder
                .connectTimeout(Duration.ofSeconds(15))
                .readTimeout(Duration.ofSeconds(15))
                .build();
    }

    /**
     * RestTemplate для Telegram Gateway API
     */
    @Bean("gatewayRestTemplate")
    @ConditionalOnProperty(value = "telegram.gateway.enabled", havingValue = "true")
    public RestTemplate gatewayRestTemplate(RestTemplateBuilder builder, TelegramGatewayProperties gatewayProperties) {
        return builder
                .connectTimeout(Duration.ofSeconds(gatewayProperties.getTimeoutSeconds()))
                .readTimeout(Duration.ofSeconds(gatewayProperties.getTimeoutSeconds()))
                .build();
    }

    /**
     * Конфигурация для основного бота (уведомления)
     */
    @Data
    @ConfigurationProperties(prefix = "telegram")
    public static class TelegramProperties {

        private boolean enabled = false;
        private String botToken;
        private String chatId;
        private String apiUrl = "https://api.telegram.org/bot";

        public String getApiUrl() {
            return apiUrl + botToken;
        }
    }

    /**
     * Конфигурация для аутентификации через Telegram
     */
    @Data
    @ConfigurationProperties(prefix = "telegram.auth")
    public static class TelegramAuthProperties {

        private boolean enabled = false;
        private String botToken;
        private String botUsername = "pizzanat_auth_bot";
        private String webhookUrl;
        private boolean webhookEnabled = false;
        private Integer tokenTtlMinutes = 10;
        private Integer rateLimitPerHour = 5;
        private String apiUrl = "https://api.telegram.org/bot";

        public String getApiUrl() {
            return apiUrl + botToken;
        }

        /**
         * Получить URL для старта аутентификации
         */
        public String getStartAuthUrl(String authToken) {
            return String.format("https://t.me/%s?start=%s", botUsername, authToken);
        }

        /**
         * Получить URL webhook (геттер для webhookUrl)
         */
        public String getWebhookUrl() {
            return webhookUrl;
        }

        /**
         * Проверить валидность конфигурации
         */
        public boolean isValid() {
            return enabled &&
                    botToken != null && !botToken.trim().isEmpty() &&
                    botUsername != null && !botUsername.trim().isEmpty();
        }

        /**
         * Проверить, настроен ли webhook
         */
        public boolean isWebhookConfigured() {
            return webhookEnabled &&
                    webhookUrl != null &&
                    !webhookUrl.trim().isEmpty();
        }

        /**
         * Проверить, включен ли Long Polling (для замещения webhook)
         */
        public boolean isLongPollingEnabled() {
            // Проверяем через системные свойства, так как Long Polling настраивается
            // отдельно
            String botEnabled = System.getProperty("telegram.bot.enabled", System.getenv("TELEGRAM_BOT_ENABLED"));
            String longPollingEnabled = System.getProperty("telegram.longpolling.enabled",
                    System.getenv("TELEGRAM_LONGPOLLING_ENABLED"));

            return "true".equalsIgnoreCase(botEnabled) && "true".equalsIgnoreCase(longPollingEnabled);
        }
    }

    /**
     * Конфигурация для Long Polling бота (отдельно от webhook auth)
     */
    @Data
    @ConfigurationProperties(prefix = "telegram.bot")
    public static class TelegramBotProperties {

        private boolean enabled = false;
        private String token;
        private String username;
        private String apiUrl = "https://api.telegram.org/bot";

        public String getApiUrl() {
            return apiUrl + token;
        }

        public String getBotToken() {
            return token;
        }

        public String getBotUsername() {
            return username;
        }

        /**
         * Получить URL для старта аутентификации
         */
        public String getStartAuthUrl(String authToken) {
            return String.format("https://t.me/%s?start=%s", username, authToken);
        }

        /**
         * Проверить валидность конфигурации
         */
        public boolean isValid() {
            return enabled &&
                    token != null && !token.trim().isEmpty() &&
                    username != null && !username.trim().isEmpty();
        }
    }
}