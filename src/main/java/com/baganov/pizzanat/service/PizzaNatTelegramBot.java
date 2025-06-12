/**
 * @file: PizzaNatTelegramBot.java
 * @description: Основной класс Telegram бота для PizzaNat с поддержкой команд и inline кнопок
 * @dependencies: TelegramBots API, Spring Boot, TelegramWebhookService
 * @created: 2025-01-11
 */
package com.baganov.pizzanat.service;

import com.baganov.pizzanat.config.TelegramConfig;
import com.baganov.pizzanat.model.dto.telegram.TelegramUserData;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import org.telegram.telegrambots.bots.TelegramLongPollingBot;
import org.telegram.telegrambots.meta.api.methods.AnswerCallbackQuery;
import org.telegram.telegrambots.meta.api.methods.send.SendMessage;
import org.telegram.telegrambots.meta.api.objects.CallbackQuery;
import org.telegram.telegrambots.meta.api.objects.Contact;
import org.telegram.telegrambots.meta.api.objects.Message;
import org.telegram.telegrambots.meta.api.objects.Update;
import org.telegram.telegrambots.meta.api.objects.User;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.InlineKeyboardMarkup;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.ReplyKeyboardMarkup;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.ReplyKeyboardRemove;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.InlineKeyboardButton;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.KeyboardButton;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.KeyboardRow;
import org.telegram.telegrambots.meta.exceptions.TelegramApiException;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Slf4j
@Component
@ConditionalOnProperty(name = "telegram.longpolling.enabled", havingValue = "true", matchIfMissing = false)
public class PizzaNatTelegramBot extends TelegramLongPollingBot {

    private final TelegramConfig.TelegramAuthProperties telegramAuthProperties;
    private final TelegramBotIntegrationService integrationService;

    // Хранение токенов авторизации для пользователей
    private final Map<Long, String> userAuthTokens = new HashMap<>();

    @Autowired
    public PizzaNatTelegramBot(TelegramConfig.TelegramAuthProperties telegramAuthProperties,
            TelegramBotIntegrationService integrationService) {
        this.telegramAuthProperties = telegramAuthProperties;
        this.integrationService = integrationService;
        log.info("🤖 PizzaNat Telegram Bot инициализирован для Long Polling");
    }

    @Override
    public String getBotUsername() {
        return telegramAuthProperties.getBotUsername();
    }

    @Override
    public String getBotToken() {
        return telegramAuthProperties.getBotToken();
    }

    @Override
    public void onUpdateReceived(Update update) {
        try {
            if (update.hasMessage()) {
                handleMessage(update.getMessage());
            } else if (update.hasCallbackQuery()) {
                handleCallbackQuery(update.getCallbackQuery());
            }
        } catch (Exception e) {
            log.error("Ошибка обработки обновления: {}", e.getMessage(), e);
        }
    }

    /**
     * Обработка входящих сообщений
     */
    private void handleMessage(Message message) {
        Long chatId = message.getChatId();
        User user = message.getFrom();

        log.debug("Получено сообщение от пользователя {} (ID: {})", user.getFirstName(), user.getId());

        // Обработка контакта
        if (message.hasContact()) {
            handleContactMessage(message);
            return;
        }

        // Обработка текстовых команд
        if (message.hasText()) {
            String messageText = message.getText();

            switch (messageText) {
                case "/start":
                    handleStartCommand(chatId, user);
                    break;
                case "/help":
                    handleHelpCommand(chatId);
                    break;
                case "/menu":
                    handleMenuCommand(chatId);
                    break;
                default:
                    handleUnknownCommand(chatId);
                    break;
            }
        }
    }

    /**
     * Обработка команды /start
     */
    private void handleStartCommand(Long chatId, User user) {
        log.info("Команда /start от пользователя: {} (ID: {})", user.getFirstName(), user.getId());

        // Создаем данные пользователя
        TelegramUserData userData = TelegramUserData.builder()
                .id(user.getId().longValue())
                .firstName(user.getFirstName())
                .lastName(user.getLastName())
                .username(user.getUserName())
                .build();

        try {
            // Инициализируем авторизацию через интеграционный сервис
            String authToken = integrationService.initializeAuth(userData);

            // Сохраняем токен для пользователя
            userAuthTokens.put(user.getId().longValue(), authToken);

            log.info("Создан токен авторизации для пользователя {}: {}", user.getId(), authToken);

            // Отправляем приветственное сообщение с кнопкой отправки телефона
            sendPhoneRequestMessage(chatId, userData);

        } catch (Exception e) {
            log.error("Ошибка инициализации авторизации для пользователя {}: {}", user.getId(), e.getMessage());
            sendErrorMessage(chatId, "Произошла ошибка при инициализации. Попробуйте позже.");
        }
    }

    /**
     * Отправка сообщения с запросом телефона
     */
    private void sendPhoneRequestMessage(Long chatId, TelegramUserData user) {
        SendMessage message = new SendMessage();
        message.setChatId(chatId);
        message.setText(String.format(
                "🍕 *Добро пожаловать в PizzaNat!*\n\n" +
                        "Привет, %s!\n\n" +
                        "Для завершения авторизации, пожалуйста, поделитесь своим номером телефона:",
                user.getDisplayName()));
        message.setParseMode("Markdown");

        // Создаем клавиатуру с кнопкой отправки контакта
        ReplyKeyboardMarkup keyboardMarkup = new ReplyKeyboardMarkup();
        keyboardMarkup.setResizeKeyboard(true);
        keyboardMarkup.setOneTimeKeyboard(true);

        List<KeyboardRow> keyboard = new ArrayList<>();
        KeyboardRow row = new KeyboardRow();

        KeyboardButton phoneButton = new KeyboardButton();
        phoneButton.setText("📱 Отправить телефон");
        phoneButton.setRequestContact(true);
        row.add(phoneButton);

        keyboard.add(row);
        keyboardMarkup.setKeyboard(keyboard);
        message.setReplyMarkup(keyboardMarkup);

        try {
            execute(message);
            log.info("Отправлено сообщение с запросом телефона пользователю {}", chatId);
        } catch (TelegramApiException e) {
            log.error("Ошибка отправки сообщения с кнопкой телефона: {}", e.getMessage());
        }
    }

    /**
     * Обработка контактного сообщения
     */
    private void handleContactMessage(Message message) {
        Contact contact = message.getContact();
        Long userId = message.getFrom().getId().longValue();
        Long chatId = message.getChatId();

        log.info("Получен контакт от пользователя {}: {}", userId, contact.getPhoneNumber());

        // Получаем токен авторизации для пользователя
        String authToken = userAuthTokens.get(userId);
        if (authToken == null) {
            log.warn("Токен авторизации не найден для пользователя {}", userId);
            sendErrorMessage(chatId, "Токен авторизации не найден. Пожалуйста, начните заново с команды /start");
            return;
        }

        try {
            // Создаем данные пользователя с номером телефона
            TelegramUserData userData = TelegramUserData.builder()
                    .id(userId)
                    .firstName(contact.getFirstName())
                    .lastName(contact.getLastName())
                    .phoneNumber(contact.getPhoneNumber())
                    .build();

            // Обновляем пользователя с номером телефона
            integrationService.updateUserWithPhone(userData);

            // Автоматически подтверждаем авторизацию
            boolean authSuccess = integrationService.confirmAuth(authToken);

            if (authSuccess) {
                // Убираем клавиатуру и отправляем сообщение об успехе
                removeKeyboard(chatId);
                sendAutoAuthSuccessMessage(chatId, userData);

                // Удаляем токен из памяти
                userAuthTokens.remove(userId);

                log.info("Автоматическая авторизация успешна для пользователя {}", userId);
            } else {
                sendErrorMessage(chatId, "Ошибка авторизации. Попробуйте позже.");
                log.error("Ошибка подтверждения авторизации для пользователя {}", userId);
            }

        } catch (Exception e) {
            log.error("Ошибка обработки контакта для пользователя {}: {}", userId, e.getMessage());
            sendErrorMessage(chatId, "Произошла ошибка при обработке номера телефона. Попробуйте позже.");
        }
    }

    /**
     * Удаление клавиатуры
     */
    private void removeKeyboard(Long chatId) {
        SendMessage message = new SendMessage();
        message.setChatId(chatId);
        message.setText("📱 Номер телефона получен!");

        ReplyKeyboardRemove keyboardRemove = new ReplyKeyboardRemove();
        keyboardRemove.setRemoveKeyboard(true);
        message.setReplyMarkup(keyboardRemove);

        try {
            execute(message);
        } catch (TelegramApiException e) {
            log.error("Ошибка удаления клавиатуры: {}", e.getMessage());
        }
    }

    /**
     * Отправка сообщения об успешной автоматической авторизации
     */
    private void sendAutoAuthSuccessMessage(Long chatId, TelegramUserData user) {
        SendMessage message = new SendMessage();
        message.setChatId(chatId);
        message.setText(String.format(
                "✅ *Авторизация завершена!*\n\n" +
                        "Добро пожаловать, %s!\n" +
                        "Теперь вы можете пользоваться всеми функциями PizzaNat.\n\n" +
                        "🍕 Приятного аппетита!",
                user.getDisplayName()));
        message.setParseMode("Markdown");

        try {
            execute(message);
        } catch (TelegramApiException e) {
            log.error("Ошибка отправки сообщения об успешной авторизации: {}", e.getMessage());
        }
    }

    /**
     * Обработка команды /help
     */
    private void handleHelpCommand(Long chatId) {
        SendMessage message = new SendMessage();
        message.setChatId(chatId);
        message.setText(
                "🤖 *Помощь по боту PizzaNat*\n\n" +
                        "Доступные команды:\n" +
                        "/start - Начать работу с ботом\n" +
                        "/help - Показать эту справку\n" +
                        "/menu - Показать меню\n\n" +
                        "Для авторизации используйте команду /start и следуйте инструкциям.");
        message.setParseMode("Markdown");

        try {
            execute(message);
        } catch (TelegramApiException e) {
            log.error("Ошибка отправки справки: {}", e.getMessage());
        }
    }

    /**
     * Обработка команды /menu
     */
    private void handleMenuCommand(Long chatId) {
        SendMessage message = new SendMessage();
        message.setChatId(chatId);
        message.setText(
                "🍕 *Меню PizzaNat*\n\n" +
                        "Для просмотра полного меню и оформления заказа используйте наше мобильное приложение.\n\n" +
                        "После авторизации через этого бота вы сможете:\n" +
                        "• Просматривать меню\n" +
                        "• Оформлять заказы\n" +
                        "• Отслеживать статус доставки\n" +
                        "• Получать уведомления");
        message.setParseMode("Markdown");

        try {
            execute(message);
        } catch (TelegramApiException e) {
            log.error("Ошибка отправки меню: {}", e.getMessage());
        }
    }

    /**
     * Обработка неизвестных команд
     */
    private void handleUnknownCommand(Long chatId) {
        SendMessage message = new SendMessage();
        message.setChatId(chatId);
        message.setText(
                "❓ Неизвестная команда.\n\n" +
                        "Используйте /help для просмотра доступных команд.");

        try {
            execute(message);
        } catch (TelegramApiException e) {
            log.error("Ошибка отправки сообщения о неизвестной команде: {}", e.getMessage());
        }
    }

    /**
     * Обработка callback запросов от inline кнопок
     */
    private void handleCallbackQuery(CallbackQuery callbackQuery) {
        String callbackData = callbackQuery.getData();
        Long chatId = callbackQuery.getMessage().getChatId();
        String messageId = callbackQuery.getId();

        log.debug("Получен callback: {} от пользователя {}", callbackData, chatId);

        try {
            // Отвечаем на callback query
            AnswerCallbackQuery answer = new AnswerCallbackQuery();
            answer.setCallbackQueryId(messageId);

            if (callbackData.startsWith("confirm_auth_")) {
                String authToken = callbackData.substring("confirm_auth_".length());
                boolean success = integrationService.confirmAuth(authToken);

                if (success) {
                    answer.setText("✅ Авторизация подтверждена!");
                    answer.setShowAlert(true);
                } else {
                    answer.setText("❌ Ошибка авторизации");
                    answer.setShowAlert(true);
                }
            } else if (callbackData.startsWith("cancel_auth_")) {
                answer.setText("❌ Авторизация отменена");
                answer.setShowAlert(true);
            }

            execute(answer);

        } catch (TelegramApiException e) {
            log.error("Ошибка обработки callback query: {}", e.getMessage());
        }
    }

    /**
     * Отправка сообщения об ошибке
     */
    private void sendErrorMessage(Long chatId, String errorText) {
        SendMessage message = new SendMessage();
        message.setChatId(chatId);
        message.setText("❌ " + errorText);

        try {
            execute(message);
        } catch (TelegramApiException e) {
            log.error("Ошибка отправки сообщения об ошибке: {}", e.getMessage());
        }
    }
}