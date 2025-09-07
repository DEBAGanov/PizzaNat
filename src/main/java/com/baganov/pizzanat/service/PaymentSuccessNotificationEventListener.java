package com.baganov.pizzanat.service;

import com.baganov.pizzanat.event.PaymentSuccessNotificationEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

/**
 * Обработчик событий успешных платежей для отправки простых уведомлений
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PaymentSuccessNotificationEventListener {

    private final AdminBotService adminBotService;
    private final TelegramUserNotificationService telegramUserNotificationService;

    /**
     * Обработка события успешного платежа - отправка простых уведомлений
     */
    @EventListener
    @Async
    public void handlePaymentSuccessNotification(PaymentSuccessNotificationEvent event) {
        log.info("🔔 Обработка события простого уведомления об оплате заказа #{}", event.getOrder().getId());

        // Отправляем простое уведомление админам
        try {
            if (adminBotService != null) {
                adminBotService.sendSimplePaymentNotification(event.getOrder());
                log.info("✅ Простое уведомление об оплате заказа #{} отправлено администраторам", event.getOrder().getId());
            }
        } catch (Exception e) {
            log.error("❌ Ошибка отправки простого уведомления об оплате заказа #{} администраторам: {}", 
                    event.getOrder().getId(), e.getMessage());
        }

        // Отправляем простое уведомление пользователю
        try {
            if (telegramUserNotificationService != null) {
                telegramUserNotificationService.sendSimplePaymentSuccessNotification(event.getOrder());
                log.info("✅ Простое уведомление об оплате отправлено пользователю заказа #{}", event.getOrder().getId());
            }
        } catch (Exception e) {
            log.error("❌ Ошибка отправки простого уведомления пользователю об оплате заказа #{}: {}", 
                    event.getOrder().getId(), e.getMessage());
        }
    }
}
