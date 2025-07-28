/**
 * @file: OrderPaymentStatus.java
 * @description: Статусы оплаты заказов
 * @dependencies: none
 * @created: 2025-01-26
 */
package com.baganov.pizzanat.entity;

/**
 * Статусы оплаты заказов
 * Синхронизируется с полем payment_status в таблице orders
 */
public enum OrderPaymentStatus {
    /**
     * Заказ не оплачен (по умолчанию)
     */
    UNPAID,
    
    /**
     * Заказ оплачен успешно
     */
    PAID,
    
    /**
     * Оплата в процессе (ожидание подтверждения)
     */
    PENDING,
    
    /**
     * Оплата отменена
     */
    CANCELLED,
    
    /**
     * Ошибка оплаты
     */
    FAILED
} 