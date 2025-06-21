/**
 * @file: PizzaNatTelegramBot.java
 * @description: Основной класс Telegram бота для PizzaNat с поддержкой команд и inline кнопок
 * @dependencies: TelegramBots API, Spring Boot, TelegramWebhookService
 * @created: 2025-01-11
 * @updated: 2025-01-15 - добавлена поддержка условного включения через переменные окружения
 */
package com.baganov.pizzanat.service;

import com.baganov.pizzanat.config.TelegramConfig;
import com.baganov.pizzanat.entity.TelegramAuthToken;
import com.baganov.pizzanat.model.dto.telegram.TelegramUpdate;
import com.baganov.pizzanat.model.dto.telegram.TelegramUserData;
import com.baganov.pizzanat.repository.TelegramAuthTokenRepository;
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
import java.util.Optional;

@Slf4j
@Component
@ConditionalOnProperty(name = "telegram.bot.enabled", havingValue = "true", matchIfMissing = false)
public class PizzaNatTelegramBot extends TelegramLongPollingBot {

    private final TelegramConfig.TelegramBotProperties telegramBotProperties;
    private final TelegramBotIntegrationService integrationService;
    private final TelegramAuthTokenRepository tokenRepository;

    // Хранение токенов авторизации для пользователей
    private final Map<Long, String> userAuthTokens = new HashMap<>();

    @Autowired
    public PizzaNatTelegramBot(TelegramConfig.TelegramBotProperties telegramBotProperties,
            TelegramBotIntegrationService integrationService,
            TelegramAuthTokenRepository tokenRepository) {
        this.telegramBotProperties = telegramBotProperties;
        this.integrationService = integrationService;
        this.tokenRepository = tokenRepository;

        // ДИАГНОСТИКА: Логируем токен для отладки
        String token = telegramBotProperties.getBotToken();
        log.info("🔍 ДИАГНОСТИКА: Основной бот использует токен: {}...",
                token != null && token.length() > 10 ? token.substring(0, 10) : "NULL");
        log.info("🤖 PizzaNat Telegram Bot инициализирован для Long Polling");
    }

    @Override
    public String getBotUsername() {
        return telegramBotProperties.getBotUsername();
    }

    @Override
    public String getBotToken() {
        return telegramBotProperties.getBotToken();
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
        if (fullCommand.startsWith("/start ")) {
            String potentialToken = fullCommand.substring(7).trim(); // 7 = длина "/start "

            // Проверяем, является ли это токеном аутентификации от мобильного приложения
            if (potentialToken.startsWith("tg_auth_") && potentialToken.length() > 8) {
                log.info("Получен токен аутентификации от мобильного приложения: {}", potentialToken);
                handleMobileAuthToken(chatId, userData, potentialToken);
                return;
            }
        }

        // Обычный запуск бота без токена - отправляем приветствие
        sendWelcomeMessage(chatId, userData);
    }

    /**
     * Обработка токена аутентификации от мобильного приложения
     */
    private void handleMobileAuthToken(Long chatId, TelegramUserData userData, String authToken) {
        log.info("Обработка токена аутентификации от мобильного приложения для пользователя: {}", userData.getId());

        try {
            // Сохраняем токен для пользователя
            userAuthTokens.put(userData.getId(), authToken);

            // Создаем/обновляем пользователя в БД
            integrationService.createOrUpdateUser(userData);
            log.info("Пользователь {} создан/обновлен в БД", userData.getId());

            // Отправляем сообщение с запросом контакта
            sendContactRequestMessage(chatId, authToken, userData);

        } catch (Exception e) {
            log.error("Ошибка обработки токена аутентификации для пользователя {}: {}", userData.getId(),
                    e.getMessage());
            sendAuthErrorMessage(chatId, "Ошибка обработки токена: " + e.getMessage());
        }
    }

    /**
     * Отправка сообщения с запросом контакта (с кнопкой отправки телефона)
     */
    private void sendContactRequestMessage(Long chatId, String authToken, TelegramUserData userData) {
        String message = String.format(
                "🍕 *Добро пожаловать в PizzaNat!*\n\n" +
                        "Привет, %s!\n\n" +
                        "Для завершения авторизации:\n" +
                        "1️⃣ Нажмите \"📱 Отправить телефон\" для быстрого заказа\n" +
                        "2️⃣ Подтвердите вход кнопкой \"✅ Подтвердить\"",
                userData.getDisplayName());

        SendMessage sendMessage = new SendMessage();
        sendMessage.setChatId(chatId);
        sendMessage.setText(message);
        sendMessage.setParseMode("Markdown");

        // Создаем обычную клавиатуру с кнопкой отправки контакта
        ReplyKeyboardMarkup keyboardMarkup = new ReplyKeyboardMarkup();
        List<KeyboardRow> keyboard = new ArrayList<>();

        KeyboardRow row = new KeyboardRow();
        KeyboardButton contactButton = new KeyboardButton("📱 Отправить телефон");
        contactButton.setRequestContact(true);
        row.add(contactButton);

        keyboard.add(row);
        keyboardMarkup.setKeyboard(keyboard);
        keyboardMarkup.setResizeKeyboard(true);
        keyboardMarkup.setOneTimeKeyboard(true);

        sendMessage.setReplyMarkup(keyboardMarkup);

        try {
            execute(sendMessage);
            log.info("Сообщение с запросом контакта отправлено пользователю {}", userData.getId());

            // Затем отправляем inline-кнопки для подтверждения
            sendAuthConfirmationMessage(chatId, authToken, userData);

        } catch (TelegramApiException e) {
            log.error("Ошибка отправки сообщения с запросом контакта: {}", e.getMessage());
        }
    }

    /**
     * Отправка сообщения с подтверждением авторизации (с inline кнопками)
     */
    private void sendAuthConfirmationMessage(Long chatId, String authToken, TelegramUserData userData) {
        String message = "После отправки телефона нажмите кнопку для подтверждения входа:";

        SendMessage sendMessage = new SendMessage();
        sendMessage.setChatId(chatId);
        sendMessage.setText(message);
        sendMessage.setParseMode("Markdown");

        // Создаем inline клавиатуру с кнопками подтверждения
        InlineKeyboardMarkup keyboardMarkup = new InlineKeyboardMarkup();
        List<List<InlineKeyboardButton>> keyboard = new ArrayList<>();

        // Первая строка - кнопка подтверждения
        List<InlineKeyboardButton> row1 = new ArrayList<>();
        InlineKeyboardButton confirmButton = new InlineKeyboardButton();
        confirmButton.setText("✅ Подтвердить вход");
        confirmButton.setCallbackData("confirm_auth_" + authToken);
        row1.add(confirmButton);

        // Вторая строка - кнопка отмены
        List<InlineKeyboardButton> row2 = new ArrayList<>();
        InlineKeyboardButton cancelButton = new InlineKeyboardButton();
        cancelButton.setText("❌ Отменить");
        cancelButton.setCallbackData("cancel_auth_" + authToken);
        row2.add(cancelButton);

        keyboard.add(row1);
        keyboard.add(row2);
        keyboardMarkup.setKeyboard(keyboard);

        sendMessage.setReplyMarkup(keyboardMarkup);

        try {
            execute(sendMessage);
            log.info("Сообщение с подтверждением авторизации отправлено пользователю {}", userData.getId());
        } catch (TelegramApiException e) {
            log.error("Ошибка отправки сообщения с подтверждением авторизации: {}", e.getMessage());
        }
    }

    /**
     * Отправка приветственного сообщения (обычный запуск бота)
     */
    private void sendWelcomeMessage(Long chatId, TelegramUserData userData) {
        String message = String.format(
                "🍕 *Добро пожаловать в PizzaNat!*\n\n" +
                        "Привет, %s!\n\n" +
                        "Это официальный бот пиццерии PizzaNat.\n\n" +
                        "Для заказа пиццы используйте наше мобильное приложение или веб-сайт.\n\n" +
                        "Команды:\n" +
                        "• /help - помощь\n" +
                        "• /menu - меню (скоро)",
                userData.getDisplayName());

        SendMessage sendMessage = new SendMessage();
        sendMessage.setChatId(chatId);
        sendMessage.setText(message);
        sendMessage.setParseMode("Markdown");

        try {
            execute(sendMessage);
        } catch (TelegramApiException e) {
            log.error("Ошибка отправки приветственного сообщения: {}", e.getMessage());
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

            // ИСПРАВЛЕНИЕ: Сначала обновляем пользователя с номером телефона
            integrationService.updateUserWithPhone(userData);
            log.info("Пользователь {} обновлен с номером телефона", userId);

            // ИСПРАВЛЕНИЕ: Обновляем токен в БД с telegramId пользователя
            try {
                // Находим токен в БД и обновляем его с telegramId
                Optional<TelegramAuthToken> tokenOpt = tokenRepository.findByAuthToken(authToken);
                if (tokenOpt.isPresent()) {
                    TelegramAuthToken token = tokenOpt.get();
                    token.setTelegramId(userId);
                    token.setTelegramUsername(message.getFrom().getUserName());
                    token.setTelegramFirstName(contact.getFirstName());
                    token.setTelegramLastName(contact.getLastName());
                    tokenRepository.save(token);
                    log.info("Токен {} обновлен с данными пользователя {}", authToken, userId);
                } else {
                    log.error("Токен {} не найден в БД для обновления", authToken);
                    sendErrorMessage(chatId, "Ошибка обновления токена. Попробуйте заново с команды /start");
                    return;
                }
            } catch (Exception e) {
                log.error("Ошибка обновления токена {} с данными пользователя: {}", authToken, e.getMessage());
                sendErrorMessage(chatId, "Ошибка обновления данных. Попробуйте заново с команды /start");
                return;
            }

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
                    sendPhoneReceivedMessage(chatId, contact.getPhoneNumber(), userData.getDisplayName());

                    log.info("Внешняя авторизация успешна для пользователя {}", userId);
                } catch (Exception e) {
                    log.error("Ошибка обработки внешней авторизации: {}", e.getMessage());
                    // sendErrorMessage(chatId, "Ошибка завершения авторизации. Попробуйте позже.");
                    // sendErrorMessage(chatId, "Ошибка ");
                }
            } else {
                // Это внутренний токен бота - используем стандартную логику
                boolean authSuccess = integrationService.confirmAuth(authToken);

                if (authSuccess) {
                    removeKeyboard(chatId);
                    sendPhoneReceivedMessage(chatId, contact.getPhoneNumber(), userData.getDisplayName());
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
     * Отправка сообщения об успешном получении номера телефона
     */
    private void sendPhoneReceivedMessage(Long chatId, String phoneNumber, String userName) {
        // Маскируем номер для безопасности
        String maskedPhone = phoneNumber.replaceAll("(\\d{1,3})(\\d{3})(\\d{3})(\\d+)", "$1***$2***$4");

        String message = String.format(
                "✅ *Номер телефона получен!*\n\n" +
                        "Спасибо, %s!\n\n" +
                        "Ваш номер: %s\n\n" +
                        "Теперь можете вернуться в приложение для завершения авторизации.",
                userName, maskedPhone);

        SendMessage sendMessage = new SendMessage();
        sendMessage.setChatId(chatId);
        sendMessage.setText(message);
        sendMessage.setParseMode("Markdown");

        try {
            execute(sendMessage);
        } catch (TelegramApiException e) {
            log.error("Ошибка отправки сообщения о получении номера: {}", e.getMessage());
        }
    }

    /**
     * Удаление клавиатуры
     */
    private void removeKeyboard(Long chatId) {
        SendMessage message = new SendMessage();
        message.setChatId(chatId);
        message.setText("🔄 Обработка...");

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
        // message.setText(String.format(
        // "✅ *Авторизация завершена!*\n\n" +
        // "Добро пожаловать, %s!\n" +
        // "Теперь вы можете пользоваться всеми функциями PizzaNat.\n\n" +
        // "🍕 Приятного аппетита!",
        // user.getDisplayName()));
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
     * Обработка callback query (inline кнопки)
     */
    private void handleCallbackQuery(CallbackQuery callbackQuery) {
        Long chatId = callbackQuery.getMessage().getChatId();
        String data = callbackQuery.getData();
        User user = callbackQuery.getFrom();

        log.info("Получен callback query от пользователя {} (ID: {}): {}", user.getFirstName(), user.getId(), data);

        // Создаем данные пользователя
        TelegramUserData userData = TelegramUserData.builder()
                .id(user.getId().longValue())
                .firstName(user.getFirstName())
                .lastName(user.getLastName())
                .username(user.getUserName())
                .build();

        try {
            // Обработка подтверждения аутентификации
            if (data.startsWith("confirm_auth_")) {
                String authToken = data.substring(13); // убираем "confirm_auth_"
                log.info("Обработка подтверждения авторизации для токена: {}", authToken);
                handleAuthConfirmation(authToken, chatId, userData);
            }
            // Обработка отмены аутентификации
            else if (data.startsWith("cancel_auth_")) {
                String authToken = data.substring(12); // убираем "cancel_auth_"
                log.info("Обработка отмены авторизации для токена: {}", authToken);
                handleAuthCancellation(authToken, chatId, userData);
            } else {
                log.warn("Неизвестный тип callback data: {}", data);
            }

            // Отвечаем на callback query
            AnswerCallbackQuery answerCallbackQuery = new AnswerCallbackQuery();
            answerCallbackQuery.setCallbackQueryId(callbackQuery.getId());
            answerCallbackQuery.setText("✅ Обработано");
            execute(answerCallbackQuery);

        } catch (Exception e) {
            log.error("Ошибка обработки callback query: {}", e.getMessage(), e);

            // Отвечаем на callback query с ошибкой
            try {
                AnswerCallbackQuery answerCallbackQuery = new AnswerCallbackQuery();
                answerCallbackQuery.setCallbackQueryId(callbackQuery.getId());
                answerCallbackQuery.setText("❌ Ошибка обработки");
                execute(answerCallbackQuery);
            } catch (TelegramApiException ex) {
                log.error("Ошибка отправки ответа на callback query: {}", ex.getMessage());
            }
        }
    }

    /**
     * Обработка подтверждения аутентификации
     */
    private void handleAuthConfirmation(String authToken, Long chatId, TelegramUserData userData) {
        log.info("Начало подтверждения авторизации для токена: {} пользователем: {}", authToken, userData.getId());
        try {
            // Подтверждаем авторизацию через сервис
            integrationService.confirmAuth(authToken, userData);

            // Отправляем сообщение об успехе
            sendAuthSuccessMessage(chatId, userData);

            // Убираем клавиатуру
            removeKeyboard(chatId);

            log.info("Аутентификация подтверждена для пользователя {} с токеном: {}", userData.getId(), authToken);

        } catch (Exception e) {
            log.error("Ошибка при подтверждении аутентификации для токена {}: {}", authToken, e.getMessage(), e);
            sendAuthErrorMessage(chatId, "Ошибка подтверждения авторизации: " + e.getMessage());
        }
    }

    /**
     * Обработка отмены аутентификации
     */
    private void handleAuthCancellation(String authToken, Long chatId, TelegramUserData userData) {
        sendAuthCancelledMessage(chatId);
        removeKeyboard(chatId);
        log.info("Аутентификация отменена пользователем {} для токена: {}", userData.getId(), authToken);
    }

    /**
     * Отправка сообщения об успешной аутентификации
     */
    private void sendAuthSuccessMessage(Long chatId, TelegramUserData userData) {
        String message = String.format(
                "✅ *Вход подтвержден!*\n\n" +
                        "Добро пожаловать, %s!\n\n" +
                        "🍕 Вы успешно вошли в PizzaNat!\n\n" +
                        "Теперь можете вернуться в приложение и продолжить заказ вкусной пиццы.",
                userData.getDisplayName());

        SendMessage sendMessage = new SendMessage();
        sendMessage.setChatId(chatId);
        sendMessage.setText(message);
        sendMessage.setParseMode("Markdown");

        try {
            execute(sendMessage);
        } catch (TelegramApiException e) {
            log.error("Ошибка отправки сообщения об успешной авторизации: {}", e.getMessage());
        }
    }

    /**
     * Отправка сообщения об отмене аутентификации
     */
    private void sendAuthCancelledMessage(Long chatId) {
        String message = "❌ *Вход отменен*\n\n" +
                "Авторизация была отменена.\n\n" +
                "Если хотите войти позже, просто перейдите по ссылке из приложения снова.";

        SendMessage sendMessage = new SendMessage();
        sendMessage.setChatId(chatId);
        sendMessage.setText(message);
        sendMessage.setParseMode("Markdown");

        try {
            execute(sendMessage);
        } catch (TelegramApiException e) {
            log.error("Ошибка отправки сообщения об отмене авторизации: {}", e.getMessage());
        }
    }

    /**
     * Отправка сообщения об ошибке аутентификации
     */
    private void sendAuthErrorMessage(Long chatId, String errorMessage) {
        String message = String.format(
                "❌ *Ошибка авторизации*\n\n" +
                        "Произошла ошибка: %s\n\n" +
                        "Попробуйте еще раз или обратитесь в поддержку.",
                errorMessage);

        SendMessage sendMessage = new SendMessage();
        sendMessage.setChatId(chatId);
        sendMessage.setText(message);
        sendMessage.setParseMode("Markdown");

        try {
            execute(sendMessage);
        } catch (TelegramApiException e) {
            log.error("Ошибка отправки сообщения об ошибке авторизации: {}", e.getMessage());
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