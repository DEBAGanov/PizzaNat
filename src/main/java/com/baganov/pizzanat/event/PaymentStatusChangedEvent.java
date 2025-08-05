/**
 * @file: PaymentStatusChangedEvent.java
 * @description: Событие изменения статуса платежа
 * @dependencies: Spring Events, PaymentStatus
 * @created: 2025-01-28
 */
package com.baganov.pizzanat.event;

import com.baganov.pizzanat.entity.PaymentStatus;
import lombok.Getter;
import org.springframework.context.ApplicationEvent;

@Getter
public class PaymentStatusChangedEvent extends ApplicationEvent {
    
    private final Integer orderId;
    private final PaymentStatus oldStatus;
    private final PaymentStatus newStatus;

    public PaymentStatusChangedEvent(Object source, Integer orderId, 
                                   PaymentStatus oldStatus, PaymentStatus newStatus) {
        super(source);
        this.orderId = orderId;
        this.oldStatus = oldStatus;
        this.newStatus = newStatus;
    }
}