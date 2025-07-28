/**
 * @file: OrderDisplayStatus.java
 * @description: Визуальные статусы заказов для админского бота с эмодзи-индикаторами
 * @dependencies: Order, Payment, PaymentStatus
 * @created: 2025-01-26
 */
package com.baganov.pizzanat.entity;

import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;

/**
 * Визуальные статусы заказов для улучшенного отображения в админском боте
 */
public enum OrderDisplayStatus {
    CASH_NEW("🟢", "НАЛИЧНЫМИ", "Заказ с оплатой наличными - готов к обработке"),
    PAYMENT_PENDING("🟡", "ОЖИДАЕТ ОПЛАТЫ", "Онлайн платеж создан, ожидается оплата"),  
    PAYMENT_POLLING("🔄", "ОПРОС ПЛАТЕЖА", "Активно проверяется статус оплаты"),
    PAYMENT_SUCCESS("🟢", "ОПЛАЧЕН", "Онлайн платеж успешно завершен"),
    PAYMENT_TIMEOUT("⏰", "ТАЙМАУТ ОПЛАТЫ", "Истекло время ожидания оплаты"),
    PAYMENT_CANCELLED("❌", "ПЛАТЕЖ ОТМЕНЕН", "Платеж отменен или завершился ошибкой");

    private final String emoji;
    private final String displayName;
    private final String description;

    OrderDisplayStatus(String emoji, String displayName, String description) {
        this.emoji = emoji;
        this.displayName = displayName;
        this.description = description;
    }

    public String getEmoji() {
        return emoji;
    }

    public String getDisplayName() {
        return displayName;
    }

    public String getDescription() {
        return description;
    }

    /**
     * Определяет визуальный статус заказа на основе способа оплаты и статуса платежей
     */
    public static OrderDisplayStatus determineStatus(Order order, Payment latestPayment) {
        // Наличные заказы
        if (order.getPaymentMethod() == PaymentMethod.CASH) {
            return CASH_NEW;
        }

        // Заказы без платежей (считаются наличными)
        if (latestPayment == null) {
            return CASH_NEW;
        }

        // Определяем статус на основе платежа
        switch (latestPayment.getStatus()) {
            case SUCCEEDED:
                return PAYMENT_SUCCESS;
                
            case PENDING:
            case WAITING_FOR_CAPTURE:
                // Проверяем возраст платежа для определения таймаута
                long minutesElapsed = ChronoUnit.MINUTES.between(
                    latestPayment.getCreatedAt(), LocalDateTime.now()
                );
                
                if (minutesElapsed >= 10) {
                    return PAYMENT_TIMEOUT;
                } else {
                    return PAYMENT_POLLING;
                }
                
            case CANCELLED:
            case FAILED:
                return PAYMENT_CANCELLED;
                
            default:
                return PAYMENT_PENDING;
        }
    }

    /**
     * Возвращает индикатор прогресса polling'а для платежей в ожидании
     */
    public static String getPollingIndicator(Payment payment) {
        if (payment == null || payment.getCreatedAt() == null) {
            return "";
        }

        long minutesElapsed = ChronoUnit.MINUTES.between(
            payment.getCreatedAt(), LocalDateTime.now()
        );

        if (minutesElapsed >= 10) {
            return "⏰ Таймаут";
        } else {
            return String.format("🔄 %d/10мин", Math.min(minutesElapsed, 10));
        }
    }

    /**
     * Возвращает полное форматированное описание статуса
     */
    public String getFormattedStatus() {
        return emoji + " " + displayName;
    }

    /**
     * Возвращает статус с дополнительной информацией для polling'а
     */
    public String getFormattedStatusWithInfo(Payment payment) {
        String baseStatus = getFormattedStatus();
        
        if (this == PAYMENT_POLLING && payment != null) {
            return baseStatus + " " + getPollingIndicator(payment);
        }
        
        return baseStatus;
    }
} 