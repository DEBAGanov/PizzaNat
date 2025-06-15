/**
 * @file: TelegramAdminNotificationService.java
 * @description: Интерфейс для отправки уведомлений администраторам
 * @dependencies: Telegram API
 * @created: 2025-01-13
 */
package com.baganov.pizzanat.service;

import org.telegram.telegrambots.meta.api.objects.replykeyboard.InlineKeyboardMarkup;

public interface TelegramAdminNotificationService {

    /**
     * Отправка сообщения с кнопками
     */
    void sendMessageWithButtons(Long chatId, String message, InlineKeyboardMarkup keyboard);

    /**
     * Отправка простого сообщения
     */
    void sendMessage(Long chatId, String message, boolean parseMarkdown);

    /**
     * Создание клавиатуры для управления заказом
     */
    InlineKeyboardMarkup createOrderManagementKeyboard(Long orderId);
}