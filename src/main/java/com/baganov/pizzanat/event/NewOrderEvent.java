/**
 * @file: NewOrderEvent.java
 * @description: Событие создания нового заказа
 * @dependencies: Order
 * @created: 2025-01-13
 */
package com.baganov.pizzanat.event;

import com.baganov.pizzanat.entity.Order;
import lombok.Getter;
import org.springframework.context.ApplicationEvent;

@Getter
public class NewOrderEvent extends ApplicationEvent {

    private final Order order;

    public NewOrderEvent(Object source, Order order) {
        super(source);
        this.order = order;
    }
}