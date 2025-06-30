/**
 * @file: PaymentMetricsService.java
 * @description: Сервис для сбора и анализа метрик платежной системы ЮKassa
 * @dependencies: MetricsConfig, PaymentRepository, MeterRegistry
 * @created: 2025-01-26
 */
package com.baganov.pizzanat.service;

import com.baganov.pizzanat.config.MetricsConfig;
import com.baganov.pizzanat.entity.Payment;
import com.baganov.pizzanat.entity.PaymentMethod;
import com.baganov.pizzanat.entity.PaymentStatus;
import com.baganov.pizzanat.repository.PaymentRepository;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Сервис для мониторинга и аналитики платежной системы
 */
@Slf4j
@Service
@ConditionalOnProperty(name = "yookassa.enabled", havingValue = "true")
public class PaymentMetricsService {

    private final PaymentRepository paymentRepository;
    private final MetricsConfig metricsConfig;
    private final MeterRegistry meterRegistry;

    // Метрики - создаются в конструкторе
    private final Counter paymentTotalCounter;
    private final Counter paymentSuccessCounter;
    private final Counter paymentFailureCounter;
    private final Counter paymentCancelledCounter;
    private final Counter sbpPaymentCounter;
    private final Counter cardPaymentCounter;
    private final Timer paymentCreationTimer;

    public PaymentMetricsService(PaymentRepository paymentRepository,
                               MetricsConfig metricsConfig,
                               MeterRegistry meterRegistry) {
        this.paymentRepository = paymentRepository;
        this.metricsConfig = metricsConfig;
        this.meterRegistry = meterRegistry;

        // Создаем метрики
        this.paymentTotalCounter = Counter.builder("yookassa.payments.total")
                .description("Общее количество созданных платежей")
                .tag("service", "yookassa")
                .register(meterRegistry);

        this.paymentSuccessCounter = Counter.builder("yookassa.payments.success")
                .description("Количество успешных платежей")
                .tag("service", "yookassa")
                .register(meterRegistry);

        this.paymentFailureCounter = Counter.builder("yookassa.payments.failure")
                .description("Количество неудачных платежей")
                .tag("service", "yookassa")
                .register(meterRegistry);

        this.paymentCancelledCounter = Counter.builder("yookassa.payments.cancelled")
                .description("Количество отмененных платежей")
                .tag("service", "yookassa")
                .register(meterRegistry);

        this.sbpPaymentCounter = Counter.builder("yookassa.payments.sbp")
                .description("Количество платежей через СБП")
                .tag("method", "sbp")
                .register(meterRegistry);

        this.cardPaymentCounter = Counter.builder("yookassa.payments.card")
                .description("Количество карточных платежей")
                .tag("method", "card")
                .register(meterRegistry);

        this.paymentCreationTimer = Timer.builder("yookassa.payments.creation.time")
                .description("Время создания платежа в ЮKassa")
                .tag("service", "yookassa")
                .register(meterRegistry);
    }

    /**
     * Обновление метрик при создании платежа
     */
    public void recordPaymentCreated(Payment payment) {
        log.debug("📊 Записываю метрику создания платежа: ID={}, метод={}, сумма={}",
                payment.getId(), payment.getMethod(), payment.getAmount());

        // Увеличиваем общий счетчик
        paymentTotalCounter.increment();
        metricsConfig.incrementTotalPayments();

        // Учитываем метод платежа
        if (payment.getMethod() == PaymentMethod.SBP) {
            sbpPaymentCounter.increment();
            metricsConfig.incrementSbpPayments();
        } else if (payment.getMethod() == PaymentMethod.BANK_CARD) {
            cardPaymentCounter.increment();
            metricsConfig.incrementCardPayments();
        }

        // Записываем теги для детального анализа
        meterRegistry.counter("yookassa.payments.by.amount.range",
                "range", getAmountRange(payment.getAmount()))
                .increment();
    }

    /**
     * Обновление метрик при изменении статуса платежа
     */
    public void recordPaymentStatusChange(Payment payment, PaymentStatus oldStatus) {
        log.debug("📊 Записываю изменение статуса платежа: ID={}, {} → {}",
                payment.getId(), oldStatus, payment.getStatus());

        PaymentStatus newStatus = payment.getStatus();

        // Увеличиваем соответствующие счетчики
        switch (newStatus) {
            case SUCCEEDED -> {
                paymentSuccessCounter.increment();
                metricsConfig.incrementSuccessfulPayments();
                recordSuccessfulPaymentMetrics(payment);
            }
            case FAILED -> {
                paymentFailureCounter.increment();
                metricsConfig.incrementFailedPayments();
                recordFailedPaymentMetrics(payment, oldStatus);
            }
            case CANCELLED -> {
                paymentCancelledCounter.increment();
                metricsConfig.incrementCancelledPayments();
            }
        }

        // Записываем время до завершения платежа
        if (newStatus.isCompleted() && payment.getCreatedAt() != null) {
            recordPaymentDuration(payment);
        }
    }

    /**
     * Измерение времени выполнения операции создания платежа
     */
    public <T> T recordPaymentCreationTime(Timer.Sample sample, T result) {
        sample.stop(paymentCreationTimer);
        return result;
    }

    /**
     * Начало измерения времени создания платежа
     */
    public Timer.Sample startPaymentCreationTimer() {
        return Timer.start(meterRegistry);
    }

    /**
     * Периодическое обновление агрегированных метрик
     */
    @Scheduled(fixedRate = 60000) // Каждую минуту
    public void updateAggregatedMetrics() {
        try {
            log.debug("📊 Начало обновления агрегированных метрик");
            updateHourlyMetrics();
            updateDailyMetrics();
            updateConversionMetrics();
            log.debug("📊 Агрегированные метрики обновлены успешно");
        } catch (Exception e) {
            log.error("❌ Ошибка обновления агрегированных метрик: {}", e.getMessage(), e);
        }
    }

    /**
     * Обновление почасовых метрик
     */
    private void updateHourlyMetrics() {
        try {
            LocalDateTime hourAgo = LocalDateTime.now().minusHours(1);
            List<Payment> recentPayments = paymentRepository.findByCreatedAtBetweenOrderByCreatedAtDesc(hourAgo,
                    LocalDateTime.now());

            long totalLastHour = recentPayments.size();
            long successfulLastHour = recentPayments.stream()
                    .mapToLong(p -> p.getStatus() == PaymentStatus.SUCCEEDED ? 1 : 0)
                    .sum();

            meterRegistry.gauge("yookassa.payments.last.hour.total", totalLastHour);
            meterRegistry.gauge("yookassa.payments.last.hour.successful", successfulLastHour);

            if (totalLastHour > 0) {
                double conversionLastHour = (double) successfulLastHour / totalLastHour * 100.0;
                meterRegistry.gauge("yookassa.payments.last.hour.conversion", conversionLastHour);
            }

            log.debug("📊 Обновлены почасовые метрики: всего={}, успешных={}", totalLastHour, successfulLastHour);
        } catch (Exception e) {
            log.error("❌ Ошибка обновления почасовых метрик: {}", e.getMessage(), e);
        }
    }

    /**
     * Обновление дневных метрик
     */
    private void updateDailyMetrics() {
        try {
            LocalDateTime dayAgo = LocalDateTime.now().minusDays(1);
            List<Payment> dailyPayments = paymentRepository.findByCreatedAtBetweenOrderByCreatedAtDesc(dayAgo,
                    LocalDateTime.now());

            // Группировка по статусам
            Map<PaymentStatus, Long> statusCounts = dailyPayments.stream()
                    .collect(Collectors.groupingBy(Payment::getStatus, Collectors.counting()));

            // Группировка по методам
            Map<PaymentMethod, Long> methodCounts = dailyPayments.stream()
                    .collect(Collectors.groupingBy(Payment::getMethod, Collectors.counting()));

            // Записываем метрики по статусам
            statusCounts.forEach((status, count) -> {
                try {
                    meterRegistry.gauge("yookassa.payments.daily.by.status." + status.name().toLowerCase(), count);
                } catch (Exception e) {
                    log.warn("Ошибка записи метрики статуса {}: {}", status, e.getMessage());
                }
            });

            // Записываем метрики по методам
            methodCounts.forEach((method, count) -> {
                try {
                    meterRegistry.gauge("yookassa.payments.daily.by.method." + method.name().toLowerCase(), count);
                } catch (Exception e) {
                    log.warn("Ошибка записи метрики метода {}: {}", method, e.getMessage());
                }
            });

            // Средний чек за день
            long successfulCount = statusCounts.getOrDefault(PaymentStatus.SUCCEEDED, 0L);
            if (successfulCount > 0) {
                BigDecimal averageAmount = dailyPayments.stream()
                        .filter(p -> p.getStatus() == PaymentStatus.SUCCEEDED)
                        .map(Payment::getAmount)
                        .reduce(BigDecimal.ZERO, BigDecimal::add)
                        .divide(BigDecimal.valueOf(successfulCount), 2, java.math.RoundingMode.HALF_UP);

                meterRegistry.gauge("yookassa.payments.daily.average.amount", averageAmount.doubleValue());

                log.debug("📊 Обновлены дневные метрики: платежей={}, средний чек={}₽",
                        dailyPayments.size(), averageAmount);
            }
        } catch (Exception e) {
            log.error("❌ Ошибка обновления дневных метрик: {}", e.getMessage(), e);
        }
    }

    /**
     * Обновление метрик конверсии
     */
    private void updateConversionMetrics() {
        try {
            // Конверсия по времени дня
            LocalDateTime now = LocalDateTime.now();
            for (int hour = 0; hour < 24; hour++) {
                try {
                    LocalDateTime startHour = now.withHour(hour).withMinute(0).withSecond(0);
                    LocalDateTime endHour = startHour.plusHours(1);

                    List<Payment> hourlyPayments = paymentRepository.findByCreatedAtBetweenOrderByCreatedAtDesc(startHour,
                            endHour);
                    if (!hourlyPayments.isEmpty()) {
                        long successful = hourlyPayments.stream()
                                .mapToLong(p -> p.getStatus() == PaymentStatus.SUCCEEDED ? 1 : 0)
                                .sum();
                        double conversion = (double) successful / hourlyPayments.size() * 100.0;

                        meterRegistry.gauge("yookassa.payments.conversion.by.hour." + hour, conversion);
                    }
                } catch (Exception e) {
                    log.warn("Ошибка обновления метрик конверсии для часа {}: {}", hour, e.getMessage());
                }
            }
        } catch (Exception e) {
            log.error("❌ Ошибка обновления метрик конверсии: {}", e.getMessage(), e);
        }
    }

    /**
     * Запись метрик успешного платежа
     */
    private void recordSuccessfulPaymentMetrics(Payment payment) {
        // Метрики по размеру платежа
        meterRegistry.counter("yookassa.payments.successful.by.amount.range",
                "range", getAmountRange(payment.getAmount()))
                .increment();

        // Метрики по методу платежа
        meterRegistry.counter("yookassa.payments.successful.by.method",
                "method", payment.getMethod().name().toLowerCase())
                .increment();
    }

    /**
     * Запись метрик неудачного платежа
     */
    private void recordFailedPaymentMetrics(Payment payment, PaymentStatus oldStatus) {
        // Метрики ошибок по этапам
        String failureStage = determineFailureStage(oldStatus);
        meterRegistry.counter("yookassa.payments.failures.by.stage",
                "stage", failureStage)
                .increment();

        // Метрики ошибок по методу платежа
        meterRegistry.counter("yookassa.payments.failures.by.method",
                "method", payment.getMethod().name().toLowerCase())
                .increment();
    }

    /**
     * Запись времени завершения платежа
     */
    private void recordPaymentDuration(Payment payment) {
        if (payment.getUpdatedAt() != null) {
            long durationSeconds = java.time.Duration.between(
                    payment.getCreatedAt(), payment.getUpdatedAt()).getSeconds();

            meterRegistry.timer("yookassa.payments.completion.time",
                    "status", payment.getStatus().name().toLowerCase())
                    .record(java.time.Duration.ofSeconds(durationSeconds));
        }
    }

    /**
     * Определение диапазона суммы платежа
     */
    private String getAmountRange(BigDecimal amount) {
        if (amount.compareTo(BigDecimal.valueOf(500)) < 0)
            return "0-500";
        if (amount.compareTo(BigDecimal.valueOf(1000)) < 0)
            return "500-1000";
        if (amount.compareTo(BigDecimal.valueOf(2000)) < 0)
            return "1000-2000";
        if (amount.compareTo(BigDecimal.valueOf(5000)) < 0)
            return "2000-5000";
        return "5000+";
    }

    /**
     * Определение этапа, на котором произошла ошибка
     */
    private String determineFailureStage(PaymentStatus oldStatus) {
        return switch (oldStatus) {
            case PENDING -> "creation";
            case WAITING_FOR_CAPTURE -> "capture";
            default -> "unknown";
        };
    }

    /**
     * Получение сводки метрик для мониторинга
     */
    public PaymentMetricsSummary getMetricsSummary() {
        try {
            LocalDateTime dayAgo = LocalDateTime.now().minusDays(1);
            List<Payment> dailyPayments = paymentRepository.findByCreatedAtBetweenOrderByCreatedAtDesc(dayAgo,
                    LocalDateTime.now());

            long total = dailyPayments.size();
            long successful = dailyPayments.stream()
                    .mapToLong(p -> p.getStatus() == PaymentStatus.SUCCEEDED ? 1 : 0)
                    .sum();
            long failed = dailyPayments.stream()
                    .mapToLong(p -> p.getStatus() == PaymentStatus.FAILED ? 1 : 0)
                    .sum();

            double conversionRate = total > 0 ? (double) successful / total * 100.0 : 0.0;

            BigDecimal totalAmount = dailyPayments.stream()
                    .filter(p -> p.getStatus() == PaymentStatus.SUCCEEDED)
                    .map(Payment::getAmount)
                    .reduce(BigDecimal.ZERO, BigDecimal::add);

            log.debug("📊 Сводка метрик: всего={}, успешных={}, неудачных={}, конверсия={}%",
                    total, successful, failed, conversionRate);

            return new PaymentMetricsSummary(total, successful, failed, conversionRate, totalAmount);

        } catch (Exception e) {
            log.error("❌ Ошибка получения сводки метрик: {}", e.getMessage(), e);
            // Возвращаем пустую сводку в случае ошибки
            return new PaymentMetricsSummary(0, 0, 0, 0.0, BigDecimal.ZERO);
        }
    }

    /**
     * DTO для сводки метрик
     */
    public record PaymentMetricsSummary(
            long totalPayments,
            long successfulPayments,
            long failedPayments,
            double conversionRate,
            BigDecimal totalAmount) {
    }
}