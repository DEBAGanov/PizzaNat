/**
 * @file: TelegramUserNotificationService.java
 * @description: Сервис для отправки персональных уведомлений пользователям в Telegram
 * @dependencies: Spring Web, Jackson, TelegramConfig
 * @created: 2025-01-11
 */
package com.baganov.pizzanat.service;

import com.baganov.pizzanat.config.TelegramConfig;
import com.baganov.pizzanat.entity.Order;
import com.baganov.pizzanat.entity.OrderItem;
import com.baganov.pizzanat.entity.User;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.math.BigDecimal;
import java.time.format.DateTimeFormatter;

/**
 * Сервис для отправки персональных уведомлений пользователям в Telegram.
 * Следует принципу Single Responsibility из SOLID.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class TelegramUserNotificationService {

    @Qualifier("telegramAuthRestTemplate")
    private final RestTemplate telegramAuthRestTemplate;

    private final TelegramConfig.TelegramAuthProperties telegramAuthProperties;

    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm");

    /**
     * Отправка персонального уведомления пользователю о создании заказа
     *
     * @param order заказ
     */
    public void sendPersonalNewOrderNotification(Order order) {
        if (!isNotificationEnabled() || !hasUserTelegramId(order)) {
            return;
        }

        try {
            String message = formatNewOrderMessage(order);
            sendPersonalMessage(order.getUser().getTelegramId(), message);

            log.info("Персональное уведомление о новом заказе #{} отправлено пользователю {} (Telegram ID: {})",
                    order.getId(), order.getUser().getUsername(), order.getUser().getTelegramId());

        } catch (Exception e) {
            log.error("Ошибка отправки персонального уведомления о новом заказе #{} пользователю {}: {}",
                    order.getId(), order.getUser().getUsername(), e.getMessage(), e);
        }
    }

    /**
     * Отправка персонального уведомления пользователю об изменении статуса заказа
     *
     * @param order     заказ
     * @param oldStatus старый статус
     * @param newStatus новый статус
     */
    public void sendPersonalOrderStatusUpdateNotification(Order order, String oldStatus, String newStatus) {
        if (!isNotificationEnabled() || !hasUserTelegramId(order)) {
            return;
        }

        try {
            String message = formatPersonalStatusUpdateMessage(order, oldStatus, newStatus);
            sendPersonalMessage(order.getUser().getTelegramId(), message);

            log.info(
                    "Персональное уведомление об изменении статуса заказа #{} отправлено пользователю {} (Telegram ID: {})",
                    order.getId(), order.getUser().getUsername(), order.getUser().getTelegramId());

        } catch (Exception e) {
            log.error("Ошибка отправки персонального уведомления об изменении статуса заказа #{} пользователю {}: {}",
                    order.getId(), order.getUser().getUsername(), e.getMessage(), e);
        }
    }

    /**
     * Отправка персонального уведомления пользователю с запросом на отзыв
     *
     * @param order заказ
     */
    public void sendReviewRequestNotification(Order order) {
        if (!isNotificationEnabled() || !hasUserTelegramId(order)) {
            return;
        }

        try {
            String message = formatReviewRequestMessage(order);
            sendPersonalMessage(order.getUser().getTelegramId(), message);

            log.info("Запрос на отзыв для заказа #{} отправлен пользователю {} (Telegram ID: {})",
                    order.getId(), order.getUser().getUsername(), order.getUser().getTelegramId());

        } catch (Exception e) {
            log.error("Ошибка отправки запроса на отзыв для заказа #{} пользователю {}: {}",
                    order.getId(), order.getUser().getUsername(), e.getMessage(), e);
        }
    }

    /**
     * Проверяет, включены ли уведомления и настроен ли Telegram
     */
    private boolean isNotificationEnabled() {
        if (!telegramAuthProperties.isValid()) {
            log.debug("Telegram auth не настроен, персональные уведомления отключены");
            return false;
        }
        return true;
    }

    /**
     * Проверяет, есть ли у пользователя заказа Telegram ID
     */
    private boolean hasUserTelegramId(Order order) {
        if (order.getUser() == null) {
            log.debug("Заказ #{} не привязан к пользователю, персональное уведомление не отправляется", order.getId());
            return false;
        }

        if (order.getUser().getTelegramId() == null) {
            log.debug("У пользователя {} нет Telegram ID, персональное уведомление не отправляется",
                    order.getUser().getUsername());
            return false;
        }

        return true;
    }

    /**
     * Форматирование персонального сообщения о новом заказе
     */
    private String formatNewOrderMessage(Order order) {
        User user = order.getUser();
        StringBuilder message = new StringBuilder();

        message.append("🍕 <b>Ваш заказ принят!</b>\n\n");

        message.append("📋 <b>Заказ #").append(order.getId()).append("</b>\n");
        message.append("📅 <b>Дата:</b> ").append(order.getCreatedAt().format(DATE_FORMATTER)).append("\n");
        message.append("📋 <b>Статус:</b> ").append(getStatusDisplayName(order.getStatus().getName())).append("\n\n");

        // Адрес доставки
        if (order.getDeliveryAddress() != null) {
            message.append("📍 <b>Адрес доставки:</b> ").append(order.getDeliveryAddress()).append("\n");
        } else if (order.getDeliveryLocation() != null) {
            message.append("📍 <b>Пункт выдачи:</b> ").append(order.getDeliveryLocation().getAddress()).append("\n");
        }

        // Способ доставки (НОВОЕ ПОЛЕ)
        if (order.getDeliveryType() != null) {
            String deliveryIcon = order.isPickup() ? "🏠" : "🚗";
            message.append("🚛 <b>Способ доставки:</b> ").append(deliveryIcon).append(" ")
                    .append(order.getDeliveryType()).append("\n");
        }

        // Комментарий
        if (order.getComment() != null && !order.getComment().trim().isEmpty()) {
            message.append("💬 <b>Комментарий:</b> ").append(order.getComment()).append("\n");
        }

        message.append("\n🛒 <b>Состав заказа:</b>\n");
        BigDecimal itemsTotal = BigDecimal.ZERO;
        for (OrderItem item : order.getItems()) {
            BigDecimal itemSubtotal = item.getPrice().multiply(BigDecimal.valueOf(item.getQuantity()));
            itemsTotal = itemsTotal.add(itemSubtotal);
            
            message.append("• ").append(item.getProduct().getName())
                    .append(" x").append(item.getQuantity())
                    .append(" = ").append(itemSubtotal).append(" ₽\n");
        }

        // Детализация суммы (ОБНОВЛЕНО)
        message.append("\n💰 <b>РАСЧЕТ СУММЫ:</b>\n");
        message.append("├ Товары: ").append(itemsTotal).append(" ₽\n");
        
        if (order.getDeliveryCost() != null && order.getDeliveryCost().compareTo(BigDecimal.ZERO) > 0) {
            message.append("├ Доставка: ").append(order.getDeliveryCost()).append(" ₽\n");
        } else if (order.isDeliveryByCourier()) {
            message.append("├ Доставка: БЕСПЛАТНО\n");
        } else if (order.isPickup()) {
            message.append("├ Доставка: Самовывоз (0 ₽)\n");
        }
        
        message.append("└ <b>ИТОГО: ").append(order.getTotalAmount()).append(" ₽</b>\n\n");
        message.append("Мы уведомим вас об изменении статуса заказа! 🔔");

        return message.toString();
    }

    /**
     * Форматирование персонального сообщения об изменении статуса
     */
    private String formatPersonalStatusUpdateMessage(Order order, String oldStatus, String newStatus) {
        StringBuilder message = new StringBuilder();

        message.append("🔄 <b>Статус заказа изменен!</b>\n\n");
        message.append("📋 <b>Заказ #").append(order.getId()).append("</b>\n");
        message.append("💰 <b>Сумма:</b> ").append(order.getTotalAmount()).append(" ₽\n\n");

        message.append("📋 <b>Статус изменен:</b>\n");
        message.append("❌ Было: ").append(getStatusDisplayName(oldStatus)).append("\n");
        message.append("✅ Стало: ").append(getStatusDisplayName(newStatus)).append("\n\n");

        // Добавляем специальные сообщения для определенных статусов
        String statusMessage = getStatusSpecialMessage(newStatus);
        if (statusMessage != null) {
            message.append(statusMessage);
        }

        return message.toString();
    }

    /**
     * Получение отображаемого названия статуса
     */
    private String getStatusDisplayName(String status) {
        return switch (status.toUpperCase()) {
            case "CREATED" -> "Создан";
            case "CONFIRMED" -> "Подтвержден";
            case "PREPARING" -> "Готовится";
            case "READY" -> "Готов к выдаче";
            case "DELIVERING" -> "Доставляется";
            case "DELIVERED" -> "Доставлен";
            case "CANCELLED" -> "Отменен";
            case "PAID" -> "Оплачен";
            default -> status;
        };
    }

    /**
     * Получение специального сообщения для статуса
     */
    private String getStatusSpecialMessage(String status) {
        return switch (status.toUpperCase()) {
            case "CONFIRMED" -> "🎉 Отлично! Ваш заказ подтвержден и передан на кухню.";
            case "PREPARING" -> "👨‍🍳 Наши повара готовят ваш заказ с особой заботой!";
            case "READY" -> "🍕 Ваш заказ готов! Можете забирать или ожидайте курьера.";
            case "DELIVERING" -> "🚗 Курьер уже в пути! Скоро будет у вас.";
            case "DELIVERED" -> "✅ Заказ доставлен! Приятного аппетита! 🍽️\n\nБудем рады видеть вас снова! ❤️";
            case "CANCELLED" -> "😔 К сожалению, заказ был отменен. Если у вас есть вопросы, обратитесь в поддержку.";
            default -> null;
        };
    }

    /**
     * Форматирование сообщения с запросом на отзыв
     */
    private String formatReviewRequestMessage(Order order) {
        StringBuilder message = new StringBuilder();

        message.append("⭐ <b>Поделитесь впечатлениями о заказе!</b>\n\n");

        message.append("📋 <b>Заказ #").append(order.getId()).append("</b>\n");
        message.append("📅 <b>Дата:</b> ").append(order.getCreatedAt().format(DATE_FORMATTER)).append("\n\n");

        // Состав заказа (краткий)
        if (order.getItems() != null && !order.getItems().isEmpty()) {
            message.append("🛒 <b>Состав заказа:</b>\n");
            int itemCount = 0;
            for (OrderItem item : order.getItems()) {
                if (itemCount >= 3) {
                    message.append("   • и еще ").append(order.getItems().size() - 3).append(" позиции\n");
                    break;
                }
                message.append("   • ").append(item.getProduct().getName());
                if (item.getQuantity() > 1) {
                    message.append(" x").append(item.getQuantity());
                }
                message.append("\n");
                itemCount++;
            }
            message.append("\n");
        }

        message.append("🍕 <b>Нам очень важно ваше мнение!</b>\n");
        message.append("Расскажите, понравился ли вам заказ, и помогите нам стать еще лучше.\n\n");
        
        message.append("👆 <b>Оставить отзыв:</b>\n");
        message.append("<a href=\"https://ya.cc/t/ldDY0YvB7VsBa8\">🔗 Перейти к форме отзыва</a>\n\n");
        
        message.append("💙 <b>Спасибо, что выбираете ДИМБО ПИЦЦА!</b>");

        return message.toString();
    }

    /**
     * Отправка персонального сообщения пользователю
     */
    public void sendPersonalMessage(Long telegramId, String text) {
        try {
            String url = telegramAuthProperties.getApiUrl() + "/sendMessage";

            TelegramPersonalMessage telegramMessage = new TelegramPersonalMessage();
            telegramMessage.setChatId(telegramId.toString());
            telegramMessage.setText(text);
            telegramMessage.setParseMode("HTML");

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);

            HttpEntity<TelegramPersonalMessage> entity = new HttpEntity<>(telegramMessage, headers);

            ResponseEntity<String> response = telegramAuthRestTemplate.postForEntity(url, entity, String.class);

            if (response.getStatusCode().is2xxSuccessful()) {
                log.debug("Персональное Telegram сообщение отправлено пользователю: {}", telegramId);
            } else {
                log.error("Ошибка отправки персонального Telegram сообщения пользователю {}: {}",
                        telegramId, response.getStatusCode());
            }

        } catch (Exception e) {
            log.error("Ошибка при отправке персонального Telegram сообщения пользователю {}: {}",
                    telegramId, e.getMessage(), e);
        }
    }

    /**
     * DTO для персональных сообщений Telegram API
     */
    @Data
    private static class TelegramPersonalMessage {
        @JsonProperty("chat_id")
        private String chatId;

        private String text;

        @JsonProperty("parse_mode")
        private String parseMode;
    }
}