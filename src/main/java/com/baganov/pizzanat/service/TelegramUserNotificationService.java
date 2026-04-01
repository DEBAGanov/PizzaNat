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
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

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
     * Отправка простого уведомления пользователю об успешной оплате заказа
     *
     * @param order заказ
     */
    public void sendSimplePaymentSuccessNotification(Order order) {
        if (!isNotificationEnabled() || !hasUserTelegramId(order)) {
            return;
        }

        try {
            // Простое сообщение: "Заказ номер ХХ успешно оплачен сумма (дата)"
            String message = String.format("Заказ номер %d успешно оплачен %s ₽ (%s)", 
                order.getId(), 
                order.getTotalAmount(), 
                java.time.LocalDateTime.now().format(DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm")));
            
            sendPersonalMessage(order.getUser().getTelegramId(), message);

            log.info("Простое уведомление об оплате заказа #{} отправлено пользователю {} (Telegram ID: {})",
                    order.getId(), order.getUser().getUsername(), order.getUser().getTelegramId());

        } catch (Exception e) {
            log.error("Ошибка отправки простого уведомления об оплате заказа #{} пользователю {}: {}",
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
     * Отправка персонального сообщения пользователю
     * @return true если успешно, false если ошибка (пользователь заблокировал бота, чат не найден и т.д.)
     */
    public boolean sendPersonalMessage(Long telegramId, String text) {
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
                return true;
            } else {
                int statusCode = response.getStatusCode().value();
                // 403 = пользователь заблокировал бота, 400 = чат не найден
                if (statusCode == 403) {
                    log.debug("⚠️ Пользователь {} заблокировал бота @DIMBOpizzaBot", telegramId);
                } else if (statusCode == 400) {
                    log.debug("⚠️ Чат не найден для пользователя {} (не начинал диалог с ботом)", telegramId);
                } else {
                    log.warn("Ошибка отправки сообщения пользователю {}: HTTP {}", telegramId, statusCode);
                }
                return false;
            }

        } catch (Exception e) {
            // Обрабатываем HTTP ошибки из исключения
            String errorMsg = e.getMessage();
            if (errorMsg != null) {
                if (errorMsg.contains("403 Forbidden") || errorMsg.contains("Forbidden: bot was blocked")) {
                    log.debug("⚠️ Пользователь {} заблокировал бота @DIMBOpizzaBot", telegramId);
                    return false;
                } else if (errorMsg.contains("400 Bad Request") || errorMsg.contains("chat not found")) {
                    log.debug("⚠️ Чат не найден для пользователя {}", telegramId);
                    return false;
                }
            }
            log.error("Ошибка при отправке персонального Telegram сообщения пользователю {}: {}",
                    telegramId, errorMsg);
            return false;
        }
    }

    /**
     * Отправка фото с подписью пользователю
     * @param telegramId ID пользователя в Telegram
     * @param photoUrl    URL фото или file_id
     * @param caption      Текст под фото (может содержать HTML)
     * @return true если успешно, false если ошибка
     */
    public boolean sendPersonalPhoto(Long telegramId, String photoUrl, String caption) {
        try {
            String url = telegramAuthProperties.getApiUrl() + "/sendPhoto";

            Map<String, Object> photoMessage = new HashMap<>();
            photoMessage.put("chat_id", telegramId.toString());
            photoMessage.put("photo", photoUrl);
            photoMessage.put("caption", caption);
            photoMessage.put("parse_mode", "HTML");

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);

            HttpEntity<Map<String, Object>> entity = new HttpEntity<>(photoMessage, headers);

            ResponseEntity<String> response = telegramAuthRestTemplate.postForEntity(url, entity, String.class);

            if (response.getStatusCode().is2xxSuccessful()) {
                log.debug("Фото отправлено пользователю: {}", telegramId);
                return true;
            } else {
                int statusCode = response.getStatusCode().value();
                if (statusCode == 403) {
                    log.debug("⚠️ Пользователь {} заблокировал бота @DIMBOpizzaBot", telegramId);
                } else if (statusCode == 400) {
                    log.debug("⚠️ Чат не найден для пользователя {}", telegramId);
                } else {
                    log.warn("Ошибка отправки фото пользователю {}: HTTP {}", telegramId, statusCode);
                }
                return false;
            }

        } catch (Exception e) {
            String errorMsg = e.getMessage();
            if (errorMsg != null) {
                if (errorMsg.contains("403 Forbidden") || errorMsg.contains("Forbidden: bot was blocked")) {
                    log.debug("⚠️ Пользователь {} заблокировал бота @DIMBOpizzaBot", telegramId);
                    return false;
                } else if (errorMsg.contains("400 Bad Request") || errorMsg.contains("chat not found")) {
                    log.debug("⚠️ Чат не найден для пользователя {}", telegramId);
                    return false;
                }
            }
            log.error("Ошибка при отправке фото пользователю {}: {}", telegramId, errorMsg);
            return false;
        }
    }

    /**
     * Отправка текстового сообщения с inline кнопками пользователю
     * @param telegramId ID пользователя в Telegram
     * @param text       Текст сообщения
     * @param buttons    Inline кнопки в формате Telegram API (List<List<Map>>)
     * @return true если успешно, false если ошибка
     */
    public boolean sendPersonalMessageWithButtons(Long telegramId, String text, List<List<Map<String, Object>>> buttons) {
        try {
            String url = telegramAuthProperties.getApiUrl() + "/sendMessage";

            Map<String, Object> message = new HashMap<>();
            message.put("chat_id", telegramId.toString());
            message.put("text", text);
            message.put("parse_mode", "HTML");
            message.put("reply_markup", Map.of("inline_keyboard", buttons));

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);

            HttpEntity<Map<String, Object>> entity = new HttpEntity<>(message, headers);

            ResponseEntity<String> response = telegramAuthRestTemplate.postForEntity(url, entity, String.class);

            if (response.getStatusCode().is2xxSuccessful()) {
                log.debug("Сообщение с кнопками отправлено пользователю: {}", telegramId);
                return true;
            } else {
                int statusCode = response.getStatusCode().value();
                if (statusCode == 403) {
                    log.debug("⚠️ Пользователь {} заблокировал бота @DIMBOpizzaBot", telegramId);
                } else if (statusCode == 400) {
                    log.debug("⚠️ Чат не найден для пользователя {}", telegramId);
                } else {
                    log.warn("Ошибка отправки сообщения пользователю {}: HTTP {}", telegramId, statusCode);
                }
                return false;
            }

        } catch (Exception e) {
            String errorMsg = e.getMessage();
            if (errorMsg != null && (errorMsg.contains("403 Forbidden") || errorMsg.contains("Forbidden: bot was blocked"))) {
                log.debug("⚠️ Пользователь {} заблокировал бота @DIMBOpizzaBot", telegramId);
                return false;
            }
            log.error("Ошибка при отправке сообщения с кнопками пользователю {}: {}", telegramId, errorMsg);
            return false;
        }
    }

    /**
     * Отправка фото с подписью и inline кнопками пользователю
     * @param telegramId ID пользователя в Telegram
     * @param photoUrl   URL фото
     * @param caption    Текст под фото
     * @param buttons    Inline кнопки в формате Telegram API (List<List<Map>>)
     * @return true если успешно, false если ошибка
     */
    public boolean sendPersonalPhotoWithButtons(Long telegramId, String photoUrl, String caption, List<List<Map<String, Object>>> buttons) {
        try {
            String url = telegramAuthProperties.getApiUrl() + "/sendPhoto";

            Map<String, Object> photoMessage = new HashMap<>();
            photoMessage.put("chat_id", telegramId.toString());
            photoMessage.put("photo", photoUrl);
            photoMessage.put("caption", caption);
            photoMessage.put("parse_mode", "HTML");
            photoMessage.put("reply_markup", Map.of("inline_keyboard", buttons));

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);

            HttpEntity<Map<String, Object>> entity = new HttpEntity<>(photoMessage, headers);

            ResponseEntity<String> response = telegramAuthRestTemplate.postForEntity(url, entity, String.class);

            if (response.getStatusCode().is2xxSuccessful()) {
                log.debug("Фото с кнопками отправлено пользователю: {}", telegramId);
                return true;
            } else {
                int statusCode = response.getStatusCode().value();
                if (statusCode == 403) {
                    log.debug("⚠️ Пользователь {} заблокировал бота @DIMBOpizzaBot", telegramId);
                } else if (statusCode == 400) {
                    log.debug("⚠️ Чат не найден для пользователя {}", telegramId);
                } else {
                    log.warn("Ошибка отправки фото пользователю {}: HTTP {}", telegramId, statusCode);
                }
                return false;
            }

        } catch (Exception e) {
            String errorMsg = e.getMessage();
            if (errorMsg != null && (errorMsg.contains("403 Forbidden") || errorMsg.contains("Forbidden: bot was blocked"))) {
                log.debug("⚠️ Пользователь {} заблокировал бота @DIMBOpizzaBot", telegramId);
                return false;
            }
            log.error("Ошибка при отправке фото с кнопками пользователю {}: {}", telegramId, errorMsg);
            return false;
        }
    }

    /**
     * Создает стандартные inline кнопки для рассылки
     * Формат: List<List<Map>> где каждый внутренний List - это строка кнопок
     * Использует web_app для открытия Mini App вместо обычного URL
     * @return список строк с кнопками в формате Telegram API
     */
    public static List<List<Map<String, Object>>> createBroadcastButtons() {
        List<List<Map<String, Object>>> buttons = new ArrayList<>();

        // Строка 1: 🍕 Открыть меню - открывает Telegram Mini App
        Map<String, Object> menuButton = new HashMap<>();
        menuButton.put("text", "🍕 Открыть меню");
        // Используем web_app для открытия Mini App
        menuButton.put("web_app", Map.of("url", "https://api.dimbopizza.ru/miniapp/menu.html"));
        buttons.add(List.of(menuButton));

        // Строка 2: 📞 Связь с поддержкой - открывает чат с ботом
        Map<String, Object> supportButton = new HashMap<>();
        supportButton.put("text", "📞 Связь с поддержкой");
        supportButton.put("url", "https://max.ru/u/f9LHodD0cOLR83c5F5U0c2SbgWoa7PRiBiEsz8WYMGec4cgJATw4If-f_Nc");
        buttons.add(List.of(supportButton));

        return buttons;
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