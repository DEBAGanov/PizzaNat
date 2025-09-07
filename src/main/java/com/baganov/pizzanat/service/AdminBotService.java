/**
 * @file: AdminBotService.java
 * @description: Сервис для работы с админским Telegram ботом
 * @dependencies: AdminBotRepository, OrderService, UserService, PaymentRepository
 * @created: 2025-06-13
 */
package com.baganov.pizzanat.service;

import com.baganov.pizzanat.entity.Order;
import com.baganov.pizzanat.entity.OrderItem;
import com.baganov.pizzanat.entity.OrderStatus;
import com.baganov.pizzanat.entity.Payment;
import com.baganov.pizzanat.entity.PaymentStatus;
import com.baganov.pizzanat.entity.PaymentMethod;
import com.baganov.pizzanat.entity.OrderPaymentStatus;
import com.baganov.pizzanat.entity.OrderDisplayStatus;
import com.baganov.pizzanat.event.NewOrderEvent;
import com.baganov.pizzanat.event.PaymentAlertEvent;
import java.time.temporal.ChronoUnit;
import com.baganov.pizzanat.model.dto.order.OrderDTO;
import com.baganov.pizzanat.model.entity.TelegramAdminUser;
import com.baganov.pizzanat.repository.TelegramAdminUserRepository;
import com.baganov.pizzanat.repository.PaymentRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.InlineKeyboardMarkup;

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
    private final TelegramUserNotificationService telegramUserNotificationService;
    private final PaymentRepository paymentRepository;
    private final UserService userService;
    private final TelegramRateLimitService rateLimitService;

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

                // Отправляем уведомление пользователю о изменении статуса
                sendStatusNotificationToUser(orderId, newStatusStr);

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
                String detailsMessage = formatOrderDetails(orderOpt.get());
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
     * Обработка запроса на отправку отзыва пользователю
     */
    public void handleOrderReviewRequest(Long chatId, String callbackData) {
        try {
            // Парсим callback data: order_review_{orderId}
            String[] parts = callbackData.split("_");
            if (parts.length != 3) {
                log.error("Некорректный формат callback data: {}", callbackData);
                return;
            }

            Long orderId = Long.parseLong(parts[2]);
            
            // Отправляем запрос на отзыв пользователю (аналогично статусам)
            sendReviewNotificationToUser(orderId);

            // Подтверждаем администратору
            String successMessage = String.format(
                "✅ *Запрос на отзыв отправлен*\n\n" +
                "📋 Заказ #%d\n" +
                "Отправлено: %s\n\n" +
                "📱 Уведомление отправлено пользователю",
                orderId,
                LocalDateTime.now().format(DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm")));
            
            telegramAdminNotificationService.sendMessage(chatId, successMessage, true);
            
            log.info("Запрос на отзыв для заказа #{} отправлен администратором chatId={}", orderId, chatId);

        } catch (Exception e) {
            log.error("Ошибка при отправке запроса на отзыв: {}", e.getMessage(), e);
            telegramAdminNotificationService.sendMessage(chatId, "❌ Произошла ошибка при отправке запроса на отзыв", false);
        }
    }

    /**
     * Отправка запроса на отзыв пользователю (аналогично sendStatusNotificationToUser)
     */
    private void sendReviewNotificationToUser(Long orderId) {
        try {
            // Получаем заказ
            Optional<Order> orderOpt = orderService.findById(orderId);
            if (!orderOpt.isPresent()) {
                log.warn("Заказ #{} не найден для отправки запроса на отзыв", orderId);
                return;
            }

            Order order = orderOpt.get();

            // Проверяем, есть ли у пользователя Telegram ID
            if (order.getUser() == null || order.getUser().getTelegramId() == null) {
                log.info("У заказа #{} нет пользователя с Telegram ID, запрос на отзыв не отправляется", orderId);
                return;
            }

            Long userTelegramId = order.getUser().getTelegramId();

            // Простое сообщение с отзывом (без сложного форматирования)
            String reviewMessage = 
                // "⭐ <b>Поделитесь впечатлениями о заказе!</b>\n\n" +
                // "📋 <b>Заказ #" + order.getId() + "</b>\n\n" +
                "🍕 <b>Нам очень важно ваше мнение!</b>\n" +
                "Расскажите, понравился ли вам заказ, и помогите нам стать еще лучше.\n\n" +
                // "👆 <b>Оставить отзыв:</b>\n" +
                "Оставьте нам отзыв или оценку ⭐⭐⭐⭐⭐\n\n" +
                "<a href=\"https://ya.cc/t/ldDY0YvB7VsBa8\">🔗 Перейти к форме отзыва</a>\n\n" +
                "💙 <b>Спасибо, что выбираете ДИМБО ПИЦЦА!</b>";

            // Отправляем сообщение напрямую через sendPersonalMessage (как статусы)
            telegramUserNotificationService.sendPersonalMessage(userTelegramId, reviewMessage);

            log.info("Запрос на отзыв для заказа #{} отправлен пользователю {}", orderId, userTelegramId);

        } catch (Exception e) {
            log.error("Ошибка отправки запроса на отзыв пользователю для заказа #{}: {}", orderId, e.getMessage(), e);
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
     * Форматирование детального сообщения о новом заказе (включает всю информацию для главного экрана)
     */
    private String formatNewOrderMessage(Order order) {
        // Определяем визуальный статус заказа с учетом реальных данных о платежах
        Payment latestPayment = getLatestPayment(order);
        OrderDisplayStatus displayStatus = determineOrderDisplayStatusFixed(order, latestPayment);
        
        StringBuilder message = new StringBuilder();
        message.append(displayStatus.getEmoji()).append(" *НОВЫЙ ЗАКАЗ #").append(order.getId())
               .append(" ").append(displayStatus.getFormattedStatusWithInfo(latestPayment))
               .append("*\n\n");

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

        // Способ доставки (НОВОЕ ПОЛЕ)
        if (order.getDeliveryType() != null) {
            String deliveryIcon = order.isPickup() ? "🏠" : "🚗";
            message.append("🚛 *Способ доставки:* ").append(deliveryIcon).append(" ")
                    .append(escapeMarkdown(order.getDeliveryType())).append("\n");
        }
        message.append("\n");

        // Комментарий
        if (order.getComment() != null && !order.getComment().trim().isEmpty()) {
            message.append("💬 *Комментарий:* ").append(escapeMarkdown(order.getComment())).append("\n\n");
        }

        // Детальный состав заказа
        message.append("🛒 *СОСТАВ ЗАКАЗА*\n");
        BigDecimal itemsTotal = BigDecimal.ZERO;
        
        for (OrderItem item : order.getItems()) {
            BigDecimal itemSubtotal = item.getPrice().multiply(BigDecimal.valueOf(item.getQuantity()));
            itemsTotal = itemsTotal.add(itemSubtotal);

            message.append("• ").append(escapeMarkdown(item.getProduct().getName()))
                    .append(" x").append(item.getQuantity())
                    .append(" = ").append(itemSubtotal).append(" ₽\n");
        }

        // Детальный расчет суммы (как в детальном просмотре)
        message.append("\n💰 *ДЕТАЛЬНЫЙ РАСЧЕТ СУММЫ:*\n");
        message.append("├ Товары: ").append(itemsTotal).append(" ₽\n");
        
        if (order.getDeliveryCost() != null && order.getDeliveryCost().compareTo(BigDecimal.ZERO) > 0) {
            message.append("├ Доставка: ").append(order.getDeliveryCost()).append(" ₽\n");
        } else if (order.isDeliveryByCourier()) {
            message.append("├ Доставка: БЕСПЛАТНО\n");
        } else if (order.isPickup()) {
            message.append("├ Доставка: Самовывоз (0 ₽)\n");
        }
        
        message.append("└ *ИТОГО: ").append(order.getTotalAmount()).append(" ₽*\n\n");

        // Информация о платеже
        appendPaymentInfo(message, order);

        return message.toString();
    }

    /**
     * Форматирование сообщения о новом заказе с пометкой о способе оплаты
     */
    private String formatNewOrderMessageWithPaymentLabel(Order order, String paymentLabel) {
        StringBuilder message = new StringBuilder();
        message.append("🆕 *НОВЫЙ ЗАКАЗ #").append(order.getId()).append(" ").append(paymentLabel).append("*\n\n");

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

        // Способ доставки
        if (order.getDeliveryType() != null) {
            String deliveryIcon = order.isPickup() ? "🏠" : "🚗";
            message.append("🚛 *Способ доставки:* ").append(deliveryIcon).append(" ")
                    .append(escapeMarkdown(order.getDeliveryType())).append("\n");
        }
        message.append("\n");

        // Комментарий
        if (order.getComment() != null && !order.getComment().trim().isEmpty()) {
            message.append("💬 *Комментарий:* ").append(escapeMarkdown(order.getComment())).append("\n\n");
        }

        // Детальный состав заказа
        message.append("🛒 *СОСТАВ ЗАКАЗА*\n");
        BigDecimal itemsTotal = BigDecimal.ZERO;
        
        for (OrderItem item : order.getItems()) {
            BigDecimal itemSubtotal = item.getPrice().multiply(BigDecimal.valueOf(item.getQuantity()));
            itemsTotal = itemsTotal.add(itemSubtotal);

            message.append("• ").append(escapeMarkdown(item.getProduct().getName()))
                    .append(" x").append(item.getQuantity())
                    .append(" = ").append(itemSubtotal).append(" ₽\n");
        }

        // Детальный расчет суммы
        message.append("\n💰 *ДЕТАЛЬНЫЙ РАСЧЕТ СУММЫ:*\n");
        message.append("├ Товары: ").append(itemsTotal).append(" ₽\n");
        
        if (order.getDeliveryCost() != null && order.getDeliveryCost().compareTo(BigDecimal.ZERO) > 0) {
            message.append("├ Доставка: ").append(order.getDeliveryCost()).append(" ₽\n");
        } else if (order.isDeliveryByCourier()) {
            message.append("├ Доставка: БЕСПЛАТНО\n");
        } else if (order.isPickup()) {
            message.append("├ Доставка: Самовывоз (0 ₽)\n");
        }
        
        message.append("└ *ИТОГО: ").append(order.getTotalAmount()).append(" ₽*\n\n");

        // Информация о платеже - отображаем правильный статус и способ оплаты
        String paymentStatus = getPaymentStatusDisplay(order);
        String paymentMethodName = order.getPaymentMethod() != null ? order.getPaymentMethod().getDisplayName() : "Не указан";
        
        message.append("💳 *СТАТУС ОПЛАТЫ:* ").append(paymentStatus).append("\n");
        message.append("💰 *СПОСОБ ОПЛАТЫ:* ").append(paymentMethodName).append("\n\n");

        return message.toString();
    }

    private String formatOrderDetails(Order order) {
        StringBuilder message = new StringBuilder();

        message.append("🔍 *ДЕТАЛИ ЗАКАЗА #").append(order.getId()).append("*\n\n");

        message.append("📅 *Время создания:* ").append(formatDateTime(order.getCreatedAt())).append("\n");
        message.append("📋 *Статус:* ").append(order.getStatus().getName()).append("\n");
        message.append("📝 *Описание статуса:* ").append(order.getStatus().getDescription()).append("\n\n");

        // Информация о пользователе
        if (order.getUser() != null) {
            message.append("👤 *ИНФОРМАЦИЯ О ПОЛЬЗОВАТЕЛЕ*\n");
            message.append("Имя: ").append(escapeMarkdown(order.getUser().getFirstName()))
                    .append(" ").append(escapeMarkdown(order.getUser().getLastName())).append("\n");
            message.append("Username: @").append(escapeMarkdown(order.getUser().getUsername())).append("\n");
            message.append("Телефон: ").append(escapeMarkdown(order.getUser().getPhone())).append("\n");
            message.append("Email: ").append(escapeMarkdown(order.getUser().getEmail())).append("\n\n");
        }

        message.append("📞 *КОНТАКТНЫЕ ДАННЫЕ ЗАКАЗА*\n");
        message.append("Имя: ").append(escapeMarkdown(order.getContactName())).append("\n");
        message.append("Телефон: ").append(escapeMarkdown(order.getContactPhone())).append("\n");

        // Адрес доставки
        if (order.getDeliveryAddress() != null) {
            message.append("\n📍 *ДОСТАВКА*\n");
            message.append("Адрес: ").append(escapeMarkdown(order.getDeliveryAddress())).append("\n");
        }

        // Способ доставки (НОВОЕ ПОЛЕ)
        if (order.getDeliveryType() != null) {
            String deliveryIcon = order.isPickup() ? "🏠" : "🚗";
            message.append("🚛 *Способ доставки:* ").append(deliveryIcon).append(" ")
                    .append(escapeMarkdown(order.getDeliveryType())).append("\n");
        }

        // Детальный состав заказа
        message.append("\n🛒 *СОСТАВ ЗАКАЗА*\n");
        BigDecimal itemsTotal = BigDecimal.ZERO;

        for (OrderItem item : order.getItems()) {
            BigDecimal itemSubtotal = item.getPrice().multiply(BigDecimal.valueOf(item.getQuantity()));
            itemsTotal = itemsTotal.add(itemSubtotal);

            message.append("• ").append(escapeMarkdown(item.getProduct().getName())).append("\n");
            message.append("  Цена: ").append(item.getPrice()).append(" ₽\n");
            message.append("  Количество: ").append(item.getQuantity()).append("\n");
            message.append("  Сумма: ").append(itemSubtotal).append(" ₽\n\n");
        }

        // Детализация суммы (ОБНОВЛЕНО)
        message.append("💰 *ДЕТАЛЬНЫЙ РАСЧЕТ СУММЫ:*\n");
        message.append("├ Товары: ").append(itemsTotal).append(" ₽\n");
        
        if (order.getDeliveryCost() != null && order.getDeliveryCost().compareTo(BigDecimal.ZERO) > 0) {
            message.append("├ Доставка: ").append(order.getDeliveryCost()).append(" ₽\n");
        } else if (order.isDeliveryByCourier()) {
            message.append("├ Доставка: БЕСПЛАТНО\n");
        } else if (order.isPickup()) {
            message.append("├ Доставка: Самовывоз (0 ₽)\n");
        }
        
        message.append("└ *ИТОГО: ").append(order.getTotalAmount()).append(" ₽*\n\n");

        // Информация о платеже
        appendPaymentInfo(message, order);

        if (order.getComment() != null && !order.getComment().trim().isEmpty()) {
            message.append("\n💬 *КОММЕНТАРИЙ*\n").append(escapeMarkdown(order.getComment()));
        }

        return message.toString();
    }

    /**
     * Добавляет улучшенную информацию о платеже к сообщению
     */
    private void appendEnhancedPaymentInfo(StringBuilder message, Order order, Payment latestPayment, OrderDisplayStatus displayStatus) {
        message.append("💳 *СТАТУС ОПЛАТЫ:* ").append(displayStatus.getFormattedStatusWithInfo(latestPayment)).append("\n");

        // Для наличных заказов
        if (displayStatus == OrderDisplayStatus.CASH_NEW) {
            message.append("💰 *СПОСОБ ОПЛАТЫ:* 💵 Наличными при доставке\n\n");
            return;
        }

        // Для онлайн платежей
        if (latestPayment != null) {
            message.append("💰 *СПОСОБ ОПЛАТЫ:* ").append(getPaymentMethodDisplayName(latestPayment.getMethod())).append("\n");

            if (latestPayment.getCreatedAt() != null) {
                message.append("🕐 *Время создания платежа:* ")
                        .append(latestPayment.getCreatedAt().format(DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm")))
                        .append("\n");
            }

            if (latestPayment.getPaidAt() != null) {
                message.append("✅ *Время оплаты:* ")
                        .append(latestPayment.getPaidAt().format(DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm")))
                        .append("\n");
            }

            // Особая обработка для разных статусов
            switch (displayStatus) {
                case PAYMENT_POLLING:
                    message.append("🔄 *Статус опроса:* Активно проверяется каждую минуту\n");
                    break;
                case PAYMENT_TIMEOUT:
                    message.append("⏰ *Внимание:* Истекло время ожидания оплаты (10 мин)\n");
                    break;
                case PAYMENT_CANCELLED:
                    message.append("❌ *Внимание:* Платеж отменен или завершился ошибкой\n");
                    break;
            }

            // Добавляем ссылку на проверку платежа для активных онлайн платежей
            if (isOnlinePayment(latestPayment.getMethod()) && latestPayment.getYookassaPaymentId() != null
                && displayStatus != OrderDisplayStatus.PAYMENT_SUCCESS) {
                String checkUrl = "https://yoomoney.ru/checkout/payments/v2/contract?orderId=" + latestPayment.getYookassaPaymentId();
                message.append("🔗 *Проверить оплату:* [Открыть в ЮMoney](").append(checkUrl).append(")\n");
            }
        }

        message.append("\n");
    }

    /**
     * СТАРЫЙ МЕТОД - оставляем для обратной совместимости
     */
    private void appendPaymentInfo(StringBuilder message, Order order) {
        try {
            Long orderId = order.getId().longValue();
                        List<Payment> payments = paymentRepository.findByOrderIdOrderByCreatedAtDesc(orderId);
            
            if (payments.isEmpty()) {
                // Для заказов наличными используем правильную логику
                String paymentStatus = getPaymentStatusDisplay(order);
                String paymentMethodName = order.getPaymentMethod() != null ? order.getPaymentMethod().getDisplayName() : "💵 Наличными при доставке";
                
                message.append("💳 *СТАТУС ОПЛАТЫ:* ").append(paymentStatus).append("\n");
                message.append("💰 *СПОСОБ ОПЛАТЫ:* ").append(paymentMethodName).append("\n\n");
                return;
            }

            // Берем последний платеж (самый новый)
            Payment latestPayment = payments.get(0);

            // Используем нашу новую логику для всех типов статусов
            String paymentStatus = getPaymentStatusDisplay(order);
            String paymentMethodName = getPaymentMethodDisplayName(latestPayment.getMethod());

            message.append("💳 *СТАТУС ОПЛАТЫ:* ").append(paymentStatus).append("\n");
            message.append("💰 *СПОСОБ ОПЛАТЫ:* ").append(paymentMethodName).append("\n");

            if (latestPayment.getCreatedAt() != null) {
                message.append("🕐 *Время создания платежа:* ")
                        .append(latestPayment.getCreatedAt().format(DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm")))
                        .append("\n");
            }

            if (latestPayment.getPaidAt() != null) {
                message.append("✅ *Время оплаты:* ")
                        .append(latestPayment.getPaidAt().format(DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm")))
                        .append("\n");
            }

            // Добавляем ссылку на проверку платежа для онлайн оплаты
            if (isOnlinePayment(latestPayment.getMethod()) && latestPayment.getYookassaPaymentId() != null) {
                String checkUrl = "https://yoomoney.ru/checkout/payments/v2/contract?orderId=" + latestPayment.getYookassaPaymentId();
                message.append("🔗 *Проверить оплату:* [Открыть в ЮMoney](").append(checkUrl).append(")\n");
            }

            message.append("\n");

        } catch (Exception e) {
            log.error("Ошибка получения информации о платеже для заказа #{}: {}", order.getId(), e.getMessage(), e);
            message.append("💳 *СТАТУС ОПЛАТЫ:* ❓ Ошибка получения данных\n\n");
        }
    }

    /**
     * Получение отображаемого названия статуса платежа
     */
    private String getPaymentStatusDisplayName(PaymentStatus status) {
        if (status == null) {
            return "❓ Неизвестно";
        }

        switch (status) {
            case PENDING:
                return "⏳ Ожидает оплаты";
            case WAITING_FOR_CAPTURE:
                return "⏳ Ожидает подтверждения";
            case SUCCEEDED:
                return "✅ Оплачено";
            case FAILED:
                return "❌ Ошибка оплаты";
            case CANCELLED:
                return "❌ Отменено";
            default:
                return status.toString();
        }
    }

    /**
     * Получает последний платеж для заказа (helper метод)
     */
    private Payment getLatestPayment(Order order) {
        try {
            Long orderId = order.getId().longValue();
            List<Payment> payments = paymentRepository.findByOrderIdOrderByCreatedAtDesc(orderId);
            return payments.isEmpty() ? null : payments.get(0);
        } catch (Exception e) {
            log.error("Ошибка получения платежа для заказа #{}: {}", order.getId(), e.getMessage(), e);
            return null;
        }
    }

    /**
     * ИСПРАВЛЕННАЯ версия определения статуса заказа
     * Учитывает случаи когда Order.paymentMethod=null но есть успешные платежи
     */
    private OrderDisplayStatus determineOrderDisplayStatusFixed(Order order, Payment latestPayment) {
        // Проверяем есть ли успешно завершенные платежи
        if (latestPayment != null && latestPayment.getStatus() == PaymentStatus.SUCCEEDED) {
            log.debug("Заказ #{} имеет успешный платеж {} ({})", 
                order.getId(), latestPayment.getId(), latestPayment.getMethod());
            return OrderDisplayStatus.PAYMENT_SUCCESS;
        }

        // Проверяем статус оплаты заказа
        if (order.getPaymentStatus() == OrderPaymentStatus.PAID) {
            log.debug("Заказ #{} имеет paymentStatus=PAID", order.getId());
            return OrderDisplayStatus.PAYMENT_SUCCESS;
        }

        // Если есть активные платежи в ожидании
        if (latestPayment != null) {
            switch (latestPayment.getStatus()) {
                case PENDING:
                case WAITING_FOR_CAPTURE:
                    // Проверяем возраст платежа для определения таймаута
                    long minutesElapsed = ChronoUnit.MINUTES.between(
                        latestPayment.getCreatedAt(), LocalDateTime.now()
                    );
                    
                    if (minutesElapsed >= 10) {
                        return OrderDisplayStatus.PAYMENT_TIMEOUT;
                    } else {
                        return OrderDisplayStatus.PAYMENT_POLLING;
                    }
                    
                case CANCELLED:
                case FAILED:
                    return OrderDisplayStatus.PAYMENT_CANCELLED;
                    
                default:
                    return OrderDisplayStatus.PAYMENT_PENDING;
            }
        }

        // По умолчанию считаем наличными
        return OrderDisplayStatus.CASH_NEW;
    }

    /**
     * Добавляет краткую информацию о платеже с улучшенным форматированием
     */
    private void appendBriefPaymentInfoEnhanced(StringBuilder message, Order order, Payment latestPayment, OrderDisplayStatus displayStatus) {
        try {
            message.append("💳 *Оплата:* ").append(displayStatus.getFormattedStatusWithInfo(latestPayment)).append("\n");
            
            // Добавляем дополнительную информацию в зависимости от статуса
            if (displayStatus == OrderDisplayStatus.PAYMENT_POLLING && latestPayment != null) {
                message.append("🔄 *Автоопрос:* ").append(OrderDisplayStatus.getPollingIndicator(latestPayment)).append("\n");
            } else if (displayStatus == OrderDisplayStatus.PAYMENT_TIMEOUT) {
                message.append("⏰ *Внимание:* Таймаут оплаты\n");
            } else if (displayStatus == OrderDisplayStatus.PAYMENT_SUCCESS && latestPayment != null && latestPayment.getPaidAt() != null) {
                message.append("✅ *Оплачено:* ").append(latestPayment.getPaidAt().format(DateTimeFormatter.ofPattern("HH:mm"))).append("\n");
            }
            
            // Ссылка на проверку для неоплаченных онлайн платежей
            if (latestPayment != null && isOnlinePayment(latestPayment.getMethod()) 
                && latestPayment.getYookassaPaymentId() != null 
                && displayStatus != OrderDisplayStatus.PAYMENT_SUCCESS) {
                String checkUrl = "https://yoomoney.ru/checkout/payments/v2/contract?orderId=" + latestPayment.getYookassaPaymentId();
                message.append("🔗 [Проверить оплату](").append(checkUrl).append(")\n");
            }
            
        } catch (Exception e) {
            log.error("Ошибка краткого отображения платежа для заказа #{}: {}", order.getId(), e.getMessage(), e);
            message.append("💳 *Оплата:* ❓ Ошибка данных\n");
        }
    }

    /**
     * Получение отображаемого названия способа оплаты
     */
    private String getPaymentMethodDisplayName(PaymentMethod method) {
        if (method == null) {
            return "❓ Неизвестно";
        }

        switch (method) {
            case SBP:
                return "📱 СБП (Система быстрых платежей)";
            case BANK_CARD:
                return "💳 Банковская карта";
            case YOOMONEY:
                return "💰 ЮMoney";
            case QIWI:
                return "🥝 QIWI";
            case WEBMONEY:
                return "💻 WebMoney";
            case ALFABANK:
                return "🏦 Альфа-Клик";
            case SBERBANK:
                return "🏛️ Сбербанк Онлайн";
            default:
                return method.toString();
        }
    }

    /**
     * Проверяет, является ли способ оплаты онлайн
     */
    private boolean isOnlinePayment(PaymentMethod method) {
        return method != null; // Все методы в PaymentMethod являются онлайн-платежами
    }

    /**
     * Добавляет краткую информацию о платеже к сообщению (для списка активных заказов)
     */
    private void appendBriefPaymentInfo(StringBuilder message, Order order) {
        try {
            Long orderId = order.getId().longValue();
                        List<Payment> payments = paymentRepository.findByOrderIdOrderByCreatedAtDesc(orderId);
            
            if (payments.isEmpty()) {
                // Для заказов наличными используем правильную логику
                String paymentStatus = getPaymentStatusDisplay(order);
                message.append("💳 *Оплата:* ").append(paymentStatus).append("\n");
                return;
            }

            // Берем последний платеж (самый новый)
            Payment latestPayment = payments.get(0);

            // Используем нашу новую логику для статуса
            String paymentStatus = getPaymentStatusDisplay(order);
            String paymentMethodName = getPaymentMethodDisplayName(latestPayment.getMethod());
            
            message.append("💳 *Оплата:* ").append(paymentStatus);
            message.append(" (").append(paymentMethodName).append(")\n");

            // Добавляем ссылку на проверку платежа для онлайн оплаты (только если не оплачено)
            if (isOnlinePayment(latestPayment.getMethod()) &&
                latestPayment.getYookassaPaymentId() != null &&
                latestPayment.getStatus() != PaymentStatus.SUCCEEDED) {
                String checkUrl = "https://yoomoney.ru/checkout/payments/v2/contract?orderId=" + latestPayment.getYookassaPaymentId();
                message.append("🔗 [Проверить оплату](").append(checkUrl).append(")\n");
            }

        } catch (Exception e) {
            log.error("Ошибка получения краткой информации о платеже для заказа #{}: {}", order.getId(), e.getMessage(), e);
            message.append("💳 *Оплата:* ❓ Ошибка получения данных\n");
        }
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
                // Определяем визуальный статус для каждого заказа с исправленной логикой
                Payment latestPayment = getLatestPayment(order);
                OrderDisplayStatus displayStatus = determineOrderDisplayStatusFixed(order, latestPayment);
                
                StringBuilder orderMessage = new StringBuilder();
                orderMessage.append(displayStatus.getEmoji()).append(" *Заказ #").append(order.getId()).append("*\n");
                orderMessage.append("Статус: ").append(getStatusDisplayName(order.getStatus())).append("\n");
                orderMessage.append("Оплата: ").append(displayStatus.getFormattedStatusWithInfo(latestPayment)).append("\n");
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
                orderMessage.append("📞 *Телефон заказа:* ").append(escapeMarkdown(order.getContactPhone())).append("\n\n");

                // Краткая информация о платеже с улучшенным форматированием
                appendBriefPaymentInfoEnhanced(orderMessage, order, latestPayment, displayStatus);

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
     * НОВАЯ ЛОГИКА: Все заказы отправляются в админский бот сразу при создании
     */
    @EventListener
    @Async
    public void handleNewOrderEvent(NewOrderEvent event) {
        try {
            Order order = event.getOrder();
            log.info("📧 Получено событие о новом заказе #{} для уведомления администраторов", order.getId());

            // Проверяем откуда пришло событие - из OrderService или из YooKassaPaymentService
            boolean isPaymentSuccessNotification = order.getPaymentStatus() == OrderPaymentStatus.PAID && 
                                                   isOnlinePayment(order.getPaymentMethod());

            if (isPaymentSuccessNotification) {
                // Это уведомление о успешной оплате - отправляем специальное сообщение
                Payment latestPayment = getLatestPayment(order);
                String paymentLabel = "✅ ЗАКАЗ ОПЛАЧЕН через " + getPaymentMethodDisplayName(order.getPaymentMethod());
                sendSuccessfulPaymentOrderNotification(order, paymentLabel);
                log.info("💰 Отправлено уведомление об успешной оплате заказа #{}", order.getId());
            } else {
                // Обычное уведомление о новом заказе - отправляем всегда для всех заказов
                notifyAdminsAboutNewOrder(order);
                log.info("✅ Уведомление о новом заказе #{} отправлено в админский бот (способ оплаты: {})", 
                    order.getId(), order.getPaymentMethod());
            }

        } catch (Exception e) {
            log.error("❌ Ошибка обработки события нового заказа #{}: {}", event.getOrder().getId(), e.getMessage(), e);
        }
    }

    /**
     * Обработчик событий алертов платежной системы
     */
    @EventListener
    @Async
    public void handlePaymentAlertEvent(PaymentAlertEvent event) {
        try {
            log.info("🚨 Получено событие алерта платежной системы: {}", event.getAlertType());
            notifyAdminsAboutPaymentAlert(event.getAlertMessage(), event.getAlertType());
        } catch (Exception e) {
            log.error("❌ Ошибка обработки события алерта платежной системы: {}", e.getMessage(), e);
        }
    }

    /**
     * Отправка алерта всем администраторам
     */
    private void notifyAdminsAboutPaymentAlert(String alertMessage, PaymentAlertEvent.AlertType alertType) {
        try {
            List<TelegramAdminUser> activeAdmins = adminUserRepository.findByIsActiveTrue();

            if (activeAdmins.isEmpty()) {
                log.warn("Нет активных администраторов для отправки алерта типа {}", alertType);
                return;
            }

            for (TelegramAdminUser admin : activeAdmins) {
                try {
                    telegramAdminNotificationService.sendMessage(admin.getTelegramChatId(), alertMessage, true);
                    log.debug("Алерт {} отправлен администратору: {}", alertType, admin.getUsername());
                } catch (Exception e) {
                    log.error("Ошибка отправки алерта администратору {}: {}", admin.getUsername(), e.getMessage());
                }
            }

            log.info("Алерт {} отправлен {} администраторам", alertType, activeAdmins.size());

        } catch (Exception e) {
            log.error("Ошибка при отправке алерта администраторам: {}", e.getMessage(), e);
        }
    }

    /**
     * Отправка алерта об отменённом платеже всем администраторам
     */
    public void sendPaymentCancelAlert(Payment payment, String reason) {
        try {
            StringBuilder message = new StringBuilder();
            message.append("❌ *ПЛАТЕЖ ОТМЕНЕН*\n\n");
            message.append("🆔 Платеж #").append(payment.getId()).append("\n");
            message.append("🛒 Заказ #").append(payment.getOrder().getId()).append("\n");
            message.append("💰 Сумма: ").append(payment.getAmount()).append(" ₽\n");
            message.append("💳 Способ: ").append(payment.getMethod()).append("\n");
            message.append("⏰ Время: ").append(payment.getCreatedAt().format(DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm"))).append("\n");
            message.append("📝 Причина: ").append(reason).append("\n\n");
            message.append("⚠️ Заказ НЕ будет отправлен в работу.");

            notifyAdminsAboutPaymentAlert(message.toString(), PaymentAlertEvent.AlertType.CRITICAL_PAYMENT_FAILURE);

        } catch (Exception e) {
            log.error("❌ Ошибка отправки алерта об отмене платежа #{}: {}", payment.getId(), e.getMessage(), e);
        }
    }

    /**
     * Отправка алерта о неудачном платеже всем администраторам
     */
    public void sendPaymentFailureAlert(Payment payment, String reason) {
        try {
            StringBuilder message = new StringBuilder();
            message.append("💥 *ПЛАТЕЖ ЗАВЕРШИЛСЯ ОШИБКОЙ*\n\n");
            message.append("🆔 Платеж #").append(payment.getId()).append("\n");
            message.append("🛒 Заказ #").append(payment.getOrder().getId()).append("\n");
            message.append("💰 Сумма: ").append(payment.getAmount()).append(" ₽\n");
            message.append("💳 Способ: ").append(payment.getMethod()).append("\n");
            message.append("⏰ Время: ").append(payment.getCreatedAt().format(DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm"))).append("\n");
            message.append("❌ Ошибка: ").append(payment.getErrorMessage() != null ? payment.getErrorMessage() : "Неизвестная ошибка").append("\n");
            message.append("📝 Детали: ").append(reason).append("\n\n");
            message.append("⚠️ Заказ НЕ будет отправлен в работу.");

            notifyAdminsAboutPaymentAlert(message.toString(), PaymentAlertEvent.AlertType.CRITICAL_PAYMENT_FAILURE);

        } catch (Exception e) {
            log.error("❌ Ошибка отправки алерта о неудаче платежа #{}: {}", payment.getId(), e.getMessage(), e);
        }
    }

    /**
     * Отправка уведомления о заказе с подтвержденной оплатой
     */
    public void sendSuccessfulPaymentOrderNotification(Order order, String paymentLabel) {
        try {
            String message = formatNewOrderMessageWithPaymentLabel(order, paymentLabel);
            
            List<TelegramAdminUser> activeAdmins = adminUserRepository.findByIsActiveTrue();

            if (activeAdmins.isEmpty()) {
                log.warn("Нет активных администраторов для отправки уведомления о заказе #{}", order.getId());
                return;
            }

            for (TelegramAdminUser admin : activeAdmins) {
                try {
                    telegramAdminNotificationService.sendMessage(admin.getTelegramChatId(), message, true);
                    log.debug("Уведомление о заказе #{} с подтвержденной оплатой отправлено администратору: {}", 
                            order.getId(), admin.getUsername());
                } catch (Exception e) {
                    log.error("Ошибка отправки уведомления администратору {}: {}", admin.getUsername(), e.getMessage());
                }
            }

            log.info("✅ Уведомление о заказе #{} с {} отправлено {} администраторам", 
                    order.getId(), paymentLabel, activeAdmins.size());

        } catch (Exception e) {
            log.error("❌ Ошибка отправки уведомления о заказе #{} с подтвержденной оплатой: {}", 
                    order.getId(), e.getMessage(), e);
        }
    }

    /**
     * Получение корректного отображения статуса оплаты
     */
    private String getPaymentStatusDisplay(Order order) {
        if (order.getPaymentStatus() != null) {
            switch (order.getPaymentStatus()) {
                case PAID:
                    return "✅ Оплачено";
                case UNPAID:
                    return "❌ Не оплачено";
                case FAILED:
                    return "❌ Ошибка оплаты";
                case CANCELLED:
                    return "❌ Платеж отменен";
                default:
                    break;
            }
        }
        
        // Для заказов наличными проверяем статус заказа
        if (order.getPaymentMethod() == PaymentMethod.CASH) {
            // Заказ наличными оплачивается при доставке
            if (order.getStatus() != null && 
                ("DELIVERED".equals(order.getStatus().getName()) || "COMPLETED".equals(order.getStatus().getName()))) {
                return "✅ Оплачено наличными";
            } else {
                return "💵 Оплата при доставке";
            }
        }
        
        // Для онлайн платежей проверяем последний платеж
        Payment latestPayment = getLatestPayment(order);
        if (latestPayment != null) {
            switch (latestPayment.getStatus()) {
                case SUCCEEDED:
                    return "✅ Оплачено";
                case PENDING:
                case WAITING_FOR_CAPTURE:
                    return "🔄 Ожидает оплаты";
                case CANCELLED:
                    return "❌ Платеж отменен";
                case FAILED:
                    return "❌ Ошибка оплаты";
                default:
                    return "❓ Статус неизвестен";
            }
        }
        
        return "❌ Не оплачено";
    }

    /**
     * Отправка простого уведомления админам об оплаченном заказе
     */
    public void sendSimplePaymentNotification(Order order) {
        try {
            // Простое сообщение: "Заказ номер ХХ успешно оплачен сумма (дата)"
            String message = String.format("Заказ номер %d успешно оплачен %s ₽ (%s)", 
                order.getId(), 
                order.getTotalAmount(), 
                LocalDateTime.now().format(DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm")));
            
            List<TelegramAdminUser> activeAdmins = adminUserRepository.findByIsActiveTrue();

            if (activeAdmins.isEmpty()) {
                log.warn("Нет активных администраторов для отправки уведомления об оплате заказа #{}", order.getId());
                return;
            }

            for (TelegramAdminUser admin : activeAdmins) {
                try {
                    telegramAdminNotificationService.sendMessage(admin.getTelegramChatId(), message, false);
                    log.debug("Простое уведомление об оплате заказа #{} отправлено администратору: {}", 
                            order.getId(), admin.getUsername());
                } catch (Exception e) {
                    log.error("Ошибка отправки уведомления об оплате администратору {}: {}", admin.getUsername(), e.getMessage());
                }
            }

            log.info("✅ Простое уведомление об оплате заказа #{} отправлено {} администраторам", order.getId(), activeAdmins.size());

        } catch (Exception e) {
            log.error("Ошибка отправки простого уведомления об оплате заказа #{}: {}", order.getId(), e.getMessage());
        }
    }

    /**
     * Проверяет есть ли для заказа активные платежи в ожидании оплаты
     * Активными считаются платежи со статусами PENDING или WAITING_FOR_CAPTURE
     * 
     * @param order заказ для проверки
     * @return true если есть активные платежи, false если нет
     */
    private boolean hasActivePendingPayments(Order order) {
        try {
            Long orderId = order.getId().longValue();
            List<Payment> payments = paymentRepository.findByOrderIdOrderByCreatedAtDesc(orderId);
            
            if (payments.isEmpty()) {
                log.debug("Заказ #{} не имеет платежей - считается оплатой наличными", order.getId());
                return false;
            }
            
            // Проверяем есть ли платежи в ожидании
            boolean hasActivePending = payments.stream()
                    .anyMatch(payment -> payment.getStatus() == PaymentStatus.PENDING || 
                                       payment.getStatus() == PaymentStatus.WAITING_FOR_CAPTURE);
            
            if (hasActivePending) {
                log.debug("Заказ #{} имеет активные платежи в ожидании", order.getId());
                return true;
            } else {
                log.debug("Заказ #{} не имеет активных платежей в ожидании", order.getId());
                return false;
            }
            
        } catch (Exception e) {
            log.error("Ошибка проверки активных платежей для заказа #{}: {}", order.getId(), e.getMessage(), e);
            // В случае ошибки предполагаем что нет активных платежей (безопасный вариант)
            return false;
        }
    }

    private String formatDateTime(LocalDateTime dateTime) {
        if (dateTime == null) {
            return "Неизвестно";
        }
        return dateTime.format(DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm"));
    }

    private String formatOrderSummary(Order order) {
        StringBuilder message = new StringBuilder();

        message.append("🆔 *Заказ #").append(order.getId()).append("*\n");
        message.append("📅 Время: ").append(formatDateTime(order.getCreatedAt())).append("\n");
        message.append("📋 Статус: *").append(order.getStatus().getName()).append("*\n\n");

        // Информация о пользователе
        if (order.getUser() != null) {
            message.append("👤 *ПОЛЬЗОВАТЕЛЬ СИСТЕМЫ*\n");
            message.append("Имя: ").append(escapeMarkdown(order.getUser().getFirstName()))
                    .append(" ").append(escapeMarkdown(order.getUser().getLastName())).append("\n");
            message.append("Username: @").append(escapeMarkdown(order.getUser().getUsername())).append("\n");
            message.append("Телефон: ").append(escapeMarkdown(order.getUser().getPhone())).append("\n");
            message.append("Email: ").append(escapeMarkdown(order.getUser().getEmail())).append("\n\n");
        }

        // Контактные данные заказа
        message.append("📞 *КОНТАКТНЫЕ ДАННЫЕ ЗАКАЗА*\n");
        message.append("Имя: ").append(escapeMarkdown(order.getContactName())).append("\n");
        message.append("Телефон: ").append(escapeMarkdown(order.getContactPhone())).append("\n\n");

        // Информация о доставке
        if (order.getDeliveryAddress() != null) {
            message.append("📍 *ДОСТАВКА*\n");
            message.append("Адрес: ").append(escapeMarkdown(order.getDeliveryAddress())).append("\n");
        } else if (order.getDeliveryLocation() != null) {
            message.append("📍 *ПУНКТ ВЫДАЧИ*\n");
            message.append("Адрес: ").append(escapeMarkdown(order.getDeliveryLocation().getAddress())).append("\n");
        }

        // Способ доставки (НОВОЕ ПОЛЕ)
        if (order.getDeliveryType() != null) {
            String deliveryIcon = order.isPickup() ? "🏠" : "🚗";
            message.append("🚛 *Способ доставки:* ").append(deliveryIcon).append(" ")
                    .append(escapeMarkdown(order.getDeliveryType())).append("\n");
        }
        message.append("\n");

        // Комментарий
        if (order.getComment() != null && !order.getComment().trim().isEmpty()) {
            message.append("💬 *Комментарий:* ").append(escapeMarkdown(order.getComment())).append("\n\n");
        }

        // Состав заказа
        message.append("🛒 *СОСТАВ ЗАКАЗА*\n");
        BigDecimal itemsTotal = BigDecimal.ZERO;
        for (OrderItem item : order.getItems()) {
            BigDecimal itemSubtotal = item.getPrice().multiply(BigDecimal.valueOf(item.getQuantity()));
            itemsTotal = itemsTotal.add(itemSubtotal);
            
            message.append("• ").append(escapeMarkdown(item.getProduct().getName()))
                    .append(" x").append(item.getQuantity())
                    .append(" = ").append(itemSubtotal)
                    .append(" ₽\n");
        }

        // Детализация суммы (ОБНОВЛЕНО)
        message.append("\n💰 *РАСЧЕТ СУММЫ:*\n");
        message.append("├ Товары: ").append(itemsTotal).append(" ₽\n");
        
        if (order.getDeliveryCost() != null && order.getDeliveryCost().compareTo(BigDecimal.ZERO) > 0) {
            message.append("├ Доставка: ").append(order.getDeliveryCost()).append(" ₽\n");
        } else if (order.isDeliveryByCourier()) {
            message.append("├ Доставка: БЕСПЛАТНО ₽\n");
        }
        
        message.append("└ *ИТОГО: ").append(order.getTotalAmount()).append(" ₽*\n\n");

        // Информация о платеже
        appendPaymentInfo(message, order);

        return message.toString();
    }

    /**
     * Отправка уведомления пользователю об изменении статуса заказа
     */
    private void sendStatusNotificationToUser(Long orderId, String newStatus) {
        try {
            // Получаем заказ
            Optional<Order> orderOpt = orderService.findById(orderId);
            if (!orderOpt.isPresent()) {
                log.warn("Заказ #{} не найден для отправки уведомления", orderId);
                return;
            }

            Order order = orderOpt.get();

            // Проверяем, есть ли у пользователя Telegram ID
            if (order.getUser() == null || order.getUser().getTelegramId() == null) {
                log.info("У заказа #{} нет пользователя с Telegram ID, уведомление не отправляется", orderId);
                return;
            }

            Long userTelegramId = order.getUser().getTelegramId();

            // Формируем сообщение о статусе
            String statusMessage = formatStatusNotificationMessage(order, newStatus);

            // Отправляем уведомление пользователю
            telegramUserNotificationService.sendPersonalMessage(userTelegramId, statusMessage);

            log.info("Уведомление о статусе заказа #{} отправлено пользователю {}", orderId, userTelegramId);

        } catch (Exception e) {
            log.error("Ошибка отправки уведомления пользователю о статусе заказа #{}: {}", orderId, e.getMessage(), e);
        }
    }

    /**
     * Форматирование сообщения уведомления о статусе заказа
     */
    private String formatStatusNotificationMessage(Order order, String status) {
        String statusEmoji = getStatusEmoji(status);
        String statusText = getStatusDisplayNameByString(status);
        String statusDescription = getStatusDescription(status);

        StringBuilder message = new StringBuilder();
        message.append(statusEmoji).append(" <b>Статус вашего заказа изменился</b>\n\n");
        message.append("🆔 <b>Заказ #").append(order.getId()).append("</b>\n");
        message.append("📊 <b>Новый статус:</b> ").append(statusText).append("\n");
        message.append("📝 ").append(statusDescription).append("\n\n");

        // Добавляем информацию о времени доставки для соответствующих статусов
        if ("READY".equalsIgnoreCase(status) && order.getDeliveryType() != null && order.getDeliveryType().contains("курьер")) {
            message.append("🚗 <b>Курьер скоро выедет к вам!</b>\n");
        } else if ("DELIVERING".equalsIgnoreCase(status)) {
            message.append("🚗 <b>Курьер уже в пути!</b>\n");
            message.append("📞 Ожидайте звонка курьера\n");
        } else if ("DELIVERED".equalsIgnoreCase(status)) {
            message.append("🎉 <b>Спасибо за заказ!</b>\n");
            message.append("🌟 Будем рады видеть вас снова!\n");
        }

        message.append("\n💬 Есть вопросы? Напишите /start для связи с нами");

        return message.toString();
    }

    /**
     * Получение эмодзи для статуса
     */
    private String getStatusEmoji(String status) {
        switch (status.toUpperCase()) {
            case "CONFIRMED": return "✅";
            case "PREPARING": return "👨‍🍳";
            case "READY": return "🍕";
            case "DELIVERING": return "🚗";
            case "DELIVERED": return "🎉";
            case "CANCELLED": return "❌";
            default: return "📋";
        }
    }

    /**
     * Получение описания статуса для пользователя
     */
    private String getStatusDescription(String status) {
        switch (status.toUpperCase()) {
            case "CONFIRMED": 
                return "Ваш заказ подтвержден и принят в работу";
            case "PREPARING": 
                return "Повар уже готовит ваш заказ";
            case "READY": 
                return "Заказ готов! Ожидает доставки или самовывоза";
            case "DELIVERING": 
                return "Курьер доставляет ваш заказ";
            case "DELIVERED": 
                return "Заказ успешно доставлен";
            case "CANCELLED": 
                return "К сожалению, заказ был отменен";
            default: 
                return "Статус заказа обновлен";
        }
    }

    /**
     * Массовая рассылка сообщения всем авторизованным пользователям
     * с учетом лимитов Telegram API
     */
    @Async
    public void broadcastMessageToAllUsers(Long adminChatId, String messageText) {
        try {
            // Получаем всех пользователей с подтвержденным Telegram ID
            List<com.baganov.pizzanat.entity.User> users = userService.getAllUsersWithTelegramId();
            
            if (users.isEmpty()) {
                sendMessageToAdmin(adminChatId, "ℹ️ Нет пользователей для отправки сообщения");
                return;
            }

            // Создаем рассылку для отслеживания прогресса
            String broadcastId = rateLimitService.createBroadcast(users.size());
            
            // Отправляем сообщение как есть (без префикса) от пользовательского бота @DIMBOpizzaBot
            String broadcastMessage = messageText;

            log.info("📤 Начинаем массовую рассылку {} пользователям (ID: {})", users.size(), broadcastId);
            
            // Уведомляем администратора о начале рассылки
            sendMessageToAdmin(adminChatId, String.format(
                "🚀 *Рассылка запущена от @DIMBOpizzaBot*\n\n" +
                "👥 Пользователей: %d\n" +
                "📝 Сообщение: \"%s\"\n\n" +
                "⏳ Ожидаемое время: ~%d мин\n" +
                "_Соблюдаем лимиты Telegram API (20 сообщений/сек)_",
                users.size(), 
                messageText.length() > 50 ? messageText.substring(0, 50) + "..." : messageText,
                estimateBroadcastTime(users.size())
            ));

            // Обрабатываем пользователей пакетами
            int batchSize = 10; // Размер пакета
            int totalBatches = (int) Math.ceil((double) users.size() / batchSize);
            
            for (int batchIndex = 0; batchIndex < totalBatches; batchIndex++) {
                int startIndex = batchIndex * batchSize;
                int endIndex = Math.min(startIndex + batchSize, users.size());
                List<com.baganov.pizzanat.entity.User> batch = users.subList(startIndex, endIndex);
                
                log.debug("📦 Обрабатываем пакет {}/{}: пользователи {}-{}", 
                    batchIndex + 1, totalBatches, startIndex + 1, endIndex);
                
                // Обрабатываем пакет
                processBatch(batch, broadcastMessage, broadcastId);
                
                // Задержка между пакетами (кроме последнего)
                if (batchIndex < totalBatches - 1) {
                    Thread.sleep(1000); // 1 секунда между пакетами
                }
            }

            // Завершаем рассылку и получаем финальную статистику
            TelegramRateLimitService.BroadcastProgress finalProgress = rateLimitService.finalizeBroadcast(broadcastId);
            
            // Отчет для администратора
            String reportMessage = String.format(
                "✅ *Рассылка от @DIMBOpizzaBot завершена*\n\n" +
                "📊 *Статистика:*\n" +
                "👥 Всего пользователей: %d\n" +
                "✅ Успешно отправлено: %d\n" +
                "❌ Ошибок: %d\n" +
                "⏱ Время выполнения: %d мин\n\n" +
                "📝 *Текст сообщения:*\n\"%s\"\n\n" +
                "_Соблюдены лимиты Telegram API (20 сообщений/сек)_",
                finalProgress.getTotalUsers(), 
                finalProgress.getSuccessCount(), 
                finalProgress.getFailureCount(),
                java.time.temporal.ChronoUnit.MINUTES.between(finalProgress.getStartedAt(), finalProgress.getCompletedAt()),
                messageText
            );

            sendMessageToAdmin(adminChatId, reportMessage);
            
            log.info("✅ Массовая рассылка {} завершена: успешно={}, ошибок={}, всего={}", 
                    broadcastId, finalProgress.getSuccessCount(), finalProgress.getFailureCount(), finalProgress.getTotalUsers());

        } catch (Exception e) {
            log.error("❌ Ошибка при массовой рассылке: {}", e.getMessage(), e);
            sendMessageToAdmin(adminChatId, "❌ Произошла ошибка при массовой рассылке: " + e.getMessage());
        }
    }

    /**
     * Обрабатывает пакет пользователей с учетом лимитов
     */
    private void processBatch(List<com.baganov.pizzanat.entity.User> batch, String message, String broadcastId) {
        for (com.baganov.pizzanat.entity.User user : batch) {
            try {
                // Проверяем лимиты перед отправкой
                if (!rateLimitService.canSendMessage()) {
                    long delay = rateLimitService.getRecommendedDelay();
                    log.debug("⏳ Достигнут лимит, ожидаем {}мс", delay);
                    Thread.sleep(delay);
                }
                
                // Отправляем сообщение
                telegramUserNotificationService.sendPersonalMessage(user.getTelegramId(), message);
                rateLimitService.registerMessageSent();
                rateLimitService.updateBroadcastProgress(broadcastId, true);
                
                // Базовая задержка между сообщениями
                Thread.sleep(rateLimitService.getRecommendedDelay());
                
            } catch (Exception e) {
                log.warn("⚠️ Ошибка отправки сообщения пользователю {}: {}", user.getTelegramId(), e.getMessage());
                rateLimitService.updateBroadcastProgress(broadcastId, false);
                
                // При ошибке увеличиваем задержку
                try {
                    Thread.sleep(2000);
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                    break;
                }
            }
        }
    }

    /**
     * Оценивает время выполнения рассылки
     */
    private int estimateBroadcastTime(int userCount) {
        // Примерно 20 сообщений в секунду с учетом задержек и пакетной обработки
        int estimatedSeconds = (userCount / 15) + (userCount / 10); // Консервативная оценка
        return Math.max(1, estimatedSeconds / 60); // Минимум 1 минута
    }

}