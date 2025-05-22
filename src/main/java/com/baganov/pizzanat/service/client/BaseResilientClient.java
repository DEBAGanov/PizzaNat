/**
 * @file: BaseResilientClient.java
 * @description: Базовый класс для клиентов с поддержкой отказоустойчивости
 * @dependencies: resilience4j
 * @created: 2023-11-01
 */
package com.baganov.pizzanat.service.client;

import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import io.github.resilience4j.retry.Retry;
import io.github.resilience4j.retry.RetryRegistry;
import lombok.extern.slf4j.Slf4j;

import java.util.function.Supplier;

/**
 * Базовый абстрактный класс для клиентов внешних сервисов
 * с поддержкой Circuit Breaker и Retry паттернов
 */
@Slf4j
public abstract class BaseResilientClient {

    private final CircuitBreakerRegistry circuitBreakerRegistry;
    private final RetryRegistry retryRegistry;

    protected BaseResilientClient(CircuitBreakerRegistry circuitBreakerRegistry, RetryRegistry retryRegistry) {
        this.circuitBreakerRegistry = circuitBreakerRegistry;
        this.retryRegistry = retryRegistry;
    }

    /**
     * Выполнение операции с применением Circuit Breaker и Retry
     * 
     * @param <T>                тип возвращаемого значения
     * @param operation          операция для выполнения
     * @param circuitBreakerName имя Circuit Breaker
     * @param retryName          имя Retry
     * @param fallback           резервное значение в случае ошибки
     * @return результат операции или резервное значение
     */
    protected <T> T executeWithResiliencePatterns(
            Supplier<T> operation,
            String circuitBreakerName,
            String retryName,
            T fallback) {
        CircuitBreaker circuitBreaker = circuitBreakerRegistry.circuitBreaker(circuitBreakerName);
        Retry retry = retryRegistry.retry(retryName);

        // Оборачиваем операцию в Retry
        Supplier<T> retryableOperation = Retry.decorateSupplier(retry, operation);

        // Дополнительно оборачиваем в CircuitBreaker
        Supplier<T> resilientOperation = CircuitBreaker.decorateSupplier(circuitBreaker, retryableOperation);

        try {
            return resilientOperation.get();
        } catch (Exception e) {
            log.error("Ошибка при выполнении операции с использованием Resilience patterns. " +
                    "CircuitBreaker: {}, Retry: {}, Ошибка: {}",
                    circuitBreakerName, retryName, e.getMessage(), e);

            return fallback;
        }
    }

    /**
     * Выполнение операции с применением Circuit Breaker и Retry без резервного
     * значения
     * 
     * @param <T>                тип возвращаемого значения
     * @param operation          операция для выполнения
     * @param circuitBreakerName имя Circuit Breaker
     * @param retryName          имя Retry
     * @return результат операции
     * @throws Exception если операция завершилась с ошибкой
     */
    protected <T> T executeWithResiliencePatternsWithoutFallback(
            Supplier<T> operation,
            String circuitBreakerName,
            String retryName) throws Exception {
        CircuitBreaker circuitBreaker = circuitBreakerRegistry.circuitBreaker(circuitBreakerName);
        Retry retry = retryRegistry.retry(retryName);

        // Оборачиваем операцию в Retry
        Supplier<T> retryableOperation = Retry.decorateSupplier(retry, operation);

        // Дополнительно оборачиваем в CircuitBreaker
        Supplier<T> resilientOperation = CircuitBreaker.decorateSupplier(circuitBreaker, retryableOperation);

        try {
            return resilientOperation.get();
        } catch (Exception e) {
            log.error("Ошибка при выполнении операции с использованием Resilience patterns. " +
                    "CircuitBreaker: {}, Retry: {}, Ошибка: {}",
                    circuitBreakerName, retryName, e.getMessage(), e);

            throw e;
        }
    }

    /**
     * Выполнение void-операции с применением Circuit Breaker и Retry
     * 
     * @param operation          операция для выполнения
     * @param circuitBreakerName имя Circuit Breaker
     * @param retryName          имя Retry
     */
    protected void executeVoidWithResiliencePatterns(
            Runnable operation,
            String circuitBreakerName,
            String retryName) {
        CircuitBreaker circuitBreaker = circuitBreakerRegistry.circuitBreaker(circuitBreakerName);
        Retry retry = retryRegistry.retry(retryName);

        // Оборачиваем операцию в Retry
        Runnable retryableOperation = Retry.decorateRunnable(retry, operation);

        // Дополнительно оборачиваем в CircuitBreaker
        Runnable resilientOperation = CircuitBreaker.decorateRunnable(circuitBreaker, retryableOperation);

        try {
            resilientOperation.run();
        } catch (Exception e) {
            log.error("Ошибка при выполнении void-операции с использованием Resilience patterns. " +
                    "CircuitBreaker: {}, Retry: {}, Ошибка: {}",
                    circuitBreakerName, retryName, e.getMessage(), e);
        }
    }
}