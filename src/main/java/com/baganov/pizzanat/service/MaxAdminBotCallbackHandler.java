/**
 * @file: MaxAdminBotCallbackHandler.java
 * @description: Обработчик callback запросов для MAX админского бота
 * @dependencies: MaxAdminBotService
 * @created: 2026-03-27
 */
package com.baganov.pizzanat.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class MaxAdminBotCallbackHandler {

    private final MaxAdminBotService maxAdminBotService;

    /**
     * Обработка callback запросов от MAX админского бота
     *
     * @param maxUserId    ID пользователя MAX
     * @param messageId    ID сообщения
     * @param callbackData данные callback
     */
    public void handleCallback(Long maxUserId, Long messageId, String callbackData) {
        try {
            log.debug("MAX Admin: Обработка callback: userId={}, messageId={}, data={}",
                    maxUserId, messageId, callbackData);

            if (callbackData.startsWith("max_order_")) {
                // Изменение статуса заказа: max_order_{orderId}_{status}
                maxAdminBotService.handleOrderStatusChange(maxUserId, messageId, callbackData);
            } else if (callbackData.startsWith("max_details_")) {
                // Запрос деталей заказа: max_details_{orderId}
                maxAdminBotService.handleOrderDetailsRequest(maxUserId, callbackData);
            } else if (callbackData.startsWith("max_review_")) {
                // Запрос на отзыв: max_review_{orderId}
                maxAdminBotService.handleOrderReviewRequest(maxUserId, callbackData);
            } else {
                log.warn("MAX Admin: Неизвестный callback data: {}", callbackData);
            }
        } catch (Exception e) {
            log.error("MAX Admin: Ошибка обработки callback: {}", e.getMessage(), e);
        }
    }

    /**
     * Обработка команд MAX админского бота
     *
     * @param command  команда
     * @param maxUserId ID пользователя MAX
     * @param username  имя пользователя
     * @param firstName имя
     */
    public void handleCommand(String command, Long maxUserId, String username, String firstName) {
        try {
            log.debug("MAX Admin: Обработка команды: command={}, userId={}", command, maxUserId);

            switch (command) {
                case "/start":
                    handleStartCommand(maxUserId, username, firstName);
                    break;
                case "/register":
                    handleRegisterCommand(maxUserId, username, firstName);
                    break;
                case "/stats":
                    handleStatsCommand(maxUserId);
                    break;
                case "/orders":
                    handleOrdersCommand(maxUserId);
                    break;
                case "/help":
                    handleHelpCommand(maxUserId);
                    break;
                default:
                    log.debug("MAX Admin: Неизвестная команда: {}", command);
                    maxAdminBotService.sendMessageToUser(maxUserId,
                            "❓ Неизвестная команда. Используйте /help для списка команд.");
            }
        } catch (Exception e) {
            log.error("MAX Admin: Ошибка обработки команды {}: {}", command, e.getMessage(), e);
        }
    }

    /**
     * Обработка команды /start (запуск бота)
     */
    private void handleStartCommand(Long maxUserId, String username, String firstName) {
        String displayName = (firstName != null && !firstName.isEmpty()) ? firstName : username;
        if (displayName == null || displayName.isEmpty()) {
            displayName = "пользователь";
        }

        String welcomeMessage = String.format("""
                👋 **Добро пожаловать, %s!**

                Это **MAX Admin Bot** для ДИМБО ПИЦЦА.

                **Для начала работы:**
                1. Используйте /register для регистрации как администратор
                2. После регистрации вы будете получать уведомления о новых заказах

                **Доступные команды:**
                `/register` - Регистрация
                `/stats` - Статистика заказов
                `/orders` - Активные заказы
                `/help` - Справка
                """, displayName);

        maxAdminBotService.sendMessageToUser(maxUserId, welcomeMessage);
        log.info("MAX Admin: Команда /start обработана для userId={}, username={}", maxUserId, username);
    }

    /**
     * Обработка команды регистрации
     */
    private void handleRegisterCommand(Long maxUserId, String username, String firstName) {
        boolean registered = maxAdminBotService.registerAdmin(maxUserId, username, firstName, null);
        if (registered) {
            maxAdminBotService.sendMessageToUser(maxUserId,
                    "✅ **Вы успешно зарегистрированы как администратор!**\n\n" +
                            "Теперь вы будете получать уведомления о новых заказах.");
        } else {
            maxAdminBotService.sendMessageToUser(maxUserId,
                    "ℹ️ Вы уже зарегистрированы как администратор.");
        }
        log.info("MAX Admin: Регистрация администратора: userId={}, result={}", maxUserId, registered);
    }

    /**
     * Обработка команды статистики
     */
    private void handleStatsCommand(Long maxUserId) {
        if (!maxAdminBotService.isRegisteredAdmin(maxUserId)) {
            log.warn("MAX Admin: Неавторизованный доступ к статистике: userId={}", maxUserId);
            maxAdminBotService.sendMessageToUser(maxUserId, "❌ У вас нет прав для просмотра статистики.");
            return;
        }

        String statsMessage = maxAdminBotService.getOrdersStats();
        maxAdminBotService.sendMessageToUser(maxUserId, statsMessage);
        log.debug("MAX Admin: Статистика отправлена: userId={}", maxUserId);
    }

    /**
     * Обработка команды списка заказов
     */
    private void handleOrdersCommand(Long maxUserId) {
        if (!maxAdminBotService.isRegisteredAdmin(maxUserId)) {
            log.warn("MAX Admin: Неавторизованный доступ к заказам: userId={}", maxUserId);
            maxAdminBotService.sendMessageToUser(maxUserId, "❌ У вас нет прав для просмотра заказов.");
            return;
        }

        maxAdminBotService.sendActiveOrdersWithButtons(maxUserId);
        log.debug("MAX Admin: Список заказов с кнопками отправлен: userId={}", maxUserId);
    }

    /**
     * Обработка команды помощи
     */
    private void handleHelpCommand(Long maxUserId) {
        String helpMessage = """
                📖 **СПРАВКА ПО КОМАНДАМ MAX АДМИН БОТА**

                **Основные команды:**
                `/register` - Регистрация как администратор
                `/stats` - Статистика заказов за сегодня
                `/orders` - Список активных заказов с кнопками управления
                `/help` - Эта справка

                **Управление заказами:**
                При получении уведомления о новом заказе вы можете:
                • ✅ Подтвердить - перевести в статус "Подтвержден"
                • 👨‍🍳 Готовится - перевести в статус "Готовится"
                • 📦 Готов - перевести в статус "Готов"
                • 🚗 В доставке - перевести в статус "Доставляется"
                • ✅ Доставлен - перевести в статус "Доставлен"
                • ❌ Отменить - отменить заказ
                • 🔍 Детали - показать полную информацию о заказе
                • ⭐ Отзыв - отправить запрос на отзыв пользователю

                **Статусы оплаты:**
                💵 - Наличными при доставке
                📱 - СБП (Система быстрых платежей)
                💳 - Банковская карта
                ✅ - Оплачено
                🔄 - Ожидает оплаты
                """;

        maxAdminBotService.sendMessageToUser(maxUserId, helpMessage);
    }

    /**
     * Обработка простого текстового сообщения
     */
    public void handleMessage(Long maxUserId, String messageText, String username, String firstName) {
        // Если сообщение начинается с /, обрабатываем как команду
        if (messageText.startsWith("/")) {
            String command = messageText.split("\\s+")[0].toLowerCase();
            handleCommand(command, maxUserId, username, firstName);
            return;
        }

        // Для обычных сообщений отправляем справку
        maxAdminBotService.sendMessageToUser(maxUserId,
                "👋 Привет! Я MAX Admin Bot для ДИМБО ПИЦЦА.\n\n" +
                        "Используйте команды:\n" +
                        "• /register - Регистрация\n" +
                        "• /stats - Статистика\n" +
                        "• /orders - Активные заказы\n" +
                        "• /help - Справка");
    }
}
