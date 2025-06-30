/**
 * @file: YooKassaConfig.java
 * @description: Конфигурация для интеграции с ЮKassa API
 * @dependencies: WebClient, Spring Boot Configuration
 * @created: 2025-01-23
 */
package com.baganov.pizzanat.config;

import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.web.reactive.function.client.WebClient;

import java.time.Duration;
import java.util.Base64;

/**
 * Конфигурация для работы с ЮKassa API
 * Активируется только при установке свойства yookassa.enabled=true
 */
@Configuration
@ConfigurationProperties(prefix = "yookassa")
@ConditionalOnProperty(name = "yookassa.enabled", havingValue = "true")
@Data
@Slf4j
public class YooKassaConfig {

    /**
     * Включение/отключение ЮKassa интеграции
     */
    private boolean enabled = false;

    /**
     * Идентификатор магазина в ЮKassa
     */
    private String shopId;

    /**
     * Секретный ключ для API
     */
    private String secretKey;

    /**
     * URL ЮKassa API
     */
    private String apiUrl = "https://api.yookassa.ru/v3";

    /**
     * URL для webhook уведомлений
     */
    private String webhookUrl;

    /**
     * Таймаут для HTTP запросов в секундах
     */
    private int timeoutSeconds = 30;

    /**
     * Максимальное количество попыток повтора запроса
     */
    private int maxRetryAttempts = 3;

    /**
     * Создает WebClient для работы с ЮKassa API
     * Настраивает базовую аутентификацию и заголовки
     */
    @Bean
    @ConditionalOnProperty(name = "yookassa.enabled", havingValue = "true")
    public WebClient yooKassaWebClient() {
        if (shopId == null || secretKey == null) {
            throw new IllegalStateException("ЮKassa shop ID и secret key должны быть настроены");
        }

        // Создаем базовую аутентификацию
        String credentials = Base64.getEncoder()
                .encodeToString((shopId + ":" + secretKey).getBytes());

        log.info("Инициализация ЮKassa WebClient. API URL: {}, Shop ID: {}", apiUrl, shopId);

        return WebClient.builder()
                .baseUrl(apiUrl)
                .defaultHeader(HttpHeaders.AUTHORIZATION, "Basic " + credentials)
                .defaultHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                .defaultHeader(HttpHeaders.ACCEPT, MediaType.APPLICATION_JSON_VALUE)
                .defaultHeader("User-Agent", "PizzaNat/1.0")
                .codecs(configurer -> configurer.defaultCodecs().maxInMemorySize(1024 * 1024)) // 1MB
                .build();
    }

    /**
     * Возвращает таймаут как Duration для использования в reactive операциях
     */
    public Duration getTimeout() {
        return Duration.ofSeconds(timeoutSeconds);
    }

    /**
     * Проверяет корректность конфигурации
     */
    public boolean isConfigurationValid() {
        return enabled && shopId != null && !shopId.isEmpty()
                && secretKey != null && !secretKey.isEmpty()
                && apiUrl != null && !apiUrl.isEmpty();
    }

    /**
     * Возвращает маскированный shop ID для логирования
     */
    public String getMaskedShopId() {
        if (shopId == null || shopId.length() <= 4) {
            return "****";
        }
        return shopId.substring(0, 4) + "****";
    }
}