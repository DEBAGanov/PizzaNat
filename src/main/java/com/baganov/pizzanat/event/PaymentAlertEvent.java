/**
 * @file: PaymentAlertEvent.java
 * @description: Событие для алертов платежной системы
 * @created: 2025-01-26
 */
package com.baganov.pizzanat.event;

import lombok.Getter;
import org.springframework.context.ApplicationEvent;

/**
 * Событие для отправки алертов платежной системы
 */
@Getter
public class PaymentAlertEvent extends ApplicationEvent {
    
    private final String alertMessage;
    private final AlertType alertType;
    
    public PaymentAlertEvent(Object source, String alertMessage, AlertType alertType) {
        super(source);
        this.alertMessage = alertMessage;
        this.alertType = alertType;
    }
    
    public enum AlertType {
        LOW_CONVERSION,
        HIGH_FAILURE_RATE,
        HIGH_AMOUNT_PAYMENT,
        CRITICAL_PAYMENT_FAILURE,
        LARGE_PAYMENT_SUCCESS,
        PENDING_PAYMENT_TIMEOUT,
        SYSTEM_ERROR
    }
} 