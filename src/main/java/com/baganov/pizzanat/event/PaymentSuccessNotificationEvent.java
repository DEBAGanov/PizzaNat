package com.baganov.pizzanat.event;

import com.baganov.pizzanat.entity.Order;
import org.springframework.context.ApplicationEvent;

/**
 * Событие успешного платежа для отправки простых уведомлений
 */
public class PaymentSuccessNotificationEvent extends ApplicationEvent {
    
    private final Order order;
    
    public PaymentSuccessNotificationEvent(Object source, Order order) {
        super(source);
        this.order = order;
    }
    
    public Order getOrder() {
        return order;
    }
}
