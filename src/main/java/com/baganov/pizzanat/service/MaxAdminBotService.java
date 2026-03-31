/**
 * @file: MaxAdminBotService.java
 * @description: Сервис для работы с админским MAX ботом
 * @dependencies: MaxAdminUserRepository, OrderService, MaxAdminNotificationService
 * @created: 2026-03-27
 */
package com.baganov.pizzanat.service;

import com.baganov.pizzanat.config.MaxBotConfig;
import com.baganov.pizzanat.entity.*;
import com.baganov.pizzanat.event.NewOrderEvent;
import com.baganov.pizzanat.event.PaymentAlertEvent;
import com.baganov.pizzanat.event.PaymentStatusChangedEvent;
import com.baganov.pizzanat.model.dto.order.OrderDTO;
import com.baganov.pizzanat.model.entity.TelegramAdminUser;
import com.baganov.pizzanat.repository.TelegramAdminUserRepository;
import com.baganov.pizzanat.repository.PaymentRepository;
import com.baganov.pizzanat.repository.UserRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.event.EventListener;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.client.RestTemplate;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.*;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional
public class MaxAdminBotService {

    private final TelegramAdminUserRepository adminUserRepository;
    private final MaxBotConfig maxBotConfig;
    private final OrderService orderService;
    private final PaymentRepository paymentRepository;
    private final UserRepository userRepository;
    private final RestTemplate restTemplate;
    private final ObjectMapper objectMapper;

    private static final DateTimeFormatter TIME_FORMATTER = DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm");

    // ==================== РЕГИСТРАЦИЯ АДМИНИСТРАТОРОВ ====================

    /**
     * Регистрация администратора
     */
    public boolean registerAdmin(Long maxUserId, String username, String firstName, String lastName) {
        try {
            // Используем telegram_chat_id для хранения MAX User ID
            Optional<TelegramAdminUser> existing = adminUserRepository.findByTelegramChatId(maxUserId);
            if (existing.isPresent()) {
                log.info("MAX: Администратор уже зарегистрирован: userId={}, username={}", maxUserId, username);
                return false;
            }

            TelegramAdminUser adminUser = TelegramAdminUser.builder()
                    .telegramChatId(maxUserId) // MAX User ID храним в telegramChatId
                    .username(username)
                    .firstName(firstName)
                    .lastName(lastName)
                    .isActive(true)
                    .registeredAt(LocalDateTime.now())
                    .build();

            adminUserRepository.save(adminUser);
            log.info("MAX: Зарегистрирован новый администратор: userId={}, username={}", maxUserId, username);
            return true;

        } catch (Exception e) {
            log.error("MAX: Ошибка при регистрации администратора: userId={}, error={}", maxUserId, e.getMessage(), e);
            return false;
        }
    }

    /**
     * Проверка, является ли пользователь зарегистрированным администратором
     */
    public boolean isRegisteredAdmin(Long maxUserId) {
        return adminUserRepository.findByTelegramChatIdAndIsActiveTrue(maxUserId).isPresent();
    }

    /**
     * Получение всех активных администраторов
     */
    public List<TelegramAdminUser> getActiveAdmins() {
        return adminUserRepository.findByIsActiveTrue();
    }

    // ==================== ОБРАБОТКА CALLBACK ====================

    /**
     * Обработка изменения статуса заказа через кнопки
     */
    public void handleOrderStatusChange(Long maxUserId, Long messageId, String callbackData) {
        try {
            // Парсим callback data: max_order_{orderId}_{newStatus}
            String[] parts = callbackData.split("_");
            if (parts.length != 4) {
                log.error("MAX: Некорректный формат callback data: {}", callbackData);
                return;
            }

            Integer orderId = Integer.parseInt(parts[2]);
            String newStatusStr = parts[3];

            // Проверяем текущий статус заказа перед изменением
            Optional<Order> orderOpt = orderService.findById(orderId.longValue());
            if (orderOpt.isPresent()) {
                Order currentOrder = orderOpt.get();
                String currentStatus = currentOrder.getStatus().getName();

                // Если статус уже установлен, не выполняем изменение
                if (newStatusStr.equalsIgnoreCase(currentStatus)) {
                    String alreadySetMessage = String.format(
                            "ℹ️ **Статус заказа #%d уже установлен**\n\n" +
                                    "Текущий статус: %s\n" +
                                    "Изменений не требуется",
                            orderId,
                            getStatusDisplayNameByString(newStatusStr));
                    sendMessageToUser(maxUserId, alreadySetMessage);

                    log.info("MAX: Статус заказа #{} уже установлен на {}, пропускаем изменение", orderId, newStatusStr);
                    return;
                }
            }

            // Обновляем статус заказа
            OrderDTO updatedOrder = orderService.updateOrderStatus(orderId, newStatusStr);

            String statusDisplayName = getStatusDisplayNameByString(newStatusStr);

            String successMessage = String.format(
                    "✅ **Статус заказа #%d изменен**\n\n" +
                            "Новый статус: %s\n" +
                            "Изменено: %s",
                    orderId,
                    statusDisplayName,
                    LocalDateTime.now().format(TIME_FORMATTER));
            sendMessageToUser(maxUserId, successMessage);

            log.info("MAX: Статус заказа #{} изменен на {} администратором userId={}",
                    orderId, newStatusStr, maxUserId);

        } catch (Exception e) {
            log.error("MAX: Ошибка при обработке изменения статуса заказа: {}", e.getMessage(), e);
            sendMessageToUser(maxUserId, "❌ Ошибка при изменении статуса заказа");
        }
    }

    /**
     * Обработка запроса деталей заказа
     */
    public void handleOrderDetailsRequest(Long maxUserId, String callbackData) {
        try {
            // Парсим callback data: max_details_{orderId}
            String[] parts = callbackData.split("_");
            if (parts.length != 3) {
                log.error("MAX: Некорректный формат callback data: {}", callbackData);
                return;
            }

            Long orderId = Long.parseLong(parts[2]);
            Optional<Order> orderOpt = orderService.findById(orderId);

            if (orderOpt.isPresent()) {
                String detailsMessage = formatOrderDetails(orderOpt.get());
                sendMessageToUser(maxUserId, detailsMessage);
            } else {
                sendMessageToUser(maxUserId, "❌ Заказ не найден");
            }

        } catch (Exception e) {
            log.error("MAX: Ошибка при получении деталей заказа: {}", e.getMessage(), e);
            sendMessageToUser(maxUserId, "❌ Произошла ошибка при получении деталей заказа");
        }
    }

    /**
     * Обработка запроса на отправку отзыва пользователю
     */
    public void handleOrderReviewRequest(Long maxUserId, String callbackData) {
        try {
            // Парсим callback data: max_review_{orderId}
            String[] parts = callbackData.split("_");
            if (parts.length != 3) {
                log.error("MAX: Некорректный формат callback data: {}", callbackData);
                return;
            }

            Long orderId = Long.parseLong(parts[2]);

            // Подтверждаем администратору
            String successMessage = String.format(
                    "✅ **Запрос на отзыв отправлен**\n\n" +
                            "📋 Заказ #%d\n" +
                            "Отправлено: %s\n\n" +
                            "📱 Уведомление отправлено пользователю",
                    orderId,
                    LocalDateTime.now().format(TIME_FORMATTER));

            sendMessageToUser(maxUserId, successMessage);

            log.info("MAX: Запрос на отзыв для заказа #{} отправлен администратором userId={}", orderId, maxUserId);

        } catch (Exception e) {
            log.error("MAX: Ошибка при отправке запроса на отзыв: {}", e.getMessage(), e);
            sendMessageToUser(maxUserId, "❌ Произошла ошибка при отправке запроса на отзыв");
        }
    }

    // ==================== КОМАНДЫ ====================

    /**
     * Получение статистики заказов
     */
    public String getOrdersStats() {
        try {
            LocalDate today = LocalDate.now();
            LocalDateTime startOfDay = today.atStartOfDay();
            LocalDateTime endOfDay = today.plusDays(1).atStartOfDay();

            List<Order> todayOrders = orderService.findOrdersByDateRange(startOfDay, endOfDay);

            long totalOrders = todayOrders.size();
            BigDecimal totalRevenue = todayOrders.stream()
                    .map(Order::getTotalAmount)
                    .reduce(BigDecimal.ZERO, BigDecimal::add);

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
                    "📊 **Статистика заказов за %s**\n\n" +
                            "📦 Всего заказов: %d\n" +
                            "💰 Общая сумма: %.2f ₽\n\n" +
                            "**По статусам:**\n" +
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
            log.error("MAX: Ошибка при получении статистики заказов: {}", e.getMessage(), e);
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
                return "📋 **Активные заказы**\n\nНет активных заказов";
            }

            StringBuilder message = new StringBuilder("📋 **Активные заказы**\n\n");

            for (Order order : activeOrders) {
                message.append(String.format(
                        "🔸 **Заказ #%d**\n" +
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
            log.error("MAX: Ошибка при получении активных заказов: {}", e.getMessage(), e);
            return "❌ Ошибка при получении списка заказов";
        }
    }

    /**
     * Отправка активных заказов с кнопками управления
     */
    public void sendActiveOrdersWithButtons(Long maxUserId) {
        try {
            List<Order> activeOrders = orderService.findActiveOrdersIncludingNew();

            if (activeOrders.isEmpty()) {
                sendMessageToUser(maxUserId, "📋 **Активные заказы**\n\nНет активных заказов");
                return;
            }

            // Отправляем заголовок
            sendMessageToUser(maxUserId, "📋 **Активные заказы (включая новые)**");

            // Отправляем каждый заказ отдельным сообщением с кнопками
            for (Order order : activeOrders) {
                Payment latestPayment = getLatestPayment(order);
                OrderDisplayStatus displayStatus = determineOrderDisplayStatus(order, latestPayment);

                StringBuilder orderMessage = new StringBuilder();
                orderMessage.append(displayStatus.getEmoji()).append(" **Заказ #").append(order.getId()).append("**\n");
                orderMessage.append("Статус: ").append(getStatusDisplayName(order.getStatus())).append("\n");
                orderMessage.append("Оплата: ").append(getPaymentStatusDisplay(order)).append("\n");
                orderMessage.append("Сумма: ").append(String.format("%.2f", order.getTotalAmount())).append(" ₽\n");
                orderMessage.append("Время: ")
                        .append(order.getCreatedAt().format(DateTimeFormatter.ofPattern("dd.MM HH:mm"))).append("\n\n");

                // Контактные данные
                orderMessage.append("📞 **Контакт:** ").append(escapeMarkdown(order.getContactName()))
                        .append("\n");
                orderMessage.append("📞 **Телефон:** ").append(escapeMarkdown(order.getContactPhone())).append("\n");

                String finalMessage = orderMessage.toString();
                List<Map<String, Object>> attachments = createOrderManagementAttachments(order.getId());

                sendMessageToUserWithButtons(maxUserId, finalMessage, attachments);
            }

            log.debug("MAX: Отправлено {} активных заказов с кнопками администратору: userId={}",
                    activeOrders.size(), maxUserId);

        } catch (Exception e) {
            log.error("MAX: Ошибка при отправке активных заказов с кнопками: {}", e.getMessage(), e);
            sendMessageToUser(maxUserId, "❌ Ошибка при получении списка заказов");
        }
    }

    // ==================== СОБЫТИЯ ====================

    /**
     * Обработчик события создания нового заказа
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
            log.info("📧 MAX: Получено событие о новом заказе #{} для уведомления администраторов", order.getId());

            notifyAdminsAboutNewOrder(order);
            log.info("✅ MAX: Уведомление о новом заказе #{} отправлено", order.getId());

        } catch (Exception e) {
            log.error("❌ MAX: Ошибка обработки события нового заказа #{}: {}",
                    event.getOrder().getId(), e.getMessage(), e);
        }
    }

    /**
     * Обработчик событий алертов платежной системы
     */
    @EventListener
    @Async
    public void handlePaymentAlertEvent(PaymentAlertEvent event) {
        if (!maxBotConfig.isAdminEnabled()) {
            return;
        }

        try {
            log.info("🚨 MAX: Получено событие алерта платежной системы: {}", event.getAlertType());
            notifyAdminsAboutPaymentAlert(event.getAlertMessage());
        } catch (Exception e) {
            log.error("❌ MAX: Ошибка обработки события алерта платежной системы: {}", e.getMessage(), e);
        }
    }

    /**
     * Обработчик события успешной оплаты
     */
    @EventListener
    @Async
    public void handlePaymentStatusChangedEvent(PaymentStatusChangedEvent event) {
        if (!maxBotConfig.isAdminEnabled()) {
            return;
        }

        try {
            // Отправляем уведомление только при успешной оплате
            if (event.getNewStatus() == PaymentStatus.SUCCEEDED) {
                Integer orderId = event.getOrderId();
                Optional<Order> orderOpt = orderService.findById(orderId.longValue());

                if (orderOpt.isPresent()) {
                    Order order = orderOpt.get();
                    notifyAdminsAboutPaymentSuccess(order);
                    log.info("✅ MAX: Уведомление об успешной оплате заказа #{} отправлено", orderId);
                }
            }
        } catch (Exception e) {
            log.error("❌ MAX: Ошибка обработки события оплаты: {}", e.getMessage(), e);
        }
    }

    /**
     * Уведомление администраторов об успешной оплате
     */
    private void notifyAdminsAboutPaymentSuccess(Order order) {
        try {
            String paymentMessage = formatPaymentSuccessMessage(order);

            List<TelegramAdminUser> activeAdmins = adminUserRepository.findByIsActiveTrue();

            if (activeAdmins.isEmpty()) {
                // Отправляем в общий чат если настроен
                if (maxBotConfig.getAdminChatId() != null && !maxBotConfig.getAdminChatId().isEmpty()) {
                    sendMessageToChat(maxBotConfig.getAdminChatId(), paymentMessage);
                }
                return;
            }

            for (TelegramAdminUser admin : activeAdmins) {
                try {
                    sendMessageToUser(admin.getTelegramChatId(), paymentMessage);
                    log.debug("MAX: Уведомление об оплате отправлено администратору: {}", admin.getUsername());
                } catch (Exception e) {
                    log.error("MAX: Ошибка отправки уведомления об оплате {}: {}", admin.getUsername(), e.getMessage());
                }
            }

        } catch (Exception e) {
            log.error("MAX: Ошибка при уведомлении об оплате: {}", e.getMessage(), e);
        }
    }

    /**
     * Форматирование сообщения об успешной оплате
     */
    private String formatPaymentSuccessMessage(Order order) {
        return String.format(
                "💳 **ОПЛАТА ПРОШЛА УСПЕШНО**\n\n" +
                        "📦 **Заказ номер %d успешно оплачен**\n" +
                        "💰 **Сумма:** %.2f ₽\n" +
                        "📅 **Дата:** %s\n\n" +
                        "👤 **Клиент:** %s\n" +
                        "📞 **Телефон:** %s",
                order.getId(),
                order.getTotalAmount(),
                LocalDateTime.now().format(TIME_FORMATTER),
                escapeMarkdown(order.getContactName()),
                escapeMarkdown(order.getContactPhone()));
    }

    /**
     * Отправка сообщения в чат
     */
    private void sendMessageToChat(String chatId, String message) {
        sendMessageToChatWithButtons(chatId, message, null);
    }

    // ==================== УВЕДОМЛЕНИЯ ====================

    /**
     * Уведомление всех администраторов о новом заказе
     */
    public void notifyAdminsAboutNewOrder(Order order) {
        try {
            List<TelegramAdminUser> activeAdmins = adminUserRepository.findByIsActiveTrue();

            if (activeAdmins.isEmpty()) {
                log.warn("MAX: Нет активных администраторов для уведомления о заказе #{}", order.getId());
                // Отправляем в общий чат если настроен
                if (maxBotConfig.getAdminChatId() != null && !maxBotConfig.getAdminChatId().isEmpty()) {
                    String orderMessage = formatNewOrderMessage(order);
                    List<Map<String, Object>> attachments = createOrderManagementAttachments(order.getId());
                    sendMessageToChatWithButtons(maxBotConfig.getAdminChatId(), orderMessage, attachments);
                }
                return;
            }

            String orderMessage = formatNewOrderMessage(order);
            List<Map<String, Object>> attachments = createOrderManagementAttachments(order.getId());

            for (TelegramAdminUser admin : activeAdmins) {
                try {
                    sendMessageToUserWithButtons(admin.getTelegramChatId(), orderMessage, attachments);
                    log.debug("MAX: Уведомление о заказе #{} отправлено администратору: {}",
                            order.getId(), admin.getUsername());
                } catch (Exception e) {
                    log.error("MAX: Ошибка отправки уведомления администратору {}: {}",
                            admin.getUsername(), e.getMessage());
                }
            }

            log.info("MAX: Уведомления о заказе #{} отправлены {} администраторам",
                    order.getId(), activeAdmins.size());

        } catch (Exception e) {
            log.error("MAX: Ошибка при уведомлении администраторов о заказе #{}: {}",
                    order.getId(), e.getMessage(), e);
        }
    }

    /**
     * Отправка алерта всем администраторам
     */
    private void notifyAdminsAboutPaymentAlert(String alertMessage) {
        try {
            List<TelegramAdminUser> activeAdmins = adminUserRepository.findByIsActiveTrue();

            if (activeAdmins.isEmpty()) {
                log.warn("MAX: Нет активных администраторов для отправки алерта");
                return;
            }

            for (TelegramAdminUser admin : activeAdmins) {
                try {
                    sendMessageToUser(admin.getTelegramChatId(), alertMessage);
                    log.debug("MAX: Алерт отправлен администратору: {}", admin.getUsername());
                } catch (Exception e) {
                    log.error("MAX: Ошибка отправки алерта администратору {}: {}",
                            admin.getUsername(), e.getMessage());
                }
            }

            log.info("MAX: Алерт отправлен {} администраторам", activeAdmins.size());

        } catch (Exception e) {
            log.error("MAX: Ошибка при отправке алерта администраторам: {}", e.getMessage(), e);
        }
    }

    // ==================== ОТПРАВКА СООБЩЕНИЙ ====================

    /**
     * Отправка сообщения пользователю
     *
     * @return true если сообщение отправлено успешно, false при ошибке
     */
    public boolean sendMessageToUser(Long maxUserId, String message) {
        return sendMessageToUserWithButtons(maxUserId, message, null);
    }

    /**
     * Отправка сообщения пользователю с кнопками
     *
     * Документация MAX API: https://dev.max.ru/docs-api/methods/POST/messages
     * URL: POST /messages?user_id={user_id}
     * Authorization: Header "Authorization: {access_token}"
     *
     * @return true если сообщение отправлено успешно, false при ошибке
     */
    public boolean sendMessageToUserWithButtons(Long maxUserId, String message,
            List<Map<String, Object>> attachments) {
        String adminBotToken = maxBotConfig.getAdminBotToken();

        if (adminBotToken == null || adminBotToken.isEmpty()) {
            log.warn("MAX: Admin bot token не настроен - нельзя отправить сообщение");
            return false;
        }

        // MAX API формат: POST /messages?user_id={user_id}
        // Токен передается в заголовке Authorization
        String url = String.format("%s/messages?user_id=%d", maxBotConfig.getApiUrl(), maxUserId);

        Map<String, Object> body = new HashMap<>();
        body.put("text", message);
        body.put("format", "markdown"); // Включаем Markdown форматирование

        if (attachments != null && !attachments.isEmpty()) {
            body.put("attachments", attachments);
        }

        try {
            // Логируем тело запроса для отладки
            String jsonBody = objectMapper.writeValueAsString(body);
            log.info("MAX API: Отправка сообщения пользователю {}: URL={}, Body={}", maxUserId, url, jsonBody);

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            headers.set("Authorization", adminBotToken); // Токен в заголовке Authorization

            HttpEntity<Map<String, Object>> entity = new HttpEntity<>(body, headers);

            var response = restTemplate.postForEntity(url, entity, String.class);
            log.info("MAX API: Ответ для пользователя {}: Status={}, Body={}", maxUserId, response.getStatusCode(), response.getBody());
            return true; // Успешно отправлено

        } catch (Exception e) {
            log.error("MAX: Error calling MAX API for user {}: {}", maxUserId, e.getMessage(), e);
            return false; // Ошибка отправки
        }
    }

    /**
     * Отправка сообщения в чат с кнопками
     *
     * Документация MAX API: https://dev.max.ru/docs-api/methods/POST/messages
     * URL: POST /messages?chat_id={chat_id}
     * Authorization: Header "Authorization: {access_token}"
     */
    private void sendMessageToChatWithButtons(String chatId, String message,
            List<Map<String, Object>> attachments) {
        String adminBotToken = maxBotConfig.getAdminBotToken();

        if (adminBotToken == null || adminBotToken.isEmpty()) {
            log.warn("MAX: Admin bot token не настроен");
            return;
        }

        // MAX API формат: POST /messages?chat_id={chat_id}
        String url = String.format("%s/messages?chat_id=%s", maxBotConfig.getApiUrl(), chatId);

        Map<String, Object> body = new HashMap<>();
        body.put("text", message);
        body.put("format", "markdown"); // Включаем Markdown форматирование

        if (attachments != null && !attachments.isEmpty()) {
            body.put("attachments", attachments);
        }

        try {
            // Логируем тело запроса для отладки
            String jsonBody = objectMapper.writeValueAsString(body);
            log.info("MAX API: Отправка сообщения в чат {}: URL={}, Body={}", chatId, url, jsonBody);

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            headers.set("Authorization", adminBotToken); // Токен в заголовке Authorization

            HttpEntity<Map<String, Object>> entity = new HttpEntity<>(body, headers);

            var response = restTemplate.postForEntity(url, entity, String.class);
            log.info("MAX API: Ответ для чата {}: Status={}, Body={}", chatId, response.getStatusCode(), response.getBody());

        } catch (Exception e) {
            log.error("MAX: Error calling MAX API for chat {}: {}", chatId, e.getMessage(), e);
        }
    }

    // ==================== INLINE КНОПКИ ====================

    /**
     * Создание inline клавиатуры для управления заказом
     * Формат соответствует MAX API документации:
     * https://dev.max.ru/docs-api
     */
    private List<Map<String, Object>> createOrderManagementAttachments(Integer orderId) {
        List<Map<String, Object>> attachments = new ArrayList<>();

        // Создаем кнопки по строкам (2D массив - array of rows)
        List<List<Map<String, Object>>> buttonRows = new ArrayList<>();

        // Строка 1: Подтвердить, Готовится
        List<Map<String, Object>> row1 = new ArrayList<>();
        row1.add(createCallbackButton("✅ Подтвердить", "max_order_" + orderId + "_CONFIRMED"));
        row1.add(createCallbackButton("👨‍🍳 Готовится", "max_order_" + orderId + "_PREPARING"));
        buttonRows.add(row1);

        // Строка 2: Готов, В доставке
        List<Map<String, Object>> row2 = new ArrayList<>();
        row2.add(createCallbackButton("📦 Готов", "max_order_" + orderId + "_READY"));
        row2.add(createCallbackButton("🚗 В доставке", "max_order_" + orderId + "_DELIVERING"));
        buttonRows.add(row2);

        // Строка 3: Доставлен, Отменить
        List<Map<String, Object>> row3 = new ArrayList<>();
        row3.add(createCallbackButton("✅ Доставлен", "max_order_" + orderId + "_DELIVERED"));
        row3.add(createCallbackButton("❌ Отменить", "max_order_" + orderId + "_CANCELLED"));
        buttonRows.add(row3);

        // Строка 4: Детали, Отзыв
        List<Map<String, Object>> row4 = new ArrayList<>();
        row4.add(createCallbackButton("🔍 Детали", "max_details_" + orderId));
        row4.add(createCallbackButton("⭐ Отзыв", "max_review_" + orderId));
        buttonRows.add(row4);

        // Формируем attachment в формате MAX API
        Map<String, Object> attachment = new HashMap<>();
        attachment.put("type", "inline_keyboard");

        Map<String, Object> payload = new HashMap<>();
        payload.put("buttons", buttonRows);
        attachment.put("payload", payload);

        attachments.add(attachment);

        log.debug("MAX: Создана inline клавиатура для заказа #{}: {} строк", orderId, buttonRows.size());
        return attachments;
    }

    /**
     * Создание callback кнопки в формате MAX API
     * Документация: https://dev.max.ru/docs-api
     */
    private Map<String, Object> createCallbackButton(String text, String payload) {
        Map<String, Object> button = new HashMap<>();
        button.put("type", "callback");
        button.put("text", text);
        button.put("payload", payload);
        return button;
    }

    // ==================== ФОРМАТИРОВАНИЕ СООБЩЕНИЙ ====================

    /**
     * Форматирование сообщения о новом заказе
     */
    private String formatNewOrderMessage(Order order) {
        StringBuilder sb = new StringBuilder();

        // Определяем статус оплаты
        Payment latestPayment = getLatestPayment(order);
        OrderDisplayStatus displayStatus = determineOrderDisplayStatus(order, latestPayment);

        // Заголовок
        String paymentEmoji = getPaymentEmoji(order.getPaymentMethod());
        String paymentLabel = order.getPaymentMethod() != null ? order.getPaymentMethod().getDisplayName() : "Не указан";
        sb.append(displayStatus.getEmoji()).append(" **НОВЫЙ ЗАКАЗ #").append(order.getId())
                .append(" ").append(paymentEmoji).append("**\n\n");

        // Время заказа
        sb.append("🕐 **Время заказа:** ").append(order.getCreatedAt().format(TIME_FORMATTER)).append("\n\n");

        // Контактные данные
        sb.append("📞 **КОНТАКТНЫЕ ДАННЫЕ**\n");
        sb.append("Имя: ").append(escapeMarkdown(order.getContactName())).append("\n");
        sb.append("Телефон: ").append(escapeMarkdown(order.getContactPhone())).append("\n\n");

        // Адрес доставки
        if (order.getDeliveryAddress() != null && !order.getDeliveryAddress().isEmpty()) {
            sb.append("📍 **ДОСТАВКА**\n");
            sb.append("Адрес: ").append(escapeMarkdown(order.getDeliveryAddress())).append("\n\n");
        } else if (order.getDeliveryLocation() != null) {
            sb.append("📍 **ПУНКТ ВЫДАЧИ**\n");
            sb.append("Пункт: ").append(escapeMarkdown(order.getDeliveryLocation().getName())).append("\n");
            if (order.getDeliveryLocation().getAddress() != null) {
                sb.append("Адрес: ").append(escapeMarkdown(order.getDeliveryLocation().getAddress())).append("\n");
            }
            sb.append("\n");
        }

        // Способ доставки
        String deliveryType = order.getDeliveryType() != null ? order.getDeliveryType() : "Доставка курьером";
        String deliveryEmoji = deliveryType.contains("Самовывоз") ? "🏠" : "🚛";
        sb.append(deliveryEmoji).append(" **Способ доставки:** ").append(escapeMarkdown(deliveryType)).append("\n\n");

        // Комментарий
        if (order.getComment() != null && !order.getComment().trim().isEmpty()) {
            sb.append("💬 **Комментарий:** ").append(escapeMarkdown(order.getComment())).append("\n\n");
        }

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
        sb.append("💳 **СТАТУС ОПЛАТЫ:** ").append(getPaymentStatusDisplay(order)).append("\n");
        sb.append("💰 **СПОСОБ ОПЛАТЫ:** ").append(paymentLabel).append("\n");

        return sb.toString();
    }

    /**
     * Форматирование деталей заказа
     */
    private String formatOrderDetails(Order order) {
        StringBuilder sb = new StringBuilder();

        sb.append("🔍 **ДЕТАЛИ ЗАКАЗА #").append(order.getId()).append("**\n\n");

        sb.append("📅 **Время создания:** ").append(formatDateTime(order.getCreatedAt())).append("\n");
        sb.append("📋 **Статус:** ").append(getStatusDisplayName(order.getStatus())).append("\n\n");

        // Пользователь системы
        if (order.getUser() != null) {
            sb.append("👤 **ПОЛЬЗОВАТЕЛЬ СИСТЕМЫ**\n");
            sb.append("Имя: ").append(escapeMarkdown(order.getUser().getFirstName()));
            if (order.getUser().getLastName() != null) {
                sb.append(" ").append(escapeMarkdown(order.getUser().getLastName()));
            }
            sb.append("\n");
            if (order.getUser().getUsername() != null) {
                sb.append("Username: @").append(escapeMarkdown(order.getUser().getUsername())).append("\n");
            }
            if (order.getUser().getPhone() != null) {
                sb.append("Телефон: ").append(escapeMarkdown(order.getUser().getPhone())).append("\n");
            }
            sb.append("\n");
        }

        // Контактные данные заказа
        sb.append("📞 **КОНТАКТНЫЕ ДАННЫЕ ЗАКАЗА**\n");
        sb.append("Имя: ").append(escapeMarkdown(order.getContactName())).append("\n");
        sb.append("Телефон: ").append(escapeMarkdown(order.getContactPhone())).append("\n\n");

        // Адрес доставки
        if (order.getDeliveryAddress() != null) {
            sb.append("📍 **ДОСТАВКА**\n");
            sb.append("Адрес: ").append(escapeMarkdown(order.getDeliveryAddress())).append("\n");
        } else if (order.getDeliveryLocation() != null) {
            sb.append("📍 **ПУНКТ ВЫДАЧИ**\n");
            sb.append("Адрес: ").append(escapeMarkdown(order.getDeliveryLocation().getAddress())).append("\n");
        }

        // Способ доставки
        if (order.getDeliveryType() != null) {
            String deliveryIcon = order.isPickup() ? "🏠" : "🚗";
            sb.append("🚛 **Способ доставки:** ").append(deliveryIcon).append(" ")
                    .append(escapeMarkdown(order.getDeliveryType())).append("\n");
        }
        sb.append("\n");

        // Состав заказа
        sb.append("🛒 **СОСТАВ ЗАКАЗА**\n");
        BigDecimal itemsTotal = BigDecimal.ZERO;
        if (order.getItems() != null) {
            for (OrderItem item : order.getItems()) {
                BigDecimal itemSubtotal = item.getPrice().multiply(BigDecimal.valueOf(item.getQuantity()));
                itemsTotal = itemsTotal.add(itemSubtotal);

                sb.append("• ").append(escapeMarkdown(item.getProduct().getName())).append("\n");
                sb.append("  Цена: ").append(item.getPrice()).append(" ₽\n");
                sb.append("  Количество: ").append(item.getQuantity()).append("\n");
                sb.append("  Сумма: ").append(itemSubtotal).append(" ₽\n\n");
            }
        }

        // Расчет суммы
        sb.append("💰 **ДЕТАЛЬНЫЙ РАСЧЕТ СУММЫ:**\n");
        sb.append("├ Товары: ").append(itemsTotal).append(" ₽\n");

        if (order.getDeliveryCost() != null && order.getDeliveryCost().compareTo(BigDecimal.ZERO) > 0) {
            sb.append("├ Доставка: ").append(order.getDeliveryCost()).append(" ₽\n");
        } else if (order.isDeliveryByCourier()) {
            sb.append("├ Доставка: БЕСПЛАТНО\n");
        } else if (order.isPickup()) {
            sb.append("├ Доставка: Самовывоз (0 ₽)\n");
        }

        sb.append("└ **ИТОГО: ").append(order.getTotalAmount()).append(" ₽**\n\n");

        // Статус оплаты
        sb.append("💳 **СТАТУС ОПЛАТЫ:** ").append(getPaymentStatusDisplay(order)).append("\n");
        sb.append("💰 **СПОСОБ ОПЛАТЫ:** ")
                .append(order.getPaymentMethod() != null ? order.getPaymentMethod().getDisplayName() : "Не указан")
                .append("\n");

        // Комментарий
        if (order.getComment() != null && !order.getComment().trim().isEmpty()) {
            sb.append("\n💬 **КОММЕНТАРИЙ**\n").append(escapeMarkdown(order.getComment()));
        }

        return sb.toString();
    }

    // ==================== ВСПОМОГАТЕЛЬНЫЕ МЕТОДЫ ====================

    /**
     * Получает последний платеж для заказа
     */
    private Payment getLatestPayment(Order order) {
        try {
            List<Payment> payments = paymentRepository.findByOrderIdOrderByCreatedAtDesc(order.getId().longValue());
            return payments.isEmpty() ? null : payments.get(0);
        } catch (Exception e) {
            log.error("MAX: Ошибка получения платежа для заказа #{}: {}", order.getId(), e.getMessage(), e);
            return null;
        }
    }

    /**
     * Определение статуса отображения заказа
     */
    private OrderDisplayStatus determineOrderDisplayStatus(Order order, Payment latestPayment) {
        if (latestPayment != null && latestPayment.getStatus() == PaymentStatus.SUCCEEDED) {
            return OrderDisplayStatus.PAYMENT_SUCCESS;
        }

        if (order.getPaymentStatus() == OrderPaymentStatus.PAID) {
            return OrderDisplayStatus.PAYMENT_SUCCESS;
        }

        if (latestPayment != null) {
            switch (latestPayment.getStatus()) {
                case PENDING:
                case WAITING_FOR_CAPTURE:
                    long minutesElapsed = ChronoUnit.MINUTES.between(
                            latestPayment.getCreatedAt(), LocalDateTime.now());

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

        return OrderDisplayStatus.CASH_NEW;
    }

    /**
     * Получение отображаемого статуса оплаты
     */
    private String getPaymentStatusDisplay(Order order) {
        if (order.getPaymentStatus() == OrderPaymentStatus.PAID) {
            return "✅ Оплачено";
        }

        if (order.getPaymentMethod() == PaymentMethod.CASH) {
            if (order.getStatus() != null &&
                    ("DELIVERED".equals(order.getStatus().getName()) ||
                            "COMPLETED".equals(order.getStatus().getName()))) {
                return "✅ Оплачено наличными";
            } else {
                return "💵 Оплата при доставке";
            }
        }

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
            case "COOKING":
                return "👨‍🍳 Готовится";
            case "READY":
                return "🍕 Готов";
            case "DELIVERING":
                return "🚗 Доставляется";
            case "DELIVERED":
            case "COMPLETED":
                return "✅ Доставлен";
            case "CANCELLED":
            case "CANCELED":
                return "❌ Отменен";
            case "CREATED":
                return "📝 Создан";
            default:
                return statusName;
        }
    }

    /**
     * Получить эмодзи для способа оплаты
     */
    private String getPaymentEmoji(PaymentMethod paymentMethod) {
        if (paymentMethod == null)
            return "💵";
        return switch (paymentMethod) {
            case SBP -> "📱";
            case BANK_CARD -> "💳";
            case CASH -> "💵";
            case YOOMONEY -> "💳";
            default -> "💵";
        };
    }

    /**
     * Экранирование символов для Markdown
     */
    private String escapeMarkdown(String text) {
        if (text == null)
            return "";
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

    private String formatDateTime(LocalDateTime dateTime) {
        if (dateTime == null) {
            return "Неизвестно";
        }
        return dateTime.format(TIME_FORMATTER);
    }

    // ==================== МАССОВАЯ РАССЫЛКА ====================

    /**
     * Массовая рассылка сообщения всем пользователям с MAX ID
     * (используем telegram_id для хранения MAX user ID)
     *
     * @param adminUserId  ID администратора MAX
     * @param messageText  текст сообщения для рассылки
     */
    @Async
    public void broadcastMessageToAllMaxUsers(Long adminUserId, String messageText) {
        try {
            // Получаем только MAX пользователей (username начинается с "max_")
            // Это важно! telegram_id хранит ID и от Telegram, и от MAX
            // Фильтруем по префиксу username чтобы отправлять только через MAX API
            List<User> users = userRepository.findByUsernameStartingWithAndIsTelegramVerifiedTrue("max_");

            if (users.isEmpty()) {
                sendMessageToUser(adminUserId, "ℹ️ **Нет пользователей MAX для отправки сообщения**\n\n" +
                        "Пользователи должны сначала запустить бот MAX и нажать кнопку 'Начать'.");
                return;
            }

            log.info("📤 MAX: Начинаем массовую рассылку {} пользователям MAX", users.size());

            // Уведомляем администратора о начале рассылки
            sendMessageToUser(adminUserId, String.format(
                    "🚀 **Рассылка запущена**\n\n" +
                            "👥 Пользователей: %d\n" +
                            "📝 Сообщение: \"%s\"\n\n" +
                            "⏳ Отправка сообщений...",
                    users.size(),
                    messageText.length() > 50 ? messageText.substring(0, 50) + "..." : messageText));

            int successCount = 0;
            int failureCount = 0;

            // Отправляем сообщения с задержкой для соблюдения лимитов MAX API (30 rps)
            for (User user : users) {
                // telegram_id хранит MAX user ID
                Long maxUserId = user.getTelegramId();
                if (maxUserId != null) {
                    boolean sent = sendMessageToUser(maxUserId, messageText);
                    if (sent) {
                        successCount++;
                        log.debug("MAX: Рассылка - сообщение отправлено пользователю {}", maxUserId);
                    } else {
                        failureCount++;
                        log.warn("MAX: Рассылка - не удалось отправить пользователю {}", maxUserId);
                    }

                    // Задержка 50мс для соблюдения лимита 30 rps
                    try {
                        Thread.sleep(50);
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                        log.warn("MAX: Рассылка прервана");
                        break;
                    }
                }
            }

            // Отчет для администратора
            String reportMessage = String.format(
                    "✅ **Рассылка завершена**\n\n" +
                            "📊 **Статистика:**\n" +
                            "👥 Всего пользователей: %d\n" +
                            "✅ Успешно отправлено: %d\n" +
                            "❌ Ошибок: %d\n\n" +
                            "📝 **Текст сообщения:**\n\"%s\"",
                    users.size(), successCount, failureCount, messageText);

            sendMessageToUser(adminUserId, reportMessage);

            log.info("✅ MAX: Массовая рассылка завершена: успешно={}, ошибок={}, всего={}",
                    successCount, failureCount, users.size());

        } catch (Exception e) {
            log.error("❌ MAX: Ошибка при массовой рассылке: {}", e.getMessage(), e);
            sendMessageToUser(adminUserId, "❌ **Произошла ошибка при массовой рассылке:** " + e.getMessage());
        }
    }

    /**
     * Сохранение MAX пользователя в таблицу users
     * (используем поля telegram_id, telegram_username для хранения MAX данных)
     *
     * @param maxUserId   ID пользователя в MAX
     * @param username    username в MAX
     * @param firstName   имя пользователя
     * @param lastName    фамилия пользователя
     * @return true если пользователь сохранен
     */
    @Transactional
    public boolean saveMaxUser(Long maxUserId, String username, String firstName, String lastName) {
        try {
            // Проверяем, существует ли пользователь с таким MAX ID
            Optional<User> existingUser = userRepository.findByTelegramId(maxUserId);

            if (existingUser.isPresent()) {
                log.debug("MAX: Пользователь уже существует: maxUserId={}", maxUserId);
                return false;
            }

            // Создаем нового пользователя
            User user = new User();
            user.setUsername("max_" + maxUserId); // Уникальный username
            user.setPassword(""); // Пустой пароль для MAX пользователей
            user.setFirstName(firstName);
            user.setLastName(lastName);
            user.setTelegramId(maxUserId); // Сохраняем MAX ID в telegram_id
            user.setTelegramUsername(username); // Сохраняем MAX username в telegram_username
            user.setIsTelegramVerified(true); // Помечаем как верифицированного MAX пользователя
            user.setActive(true);

            userRepository.save(user);
            log.info("MAX: Сохранен новый пользователь: maxUserId={}, username={}", maxUserId, username);
            return true;

        } catch (Exception e) {
            log.error("MAX: Ошибка при сохранении пользователя: {}", e.getMessage(), e);
            return false;
        }
    }
}
