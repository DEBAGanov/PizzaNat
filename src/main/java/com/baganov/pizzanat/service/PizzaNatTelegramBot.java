/**
 * @file: PizzaNatTelegramBot.java
 * @description: Основной класс Telegram бота для PizzaNat с поддержкой команд и inline кнопок
 * @dependencies: TelegramBots API, Spring Boot, TelegramWebhookService
 * @created: 2025-01-11
 */
package com.baganov.pizzanat.service;

import com.baganov.pizzanat.config.TelegramConfig;
import com.baganov.pizzanat.model.dto.telegram.TelegramUpdate;
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
            String messageText = message.getText().trim();

            // Обработка команды /start (с токеном или без)
            if (messageText.equals("/start") || messageText.startsWith("/start ")) {
                handleStartCommand(chatId, user, messageText);
            }
            // Обработка других команд
            else if (messageText.equals("/help")) {
                handleHelpCommand(chatId);
            } else if (messageText.equals("/menu")) {
                handleMenuCommand(chatId);
            } else {
                handleUnknownCommand(chatId);
            }
        }
    }

    /**
     * Обработка команды /start (с токеном или без)
     */
    private void handleStartCommand(Long chatId, User user, String fullCommand) {
        log.info("Команда /start от пользователя: {} (ID: {}), команда: {}", user.getFirstName(), user.getId(),
                fullCommand);

        // Создаем данные пользователя
        TelegramUserData userData = TelegramUserData.builder()
                .id(user.getId().longValue())
                .firstName(user.getFirstName())
                .lastName(user.getLastName())
                .username(user.getUserName())
                .build();

        // Проверяем, есть ли токен в команде
        String authToken = null;
        if (fullCommand.startsWith("/start ")) {
            String potentialToken = fullCommand.substring(7).trim(); // 7 = длина "/start "

            // Проверяем, является ли это токеном аутентификации
            if (potentialToken.startsWith("tg_auth_") && potentialToken.length() > 8) {
                authToken = potentialToken;
                log.info("Получен токен аутентификации из команды /start: {}", authToken);

                try {
                    // Для внешнего токена сразу создаем пользователя в БД
                    integrationService.createOrUpdateUser(userData);
                    log.info("Пользователь {} создан/обновлен в БД при внешнем токене", user.getId());
                } catch (Exception e) {
                    log.error("Ошибка создания/обновления пользователя {} в БД: {}", user.getId(), e.getMessage());
                    // Продолжаем, так как пользователь может быть создан позже при отправке
                    // контакта
                }

                // Сохраняем внешний токен для пользователя (от приложения)
                userAuthTokens.put(user.getId().longValue(), authToken);

                // Отправляем сообщение с запросом телефона для завершения авторизации
                sendPhoneRequestMessage(chatId, userData);
                return;
            }
        }

        // Если токена нет или он некорректный - создаем новый (обычный запуск бота)
        try {
            // Инициализируем новую авторизацию через интеграционный сервис
            String newAuthToken = integrationService.initializeAuth(userData);

            // Сохраняем токен для пользователя
            userAuthTokens.put(user.getId().longValue(), newAuthToken);

            log.info("Создан новый токен авторизации для пользователя {}: {}", user.getId(), newAuthToken);

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

            // Создаем или обновляем пользователя с номером телефона
            integrationService.updateUserWithPhone(userData);

            // Проверяем, какой тип токена у нас (внешний от приложения или внутренний от
            // бота)
            boolean isExternalToken = authToken.startsWith("tg_auth_") && authToken.length() > 20;

            if (isExternalToken) {
                // Это токен от приложения - используем webhook сервис для подтверждения
                log.info("Обрабатываем внешний токен авторизации: {}", authToken);

                try {
                    // Создаем имитацию callback query для webhook сервиса
                    TelegramUpdate update = createAuthConfirmationUpdate(userData, chatId, authToken);
                    integrationService.processUpdateViaWebhook(update);

                    // Отправляем сообщение об успехе
                    removeKeyboard(chatId);
                    sendAutoAuthSuccessMessage(chatId, userData);

                    log.info("Внешняя авторизация успешна для пользователя {}", userId);
                } catch (Exception e) {
                    log.error("Ошибка обработки внешней авторизации: {}", e.getMessage());
                    sendErrorMessage(chatId, "Ошибка завершения авторизации. Попробуйте позже.");
                }
            } else {
                // Это внутренний токен бота - используем стандартную логику
                boolean authSuccess = integrationService.confirmAuth(authToken);

                if (authSuccess) {
                    removeKeyboard(chatId);
                    sendAutoAuthSuccessMessage(chatId, userData);
                    log.info("Внутренняя авторизация успешна для пользователя {}", userId);
                } else {
                    sendErrorMessage(chatId, "Ошибка авторизации. Попробуйте позже.");
                    log.error("Ошибка подтверждения внутренней авторизации для пользователя {}", userId);
                }
            }

            // Удаляем токен из памяти в любом случае
            userAuthTokens.remove(userId);

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
     * Создание TelegramUpdate для подтверждения авторизации через webhook сервис
     */
    private TelegramUpdate createAuthConfirmationUpdate(TelegramUserData userData, Long chatId, String authToken) {
        // Создаем чат для callback query
        TelegramUpdate.TelegramChat chat = TelegramUpdate.TelegramChat.builder()
                .id(chatId)
                .type("private")
                .build();

        // Создаем сообщение для callback query
        TelegramUpdate.TelegramMessage message = TelegramUpdate.TelegramMessage.builder()
                .messageId(System.currentTimeMillis())
                .text("/start " + authToken)
                .date(System.currentTimeMillis() / 1000)
                .from(userData)
                .chat(chat)
                .build();

        // Создаем callback query для подтверждения авторизации
        TelegramUpdate.TelegramCallbackQuery callbackQuery = TelegramUpdate.TelegramCallbackQuery.builder()
                .id("longpolling_auth_" + System.currentTimeMillis())
                .data("confirm_auth_" + authToken)
                .from(userData)
                .message(message)
                .build();

        return TelegramUpdate.builder()
                .updateId(System.currentTimeMillis())
                .callbackQuery(callbackQuery)
                .build();
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