/**
 * @file: TelegramAdminNotificationServiceImpl.java
 * @description: Реализация сервиса для отправки уведомлений администраторам
 * @dependencies: TelegramBots API
 * @created: 2025-01-13
 */
package com.baganov.pizzanat.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationContext;
import org.springframework.stereotype.Service;
import org.telegram.telegrambots.meta.api.methods.send.SendMessage;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.InlineKeyboardMarkup;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.InlineKeyboardButton;
import org.telegram.telegrambots.meta.exceptions.TelegramApiException;

import java.util.ArrayList;
import java.util.List;

@Slf4j
@Service
public class TelegramAdminNotificationServiceImpl implements TelegramAdminNotificationService {

    @Autowired
    private ApplicationContext applicationContext;

    @Override
    public void sendMessageWithButtons(Long chatId, String message, InlineKeyboardMarkup keyboard) {
        try {
            // Получаем бот из контекста Spring по имени класса
            Object adminBot = applicationContext.getBean("pizzaNatAdminBot");

            // Используем рефлексию для вызова метода
            adminBot.getClass()
                    .getMethod("sendMessageWithButtons", Long.class, String.class, InlineKeyboardMarkup.class)
                    .invoke(adminBot, chatId, message, keyboard);

        } catch (Exception e) {
            log.error("Ошибка отправки сообщения с кнопками: {}", e.getMessage(), e);
        }
    }

    @Override
    public void sendMessage(Long chatId, String message, boolean parseMarkdown) {
        try {
            // Получаем бот из контекста Spring по имени класса
            Object adminBot = applicationContext.getBean("pizzaNatAdminBot");

            // Используем рефлексию для вызова метода
            adminBot.getClass().getMethod("sendMessage", Long.class, String.class, boolean.class)
                    .invoke(adminBot, chatId, message, parseMarkdown);

        } catch (Exception e) {
            log.error("Ошибка отправки сообщения: {}", e.getMessage(), e);
        }
    }

    @Override
    public InlineKeyboardMarkup createOrderManagementKeyboard(Long orderId) {
        try {
            // Получаем бот из контекста Spring по имени класса
            Object adminBot = applicationContext.getBean("pizzaNatAdminBot");

            // Используем рефлексию для вызова метода
            return (InlineKeyboardMarkup) adminBot.getClass()
                    .getMethod("createOrderManagementKeyboard", Long.class)
                    .invoke(adminBot, orderId);

        } catch (Exception e) {
            log.error("Ошибка создания клавиатуры: {}", e.getMessage(), e);
            return createFallbackKeyboard(orderId);
        }
    }

    /**
     * Создает простую клавиатуру как fallback
     */
    private InlineKeyboardMarkup createFallbackKeyboard(Long orderId) {
        InlineKeyboardMarkup keyboard = new InlineKeyboardMarkup();
        List<List<InlineKeyboardButton>> rows = new ArrayList<>();

        // Первая строка - статусы заказа
        List<InlineKeyboardButton> statusRow1 = new ArrayList<>();
        statusRow1.add(createButton("✅ Подтвердить", "order_status_" + orderId + "_CONFIRMED"));
        statusRow1.add(createButton("👨‍🍳 Готовится", "order_status_" + orderId + "_PREPARING"));
        rows.add(statusRow1);

        List<InlineKeyboardButton> statusRow2 = new ArrayList<>();
        statusRow2.add(createButton("🍕 Готов", "order_status_" + orderId + "_READY"));
        statusRow2.add(createButton("🚗 Доставляется", "order_status_" + orderId + "_DELIVERING"));
        rows.add(statusRow2);

        List<InlineKeyboardButton> statusRow3 = new ArrayList<>();
        statusRow3.add(createButton("✅ Доставлен", "order_status_" + orderId + "_DELIVERED"));
        statusRow3.add(createButton("❌ Отменить", "order_status_" + orderId + "_CANCELLED"));
        rows.add(statusRow3);

        // Кнопки деталей и отзыва
        List<InlineKeyboardButton> actionsRow = new ArrayList<>();
        actionsRow.add(createButton("📋 Детали заказа", "order_details_" + orderId));
        actionsRow.add(createButton("📝 Отзыв", "order_review_" + orderId));
        rows.add(actionsRow);

        keyboard.setKeyboard(rows);
        return keyboard;
    }

    private InlineKeyboardButton createButton(String text, String callbackData) {
        InlineKeyboardButton button = new InlineKeyboardButton();
        button.setText(text);
        button.setCallbackData(callbackData);
        return button;
    }
}