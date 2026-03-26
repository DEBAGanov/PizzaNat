package com.baganov.pizzanat.service;

import com.baganov.pizzanat.config.MaxBotConfig;
import com.baganov.pizzanat.entity.Order;
import com.baganov.pizzanat.entity.Payment;
import com.baganov.pizzanat.event.OrderStatusChangedEvent;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.event.EventListener;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.util.HashMap;
import java.util.Map;

/**
 * Сервис для отправки административных уведомлений в MAX
 *
 * Интегрируется с MAX Admin Bot для уведомлений о:
 * - Новых заказах
 * - Оплатах
 * - Изменении статусов заказов
 *
 * Использует MAX API: https://api.max.ru
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class MaxAdminNotificationService {

    private final MaxBotConfig maxBotConfig;
    private final RestTemplate restTemplate;
    private final ObjectMapper objectMapper;

    private static final String MAX_API_BASE_URL = "https://api.max.ru";

    /**
     * Отправка уведомления о новом заказе
     *
     * @param order заказ
     */
    public void sendNewOrderNotification(Order order) {
        if (!maxBotConfig.isAdminEnabled()) {
            log.debug("MAX admin notifications disabled");
            return;
        }

        try {
            String message = formatNewOrderMessage(order);
            sendMessageToMaxChat(message, order.getId());
            log.info("✅ MAX admin notification sent for new order: {}", order.getId());
        } catch (Exception e) {
            log.error("❌ Failed to send MAX new order notification: {}", e.getMessage());
        }
    }

    /**
     * Отправка уведомления об оплате
     *
     * @param payment платеж
     * @param order связанный заказ
     */
    public void sendPaymentNotification(Payment payment, Order order) {
        if (!maxBotConfig.isAdminEnabled()) {
                return;
        }

        try {
            String message = formatPaymentMessage(payment, order);
            sendMessageToMaxChat(message, order.getId());
            log.info("✅ MAX admin payment notification sent for order: {}", order.getId());
        } catch (Exception e) {
            log.error("❌ Failed to send MAX payment notification: {}", e.getMessage());
        }
    }

    /**
     * Отправка уведомления об изменении статуса заказа
     *
     * @param event событие изменения статуса
     */
    @EventListener
    public void handleOrderStatusChanged(OrderStatusChangedEvent event) {
        if (!maxBotConfig.isAdminEnabled()) {
            return;
        }

        try {
            String message = formatStatusChangeMessage(event);
            sendMessageToMaxChat(message, event.getOrderId());
            log.info("✅ MAX admin status change notification sent for order: {}",
                    event.getOrderId());
        } catch (Exception e) {
            log.error("❌ Failed to send MAX status change notification: {}", e.getMessage());
        }
    }

    /**
     * Отправка сообщения в чат MAX
     *
     * @param message текст сообщения
     * @param orderId ID заказа для логирования
     */
    private void sendMessageToMaxChat(String message, Integer orderId) {
        String adminChatId = maxBotConfig.getAdminChatId();
        String adminBotToken = maxBotConfig.getAdminBotToken();

        if (adminChatId == null || adminChatId.isEmpty()) {
            log.warn("MAX admin chat ID not configured");
            return;
        }

        if (adminBotToken == null || adminBotToken.isEmpty()) {
            log.warn("MAX admin bot token not configured");
            return;
        }

        String url = String.format("%s/bots/%s/messages", MAX_API_BASE_URL, adminBotToken);

        Map<String, Object> body = new HashMap<>();
        body.put("chat_id", adminChatId);
        body.put("text", message);

        try {
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);

            HttpEntity<Map<String, Object>> entity = new HttpEntity<>(body, headers);

            var response = restTemplate.postForEntity(url, entity, String.class);
            log.debug("MAX API response for order {}: Status: {}",
                    orderId, response.getStatusCode());

        } catch (Exception e) {
            log.error("Error calling MAX API: {}", e.getMessage());
            throw e;
        }
    }

    /**
     * Форматирование сообщения о новом заказе
     */
    private String formatNewOrderMessage(Order order) {
        StringBuilder sb = new StringBuilder();

        sb.append("🆕 **НОВЫЙ ЗААЗ**\n\n");
        sb.append(String.format("**Заказ #%d**", order.getId())).append("\n\n");
        sb.append("**Клиент:** ").append(order.getContactName()).append("\n");
        sb.append("**Телефон:** ").append(order.getContactPhone()).append("\n");
        sb.append("**Тип доставки:** ").append(order.getDeliveryType()).append("\n");

        if (order.getDeliveryAddress() != null && !order.getDeliveryAddress().isEmpty()) {
            sb.append("**Адрес:** ").append(order.getDeliveryAddress()).append("\n");
        }

        sb.append("**Сумма:** ").append(String.format("%.2f ₽", order.getTotalAmount())).append("\n");
        sb.append("**Статус:** ").append(order.getStatus().toString()).append("\n");

        return sb.toString();
    }

    /**
     * Форматирование сообщения об оплате
     */
    private String formatPaymentMessage(Payment payment, Order order) {
        StringBuilder sb = new StringBuilder();

        sb.append("💰 **ОПЛАТА ЗААЗ**\n\n");
        sb.append(String.format("**Заказ #%d**", order.getId())).append("\n");
        sb.append("**Сумма:** ").append(String.format("%.2f ₽", payment.getAmount())).append("\n");
        sb.append("**Статус:** ").append(payment.getStatus()).append("\n");
        sb.append("**ID платежа:** ").append(payment.getId()).append("\n");

        return sb.toString();
    }

    /**
     * Форматирование сообщения об изменении статуса
     */
    private String formatStatusChangeMessage(OrderStatusChangedEvent event) {
        StringBuilder sb = new StringBuilder();

        sb.append("📝 **ИЗМЕНЕНИЕ СТАТУСА**\n\n");
        sb.append(String.format("**Заказ #%d**", event.getOrderId())).append("\n");
        sb.append("**Новый статус:** ").append(event.getNewStatus()).append("\n");
        sb.append("**Время:** ").append(java.time.Instant.ofEpochMilli(event.getTimestamp())).append("\n");

        return sb.toString();
    }
}
