/**
 * @file: MetricsConfig.java
 * @description: Конфигурация метрик для мониторинга ЮKassa интеграции
 * @dependencies: Micrometer, Spring Boot Actuator
 * @created: 2025-01-26
 */
package com.baganov.pizzanat.config;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

import java.util.concurrent.atomic.AtomicLong;

/**
 * Конфигурация метрик для мониторинга платежной системы
 */
@Slf4j
@Configuration
@EnableScheduling
@RequiredArgsConstructor
public class MetricsConfig {

    private final MeterRegistry meterRegistry;

    // Счетчики для платежей
    private final AtomicLong totalPayments = new AtomicLong(0);
    private final AtomicLong successfulPayments = new AtomicLong(0);
    private final AtomicLong failedPayments = new AtomicLong(0);
    private final AtomicLong cancelledPayments = new AtomicLong(0);

    // Счетчики для webhook
    private final AtomicLong webhookReceived = new AtomicLong(0);
    private final AtomicLong webhookProcessed = new AtomicLong(0);
    private final AtomicLong webhookFailed = new AtomicLong(0);

    // Счетчики для СБП
    private final AtomicLong sbpPayments = new AtomicLong(0);
    private final AtomicLong cardPayments = new AtomicLong(0);

    /**
     * Счетчик общего количества платежей
     */
    @Bean
    public Counter paymentTotalCounter() {
        return Counter.builder("yookassa.payments.total")
                .description("Общее количество созданных платежей")
                .tag("service", "yookassa")
                .register(meterRegistry);
    }

    /**
     * Счетчик успешных платежей
     */
    @Bean
    public Counter paymentSuccessCounter() {
        return Counter.builder("yookassa.payments.success")
                .description("Количество успешных платежей")
                .tag("service", "yookassa")
                .register(meterRegistry);
    }

    /**
     * Счетчик неудачных платежей
     */
    @Bean
    public Counter paymentFailureCounter() {
        return Counter.builder("yookassa.payments.failure")
                .description("Количество неудачных платежей")
                .tag("service", "yookassa")
                .register(meterRegistry);
    }

    /**
     * Счетчик отмененных платежей
     */
    @Bean
    public Counter paymentCancelledCounter() {
        return Counter.builder("yookassa.payments.cancelled")
                .description("Количество отмененных платежей")
                .tag("service", "yookassa")
                .register(meterRegistry);
    }

    /**
     * Таймер времени создания платежа
     */
    @Bean
    public Timer paymentCreationTimer() {
        return Timer.builder("yookassa.payments.creation.time")
                .description("Время создания платежа в ЮKassa")
                .tag("service", "yookassa")
                .register(meterRegistry);
    }

    /**
     * Таймер времени обработки webhook
     */
    @Bean
    public Timer webhookProcessingTimer() {
        return Timer.builder("yookassa.webhook.processing.time")
                .description("Время обработки webhook уведомлений")
                .tag("service", "yookassa")
                .register(meterRegistry);
    }

    /**
     * Счетчик webhook уведомлений
     */
    @Bean
    public Counter webhookReceivedCounter() {
        return Counter.builder("yookassa.webhook.received")
                .description("Количество полученных webhook уведомлений")
                .tag("service", "yookassa")
                .register(meterRegistry);
    }

    /**
     * Счетчик обработанных webhook
     */
    @Bean
    public Counter webhookProcessedCounter() {
        return Counter.builder("yookassa.webhook.processed")
                .description("Количество успешно обработанных webhook")
                .tag("service", "yookassa")
                .register(meterRegistry);
    }

    /**
     * Счетчик ошибок webhook
     */
    @Bean
    public Counter webhookFailedCounter() {
        return Counter.builder("yookassa.webhook.failed")
                .description("Количество ошибок обработки webhook")
                .tag("service", "yookassa")
                .register(meterRegistry);
    }

    /**
     * Счетчик СБП платежей
     */
    @Bean
    public Counter sbpPaymentCounter() {
        return Counter.builder("yookassa.payments.sbp")
                .description("Количество платежей через СБП")
                .tag("method", "sbp")
                .register(meterRegistry);
    }

    /**
     * Счетчик карточных платежей
     */
    @Bean
    public Counter cardPaymentCounter() {
        return Counter.builder("yookassa.payments.card")
                .description("Количество карточных платежей")
                .tag("method", "card")
                .register(meterRegistry);
    }

    /**
     * Gauge для конверсии платежей
     */
    @Bean
    public Gauge paymentConversionRate() {
        return Gauge.builder("yookassa.payments.conversion.rate", this::calculateConversionRate)
                .description("Коэффициент конверсии платежей (успешные/общие)")
                .tag("service", "yookassa")
                .register(meterRegistry);
    }

    /**
     * Gauge для среднего чека
     */
    @Bean
    public Gauge averagePaymentAmount() {
        return Gauge.builder("yookassa.payments.average.amount", this::calculateAverageAmount)
                .description("Средний размер платежа")
                .tag("service", "yookassa")
                .register(meterRegistry);
    }

    /**
     * Расчет коэффициента конверсии
     */
    private double calculateConversionRate() {
        long total = totalPayments.get();
        if (total == 0)
            return 0.0;
        return (double) successfulPayments.get() / total * 100.0;
    }

    /**
     * Расчет среднего чека (заглушка, будет реализовано в сервисе)
     */
    private double calculateAverageAmount() {
        // Будет реализовано в PaymentMetricsService
        return 0.0;
    }

    // Методы для обновления счетчиков
    public void incrementTotalPayments() {
        totalPayments.incrementAndGet();
    }

    public void incrementSuccessfulPayments() {
        successfulPayments.incrementAndGet();
    }

    public void incrementFailedPayments() {
        failedPayments.incrementAndGet();
    }

    public void incrementCancelledPayments() {
        cancelledPayments.incrementAndGet();
    }

    public void incrementWebhookReceived() {
        webhookReceived.incrementAndGet();
    }

    public void incrementWebhookProcessed() {
        webhookProcessed.incrementAndGet();
    }

    public void incrementWebhookFailed() {
        webhookFailed.incrementAndGet();
    }

    public void incrementSbpPayments() {
        sbpPayments.incrementAndGet();
    }

    public void incrementCardPayments() {
        cardPayments.incrementAndGet();
    }
}