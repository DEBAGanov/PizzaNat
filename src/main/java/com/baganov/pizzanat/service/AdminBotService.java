/**
 * @file: AdminBotService.java
 * @description: Сервис для работы с админским Telegram ботом
 * @dependencies: AdminBotRepository, OrderService, UserService
 * @created: 2025-06-13
 */
package com.baganov.pizzanat.service;

import com.baganov.pizzanat.entity.Order;
import com.baganov.pizzanat.entity.OrderItem;
import com.baganov.pizzanat.entity.OrderStatus;
import com.baganov.pizzanat.model.dto.order.OrderDTO;
import com.baganov.pizzanat.model.entity.TelegramAdminUser;
import com.baganov.pizzanat.repository.TelegramAdminUserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.InlineKeyboardMarkup;
import com.baganov.pizzanat.event.NewOrderEvent;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Async;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Optional;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional
public class AdminBotService {

    private final TelegramAdminUserRepository adminUserRepository;
    private final OrderService orderService;
    private final TelegramAdminNotificationService telegramAdminNotificationService;

    /**
     * Регистрация администратора
     */
    public boolean registerAdmin(Long telegramChatId, String username, String firstName) {
        try {
            // Проверяем, не зарегистрирован ли уже
            Optional<TelegramAdminUser> existing = adminUserRepository.findByTelegramChatId(telegramChatId);
            if (existing.isPresent()) {
                log.info("Администратор уже зарегистрирован: chatId={}, username={}", telegramChatId, username);
                return false;
            }

            // Создаем нового администратора
            TelegramAdminUser adminUser = TelegramAdminUser.builder()
                    .telegramChatId(telegramChatId)
                    .username(username)
                    .firstName(firstName)
                    .isActive(true)
                    .registeredAt(LocalDateTime.now())
                    .build();

            adminUserRepository.save(adminUser);
            log.info("Зарегистрирован новый администратор: chatId={}, username={}", telegramChatId, username);
            return true;

        } catch (Exception e) {
            log.error("Ошибка при регистрации администратора: chatId={}, error={}", telegramChatId, e.getMessage(), e);
            return false;
        }
    }

    /**
     * Проверка, является ли пользователь зарегистрированным администратором
     */
    public boolean isRegisteredAdmin(Long telegramChatId) {
        return adminUserRepository.findByTelegramChatIdAndIsActiveTrue(telegramChatId).isPresent();
    }

    /**
     * Уведомление всех администраторов о новом заказе
     */
    public void notifyAdminsAboutNewOrder(Order order) {
        try {
            List<TelegramAdminUser> activeAdmins = adminUserRepository.findByIsActiveTrue();

            if (activeAdmins.isEmpty()) {
                log.warn("Нет активных администраторов для уведомления о заказе #{}", order.getId());
                return;
            }

            String orderMessage = formatNewOrderMessage(order);
            InlineKeyboardMarkup keyboard = telegramAdminNotificationService
                    .createOrderManagementKeyboard(order.getId().longValue());

            for (TelegramAdminUser admin : activeAdmins) {
                try {
                    telegramAdminNotificationService.sendMessageWithButtons(admin.getTelegramChatId(), orderMessage,
                            keyboard);
                    log.debug("Уведомление о заказе #{} отправлено администратору: {}", order.getId(),
                            admin.getUsername());
                } catch (Exception e) {
                    log.error("Ошибка отправки уведомления администратору {}: {}", admin.getUsername(), e.getMessage());
                }
            }

            log.info("Уведомления о заказе #{} отправлены {} администраторам", order.getId(), activeAdmins.size());

        } catch (Exception e) {
            log.error("Ошибка при уведомлении администраторов о заказе #{}: {}", order.getId(), e.getMessage(), e);
        }
    }

    /**
     * Обработка изменения статуса заказа через кнопки
     */
    public void handleOrderStatusChange(Long chatId, Integer messageId, String callbackData) {
        try {
            // Парсим callback data: order_status_{orderId}_{newStatus}
            String[] parts = callbackData.split("_");
            if (parts.length != 4) {
                log.error("Некорректный формат callback data: {}", callbackData);
                return;
            }

            Long orderId = Long.parseLong(parts[2]);
            String newStatusStr = parts[3];

            // Обновляем статус заказа с отправкой уведомлений пользователям
            try {
                // Проверяем текущий статус заказа перед изменением
                Optional<Order> orderOpt = orderService.findById(orderId);
                if (orderOpt.isPresent()) {
                    Order currentOrder = orderOpt.get();
                    String currentStatus = currentOrder.getStatus().getName();

                    // Если статус уже установлен, не выполняем изменение
                    if (newStatusStr.equalsIgnoreCase(currentStatus)) {
                        String alreadySetMessage = String.format(
                                "ℹ️ *Статус заказа #%d уже установлен*\n\n" +
                                        "Текущий статус: %s\n" +
                                        "Изменений не требуется",
                                orderId,
                                getStatusDisplayNameByString(newStatusStr));
                        telegramAdminNotificationService.sendMessage(chatId, alreadySetMessage, true);

                        log.info("Статус заказа #{} уже установлен на {}, пропускаем изменение", orderId, newStatusStr);
                        return;
                    }
                }

                OrderDTO updatedOrder = orderService.updateOrderStatus(orderId.intValue(), newStatusStr);

                String statusDisplayName = getStatusDisplayNameByString(newStatusStr);

                String successMessage = String.format(
                        "✅ *Статус заказа #%d изменен*\n\n" +
                                "Новый статус: %s\n" +
                                "Изменено: %s\n\n" +
                                "📱 Уведомление отправлено пользователю",
                        orderId,
                        statusDisplayName,
                        LocalDateTime.now().format(DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm")));
                telegramAdminNotificationService.sendMessage(chatId, successMessage, true);

                log.info("Статус заказа #{} изменен на {} администратором chatId={} (с уведомлением пользователю)",
                        orderId, newStatusStr, chatId);
            } catch (Exception e) {
                log.error("Ошибка при изменении статуса заказа #{}: {}", orderId, e.getMessage());
                telegramAdminNotificationService.sendMessage(chatId, "❌ Ошибка при изменении статуса заказа", false);
            }

        } catch (Exception e) {
            log.error("Ошибка при обработке изменения статуса заказа: {}", e.getMessage(), e);
            telegramAdminNotificationService.sendMessage(chatId, "❌ Произошла ошибка при обработке запроса", false);
        }
    }

    /**
     * Обработка запроса деталей заказа
     */
    public void handleOrderDetailsRequest(Long chatId, String callbackData) {
        try {
            // Парсим callback data: order_details_{orderId}
            String[] parts = callbackData.split("_");
            if (parts.length != 3) {
                log.error("Некорректный формат callback data: {}", callbackData);
                return;
            }

            Long orderId = Long.parseLong(parts[2]);
            Optional<Order> orderOpt = orderService.findById(orderId);

            if (orderOpt.isPresent()) {
                String detailsMessage = formatOrderDetailsMessage(orderOpt.get());
                telegramAdminNotificationService.sendMessage(chatId, detailsMessage, true);
            } else {
                telegramAdminNotificationService.sendMessage(chatId, "❌ Заказ не найден", false);
            }

        } catch (Exception e) {
            log.error("Ошибка при получении деталей заказа: {}", e.getMessage(), e);
            telegramAdminNotificationService.sendMessage(chatId, "❌ Произошла ошибка при получении деталей заказа",
                    false);
        }
    }

    /**
     * Получение статистики заказов
     */
    public String getOrdersStats() {
        try {
            LocalDate today = LocalDate.now();
            LocalDateTime startOfDay = today.atStartOfDay();
            LocalDateTime endOfDay = today.plusDays(1).atStartOfDay();

            // Получаем статистику за сегодня
            List<Order> todayOrders = orderService.findOrdersByDateRange(startOfDay, endOfDay);

            long totalOrders = todayOrders.size();
            BigDecimal totalRevenue = todayOrders.stream()
                    .map(Order::getTotalAmount)
                    .reduce(BigDecimal.ZERO, BigDecimal::add);

            // Статистика по статусам
            long pendingCount = todayOrders.stream()
                    .filter(o -> "CREATED".equals(o.getStatus().getName()) || "PENDING".equals(o.getStatus().getName()))
                    .count();
            long confirmedCount = todayOrders.stream().filter(o -> "CONFIRMED".equals(o.getStatus().getName())).count();
            long preparingCount = todayOrders.stream().filter(
                    o -> "PREPARING".equals(o.getStatus().getName()) || "COOKING".equals(o.getStatus().getName()))
                    .count();
            long readyCount = todayOrders.stream().filter(o -> "READY".equals(o.getStatus().getName())).count();
            long deliveringCount = todayOrders.stream().filter(o -> "DELIVERING".equals(o.getStatus().getName()))
                    .count();
            long deliveredCount = todayOrders.stream().filter(
                    o -> "DELIVERED".equals(o.getStatus().getName()) || "COMPLETED".equals(o.getStatus().getName()))
                    .count();
            long cancelledCount = todayOrders.stream().filter(
                    o -> "CANCELLED".equals(o.getStatus().getName()) || "CANCELED".equals(o.getStatus().getName()))
                    .count();

            return String.format(
                    "📊 *Статистика заказов за %s*\n\n" +
                            "📦 Всего заказов: %d\n" +
                            "💰 Общая сумма: %.2f ₽\n\n" +
                            "*По статусам:*\n" +
                            "🆕 Новые: %d\n" +
                            "✅ Подтвержденные: %d\n" +
                            "👨‍🍳 Готовятся: %d\n" +
                            "🍕 Готовы: %d\n" +
                            "🚗 Доставляются: %d\n" +
                            "✅ Доставлены: %d\n" +
                            "❌ Отменены: %d",
                    today.format(DateTimeFormatter.ofPattern("dd.MM.yyyy")),
                    totalOrders, totalRevenue,
                    pendingCount, confirmedCount, preparingCount,
                    readyCount, deliveringCount, deliveredCount, cancelledCount);

        } catch (Exception e) {
            log.error("Ошибка при получении статистики заказов: {}", e.getMessage(), e);
            return "❌ Ошибка при получении статистики";
        }
    }

    /**
     * Получение списка активных заказов
     */
    public String getActiveOrders() {
        try {
            List<Order> activeOrders = orderService.findActiveOrders();

            if (activeOrders.isEmpty()) {
                return "📋 *Активные заказы*\n\nНет активных заказов";
            }

            StringBuilder message = new StringBuilder("📋 *Активные заказы*\n\n");

            for (Order order : activeOrders) {
                message.append(String.format(
                        "🔸 *Заказ #%d*\n" +
                                "Статус: %s\n" +
                                "Сумма: %.2f ₽\n" +
                                "Время: %s\n\n",
                        order.getId(),
                        getStatusDisplayName(order.getStatus()),
                        order.getTotalAmount(),
                        order.getCreatedAt().format(DateTimeFormatter.ofPattern("dd.MM HH:mm"))));
            }

            return message.toString();

        } catch (Exception e) {
            log.error("Ошибка при получении активных заказов: {}", e.getMessage(), e);
            return "❌ Ошибка при получении списка заказов";
        }
    }

    /**
     * Экранирование специальных символов для Markdown
     */
    private String escapeMarkdown(String text) {
        if (text == null) {
            return "";
        }

        // Экранируем специальные символы Markdown
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

    /**
     * Форматирование сообщения о новом заказе
     */
    private String formatNewOrderMessage(Order order) {
        StringBuilder message = new StringBuilder();
        message.append("🆕 *НОВЫЙ ЗАКАЗ #").append(order.getId()).append("*\n\n");

        message.append("🕐 *Время заказа:* ")
                .append(order.getCreatedAt().format(DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm"))).append("\n\n");

        // Информация о пользователе из системы
        if (order.getUser() != null) {
            message.append("👤 *ПОЛЬЗОВАТЕЛЬ СИСТЕМЫ*\n");
            message.append("Имя: ").append(escapeMarkdown(order.getUser().getFirstName()));
            if (order.getUser().getLastName() != null) {
                message.append(" ").append(escapeMarkdown(order.getUser().getLastName()));
            }
            message.append("\n");

            if (order.getUser().getUsername() != null) {
                message.append("Username: @").append(escapeMarkdown(order.getUser().getUsername())).append("\n");
            }

            if (order.getUser().getPhone() != null) {
                message.append("Телефон: ").append(escapeMarkdown(order.getUser().getPhone())).append("\n");
            }

            if (order.getUser().getEmail() != null) {
                message.append("Email: ").append(escapeMarkdown(order.getUser().getEmail())).append("\n");
            }
            message.append("\n");
        }

        // Контактные данные заказа
        message.append("📞 *КОНТАКТНЫЕ ДАННЫЕ ЗАКАЗА*\n");
        message.append("Имя: ").append(escapeMarkdown(order.getContactName())).append("\n");
        message.append("Телефон: ").append(escapeMarkdown(order.getContactPhone())).append("\n\n");

        // Адрес доставки
        if (order.getDeliveryAddress() != null) {
            message.append("📍 *ДОСТАВКА*\n");
            message.append("Адрес: ").append(escapeMarkdown(order.getDeliveryAddress())).append("\n\n");
        } else if (order.getDeliveryLocation() != null) {
            message.append("📍 *ПУНКТ ВЫДАЧИ*\n");
            message.append("Адрес: ").append(escapeMarkdown(order.getDeliveryLocation().getAddress())).append("\n\n");
        }

        // Комментарий
        if (order.getComment() != null && !order.getComment().trim().isEmpty()) {
            message.append("💬 *Комментарий:* ").append(escapeMarkdown(order.getComment())).append("\n\n");
        }

        // Состав заказа
        message.append("🛒 *СОСТАВ ЗАКАЗА*\n");
        for (OrderItem item : order.getItems()) {
            message.append("• ").append(escapeMarkdown(item.getProduct().getName()))
                    .append(" x").append(item.getQuantity())
                    .append(" = ").append(item.getPrice().multiply(BigDecimal.valueOf(item.getQuantity())))
                    .append(" ₽\n");
        }

        message.append("\n💰 *Общая сумма:* ").append(order.getTotalAmount()).append(" ₽");

        return message.toString();
    }

    /**
     * Форматирование детальной информации о заказе
     */
    private String formatOrderDetailsMessage(Order order) {
        StringBuilder message = new StringBuilder();
        message.append("📋 *ДЕТАЛИ ЗАКАЗА #").append(order.getId()).append("*\n\n");

        message.append("📊 *Статус:* ").append(getStatusDisplayName(order.getStatus())).append("\n");
        message.append("🕐 *Создан:* ")
                .append(order.getCreatedAt().format(DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm"))).append("\n");

        if (order.getUpdatedAt() != null && !order.getUpdatedAt().equals(order.getCreatedAt())) {
            message.append("🔄 *Обновлен:* ")
                    .append(order.getUpdatedAt().format(DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm"))).append("\n");
        }

        // Информация о пользователе системы
        if (order.getUser() != null) {
            message.append("\n👤 *ПОЛЬЗОВАТЕЛЬ СИСТЕМЫ*\n");
            message.append("Имя: ").append(escapeMarkdown(order.getUser().getFirstName()));
            if (order.getUser().getLastName() != null) {
                message.append(" ").append(escapeMarkdown(order.getUser().getLastName()));
            }
            message.append("\n");

            if (order.getUser().getUsername() != null) {
                message.append("Username: @").append(escapeMarkdown(order.getUser().getUsername())).append("\n");
            }

            if (order.getUser().getPhone() != null) {
                message.append("Телефон пользователя: ").append(escapeMarkdown(order.getUser().getPhone()))
                        .append("\n");
            }

            if (order.getUser().getEmail() != null) {
                message.append("Email: ").append(escapeMarkdown(order.getUser().getEmail())).append("\n");
            }
        }

        // Контактные данные заказа
        message.append("\n📞 *КОНТАКТНЫЕ ДАННЫЕ ЗАКАЗА*\n");
        message.append("Имя: ").append(escapeMarkdown(order.getContactName())).append("\n");
        message.append("Телефон: ").append(escapeMarkdown(order.getContactPhone())).append("\n");

        // Адрес доставки
        if (order.getDeliveryAddress() != null) {
            message.append("\n📍 *ДОСТАВКА*\n");
            message.append("Адрес: ").append(escapeMarkdown(order.getDeliveryAddress())).append("\n");
        }

        // Детальный состав заказа
        message.append("\n🛒 *СОСТАВ ЗАКАЗА*\n");
        BigDecimal totalAmount = BigDecimal.ZERO;

        for (OrderItem item : order.getItems()) {
            BigDecimal itemTotal = item.getPrice().multiply(BigDecimal.valueOf(item.getQuantity()));
            totalAmount = totalAmount.add(itemTotal);

            message.append("• ").append(escapeMarkdown(item.getProduct().getName())).append("\n");
            message.append("  Цена: ").append(item.getPrice()).append(" ₽\n");
            message.append("  Количество: ").append(item.getQuantity()).append("\n");
            message.append("  Сумма: ").append(itemTotal).append(" ₽\n\n");
        }

        message.append("💰 *ИТОГО: ").append(order.getTotalAmount()).append(" ₽*");

        if (order.getComment() != null && !order.getComment().trim().isEmpty()) {
            message.append("\n\n💬 *КОММЕНТАРИЙ*\n").append(escapeMarkdown(order.getComment()));
        }

        return message.toString();
    }

    /**
     * Получение отображаемого названия статуса
     */
    private String getStatusDisplayName(OrderStatus status) {
        if (status == null || status.getName() == null) {
            return "❓ Неизвестно";
        }

        return getStatusDisplayNameByString(status.getName());
    }

    /**
     * Получение отображаемого названия статуса по строке
     */
    private String getStatusDisplayNameByString(String statusName) {
        if (statusName == null) {
            return "❓ Неизвестно";
        }

        switch (statusName.toUpperCase()) {
            case "PENDING":
                return "🆕 Новый";
            case "CONFIRMED":
                return "✅ Подтвержден";
            case "PREPARING":
                return "👨‍🍳 Готовится";
            case "COOKING":
                return "👨‍🍳 Готовится";
            case "READY":
                return "🍕 Готов";
            case "DELIVERING":
                return "🚗 Доставляется";
            case "DELIVERED":
                return "✅ Доставлен";
            case "CANCELLED":
                return "❌ Отменен";
            case "CREATED":
                return "📝 Создан";
            case "PAID":
                return "💰 Оплачен";
            default:
                return statusName;
        }
    }

    /**
     * Отправка сообщения конкретному администратору
     */
    public void sendMessageToAdmin(Long chatId, String message) {
        try {
            telegramAdminNotificationService.sendMessage(chatId, message, true);
            log.debug("Сообщение отправлено администратору: chatId={}", chatId);
        } catch (Exception e) {
            log.error("Ошибка отправки сообщения администратору chatId={}: {}", chatId, e.getMessage(), e);
        }
    }

    /**
     * Отправка активных заказов с кнопками управления
     */
    public void sendActiveOrdersWithButtons(Long chatId) {
        try {
            List<Order> activeOrders = orderService.findActiveOrdersIncludingNew();

            if (activeOrders.isEmpty()) {
                telegramAdminNotificationService.sendMessage(chatId, "📋 *Активные заказы*\n\nНет активных заказов",
                        true);
                return;
            }

            // Отправляем заголовок
            telegramAdminNotificationService.sendMessage(chatId, "📋 *Активные заказы (включая новые)*", true);

            // Отправляем каждый заказ отдельным сообщением с кнопками
            for (Order order : activeOrders) {
                StringBuilder orderMessage = new StringBuilder();
                orderMessage.append("🔸 *Заказ #").append(order.getId()).append("*\n");
                orderMessage.append("Статус: ").append(getStatusDisplayName(order.getStatus())).append("\n");
                orderMessage.append("Сумма: ").append(String.format("%.2f", order.getTotalAmount())).append(" ₽\n");
                orderMessage.append("Время: ")
                        .append(order.getCreatedAt().format(DateTimeFormatter.ofPattern("dd.MM HH:mm"))).append("\n\n");

                // Информация о пользователе системы
                if (order.getUser() != null) {
                    orderMessage.append("👤 *Пользователь:* ");
                    orderMessage.append(escapeMarkdown(order.getUser().getFirstName()));
                    if (order.getUser().getLastName() != null) {
                        orderMessage.append(" ").append(escapeMarkdown(order.getUser().getLastName()));
                    }
                    if (order.getUser().getUsername() != null) {
                        orderMessage.append(" (@").append(escapeMarkdown(order.getUser().getUsername())).append(")");
                    }
                    orderMessage.append("\n");

                    if (order.getUser().getPhone() != null) {
                        orderMessage.append("📱 *Телефон пользователя:* ")
                                .append(escapeMarkdown(order.getUser().getPhone()))
                                .append("\n");
                    }
                    orderMessage.append("\n");
                }

                // Контактные данные заказа
                orderMessage.append("📞 *Контакт заказа:* ").append(escapeMarkdown(order.getContactName()))
                        .append("\n");
                orderMessage.append("📞 *Телефон заказа:* ").append(escapeMarkdown(order.getContactPhone()));

                String finalMessage = orderMessage.toString();

                InlineKeyboardMarkup keyboard = telegramAdminNotificationService
                        .createOrderManagementKeyboard(order.getId().longValue());

                telegramAdminNotificationService.sendMessageWithButtons(chatId, finalMessage, keyboard);
            }

            log.debug("Отправлено {} активных заказов с кнопками администратору: chatId={}", activeOrders.size(),
                    chatId);

        } catch (Exception e) {
            log.error("Ошибка при отправке активных заказов с кнопками: {}", e.getMessage(), e);
            telegramAdminNotificationService.sendMessage(chatId, "❌ Ошибка при получении списка заказов", false);
        }
    }

    /**
     * Обработчик события создания нового заказа
     */
    @EventListener
    @Async
    public void handleNewOrderEvent(NewOrderEvent event) {
        try {
            notifyAdminsAboutNewOrder(event.getOrder());
            log.debug("Уведомление админского бота о новом заказе #{} отправлено", event.getOrder().getId());
        } catch (Exception e) {
            log.error("Ошибка обработки события нового заказа #{}: {}", event.getOrder().getId(), e.getMessage(), e);
        }
    }
}