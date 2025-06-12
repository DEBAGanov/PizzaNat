package com.baganov.pizzanat.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestTemplate;

import java.time.Duration;

/**
 * Конфигурация для интеграции с Exolve SMS API.
 * Следует принципу Single Responsibility - только настройка клиента.
 */
@Configuration
public class ExolveConfig {

    @Value("${exolve.api.url:https://api.exolve.ru/messaging/v1/SendSMS}")
    private String exolveApiUrl;

    @Value("${exolve.api.key:}")
    private String exolveApiKey;

    @Value("${exolve.sender.name:PizzaNat}")
    private String exolveSenderName;

    @Value("${exolve.timeout.seconds:10}")
    private Integer exolveTimeoutSeconds;

    @Value("${exolve.retry.max-attempts:3}")
    private Integer maxRetryAttempts;

    @Value("${exolve.circuit-breaker.failure-threshold:5}")
    private Integer circuitBreakerFailureThreshold;

    /**
     * RestTemplate для вызовов Exolve API с настройками таймаутов
     */
    @Bean("exolveRestTemplate")
    public RestTemplate exolveRestTemplate(RestTemplateBuilder builder) {
        return builder
                .rootUri(exolveApiUrl)
                .connectTimeout(Duration.ofSeconds(exolveTimeoutSeconds))
                .readTimeout(Duration.ofSeconds(exolveTimeoutSeconds))
                .requestFactory(this::createRequestFactory)
                .build();
    }

    /**
     * Создание фабрики HTTP-запросов с расширенными настройками
     */
    private SimpleClientHttpRequestFactory createRequestFactory() {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(exolveTimeoutSeconds * 1000);
        factory.setReadTimeout(exolveTimeoutSeconds * 1000);
        return factory;
    }

    // === Геттеры для настроек ===

    public String getExolveApiUrl() {
        return exolveApiUrl;
    }

    public String getExolveApiKey() {
        return exolveApiKey;
    }

    public String getExolveSenderName() {
        return exolveSenderName;
    }

    public Integer getExolveTimeoutSeconds() {
        return exolveTimeoutSeconds;
    }

    public Integer getMaxRetryAttempts() {
        return maxRetryAttempts;
    }

    public Integer getCircuitBreakerFailureThreshold() {
        return circuitBreakerFailureThreshold;
    }

    /**
     * Проверка корректности настроек
     */
    public boolean isConfigured() {
        return exolveApiKey != null && !exolveApiKey.trim().isEmpty() &&
                exolveApiUrl != null && !exolveApiUrl.trim().isEmpty();
    }
}