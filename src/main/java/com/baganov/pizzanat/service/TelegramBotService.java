/**
 * @file: TelegramBotService.java
 * @description: Сервис для отправки уведомлений в Telegram бот с поддержкой условного включения
 * @dependencies: Spring Web, Jackson
 * @created: 2025-05-31
 * @updated: 2025-01-15 - добавлена поддержка условного включения ботов
 */
package com.baganov.pizzanat.service;

import com.baganov.pizzanat.config.TelegramConfig;
import com.baganov.pizzanat.entity.Order;
import com.baganov.pizzanat.entity.OrderItem;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.math.BigDecimal;
import java.time.format.DateTimeFormatter;

@Slf4j
@Service
@RequiredArgsConstructor
public class TelegramBotService {

    private final TelegramConfig.TelegramProperties telegramProperties;
    private final RestTemplate telegramRestTemplate;

    @Value("${telegram.enabled:true}")
    private boolean telegramEnabled;

    @Value("${telegram.bot.enabled:true}")
    private boolean mainBotEnabled;

    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm");

    /**
     * Отправка уведомления о создании нового заказа
     */
    public void sendNewOrderNotification(Order order) {
        if (!isTelegramNotificationsEnabled()) {
            log.debug("🚫 Telegram уведомления отключены");
            return;
        }

        String message = formatNewOrderMessage(order);
        sendMessage(message);
    }

    /**
     * Отправка уведомления об изменении статуса заказа
     */
    public void sendOrderStatusUpdateNotification(Order order, String oldStatus, String newStatus) {
        if (!isTelegramNotificationsEnabled()) {
            log.debug("🚫 Telegram уведомления отключены");
            return;
        }

        String message = formatStatusUpdateMessage(order, oldStatus, newStatus);
        sendMessage(message);
    }

    /**
     * Проверяет, включены ли Telegram уведомления
     */
    private boolean isTelegramNotificationsEnabled() {
        if (!telegramEnabled) {
            log.debug("Telegram полностью отключен (TELEGRAM_ENABLED=false)");
            return false;
        }

        if (!mainBotEnabled) {
            log.debug("Основной Telegram бот отключен (TELEGRAM_BOT_ENABLED=false)");
            return false;
        }

        if (!telegramProperties.isEnabled()) {
            log.debug("Telegram уведомления отключены в конфигурации");
            return false;
        }

        return true;
    }

    /**
     * Форматирование сообщения о новом заказе
     */
    private String formatNewOrderMessage(Order order) {
        StringBuilder message = new StringBuilder();
        message.append("🍕 <b>НОВЫЙ ЗАКАЗ #").append(order.getId()).append("</b>\n\n");

        message.append("📅 <b>Дата:</b> ").append(order.getCreatedAt().format(DATE_FORMATTER)).append("\n");

        // Информация о пользователе системы
        if (order.getUser() != null) {
            message.append("👤 <b>ПОЛЬЗОВАТЕЛЬ СИСТЕМЫ</b>\n");
            message.append("Имя: ").append(order.getUser().getFirstName());
            if (order.getUser().getLastName() != null) {
                message.append(" ").append(order.getUser().getLastName());
            }
            message.append("\n");

            if (order.getUser().getUsername() != null) {
                message.append("Username: @").append(order.getUser().getUsername()).append("\n");
            }

            if (order.getUser().getPhone() != null) {
                message.append("Телефон пользователя: ").append(order.getUser().getPhone()).append("\n");
            } else if (order.getUser().getPhoneNumber() != null) {
                message.append("Телефон пользователя: ").append(order.getUser().getPhoneNumber()).append("\n");
            }

            if (order.getUser().getEmail() != null) {
                message.append("Email: ").append(order.getUser().getEmail()).append("\n");
            }
            message.append("\n");
        }

        // Контактные данные заказа
        message.append("📞 <b>КОНТАКТНЫЕ ДАННЫЕ ЗАКАЗА</b>\n");
        message.append("Имя: ").append(order.getContactName()).append("\n");
        message.append("Телефон: ").append(order.getContactPhone()).append("\n");

        // Адрес доставки
        if (order.getDeliveryAddress() != null) {
            message.append("📍 <b>Адрес доставки:</b> ").append(order.getDeliveryAddress()).append("\n");
        } else if (order.getDeliveryLocation() != null) {
            message.append("📍 <b>Пункт выдачи:</b> ").append(order.getDeliveryLocation().getAddress()).append("\n");
        }

        // Комментарий
        if (order.getComment() != null && !order.getComment().trim().isEmpty()) {
            message.append("💬 <b>Комментарий:</b> ").append(order.getComment()).append("\n");
        }

        message.append("📋 <b>Статус:</b> ").append(order.getStatus().getName()).append("\n\n");

        // Состав заказа
        message.append("🛒 <b>СОСТАВ ЗАКАЗА:</b>\n");
        for (OrderItem item : order.getItems()) {
            message.append("• ").append(item.getProduct().getName())
                    .append(" x").append(item.getQuantity())
                    .append(" = ").append(item.getPrice()).append(" ₽\n");
        }

        message.append("\n💰 <b>ИТОГО: ").append(order.getTotalAmount()).append(" ₽</b>");

        return message.toString();
    }

    /**
     * Форматирование сообщения об изменении статуса
     */
    private String formatStatusUpdateMessage(Order order, String oldStatus, String newStatus) {
        StringBuilder message = new StringBuilder();
        message.append("🔄 <b>ИЗМЕНЕНИЕ СТАТУСА ЗАКАЗА #").append(order.getId()).append("</b>\n\n");

        // Информация о пользователе системы
        if (order.getUser() != null) {
            message.append("👤 <b>ПОЛЬЗОВАТЕЛЬ СИСТЕМЫ</b>\n");
            message.append("Имя: ").append(order.getUser().getFirstName());
            if (order.getUser().getLastName() != null) {
                message.append(" ").append(order.getUser().getLastName());
            }
            message.append("\n");

            if (order.getUser().getUsername() != null) {
                message.append("Username: @").append(order.getUser().getUsername()).append("\n");
            }

            if (order.getUser().getPhone() != null) {
                message.append("Телефон пользователя: ").append(order.getUser().getPhone()).append("\n");
            } else if (order.getUser().getPhoneNumber() != null) {
                message.append("Телефон пользователя: ").append(order.getUser().getPhoneNumber()).append("\n");
            }
            message.append("\n");
        }

        // Контактные данные заказа
        message.append("📞 <b>КОНТАКТНЫЕ ДАННЫЕ ЗАКАЗА</b>\n");
        message.append("Имя: ").append(order.getContactName()).append("\n");
        message.append("Телефон: ").append(order.getContactPhone()).append("\n");
        message.append("💰 <b>Сумма:</b> ").append(order.getTotalAmount()).append(" ₽\n\n");

        message.append("📋 <b>Статус изменен:</b>\n");
        message.append("❌ Было: ").append(oldStatus).append("\n");
        message.append("✅ Стало: ").append(newStatus);

        return message.toString();
    }

    /**
     * Отправка сообщения в Telegram
     */
    private void sendMessage(String text) {
        try {
            String url = telegramProperties.getApiUrl() + "/sendMessage";

            TelegramMessage telegramMessage = new TelegramMessage();
            telegramMessage.setChatId(telegramProperties.getChatId());
            telegramMessage.setText(text);
            telegramMessage.setParseMode("HTML");

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);

            HttpEntity<TelegramMessage> entity = new HttpEntity<>(telegramMessage, headers);

            ResponseEntity<String> response = telegramRestTemplate.postForEntity(url, entity, String.class);

            if (response.getStatusCode().is2xxSuccessful()) {
                log.info("Telegram уведомление отправлено успешно");
            } else {
                log.error("Ошибка отправки Telegram уведомления: {}", response.getStatusCode());
            }

        } catch (Exception e) {
            log.error("Ошибка при отправке Telegram уведомления: {}", e.getMessage(), e);
        }
    }

    /**
     * DTO для Telegram API
     */
    @Data
    private static class TelegramMessage {
        @JsonProperty("chat_id")
        private String chatId;

        private String text;

        @JsonProperty("parse_mode")
        private String parseMode;
    }
}