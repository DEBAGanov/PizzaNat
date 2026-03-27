package com.baganov.pizzanat.service;

import com.baganov.pizzanat.config.MaxBotConfig;
import com.baganov.pizzanat.entity.Order;
import com.baganov.pizzanat.entity.OrderItem;
import com.baganov.pizzanat.entity.Payment;
import com.baganov.pizzanat.entity.DeliveryLocation;
import com.baganov.pizzanat.event.NewOrderEvent;
import com.baganov.pizzanat.event.OrderStatusChangedEvent;
import com.baganov.pizzanat.entity.OrderPaymentStatus;
import com.baganov.pizzanat.entity.PaymentMethod;
import com.baganov.pizzanat.repository.DeliveryLocationRepository;
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

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
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
    private final DeliveryLocationRepository deliveryLocationRepository;

    private static final String MAX_API_BASE_URL = "https://api.max.ru";
    private static final DateTimeFormatter TIME_FORMATTER = DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm");

    /**
     * Обработчик события нового заказа
     * Аналогично Telegram Admin Bot - отправляет полноценные уведомления с inline кнопками
     */
    @EventListener
    @Async
    public void handleNewOrderEvent(NewOrderEvent event) {
        if (!maxBotConfig.isAdminEnabled()) {
            log.debug("MAX admin notifications disabled");
            return;
        }

        try {
            Order order = event.getOrder();
            log.info("📧 MAX: Получено событие о новом заказе #{}", order.getId());

            String message = formatNewOrderMessage(order);
            List<Map<String, Object>> attachments = createOrderManagementAttachments(order.getId());

            sendMessageToMaxChat(message, order.getId(), attachments);
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
            sendMessageToMaxChat(message, order.getId(), null);
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
    @Async
    public void handleOrderStatusChanged(OrderStatusChangedEvent event) {
        if (!maxBotConfig.isAdminEnabled()) {
            return;
        }

        try {
            String message = formatStatusChangeMessage(event);
            sendMessageToMaxChat(message, event.getOrderId(), null);
            log.info("✅ MAX admin status change notification sent for order: {}",
                    event.getOrderId());
        } catch (Exception e) {
            log.error("❌ Failed to send MAX status change notification: {}", e.getMessage());
        }
    }

    /**
     * Создание inline кнопок для управления заказом (как в Telegram)
     */
    private List<Map<String, Object>> createOrderManagementAttachments(Integer orderId) {
        List<Map<String, Object>> attachments = new ArrayList<>();

        // Кнопки изменения статуса (строка 1)
        Map<String, Object> row1 = new HashMap<>();
        row1.put("type", "buttons");
        List<Map<String, Object>> buttons1 = new ArrayList<>();

        buttons1.add(createCallbackButton("✅ Подтвердить", "max_order_" + orderId + "_CONFIRMED"));
        buttons1.add(createCallbackButton("👨‍🍳 Готовится", "max_order_" + orderId + "_PREPARING"));
        row1.put("buttons", buttons1);
        attachments.add(row1);

        // Кнопки изменения статуса (строка 2)
        Map<String, Object> row2 = new HashMap<>();
        row2.put("type", "buttons");
        List<Map<String, Object>> buttons2 = new ArrayList<>();

        buttons2.add(createCallbackButton("📦 Готов", "max_order_" + orderId + "_READY"));
        buttons2.add(createCallbackButton("🚗 В доставке", "max_order_" + orderId + "_OUT_FOR_DELIVERY"));
        row2.put("buttons", buttons2);
        attachments.add(row2);

        // Кнопки изменения статуса (строка 3)
        Map<String, Object> row3 = new HashMap<>();
        row3.put("type", "buttons");
        List<Map<String, Object>> buttons3 = new ArrayList<>();

        buttons3.add(createCallbackButton("✅ Доставлен", "max_order_" + orderId + "_DELIVERED"));
        buttons3.add(createCallbackButton("❌ Отменить", "max_order_" + orderId + "_CANCELLED"));
        row3.put("buttons", buttons3);
        attachments.add(row3);

        return attachments;
    }

    /**
     * Создание callback кнопки
     */
    private Map<String, Object> createCallbackButton(String text, String callbackData) {
        Map<String, Object> button = new HashMap<>();
        button.put("text", text);
        button.put("callbackData", callbackData);
        return button;
    }

    /**
     * Отправка сообщения в чат MAX
     *
     * @param message текст сообщения
     * @param orderId ID заказа для логирования
     * @param attachments вложения (inline кнопки)
     */
    private void sendMessageToMaxChat(String message, Integer orderId, List<Map<String, Object>> attachments) {
        String adminChatId = maxBotConfig.getAdminChatId();
        String adminBotToken = maxBotConfig.getAdminBotToken();

        if (adminChatId == null || adminChatId.isEmpty()) {
            log.warn("MAX admin chat ID not configured - cannot send notification for order {}", orderId);
            return;
        }

        if (adminBotToken == null || adminBotToken.isEmpty()) {
            log.warn("MAX admin bot token not configured - cannot send notification for order {}", orderId);
            return;
        }

        String url = String.format("%s/bots/%s/messages", MAX_API_BASE_URL, adminBotToken);

        Map<String, Object> body = new HashMap<>();
        body.put("chat_id", adminChatId);
        body.put("text", message);

        if (attachments != null && !attachments.isEmpty()) {
            body.put("attachments", attachments);
        }

        try {
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);

            HttpEntity<Map<String, Object>> entity = new HttpEntity<>(body, headers);

            var response = restTemplate.postForEntity(url, entity, String.class);
            log.debug("MAX API response for order {}: Status: {}",
                    orderId, response.getStatusCode());

        } catch (Exception e) {
            log.error("Error calling MAX API for order {}: {}", orderId, e.getMessage());
            throw e;
        }
    }

    /**
     * Форматирование сообщения о новом заказе (полный формат как в Telegram)
     */
    private String formatNewOrderMessage(Order order) {
        StringBuilder sb = new StringBuilder();

        // Заголовок с эмодзи и способом оплаты
        String paymentEmoji = getPaymentEmoji(order.getPaymentMethod() != null ? order.getPaymentMethod() : null);
        String paymentLabel = order.getPaymentMethod() != null ? order.getPaymentMethod().getDisplayName() : "Не указан";
        sb.append("🟢 **НОВЫЙ ЗАКАЗ #").append(order.getId()).append("** 🟢 ").append(paymentEmoji).append("\n\n");

        // Время заказа
        sb.append("🕐 **Время заказа:** ").append(LocalDateTime.now().format(TIME_FORMATTER)).append("\n\n");

        // Контактные данные
        sb.append("📞 **КОНТАКТНЫЕ ДАННЫЕ ЗАКАЗА**\n");
        sb.append("Имя: ").append(escapeMarkdown(order.getContactName())).append("\n");
        sb.append("Телефон: ").append(escapeMarkdown(order.getContactPhone())).append("\n\n");

        // Пункт выдачи / Доставка
        sb.append("📍 **ПУНКТ ВЫДАЧИ**\n");
        if (order.getDeliveryAddress() != null && !order.getDeliveryAddress().isEmpty()) {
            sb.append("Адрес: ").append(escapeMarkdown(order.getDeliveryAddress())).append("\n");
        }
        if (order.getDeliveryLocation() != null) {
            sb.append("Пункт: ").append(escapeMarkdown(order.getDeliveryLocation().getName())).append("\n");
        }
        sb.append("\n");

        // Способ доставки
        String deliveryType = order.getDeliveryType() != null ? order.getDeliveryType() : "Доставка курьером";
        String deliveryEmoji = deliveryType.contains("Самовывоз") ? "🏠" : "🚛";
        sb.append(deliveryEmoji).append(" **Способ доставки:** ").append(deliveryEmoji).append(" ")
                .append(escapeMarkdown(deliveryType)).append("\n\n");

        // Состав заказа
        sb.append("🛒 **СОСТАВ ЗАКАЗА**\n");
        BigDecimal itemsTotal = BigDecimal.ZERO;
        if (order.getItems() != null) {
            for (OrderItem item : order.getItems()) {
                BigDecimal itemTotal = item.getPrice().multiply(BigDecimal.valueOf(item.getQuantity()));
                itemsTotal = itemsTotal.add(itemTotal);
                sb.append("• ").append(escapeMarkdown(item.getProduct().getName()))
                        .append(" x").append(item.getQuantity())
                        .append(" = ").append(String.format("%.2f ₽", itemTotal))
                        .append("\n");
            }
        }
        sb.append("\n");

        // Детальный расчёт
        sb.append("💰 **ДЕТАЛЬНЫЙ РАСЧЕТ СУММЫ:**\n");
        sb.append("├ Товары: ").append(String.format("%.2f ₽", itemsTotal)).append("\n");

        BigDecimal deliveryCost = order.getDeliveryCost() != null ? order.getDeliveryCost() : BigDecimal.ZERO;
        String deliveryCostStr = deliveryCost.compareTo(BigDecimal.ZERO) == 0 ? "Бесплатно" : String.format("%.2f ₽", deliveryCost);
        sb.append("├ Доставка: ").append(deliveryCostStr).append("\n");

        BigDecimal total = order.getTotalAmount() != null ? order.getTotalAmount() : itemsTotal.add(deliveryCost);
        sb.append("└ **ИТОГО: ").append(String.format("%.2f ₽", total)).append("**\n\n");

        // Статус оплаты
        sb.append("💳 **СТАТУС ОПЛАТЫ:** ");
        if (order.getPaymentStatus() == OrderPaymentStatus.PAID) {
            sb.append("✅ Оплачено");
        } else {
            sb.append("❌ Не оплачено");
        }
        sb.append("\n");

        sb.append("💰 **СПОСОБ ОПЛАТЫ:** ").append(order.getPaymentMethod() != null ? order.getPaymentMethod().getDisplayName() : "Не указан").append("\n");

        return sb.toString();
    }

    /**
     * Форматирование сообщения об оплате
     */
    private String formatPaymentMessage(Payment payment, Order order) {
        StringBuilder sb = new StringBuilder();

        sb.append("💰 **ОПЛАТА ЗАКАЗА**\n\n");
        sb.append("**Заказ #").append(order.getId()).append("**\n");
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
        sb.append("**Заказ #").append(event.getOrderId()).append("**\n");
        sb.append("**Новый статус:** ").append(event.getNewStatus()).append("\n");
        sb.append("**Время:** ").append(LocalDateTime.now().format(TIME_FORMATTER)).append("\n");

        return sb.toString();
    }

    /**
     * Получить эмодзи для способа оплаты
     */
    private String getPaymentEmoji(PaymentMethod paymentMethod) {
        if (paymentMethod == null) return "💵";
        return switch (paymentMethod) {
            case SBP -> "📱";
            case BANK_CARD -> "💳";
            case CASH -> "💵";
            case YOOMONEY -> "💳";
            default -> "💵";
        };
    }

    /**
     * Получить название способа оплаты
     */
    private String getPaymentMethodLabel(PaymentMethod paymentMethod) {
        if (paymentMethod == null) return "Наличные";
        return paymentMethod.getDisplayName();
    }

    /**
     * Экранирование символов для Markdown
     */
    private String escapeMarkdown(String text) {
        if (text == null) return "";
        return text.replace("_", "\\_")
                .replace("*", "\\*")
                .replace("[", "\\[")
                .replace("]", "\\]")
                .replace("(", "\\(")
                .replace(")", "\\)")
                .replace("~", "\\~")
                .replace("`", "\\`")
                .replace(">", "\\>")
                .replace("#", "\\#")
                .replace("+", "\\+")
                .replace("-", "\\-")
                .replace("=", "\\=")
                .replace("|", "\\|")
                .replace("{", "\\{")
                .replace("}", "\\}")
                .replace(".", "\\.")
                .replace("!", "\\!");
    }
}
