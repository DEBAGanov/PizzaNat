package com.baganov.pizzanat.service;

import com.baganov.pizzanat.config.TelegramConfig;
import com.baganov.pizzanat.model.dto.telegram.TelegramUpdate;
import com.baganov.pizzanat.model.dto.telegram.TelegramUserData;
import com.baganov.pizzanat.util.TokenGenerator;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.time.LocalDateTime;
import java.util.Map;

/**
 * Сервис для обработки Telegram webhook и управления ботом.
 * Следует принципу Single Responsibility из SOLID.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class TelegramWebhookService {

    @Qualifier("telegramAuthRestTemplate")
    private final RestTemplate telegramAuthRestTemplate;

    private final TelegramAuthService telegramAuthService;
    private final TokenGenerator tokenGenerator;
    private final TelegramConfig.TelegramAuthProperties telegramAuthProperties;

    /**
     * Обработка входящего update от Telegram
     *
     * @param update данные от Telegram webhook
     */
    public void processUpdate(TelegramUpdate update) {
        try {
            if (update == null) {
                log.warn("Получен пустой Telegram update");
                return;
            }

            log.debug("Обработка Telegram update: {}", update.getUpdateId());

            // Обработка сообщений
            if (update.hasMessage()) {
                processMessage(update);
            }

            // Обработка callback query (inline кнопки)
            if (update.hasCallbackQuery()) {
                processCallbackQuery(update);
            }

        } catch (Exception e) {
            log.error("Ошибка при обработке Telegram update: {}", e.getMessage(), e);
        }
    }

    /**
     * Обработка текстовых сообщений
     *
     * @param update Telegram update с сообщением
     */
    private void processMessage(TelegramUpdate update) {
        try {
            TelegramUpdate.TelegramMessage message = update.getMessage();

            if (message == null) {
                log.warn("Получено пустое сообщение в update: {}", update.getUpdateId());
                return;
            }

            if (message.getChat() == null || message.getFrom() == null) {
                log.warn("Некорректное сообщение без chat или from в update: {}", update.getUpdateId());
                return;
            }

            Long chatId = message.getChat().getId();
            TelegramUserData user = message.getFrom();

            log.debug("Обработка сообщения от пользователя {}: {}", user.getId(),
                    message.getText() != null ? message.getText() : "контакт/медиа");

            // Обработка контактных данных
            if (message.hasContact()) {
                handleContactMessage(message, chatId, user);
                return;
            }

            // Обработка текстовых сообщений
            if (message.getText() == null) {
                log.debug("Получено не-текстовое сообщение от пользователя {}", user.getId());
                return;
            }

            String text = message.getText().trim();

            // Обработка команды /start с токеном
            if (text.startsWith("/start ")) {
                handleStartCommand(text, chatId, user);
            }
            // Обработка других команд
            else if (text.equals("/start")) {
                sendWelcomeMessage(chatId);
            } else if (text.equals("/help")) {
                sendHelpMessage(chatId);
            } else {
                // Неизвестная команда
                sendUnknownCommandMessage(chatId);
            }

        } catch (Exception e) {
            log.error("Ошибка при обработке сообщения в update {}: {}",
                    update.getUpdateId(), e.getMessage(), e);
            // Не перебрасываем исключение, чтобы не возвращать 500
        }
    }

    /**
     * Обработка callback query от inline кнопок
     *
     * @param update Telegram update с callback query
     */
    private void processCallbackQuery(TelegramUpdate update) {
        TelegramUpdate.TelegramCallbackQuery callbackQuery = update.getCallbackQuery();

        if (callbackQuery == null || callbackQuery.getData() == null) {
            return;
        }

        String data = callbackQuery.getData();
        Long chatId = callbackQuery.getMessage().getChat().getId();
        TelegramUserData user = callbackQuery.getFrom();

        log.debug("Обработка callback query от пользователя {}: {}", user.getId(), data);

        // Обработка подтверждения аутентификации
        if (data.startsWith("confirm_auth_")) {
            String authToken = data.substring(13); // убираем "confirm_auth_"
            handleAuthConfirmation(authToken, chatId, user);
        }
        // Обработка отмены аутентификации
        else if (data.startsWith("cancel_auth_")) {
            String authToken = data.substring(12); // убираем "cancel_auth_"
            handleAuthCancellation(authToken, chatId, user);
        }

        // Отвечаем на callback query
        answerCallbackQuery(callbackQuery.getId());
    }

    /**
     * Обработка команды /start с токеном аутентификации
     *
     * @param command полная команда
     * @param chatId  ID чата
     * @param user    данные пользователя
     */
    private void handleStartCommand(String command, Long chatId, TelegramUserData user) {
        String authToken = tokenGenerator.extractTokenFromStartCommand(command);

        if (authToken != null) {
            log.info("Получен запрос аутентификации от пользователя {} с токеном: {}",
                    user.getId(), authToken);
            sendAuthConfirmationMessage(chatId, authToken, user);
        } else {
            log.warn("Некорректный токен в команде /start от пользователя {}: {}",
                    user.getId(), command);
            sendInvalidTokenMessage(chatId);
        }
    }

    /**
     * Обработка подтверждения аутентификации
     *
     * @param authToken токен аутентификации
     * @param chatId    ID чата
     * @param user      данные пользователя
     */
    private void handleAuthConfirmation(String authToken, Long chatId, TelegramUserData user) {
        try {
            telegramAuthService.confirmAuth(authToken, user);
            sendAuthSuccessMessage(chatId, user);
            log.info("Аутентификация подтверждена для пользователя {} с токеном: {}",
                    user.getId(), authToken);
        } catch (Exception e) {
            log.error("Ошибка при подтверждении аутентификации для токена {}: {}",
                    authToken, e.getMessage());
            sendAuthErrorMessage(chatId, e.getMessage());
        }
    }

    /**
     * Обработка отмены аутентификации
     *
     * @param authToken токен аутентификации
     * @param chatId    ID чата
     * @param user      данные пользователя
     */
    private void handleAuthCancellation(String authToken, Long chatId, TelegramUserData user) {
        sendAuthCancelledMessage(chatId);
        log.info("Аутентификация отменена пользователем {} для токена: {}",
                user.getId(), authToken);
    }

    /**
     * Отправка сообщения с подтверждением аутентификации
     *
     * @param chatId    ID чата
     * @param authToken токен аутентификации
     * @param user      данные пользователя
     */
    private void sendAuthConfirmationMessage(Long chatId, String authToken, TelegramUserData user) {
        String message = String.format(
                "🍕 *Добро пожаловать в PizzaNat!*\n\n" +
                        "Привет, %s!\n\n" +
                        "Для завершения авторизации необходимо " +
                        "поделиться номером телефона через кнопку ниже",
                user.getDisplayName());

        // Создаем клавиатуру с кнопкой запроса контакта
        Map<String, Object> keyboard = Map.of(
                "keyboard", new Object[][] {
                        {
                                Map.of(
                                        "text", "📞 Поделиться номером телефона",
                                        "request_contact", true)
                        }
                },
                "resize_keyboard", true,
                "one_time_keyboard", true);

        sendMessage(chatId, message, "Markdown", keyboard);
    }

    /**
     * Отправка сообщения об успешной аутентификации
     *
     * @param chatId ID чата
     * @param user   данные пользователя
     */
    private void sendAuthSuccessMessage(Long chatId, TelegramUserData user) {
        String message = String.format(
                "✅ *Аутентификация успешна!*\n\n" +
                        "Добро пожаловать, %s!\n\n" +
                        "Вы успешно вошли в приложение PizzaNat. " +
                        "Теперь вы можете вернуться в приложение и продолжить заказ.",
                user.getDisplayName());

        sendMessage(chatId, message, "Markdown", null);
    }

    /**
     * Отправка сообщения об отмене аутентификации
     *
     * @param chatId ID чата
     */
    private void sendAuthCancelledMessage(Long chatId) {
        String message = "❌ *Аутентификация отменена*\n\n" +
                "Вход в приложение был отменен. " +
                "Если вы передумали, запросите новую ссылку в приложении.";

        sendMessage(chatId, message, "Markdown", null);
    }

    /**
     * Отправка сообщения об ошибке аутентификации
     *
     * @param chatId       ID чата
     * @param errorMessage сообщение об ошибке
     */
    private void sendAuthErrorMessage(Long chatId, String errorMessage) {
        String message = "❌ *Ошибка аутентификации*\n\n" +
                "Произошла ошибка при входе в приложение. " +
                "Попробуйте запросить новую ссылку.\n\n" +
                "_Детали: " + errorMessage + "_";

        sendMessage(chatId, message, "Markdown", null);
    }

    /**
     * Отправка приветственного сообщения
     *
     * @param chatId ID чата
     */
    private void sendWelcomeMessage(Long chatId) {
        String message = "🍕 *Добро пожаловать в PizzaNat!*\n\n" +
                "Это бот для аутентификации в мобильном приложении.\n\n" +
                "Для входа в приложение используйте ссылку из приложения.\n\n" +
                "Команды:\n" +
                "/help - справка";

        sendMessage(chatId, message, "Markdown", null);
    }

    /**
     * Отправка справочного сообщения
     *
     * @param chatId ID чата
     */
    private void sendHelpMessage(Long chatId) {
        String message = "ℹ️ *Справка PizzaNat Bot*\n\n" +
                "*Как войти в приложение:*\n" +
                "1. Откройте мобильное приложение PizzaNat\n" +
                "2. Выберите \"Войти через Telegram\"\n" +
                "3. Нажмите на полученную ссылку\n" +
                "4. Подтвердите вход в этом боте\n\n" +
                "*Команды:*\n" +
                "/start - начать работу\n" +
                "/help - эта справка";

        sendMessage(chatId, message, "Markdown", null);
    }

    /**
     * Отправка сообщения о неизвестной команде
     *
     * @param chatId ID чата
     */
    private void sendUnknownCommandMessage(Long chatId) {
        String message = "❓ Неизвестная команда.\n\n" +
                "Используйте /help для получения справки.";

        sendMessage(chatId, message, null, null);
    }

    /**
     * Отправка сообщения о некорректном токене
     *
     * @param chatId ID чата
     */
    private void sendInvalidTokenMessage(Long chatId) {
        String message = "❌ *Некорректная ссылка*\n\n" +
                "Ссылка для аутентификации некорректна или устарела.\n\n" +
                "Запросите новую ссылку в мобильном приложении.";

        sendMessage(chatId, message, "Markdown", null);
    }

    /**
     * Отправка сообщения в Telegram
     *
     * @param chatId      ID чата
     * @param text        текст сообщения
     * @param parseMode   режим парсинга (Markdown, HTML)
     * @param replyMarkup клавиатура
     */
    private void sendMessage(Long chatId, String text, String parseMode, Object replyMarkup) {
        try {
            if (!telegramAuthProperties.isValid()) {
                log.warn("Telegram auth не настроен, сообщение не отправлено");
                return;
            }

            String url = telegramAuthProperties.getApiUrl() + "/sendMessage";

            Map<String, Object> request = new java.util.HashMap<>();
            request.put("chat_id", chatId);
            request.put("text", text);

            if (parseMode != null) {
                request.put("parse_mode", parseMode);
            }

            if (replyMarkup != null) {
                request.put("reply_markup", replyMarkup);
            }

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);

            HttpEntity<Map<String, Object>> entity = new HttpEntity<>(request, headers);

            ResponseEntity<String> response = telegramAuthRestTemplate.postForEntity(url, entity, String.class);

            if (response.getStatusCode().is2xxSuccessful()) {
                log.debug("Telegram сообщение отправлено в чат: {}", chatId);
            } else {
                log.error("Ошибка отправки Telegram сообщения: {}", response.getStatusCode());
            }

        } catch (Exception e) {
            log.error("Ошибка при отправке Telegram сообщения: {}", e.getMessage(), e);
        }
    }

    /**
     * Ответ на callback query
     *
     * @param callbackQueryId ID callback query
     */
    private void answerCallbackQuery(String callbackQueryId) {
        try {
            if (!telegramAuthProperties.isValid()) {
                return;
            }

            String url = telegramAuthProperties.getApiUrl() + "/answerCallbackQuery";

            Map<String, Object> request = Map.of("callback_query_id", callbackQueryId);

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);

            HttpEntity<Map<String, Object>> entity = new HttpEntity<>(request, headers);

            telegramAuthRestTemplate.postForEntity(url, entity, String.class);

        } catch (Exception e) {
            log.error("Ошибка при ответе на callback query: {}", e.getMessage(), e);
        }
    }

    /**
     * Регистрация webhook в Telegram
     *
     * @return true если успешно
     */
    public boolean registerWebhook() {
        try {
            if (!telegramAuthProperties.isValid() || telegramAuthProperties.getWebhookUrl() == null) {
                log.error("Webhook URL не настроен");
                return false;
            }

            String url = telegramAuthProperties.getApiUrl() + "/setWebhook";

            Map<String, Object> request = Map.of("url", telegramAuthProperties.getWebhookUrl());

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);

            HttpEntity<Map<String, Object>> entity = new HttpEntity<>(request, headers);

            ResponseEntity<String> response = telegramAuthRestTemplate.postForEntity(url, entity, String.class);

            boolean success = response.getStatusCode().is2xxSuccessful();
            log.info("Регистрация webhook: {}, URL: {}",
                    success ? "успешно" : "ошибка", telegramAuthProperties.getWebhookUrl());

            return success;

        } catch (Exception e) {
            log.error("Ошибка при регистрации webhook: {}", e.getMessage(), e);
            return false;
        }
    }

    /**
     * Удаление webhook из Telegram
     *
     * @return true если успешно
     */
    public boolean deleteWebhook() {
        try {
            if (!telegramAuthProperties.isValid()) {
                return false;
            }

            String url = telegramAuthProperties.getApiUrl() + "/deleteWebhook";

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);

            HttpEntity<String> entity = new HttpEntity<>(headers);

            ResponseEntity<String> response = telegramAuthRestTemplate.postForEntity(url, entity, String.class);

            boolean success = response.getStatusCode().is2xxSuccessful();
            log.info("Удаление webhook: {}", success ? "успешно" : "ошибка");

            return success;

        } catch (Exception e) {
            log.error("Ошибка при удалении webhook: {}", e.getMessage(), e);
            return false;
        }
    }

    /**
     * Получение информации о webhook
     *
     * @return информация о webhook
     */
    public Object getWebhookInfo() {
        try {
            if (!telegramAuthProperties.isValid()) {
                return Map.of(
                        "error", "Telegram auth не настроен",
                        "configured", false);
            }

            String url = telegramAuthProperties.getApiUrl() + "/getWebhookInfo";

            ResponseEntity<String> response = telegramAuthRestTemplate.getForEntity(url, String.class);

            if (response.getStatusCode().is2xxSuccessful()) {
                return Map.of(
                        "webhookInfo", response.getBody(),
                        "configured", true,
                        "timestamp", LocalDateTime.now().toString());
            } else {
                return Map.of(
                        "error", "Ошибка получения информации",
                        "status", response.getStatusCode());
            }

        } catch (Exception e) {
            log.error("Ошибка при получении информации о webhook: {}", e.getMessage(), e);
            return Map.of(
                    "error", e.getMessage(),
                    "timestamp", LocalDateTime.now().toString());
        }
    }

    /**
     * Обработка получения контактных данных от пользователя
     *
     * @param message сообщение с контактом
     * @param chatId  ID чата
     * @param user    данные пользователя
     */
    private void handleContactMessage(TelegramUpdate.TelegramMessage message, Long chatId, TelegramUserData user) {
        TelegramUpdate.TelegramContact contact = message.getContact();

        if (contact == null) {
            log.warn("Получен null контакт от пользователя {}", user.getId());
            sendContactErrorMessage(chatId);
            return;
        }

        log.info("Получен контакт от пользователя {}: телефон {}, имя '{}'",
                user.getId(),
                contact.getPhoneNumber() != null
                        ? contact.getPhoneNumber().replaceAll("(\\d{1,3})(\\d{3})(\\d{3})(\\d+)", "$1***$2***$4")
                        : "null",
                contact.getFullName());

        try {
            // Проверяем, что это контакт самого пользователя
            if (!contact.isOwnContact(user.getId())) {
                sendNotOwnContactMessage(chatId, contact.getFullName());
                return;
            }

            // Валидируем номер телефона
            if (contact.getPhoneNumber() == null || contact.getPhoneNumber().trim().isEmpty()) {
                sendInvalidPhoneMessage(chatId);
                return;
            }

            // Обновляем данные пользователя с номером телефона
            TelegramUserData updatedUser = TelegramUserData.builder()
                    .id(user.getId())
                    .username(user.getUsername())
                    .firstName(contact.getFirstName() != null ? contact.getFirstName() : user.getFirstName())
                    .lastName(contact.getLastName() != null ? contact.getLastName() : user.getLastName())
                    .phoneNumber(contact.getPhoneNumber())
                    .build();

            // Сохраняем данные пользователя с номером телефона
            telegramAuthService.updateUserWithPhoneNumber(updatedUser);

            sendPhoneReceivedMessage(chatId, contact.getPhoneNumber(), user.getDisplayName());

        } catch (Exception e) {
            log.error("Ошибка при обработке контакта от пользователя {}: {}", user.getId(), e.getMessage(), e);
            sendContactErrorMessage(chatId);
        }
    }

    /**
     * Отправка сообщения об успешном получении номера телефона
     *
     * @param chatId      ID чата
     * @param phoneNumber номер телефона
     * @param userName    имя пользователя
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

        // Убираем клавиатуру
        Map<String, Object> keyboard = Map.of("remove_keyboard", true);

        sendMessage(chatId, message, "Markdown", keyboard);
    }

    /**
     * Отправка сообщения об ошибке обработки контакта
     *
     * @param chatId ID чата
     */
    private void sendContactErrorMessage(Long chatId) {
        String message = "❌ *Ошибка обработки контакта*\n\n" +
                "Произошла ошибка при обработке ваших контактных данных. " +
                "Попробуйте еще раз или обратитесь в поддержку.";

        sendMessage(chatId, message, "Markdown", null);
    }

    /**
     * Отправка сообщения о неверном контакте (не свой)
     *
     * @param chatId      ID чата
     * @param contactName имя контакта
     */
    private void sendNotOwnContactMessage(Long chatId, String contactName) {
        String message = String.format(
                "⚠️ *Необходим ваш контакт*\n\n" +
                        "Вы отправили контакт: %s\n\n" +
                        "Для авторизации необходимо поделиться " +
                        "*вашим собственным* номером телефона " +
                        "через кнопку \"📞 Поделиться номером телефона\".",
                contactName != null ? contactName : "Неизвестный");

        sendMessage(chatId, message, "Markdown", null);
    }

    /**
     * Отправка сообщения о неверном номере телефона
     *
     * @param chatId ID чата
     */
    private void sendInvalidPhoneMessage(Long chatId) {
        String message = "❌ *Неверный номер телефона*\n\n" +
                "Не удалось получить ваш номер телефона. " +
                "Убедитесь, что в настройках Telegram указан номер телефона, " +
                "и попробуйте еще раз.";

        sendMessage(chatId, message, "Markdown", null);
    }
}