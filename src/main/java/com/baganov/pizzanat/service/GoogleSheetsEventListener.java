/**
 * @file: GoogleSheetsEventListener.java
 * @description: Обработчик событий для автоматического обновления Google Sheets
 * @dependencies: Spring Events, GoogleSheetsService, Order Events
 * @created: 2025-01-28
 */
package com.baganov.pizzanat.service;

import com.baganov.pizzanat.event.NewOrderEvent;
import com.baganov.pizzanat.event.OrderStatusChangedEvent;
import com.baganov.pizzanat.event.PaymentStatusChangedEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(name = "google.sheets.enabled", havingValue = "true")
public class GoogleSheetsEventListener {

    private final GoogleSheetsService googleSheetsService;

    /**
     * Обработка события создания нового заказа
     */
    @EventListener
    public void handleNewOrderEvent(NewOrderEvent event) {
        try {
            log.info("📊 Получено событие нового заказа #{} для Google Sheets", 
                    event.getOrder().getId());
            
            googleSheetsService.addOrderToSheet(event.getOrder());
            
        } catch (Exception e) {
            log.error("❌ Ошибка обработки события нового заказа #{} для Google Sheets: {}", 
                    event.getOrder().getId(), e.getMessage(), e);
        }
    }

    /**
     * Обработка события изменения статуса заказа
     */
    @EventListener
    public void handleOrderStatusChangedEvent(OrderStatusChangedEvent event) {
        try {
            log.info("🔄 Получено событие изменения статуса заказа #{} для Google Sheets: {} → {}", 
                    event.getOrderId(), event.getOldStatus(), event.getNewStatus());
            
            googleSheetsService.updateOrderStatus(event.getOrderId(), event.getNewStatus());
            
        } catch (Exception e) {
            log.error("❌ Ошибка обработки события изменения статуса заказа #{}: {}", 
                    event.getOrderId(), e.getMessage(), e);
        }
    }

    /**
     * Обработка события изменения статуса платежа
     */
    @EventListener
    public void handlePaymentStatusChangedEvent(PaymentStatusChangedEvent event) {
        try {
            log.info("💳 Получено событие изменения статуса платежа для заказа #{}: {} → {}", 
                    event.getOrderId(), event.getOldStatus(), event.getNewStatus());
            
            googleSheetsService.updatePaymentStatus(
                    event.getOrderId(), 
                    event.getNewStatus().getDescription()
            );
            
        } catch (Exception e) {
            log.error("❌ Ошибка обработки события изменения статуса платежа для заказа #{}: {}", 
                    event.getOrderId(), e.getMessage(), e);
        }
    }
}