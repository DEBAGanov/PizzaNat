/**
 * @file: AdminBotCallbackHandler.java
 * @description: Обработчик callback запросов для админского бота
 * @dependencies: AdminBotService
 * @created: 2025-01-13
 */
package com.baganov.pizzanat.telegram;

import com.baganov.pizzanat.service.AdminBotService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class AdminBotCallbackHandler {

    private final AdminBotService adminBotService;

    /**
     * Обработка callback запросов от админского бота
     */
    public void handleCallback(Long chatId, Integer messageId, String callbackData) {
        try {
            if (callbackData.startsWith("order_status_")) {
                adminBotService.handleOrderStatusChange(chatId, messageId, callbackData);
            } else if (callbackData.startsWith("order_details_")) {
                adminBotService.handleOrderDetailsRequest(chatId, callbackData);
            } else {
                log.warn("Неизвестный callback data: {}", callbackData);
            }
        } catch (Exception e) {
            log.error("Ошибка обработки callback: {}", e.getMessage(), e);
        }
    }

    /**
     * Обработка команд админского бота
     */
    public void handleCommand(String command, Long chatId, String username, String firstName) {
        try {
            switch (command) {
                case "/register":
                    handleRegisterCommand(chatId, username, firstName);
                    break;
                case "/stats":
                    handleStatsCommand(chatId);
                    break;
                case "/orders":
                    handleOrdersCommand(chatId);
                    break;
                default:
                    log.debug("Неизвестная команда: {}", command);
            }
        } catch (Exception e) {
            log.error("Ошибка обработки команды {}: {}", command, e.getMessage(), e);
        }
    }

    private void handleRegisterCommand(Long chatId, String username, String firstName) {
        boolean registered = adminBotService.registerAdmin(chatId, username, firstName);
        log.info("Регистрация администратора: chatId={}, result={}", chatId, registered);
    }

    private void handleStatsCommand(Long chatId) {
        if (!adminBotService.isRegisteredAdmin(chatId)) {
            log.warn("Неавторизованный доступ к статистике: chatId={}", chatId);
            return;
        }

        String statsMessage = adminBotService.getOrdersStats();
        adminBotService.sendMessageToAdmin(chatId, statsMessage);
        log.debug("Статистика отправлена: chatId={}", chatId);
    }

    private void handleOrdersCommand(Long chatId) {
        if (!adminBotService.isRegisteredAdmin(chatId)) {
            log.warn("Неавторизованный доступ к заказам: chatId={}", chatId);
            return;
        }

        adminBotService.sendActiveOrdersWithButtons(chatId);
        log.debug("Список заказов с кнопками отправлен: chatId={}", chatId);
    }
}