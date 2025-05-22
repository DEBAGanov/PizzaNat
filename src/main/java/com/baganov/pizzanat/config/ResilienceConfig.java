/**
 * @file: ResilienceConfig.java
 * @description: Конфигурация Resilience4j для отказоустойчивости системы
 * @dependencies: resilience4j
 * @created: 2023-11-01
 */
package com.baganov.pizzanat.config;

import io.github.resilience4j.circuitbreaker.CircuitBreakerConfig;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import io.github.resilience4j.retry.RetryConfig;
import io.github.resilience4j.retry.RetryRegistry;
import io.github.resilience4j.timelimiter.TimeLimiterConfig;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Duration;
import java.util.concurrent.TimeoutException;

@Configuration
public class ResilienceConfig {

        /**
         * Настройка реестра Circuit Breaker с конфигурацией по умолчанию
         *
         * @return CircuitBreakerRegistry с настроенными параметрами
         */
        @Bean
        public CircuitBreakerRegistry circuitBreakerRegistry() {
                CircuitBreakerConfig circuitBreakerConfig = CircuitBreakerConfig.custom()
                                .failureRateThreshold(50) // Порог процента неудачных запросов, после которого CB
                                                          // переходит в состояние
                                                          // OPEN
                                .waitDurationInOpenState(Duration.ofSeconds(10)) // Время ожидания в состоянии OPEN до
                                                                                 // перехода в
                                                                                 // HALF_OPEN
                                .minimumNumberOfCalls(5) // Минимальное количество вызовов для расчета процента ошибок
                                .slidingWindowSize(10) // Размер скользящего окна для сбора статистики
                                .permittedNumberOfCallsInHalfOpenState(3) // Допустимое количество вызовов в состоянии
                                                                          // HALF_OPEN
                                .automaticTransitionFromOpenToHalfOpenEnabled(true) // Автоматический переход из OPEN в
                                                                                    // HALF_OPEN
                                .recordExceptions( // Типы исключений, которые будут считаться ошибками
                                                Exception.class,
                                                TimeoutException.class)
                                .build();

                return CircuitBreakerRegistry.of(circuitBreakerConfig);
        }

        /**
         * Настройка реестра Retry с конфигурацией по умолчанию
         *
         * @return RetryRegistry с настроенными параметрами
         */
        @Bean
        public RetryRegistry retryRegistry() {
                RetryConfig retryConfig = RetryConfig.custom()
                                .maxAttempts(3) // Максимальное количество попыток
                                .waitDuration(Duration.ofMillis(500)) // Время ожидания между попытками
                                .retryExceptions( // Типы исключений, при которых будут выполняться повторные попытки
                                                Exception.class,
                                                TimeoutException.class)
                                .build();

                return RetryRegistry.of(retryConfig);
        }

        /**
         * Настройка TimeLimiter для ограничения времени выполнения операций
         *
         * @return TimeLimiterConfig с настроенными параметрами
         */
        @Bean
        public TimeLimiterConfig timeLimiterConfig() {
                return TimeLimiterConfig.custom()
                                .timeoutDuration(Duration.ofSeconds(5)) // Максимальное время выполнения операции
                                .cancelRunningFuture(true) // Отмена операции, если превышен таймаут
                                .build();
        }

        /**
         * Специальная конфигурация Circuit Breaker для платежного сервиса
         *
         * @return CircuitBreakerConfig с настройками для платежной системы
         */
        @Bean
        public CircuitBreakerConfig paymentServiceCircuitBreakerConfig() {
                return CircuitBreakerConfig.custom()
                                .failureRateThreshold(30) // Более низкий порог для критичных операций
                                .waitDurationInOpenState(Duration.ofSeconds(20)) // Более длительное ожидание перед
                                                                                 // попытками
                                .minimumNumberOfCalls(3) // Меньше вызовов для быстрой реакции
                                .slidingWindowSize(10)
                                .permittedNumberOfCallsInHalfOpenState(2)
                                .automaticTransitionFromOpenToHalfOpenEnabled(true)
                                .recordExceptions(
                                                Exception.class,
                                                TimeoutException.class)
                                .build();
        }

        /**
         * Специальная конфигурация Retry для платежного сервиса
         *
         * @return RetryConfig с настройками для платежной системы
         */
        @Bean
        public RetryConfig paymentServiceRetryConfig() {
                return RetryConfig.custom()
                                .maxAttempts(5) // Больше попыток для платежных операций
                                .waitDuration(Duration.ofSeconds(2)) // Более длительное ожидание между попытками
                                .retryExceptions(
                                                Exception.class,
                                                TimeoutException.class)
                                .build();
        }
}