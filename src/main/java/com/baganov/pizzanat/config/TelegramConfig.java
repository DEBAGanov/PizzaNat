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
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestTemplate;

@Configuration
@EnableConfigurationProperties(TelegramConfig.TelegramProperties.class)
public class TelegramConfig {

    @Bean
    public RestTemplate telegramRestTemplate() {
        return new RestTemplate();
    }

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
}