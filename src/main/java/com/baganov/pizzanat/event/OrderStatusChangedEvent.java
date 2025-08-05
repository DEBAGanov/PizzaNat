/**
 * @file: OrderStatusChangedEvent.java
 * @description: Событие изменения статуса заказа
 * @dependencies: Spring Events
 * @created: 2025-01-28
 */
package com.baganov.pizzanat.event;

import lombok.Getter;
import org.springframework.context.ApplicationEvent;

@Getter
public class OrderStatusChangedEvent extends ApplicationEvent {
    
    private final Integer orderId;
    private final String oldStatus;
    private final String newStatus;

    public OrderStatusChangedEvent(Object source, Integer orderId, String oldStatus, String newStatus) {
        super(source);
        this.orderId = orderId;
        this.oldStatus = oldStatus;
        this.newStatus = newStatus;
    }
}