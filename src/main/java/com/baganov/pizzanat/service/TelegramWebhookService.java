package com.baganov.pizzanat.service;

import com.baganov.pizzanat.config.TelegramConfig;
import com.baganov.pizzanat.entity.TelegramAuthToken;
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
import java.util.List;
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
     * Обработка webhook обновления от Telegram
     *
     * @param update данные обновления
     */
    public void processUpdate(TelegramUpdate update) {
        log.info("WEBHOOK_UPDATE: Получено обновление ID: {}. Полные данные: {}",
                update.getUpdateId(), update);

        try {
            if (update.getMessage() != null) {
                log.info("WEBHOOK_UPDATE: Обработка сообщения. Message: {}", update.getMessage());
                processMessage(update);
            } else if (update.getCallbackQuery() != null) {
                log.info("WEBHOOK_UPDATE: Обработка callback query. CallbackQuery: {}", update.getCallbackQuery());
                processCallbackQuery(update);
            } else {
                log.warn("WEBHOOK_UPDATE: Неизвестный тип обновления. Update: {}", update);
            }
        } catch (Exception e) {
            log.error("WEBHOOK_UPDATE: Ошибка при обработке обновления {}: {}",
                    update.getUpdateId(), e.getMessage(), e);
        }

        log.info("WEBHOOK_UPDATE: Завершена обработка обновления ID: {}", update.getUpdateId());
    }

    /**
     * Обработка текстовых сообщений
     *
     * @param update Telegram update с сообщением
     */
    private void processMessage(TelegramUpdate update) {
        log.info("PROCESS_MESSAGE: Начало обработки сообщения для update: {}", update.getUpdateId());
        try {
            TelegramUpdate.TelegramMessage message = update.getMessage();

            if (message == null) {
                log.warn("PROCESS_MESSAGE: Получено пустое сообщение в update: {}", update.getUpdateId());
                return;
            }

            if (message.getChat() == null || message.getFrom() == null) {
                log.warn("PROCESS_MESSAGE: Некорректное сообщение без chat или from в update: {}",
                        update.getUpdateId());
                return;
            }

            Long chatId = message.getChat().getId();
            TelegramUserData user = message.getFrom();

            log.info("PROCESS_MESSAGE: Обработка сообщения от пользователя {}: {}", user.getId(),
                    message.getText() != null ? message.getText() : "контакт/медиа");

            // Обработка контактных данных (опционально)
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
     * Обработка callback query (нажатия inline-кнопок)
     *
     * @param update данные от Telegram
     */
    private void processCallbackQuery(TelegramUpdate update) {
        log.info("CALLBACK_QUERY: Начало обработки callback query");
        TelegramUpdate.TelegramCallbackQuery callbackQuery = update.getCallbackQuery();

        if (callbackQuery == null || callbackQuery.getData() == null) {
            log.warn("CALLBACK_QUERY: Пустой callback query или отсутствуют данные");
            return;
        }

        String data = callbackQuery.getData();

        // Проверяем наличие сообщения в callback query
        Long chatId;
        if (callbackQuery.getMessage() != null && callbackQuery.getMessage().getChat() != null) {
            chatId = callbackQuery.getMessage().getChat().getId();
        } else {
            // Для callback query без сообщения используем ID пользователя как chatId
            chatId = callbackQuery.getFrom().getId();
            log.debug("CALLBACK_QUERY: Сообщение отсутствует, используем ID пользователя как chatId: {}", chatId);
        }

        TelegramUserData user = callbackQuery.getFrom();

        log.info("CALLBACK_QUERY: Обработка callback query от пользователя {}: {}", user.getId(), data);

        // Обработка подтверждения аутентификации
        if (data.startsWith("confirm_auth_")) {
            String authToken = data.substring(13); // убираем "confirm_auth_"
            log.info("CALLBACK_QUERY: Найден токен подтверждения: {}", authToken);
            handleAuthConfirmation(authToken, chatId, user);
        }
        // Обработка отмены аутентификации
        else if (data.startsWith("cancel_auth_")) {
            String authToken = data.substring(12); // убираем "cancel_auth_"
            log.info("CALLBACK_QUERY: Найден токен отмены: {}", authToken);
            handleAuthCancellation(authToken, chatId, user);
        } else {
            log.warn("CALLBACK_QUERY: Неизвестный тип данных: {}", data);
        }

        // Отвечаем на callback query (ошибки callback query не критичны)
        answerCallbackQuery(callbackQuery.getId());
        log.info("CALLBACK_QUERY: Завершена обработка callback query");
    }

    /**
     * Обработка команды /start с токеном аутентификации
     *
     * @param command полная команда
     * @param chatId  ID чата
     * @param user    данные пользователя
     */
    private void handleStartCommand(String command, Long chatId, TelegramUserData user) {
        log.info("HANDLE_START: Обработка команды /start. Команда: '{}', ChatId: {}, UserId: {}",
                command, chatId, user.getId());

        String authToken = tokenGenerator.extractTokenFromStartCommand(command);
        log.info("HANDLE_START: Извлеченный токен: '{}'", authToken);

        if (authToken != null) {
            log.info("HANDLE_START: Токен валиден. Получен запрос аутентификации от пользователя {} с токеном: {}",
                    user.getId(), authToken);
            sendAuthConfirmationMessage(chatId, authToken, user);
            log.info("HANDLE_START: Сообщение с подтверждением отправлено пользователю {}", user.getId());
        } else {
            log.warn("HANDLE_START: Некорректный токен в команде /start от пользователя {}: '{}'",
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
        log.info("AUTH_CONFIRM: Начало подтверждения авторизации. Токен: {}, Пользователь: {}",
                authToken, user.getId());
        try {
            // Подтверждаем авторизацию
            telegramAuthService.confirmAuth(authToken, user);

            // Отправляем сообщение об успехе
            sendAuthSuccessMessage(chatId, user);

            log.info("AUTH_CONFIRM: Аутентификация подтверждена для пользователя {} с токеном: {}",
                    user.getId(), authToken);

        } catch (Exception e) {
            log.error("AUTH_CONFIRM: Ошибка при подтверждении аутентификации для токена {}: {}",
                    authToken, e.getMessage(), e);
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
                        "Для завершения авторизации:\n" +
                        "1️⃣ Нажмите \"📱 Отправить телефон\" для быстрого заказа\n" +
                        "2️⃣ Подтвердите вход кнопкой \"✅ Подтвердить\"",
                user.getDisplayName());

        // Создаем обычную клавиатуру с кнопкой отправки контакта
        Map<String, Object> replyKeyboard = Map.of(
                "keyboard", new Object[][] {
                        {
                                Map.of(
                                        "text", "📱 Отправить телефон",
                                        "request_contact", true)
                        }
                },
                "resize_keyboard", true,
                "one_time_keyboard", true);

        // Отправляем сообщение с обычной клавиатурой
        sendMessage(chatId, message, "Markdown", replyKeyboard);

        // Затем отправляем inline-кнопки для подтверждения
        String confirmMessage = "После отправки телефона нажмите кнопку для подтверждения входа:";

        Map<String, Object> inlineKeyboard = Map.of(
                "inline_keyboard", new Object[][] {
                        {
                                Map.of(
                                        "text", "✅ Подтвердить вход",
                                        "callback_data", "confirm_auth_" + authToken),
                                Map.of(
                                        "text", "❌ Отменить",
                                        "callback_data", "cancel_auth_" + authToken)
                        }
                });

        sendMessage(chatId, confirmMessage, null, inlineKeyboard);
    }

    /**
     * Отправка сообщения об успешной аутентификации
     *
     * @param chatId ID чата
     * @param user   данные пользователя
     */
    private void sendAuthSuccessMessage(Long chatId, TelegramUserData user) {
        String message = String.format(
                "✅ *Вход подтвержден!*\n\n" +
                        "Добро пожаловать, %s!\n\n" +
                        "🍕 Вы успешно вошли в PizzaNat!\n\n" +
                        "Теперь можете вернуться в приложение и продолжить заказ вкусной пиццы.",
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
        // Экранируем специальные символы в сообщении об ошибке для Markdown
        // Убираем экранирование % так как оно вызывает проблемы с форматированием
        String safeErrorMessage = errorMessage != null
                ? errorMessage.replace("_", "\\_").replace("*", "\\*").replace("[", "\\[").replace("]", "\\]")
                : "Неизвестная ошибка";

        String message = "❌ *Ошибка аутентификации*\n\n" +
                "Произошла ошибка при входе в приложение. " +
                "Попробуйте запросить новую ссылку.\n\n" +
                "_Детали: " + safeErrorMessage + "_";

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
            // ИСПРАВЛЕНИЕ: Логируем callback query ошибки как debug/warn, а не error
            // Эти ошибки часто возникают из-за таймаутов Telegram и не критичны
            if (e.getMessage() != null && e.getMessage().contains("query is too old")) {
                log.debug("Callback query устарел (это нормально): {}", e.getMessage());
            } else {
                log.warn("Ошибка при ответе на callback query (не критично): {}", e.getMessage());
            }
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

            // ИСПРАВЛЕНИЕ: Ищем и обновляем токен с telegramId пользователя
            // Это критически важно для связи контакта с токеном авторизации
            try {
                // Ищем PENDING токен без telegramId (недавно созданный)
                List<TelegramAuthToken> pendingTokens = telegramAuthService.findPendingTokensWithoutTelegramId();

                if (!pendingTokens.isEmpty()) {
                    // Берем самый свежий токен (последний созданный)
                    TelegramAuthToken tokenToUpdate = pendingTokens.get(pendingTokens.size() - 1);

                    // Обновляем токен данными пользователя
                    telegramAuthService.updateTokenWithUserData(tokenToUpdate.getAuthToken(), updatedUser);

                    log.info("Токен {} успешно связан с пользователем {} после получения контакта",
                            tokenToUpdate.getAuthToken(), user.getId());
                } else {
                    log.warn("Не найдено PENDING токенов без telegramId для пользователя {}", user.getId());
                }
            } catch (Exception e) {
                log.error("Ошибка при обновлении токена для пользователя {}: {}", user.getId(), e.getMessage());
                // Продолжаем выполнение, так как пользователь уже создан
            }

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