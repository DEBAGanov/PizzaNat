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
import org.springframework.stereotype.Component;
import org.telegram.telegrambots.bots.TelegramLongPollingBot;
import org.telegram.telegrambots.meta.api.methods.AnswerCallbackQuery;
import org.telegram.telegrambots.meta.api.methods.send.SendMessage;
import org.telegram.telegrambots.meta.api.objects.CallbackQuery;
import org.telegram.telegrambots.meta.api.objects.Contact;
import org.telegram.telegrambots.meta.api.objects.Message;
import org.telegram.telegrambots.meta.api.objects.Update;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.InlineKeyboardMarkup;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.ReplyKeyboardMarkup;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.InlineKeyboardButton;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.KeyboardButton;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.KeyboardRow;
import org.telegram.telegrambots.meta.exceptions.TelegramApiException;

import java.util.ArrayList;
import java.util.List;

@Slf4j
@Component
public class PizzaNatTelegramBot extends TelegramLongPollingBot {

    private final TelegramConfig.TelegramAuthProperties telegramAuthProperties;
    private final TelegramAuthService telegramAuthService;

    @Autowired
    public PizzaNatTelegramBot(TelegramConfig.TelegramAuthProperties telegramAuthProperties,
            TelegramAuthService telegramAuthService) {
        this.telegramAuthProperties = telegramAuthProperties;
        this.telegramAuthService = telegramAuthService;
        log.info("PizzaNat Telegram Bot инициализирован");
    }

    @Override
    public String getBotUsername() {
        return "PizzaNatBot"; // Имя вашего бота
    }

    @Override
    public String getBotToken() {
        return telegramAuthProperties.getBotToken();
    }

    @Override
    public void onUpdateReceived(Update update) {
        log.info("Получено обновление от Telegram: {}", update.getUpdateId());

        try {
            if (update.hasMessage()) {
                handleMessage(update.getMessage());
            } else if (update.hasCallbackQuery()) {
                handleCallbackQuery(update.getCallbackQuery());
            }
        } catch (Exception e) {
            log.error("Ошибка при обработке обновления {}: {}", update.getUpdateId(), e.getMessage(), e);
        }
    }

    /**
     * Обработка входящих сообщений
     */
    private void handleMessage(Message message) {
        Long chatId = message.getChatId();
        String text = message.getText();

        log.info("Обработка сообщения от пользователя {}: {}",
                message.getFrom().getId(), text != null ? text : "контакт/медиа");

        // Обработка контактов
        if (message.hasContact()) {
            handleContactMessage(message);
            return;
        }

        // Обработка текстовых команд
        if (text == null) {
            return;
        }

        if (text.startsWith("/start")) {
            handleStartCommand(message);
        } else if (text.equals("/help")) {
            handleHelpCommand(message);
        } else if (text.equals("/menu")) {
            handleMenuCommand(message);
        } else {
            handleUnknownCommand(message);
        }
    }

    /**
     * Обработка callback query (нажатия inline кнопок)
     */
    private void handleCallbackQuery(CallbackQuery callbackQuery) {
        String data = callbackQuery.getData();
        Long chatId = callbackQuery.getMessage().getChatId();

        log.info("Обработка callback query: {}", data);

        try {
            if (data.startsWith("confirm_auth_")) {
                String authToken = data.substring(13);
                handleAuthConfirmation(callbackQuery, authToken);
            } else if (data.startsWith("cancel_auth_")) {
                String authToken = data.substring(12);
                handleAuthCancellation(callbackQuery, authToken);
            } else if (data.equals("request_phone")) {
                handlePhoneRequest(callbackQuery);
            }

            // Отвечаем на callback query
            answerCallbackQuery(callbackQuery.getId(), null);

        } catch (Exception e) {
            log.error("Ошибка при обработке callback query: {}", e.getMessage(), e);
            answerCallbackQuery(callbackQuery.getId(), "Произошла ошибка");
        }
    }

    /**
     * Обработка команды /start
     */
    private void handleStartCommand(Message message) {
        Long chatId = message.getChatId();
        String text = message.getText();

        if (text.contains(" ")) {
            // Команда с токеном: /start TOKEN
            String authToken = text.substring(text.indexOf(" ") + 1).trim();
            log.info("Получен токен авторизации: {}", authToken);

            TelegramUserData user = convertToTelegramUserData(message.getFrom());
            sendAuthConfirmationMessage(chatId, authToken, user);
        } else {
            // Обычная команда /start
            sendWelcomeMessage(chatId);
        }
    }

    /**
     * Обработка команды /help
     */
    private void handleHelpCommand(Message message) {
        SendMessage sendMessage = new SendMessage();
        sendMessage.setChatId(message.getChatId());
        sendMessage.setText(
                "ℹ️ *Справка PizzaNat Bot*\n\n" +
                        "*Как войти в приложение:*\n" +
                        "1. Откройте мобильное приложение PizzaNat\n" +
                        "2. Выберите \"Войти через Telegram\"\n" +
                        "3. Нажмите на полученную ссылку\n" +
                        "4. Подтвердите вход в этом боте\n\n" +
                        "*Команды:*\n" +
                        "/start - начать работу\n" +
                        "/help - эта справка\n" +
                        "/menu - главное меню");
        sendMessage.setParseMode("Markdown");

        try {
            execute(sendMessage);
        } catch (TelegramApiException e) {
            log.error("Ошибка отправки справки: {}", e.getMessage());
        }
    }

    /**
     * Обработка команды /menu
     */
    private void handleMenuCommand(Message message) {
        SendMessage sendMessage = new SendMessage();
        sendMessage.setChatId(message.getChatId());
        sendMessage.setText("🍕 *Главное меню PizzaNat*\n\nВыберите действие:");
        sendMessage.setParseMode("Markdown");

        // Создаем inline клавиатуру
        InlineKeyboardMarkup markup = new InlineKeyboardMarkup();
        List<List<InlineKeyboardButton>> rows = new ArrayList<>();

        List<InlineKeyboardButton> row1 = new ArrayList<>();
        InlineKeyboardButton phoneButton = new InlineKeyboardButton();
        phoneButton.setText("📱 Отправить телефон");
        phoneButton.setCallbackData("request_phone");
        row1.add(phoneButton);

        rows.add(row1);
        markup.setKeyboard(rows);
        sendMessage.setReplyMarkup(markup);

        try {
            execute(sendMessage);
        } catch (TelegramApiException e) {
            log.error("Ошибка отправки меню: {}", e.getMessage());
        }
    }

    /**
     * Обработка неизвестных команд
     */
    private void handleUnknownCommand(Message message) {
        SendMessage sendMessage = new SendMessage();
        sendMessage.setChatId(message.getChatId());
        sendMessage.setText("❓ Неизвестная команда.\n\nИспользуйте /help для получения справки.");

        try {
            execute(sendMessage);
        } catch (TelegramApiException e) {
            log.error("Ошибка отправки сообщения о неизвестной команде: {}", e.getMessage());
        }
    }

    /**
     * Отправка приветственного сообщения
     */
    private void sendWelcomeMessage(Long chatId) {
        SendMessage sendMessage = new SendMessage();
        sendMessage.setChatId(chatId);
        sendMessage.setText(
                "🍕 *Добро пожаловать в PizzaNat!*\n\n" +
                        "Это бот для аутентификации в мобильном приложении.\n\n" +
                        "Для входа в приложение используйте ссылку из приложения.\n\n" +
                        "Команды:\n" +
                        "/help - справка\n" +
                        "/menu - главное меню");
        sendMessage.setParseMode("Markdown");

        try {
            execute(sendMessage);
        } catch (TelegramApiException e) {
            log.error("Ошибка отправки приветственного сообщения: {}", e.getMessage());
        }
    }

    /**
     * Отправка сообщения с подтверждением авторизации
     */
    private void sendAuthConfirmationMessage(Long chatId, String authToken, TelegramUserData user) {
        // Первое сообщение с кнопкой отправки телефона
        SendMessage phoneMessage = new SendMessage();
        phoneMessage.setChatId(chatId);
        phoneMessage.setText(String.format(
                "🍕 *Добро пожаловать в PizzaNat!*\n\n" +
                        "Привет, %s!\n\n" +
                        "Для завершения авторизации нажмите кнопку ниже:",
                user.getDisplayName()));
        phoneMessage.setParseMode("Markdown");

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
        phoneMessage.setReplyMarkup(keyboardMarkup);

        try {
            execute(phoneMessage);
        } catch (TelegramApiException e) {
            log.error("Ошибка отправки сообщения с кнопкой телефона: {}", e.getMessage());
        }

        // Второе сообщение с inline кнопками подтверждения
        SendMessage confirmMessage = new SendMessage();
        confirmMessage.setChatId(chatId);
        confirmMessage.setText("После отправки телефона нажмите кнопку для подтверждения входа:");

        InlineKeyboardMarkup inlineMarkup = new InlineKeyboardMarkup();
        List<List<InlineKeyboardButton>> rows = new ArrayList<>();

        List<InlineKeyboardButton> row1 = new ArrayList<>();

        InlineKeyboardButton confirmButton = new InlineKeyboardButton();
        confirmButton.setText("✅ Подтвердить вход");
        confirmButton.setCallbackData("confirm_auth_" + authToken);
        row1.add(confirmButton);

        InlineKeyboardButton cancelButton = new InlineKeyboardButton();
        cancelButton.setText("❌ Отменить");
        cancelButton.setCallbackData("cancel_auth_" + authToken);
        row1.add(cancelButton);

        rows.add(row1);
        inlineMarkup.setKeyboard(rows);
        confirmMessage.setReplyMarkup(inlineMarkup);

        try {
            execute(confirmMessage);
        } catch (TelegramApiException e) {
            log.error("Ошибка отправки сообщения с подтверждением: {}", e.getMessage());
        }
    }

    /**
     * Обработка подтверждения авторизации
     */
    private void handleAuthConfirmation(CallbackQuery callbackQuery, String authToken) {
        Long chatId = callbackQuery.getMessage().getChatId();
        TelegramUserData user = convertToTelegramUserData(callbackQuery.getFrom());

        try {
            telegramAuthService.confirmAuth(authToken, user);
            sendAuthSuccessMessage(chatId, user);
            log.info("Аутентификация подтверждена для пользователя {} с токеном: {}",
                    user.getId(), authToken);
        } catch (Exception e) {
            log.error("Ошибка при подтверждении аутентификации для токена {}: {}",
                    authToken, e.getMessage(), e);
            sendAuthErrorMessage(chatId, e.getMessage());
        }
    }

    /**
     * Обработка отмены авторизации
     */
    private void handleAuthCancellation(CallbackQuery callbackQuery, String authToken) {
        Long chatId = callbackQuery.getMessage().getChatId();
        sendAuthCancelledMessage(chatId);
        log.info("Аутентификация отменена пользователем {} для токена: {}",
                callbackQuery.getFrom().getId(), authToken);
    }

    /**
     * Обработка запроса на отправку телефона
     */
    private void handlePhoneRequest(CallbackQuery callbackQuery) {
        Long chatId = callbackQuery.getMessage().getChatId();

        SendMessage sendMessage = new SendMessage();
        sendMessage.setChatId(chatId);
        sendMessage.setText("📱 Для отправки номера телефона используйте кнопку ниже:");

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
        sendMessage.setReplyMarkup(keyboardMarkup);

        try {
            execute(sendMessage);
        } catch (TelegramApiException e) {
            log.error("Ошибка отправки запроса телефона: {}", e.getMessage());
        }
    }

    /**
     * Обработка получения контакта
     */
    private void handleContactMessage(Message message) {
        Contact contact = message.getContact();
        Long chatId = message.getChatId();

        if (contact == null) {
            log.warn("Получен null контакт от пользователя {}", message.getFrom().getId());
            return;
        }

        log.info("Получен контакт от пользователя {}: телефон {}",
                message.getFrom().getId(),
                contact.getPhoneNumber() != null
                        ? contact.getPhoneNumber().replaceAll("(\\d{1,3})(\\d{3})(\\d{3})(\\d+)", "$1***$2***$4")
                        : "null");

        try {
            // Проверяем, что это контакт самого пользователя
            if (!contact.getUserId().equals(message.getFrom().getId())) {
                sendNotOwnContactMessage(chatId, contact.getFirstName() + " " + contact.getLastName());
                return;
            }

            // Создаем обновленные данные пользователя с номером телефона
            TelegramUserData updatedUser = TelegramUserData.builder()
                    .id(message.getFrom().getId())
                    .username(message.getFrom().getUserName())
                    .firstName(
                            contact.getFirstName() != null ? contact.getFirstName() : message.getFrom().getFirstName())
                    .lastName(contact.getLastName() != null ? contact.getLastName() : message.getFrom().getLastName())
                    .phoneNumber(contact.getPhoneNumber())
                    .build();

            // Сохраняем данные пользователя с номером телефона
            telegramAuthService.updateUserWithPhoneNumber(updatedUser);

            sendPhoneReceivedMessage(chatId, contact.getPhoneNumber(), updatedUser.getDisplayName());

        } catch (Exception e) {
            log.error("Ошибка при обработке контакта от пользователя {}: {}",
                    message.getFrom().getId(), e.getMessage(), e);
            sendContactErrorMessage(chatId);
        }
    }

    /**
     * Отправка сообщения об успешной авторизации
     */
    private void sendAuthSuccessMessage(Long chatId, TelegramUserData user) {
        SendMessage sendMessage = new SendMessage();
        sendMessage.setChatId(chatId);
        sendMessage.setText(String.format(
                "✅ *Вход подтвержден!*\n\n" +
                        "Добро пожаловать, %s!\n\n" +
                        "🍕 Вы успешно вошли в PizzaNat!\n\n" +
                        "Теперь можете вернуться в приложение и продолжить заказ вкусной пиццы.",
                user.getDisplayName()));
        sendMessage.setParseMode("Markdown");

        try {
            execute(sendMessage);
        } catch (TelegramApiException e) {
            log.error("Ошибка отправки сообщения об успешной авторизации: {}", e.getMessage());
        }
    }

    /**
     * Отправка сообщения об ошибке авторизации
     */
    private void sendAuthErrorMessage(Long chatId, String errorMessage) {
        SendMessage sendMessage = new SendMessage();
        sendMessage.setChatId(chatId);
        sendMessage.setText(
                "❌ *Ошибка аутентификации*\n\n" +
                        "Произошла ошибка при входе в приложение. " +
                        "Попробуйте запросить новую ссылку.\n\n" +
                        "_Детали: " + errorMessage + "_");
        sendMessage.setParseMode("Markdown");

        try {
            execute(sendMessage);
        } catch (TelegramApiException e) {
            log.error("Ошибка отправки сообщения об ошибке авторизации: {}", e.getMessage());
        }
    }

    /**
     * Отправка сообщения об отмене авторизации
     */
    private void sendAuthCancelledMessage(Long chatId) {
        SendMessage sendMessage = new SendMessage();
        sendMessage.setChatId(chatId);
        sendMessage.setText(
                "❌ *Аутентификация отменена*\n\n" +
                        "Вход в приложение был отменен. " +
                        "Если вы передумали, запросите новую ссылку в приложении.");
        sendMessage.setParseMode("Markdown");

        try {
            execute(sendMessage);
        } catch (TelegramApiException e) {
            log.error("Ошибка отправки сообщения об отмене авторизации: {}", e.getMessage());
        }
    }

    /**
     * Отправка сообщения об успешном получении телефона
     */
    private void sendPhoneReceivedMessage(Long chatId, String phoneNumber, String userName) {
        String maskedPhone = phoneNumber.replaceAll("(\\d{1,3})(\\d{3})(\\d{3})(\\d+)", "$1***$2***$4");

        SendMessage sendMessage = new SendMessage();
        sendMessage.setChatId(chatId);
        sendMessage.setText(String.format(
                "✅ *Номер телефона получен!*\n\n" +
                        "Спасибо, %s!\n\n" +
                        "Ваш номер: %s\n\n" +
                        "Теперь можете вернуться в приложение для завершения авторизации.",
                userName, maskedPhone));
        sendMessage.setParseMode("Markdown");

        try {
            execute(sendMessage);
        } catch (TelegramApiException e) {
            log.error("Ошибка отправки сообщения о получении телефона: {}", e.getMessage());
        }
    }

    /**
     * Отправка сообщения об ошибке контакта
     */
    private void sendContactErrorMessage(Long chatId) {
        SendMessage sendMessage = new SendMessage();
        sendMessage.setChatId(chatId);
        sendMessage.setText(
                "❌ *Ошибка обработки контакта*\n\n" +
                        "Произошла ошибка при обработке ваших контактных данных. " +
                        "Попробуйте еще раз или обратитесь в поддержку.");
        sendMessage.setParseMode("Markdown");

        try {
            execute(sendMessage);
        } catch (TelegramApiException e) {
            log.error("Ошибка отправки сообщения об ошибке контакта: {}", e.getMessage());
        }
    }

    /**
     * Отправка сообщения о неверном контакте
     */
    private void sendNotOwnContactMessage(Long chatId, String contactName) {
        SendMessage sendMessage = new SendMessage();
        sendMessage.setChatId(chatId);
        sendMessage.setText(String.format(
                "⚠️ *Необходим ваш контакт*\n\n" +
                        "Вы отправили контакт: %s\n\n" +
                        "Для авторизации необходимо поделиться " +
                        "*вашим собственным* номером телефона " +
                        "через кнопку \"📱 Отправить телефон\".",
                contactName != null ? contactName : "Неизвестный"));
        sendMessage.setParseMode("Markdown");

        try {
            execute(sendMessage);
        } catch (TelegramApiException e) {
            log.error("Ошибка отправки сообщения о неверном контакте: {}", e.getMessage());
        }
    }

    /**
     * Ответ на callback query
     */
    private void answerCallbackQuery(String callbackQueryId, String text) {
        AnswerCallbackQuery answer = new AnswerCallbackQuery();
        answer.setCallbackQueryId(callbackQueryId);
        if (text != null) {
            answer.setText(text);
        }

        try {
            execute(answer);
        } catch (TelegramApiException e) {
            log.error("Ошибка при ответе на callback query: {}", e.getMessage());
        }
    }

    /**
     * Конвертация Telegram User в TelegramUserData
     */
    private TelegramUserData convertToTelegramUserData(org.telegram.telegrambots.meta.api.objects.User user) {
        return TelegramUserData.builder()
                .id(user.getId())
                .username(user.getUserName())
                .firstName(user.getFirstName())
                .lastName(user.getLastName())
                .build();
    }
}