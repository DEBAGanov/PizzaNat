/**
 * @file: MaxAdminBotController.java
 * @description: REST контроллер для обработки webhook от MAX Admin Bot
 * @dependencies: MaxAdminBotCallbackHandler, MaxBotConfig
 * @created: 2026-03-27
 */
package com.baganov.pizzanat.controller;

import com.baganov.pizzanat.config.MaxBotConfig;
import com.baganov.pizzanat.service.MaxAdminBotCallbackHandler;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

/**
 * REST контроллер для MAX Admin Bot webhook
 *
 * MAX API отправляет webhook-уведомления на этот endpoint
 * при взаимодействии пользователей с ботом.
 *
 * Документация: https://dev.max.ru/docs/api/bot-apis
 */
@Slf4j
@RestController
@RequestMapping("/max-admin")
@RequiredArgsConstructor
@Tag(name = "MAX Admin Bot", description = "Webhook API для MAX Admin Bot")
public class MaxAdminBotController {

    private final MaxAdminBotCallbackHandler callbackHandler;
    private final MaxBotConfig maxBotConfig;
    private final ObjectMapper objectMapper;

    /**
     * Webhook endpoint для MAX Admin Bot
     *
     * MAX отправляет POST запросы при:
     * - Нажатии на inline кнопки (callback)
     * - Отправке сообщений боту
     * - Командах (/start, /help и т.д.)
     *
     * @param payload JSON payload от MAX
     * @return 200 OK
     */
    @PostMapping("/")
    @Operation(
            summary = "Webhook для MAX Admin Bot",
            description = "Принимает webhook-уведомления от MAX API о взаимодействии с ботом"
    )
    public ResponseEntity<Void> handleWebhook(@RequestBody String payload) {
        log.debug("MAX Admin: Получен webhook: {}", payload);

        try {
            JsonNode root = objectMapper.readTree(payload);

            // Определяем тип события
            String updateType = root.has("update_type") ? root.get("update_type").asText() : null;

            if (updateType == null) {
                log.warn("MAX Admin: Webhook без update_type: {}", payload);
                return ResponseEntity.ok().build();
            }

            switch (updateType) {
                case "message_created":
                    handleMessageCreated(root);
                    break;
                case "message_callback":
                    handleMessageCallback(root);
                    break;
                case "bot_started":
                    handleBotStarted(root);
                    break;
                case "message_chat_created":
                    log.info("MAX Admin: Bot added to chat");
                    break;
                default:
                    log.debug("MAX Admin: Неизвестный update_type: {}", updateType);
            }

            return ResponseEntity.ok().build();

        } catch (Exception e) {
            log.error("MAX Admin: Ошибка обработки webhook: {}", e.getMessage(), e);
            return ResponseEntity.ok().build(); // Всегда возвращаем 200 для MAX
        }
    }

    /**
     * Обработка нового сообщения
     */
    private void handleMessageCreated(JsonNode root) {
        try {
            JsonNode message = root.path("message");
            JsonNode sender = message.path("sender");
            JsonNode recipient = message.path("recipient");

            Long userId = sender.has("user_id") ? sender.get("user_id").asLong() : null;
            String username = sender.has("username") ? sender.get("username").asText() : null;
            String firstName = sender.has("name") ? sender.get("name").asText() : null;
            String messageText = message.has("text") ? message.get("text").asText() : "";

            if (userId == null) {
                log.warn("MAX Admin: Message without user_id");
                return;
            }

            log.info("MAX Admin: Получено сообщение от userId={}, username={}, text={}",
                    userId, username, messageText.substring(0, Math.min(50, messageText.length())));

            callbackHandler.handleMessage(userId, messageText, username, firstName);

        } catch (Exception e) {
            log.error("MAX Admin: Ошибка обработки message_created: {}", e.getMessage(), e);
        }
    }

    /**
     * Обработка callback от inline кнопки
     */
    private void handleMessageCallback(JsonNode root) {
        try {
            JsonNode callback = root.path("callback");
            JsonNode sender = callback.path("sender");

            Long userId = sender.has("user_id") ? sender.get("user_id").asLong() : null;
            Long messageId = callback.has("message_id") ? callback.get("message_id").asLong() : null;
            String callbackData = callback.has("payload") ? callback.get("payload").asText() : null;

            if (userId == null || callbackData == null) {
                log.warn("MAX Admin: Callback без обязательных данных: userId={}, data={}", userId, callbackData);
                return;
            }

            log.info("MAX Admin: Получен callback от userId={}, messageId={}, data={}",
                    userId, messageId, callbackData);

            callbackHandler.handleCallback(userId, messageId, callbackData);

        } catch (Exception e) {
            log.error("MAX Admin: Ошибка обработки message_callback: {}", e.getMessage(), e);
        }
    }

    /**
     * Обработка запуска бота (/start)
     */
    private void handleBotStarted(JsonNode root) {
        try {
            JsonNode initiator = root.path("initiator");
            JsonNode user = initiator.path("user");

            Long userId = user.has("user_id") ? user.get("user_id").asLong() : null;
            String username = user.has("username") ? user.get("username").asText() : null;
            String firstName = user.has("name") ? user.get("name").asText() : null;

            if (userId == null) {
                log.warn("MAX Admin: Bot started without user_id");
                return;
            }

            log.info("MAX Admin: Bot запущен пользователем userId={}, username={}", userId, username);

            // Отправляем приветственное сообщение
            String welcomeMessage = """
                    👋 **Добро пожаловать в MAX Admin Bot!**

                    Это административный бот для ДИМБО ПИЦЦА.

                    **Для начала работы:**
                    1. Используйте /register для регистрации как администратор
                    2. После регистрации вы будете получать уведомления о новых заказах

                    **Доступные команды:**
                    /register - Регистрация
                    /stats - Статистика заказов
                    /orders - Активные заказы
                    /help - Справка
                    """;

            callbackHandler.handleCommand("/start", userId, username, firstName);

        } catch (Exception e) {
            log.error("MAX Admin: Ошибка обработки bot_started: {}", e.getMessage(), e);
        }
    }

    /**
     * Health check endpoint
     */
    @GetMapping("/health")
    @Operation(summary = "Health check", description = "Проверка работоспособности MAX Admin Bot webhook")
    public ResponseEntity<String> healthCheck() {
        boolean adminEnabled = maxBotConfig.isAdminEnabled();
        boolean hasToken = maxBotConfig.getAdminBotToken() != null && !maxBotConfig.getAdminBotToken().isEmpty();

        String status = String.format(
                "{\"status\": \"UP\", \"adminEnabled\": %s, \"hasToken\": %s}",
                adminEnabled, hasToken);

        return ResponseEntity.ok(status);
    }
}
