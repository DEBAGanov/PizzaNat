/**
 * @file: MaxAdminBotPollingService.java
 * @description: Сервис Long Polling для MAX Admin Bot
 *               Получает обновления через GET /updates и обрабатывает их
 * @created: 2026-03-28
 */
package com.baganov.pizzanat.service;

import com.baganov.pizzanat.config.MaxBotConfig;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Long Polling сервис для MAX Admin Bot
 *
 * Аналогично Telegram боту - использует GET /updates для получения событий
 * вместо webhook.
 *
 * Документация MAX API: https://dev.max.ru/docs-api/methods/GET/updates
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class MaxAdminBotPollingService {

    private final MaxBotConfig maxBotConfig;
    private final MaxAdminBotCallbackHandler callbackHandler;
    private final ObjectMapper objectMapper;
    private final RestTemplate restTemplate;

    private final AtomicBoolean running = new AtomicBoolean(false);
    private final AtomicLong marker = new AtomicLong(0); // Маркер для Long Polling

    private Thread pollingThread;

    @PostConstruct
    public void startPolling() {
        if (!maxBotConfig.isAdminEnabled()) {
            log.info("MAX Admin Bot polling disabled (adminEnabled=false)");
            return;
        }

        if (maxBotConfig.getAdminBotToken() == null || maxBotConfig.getAdminBotToken().isEmpty()) {
            log.warn("MAX Admin Bot token not configured - polling not started");
            return;
        }

        running.set(true);

        pollingThread = new Thread(() -> {
            log.info("🔄 MAX Admin Bot Long Polling started");

            while (running.get()) {
                try {
                    pollUpdates();
                } catch (Exception e) {
                    log.error("MAX Admin Bot polling error: {}", e.getMessage());

                    // Пауза перед повторной попыткой
                    try {
                        Thread.sleep(5000);
                    } catch (InterruptedException ie) {
                        Thread.currentThread().interrupt();
                        break;
                    }
                }
            }

            log.info("🛑 MAX Admin Bot Long Polling stopped");
        }, "max-admin-bot-polling");

        pollingThread.setDaemon(true);
        pollingThread.start();

        log.info("✅ MAX Admin Bot polling thread started");
    }

    @PreDestroy
    public void stopPolling() {
        log.info("Stopping MAX Admin Bot polling...");
        running.set(false);

        if (pollingThread != null) {
            pollingThread.interrupt();
        }
    }

    /**
     * Выполнение Long Polling запроса к MAX API
     *
     * GET /updates?marker={marker}&timeout=30
     *
     * Документация: https://dev.max.ru/docs-api/methods/GET/updates
     */
    private void pollUpdates() {
        try {
            String adminBotToken = maxBotConfig.getAdminBotToken();
            String url = String.format("%s/updates?marker=%d&timeout=30",
                    maxBotConfig.getApiUrl(), marker.get());

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            headers.set("Authorization", adminBotToken);

            HttpEntity<Void> entity = new HttpEntity<>(headers);

            // Используем RestTemplate через exchange для GET с заголовками
            var response = restTemplate.exchange(url, HttpMethod.GET, entity, String.class);

            if (response.getStatusCode().is2xxSuccessful() && response.getBody() != null) {
                processUpdates(response.getBody());
            }

        } catch (Exception e) {
            log.error("Error polling MAX updates: {}", e.getMessage());

            // Если ошибка связана с маркером, сбрасываем его
            if (e.getMessage() != null && e.getMessage().contains("marker")) {
                log.warn("Resetting MAX polling marker due to error");
                marker.set(0);
            }

            throw new RuntimeException("Polling failed", e);
        }
    }

    /**
     * Обработка полученных обновлений
     */
    private void processUpdates(String responseBody) {
        try {
            JsonNode root = objectMapper.readTree(responseBody);

            // Обновляем маркер для следующего запроса
            if (root.has("marker")) {
                marker.set(root.get("marker").asLong());
            }

            JsonNode updates = root.path("updates");

            if (!updates.isArray() || updates.isEmpty()) {
                return; // Нет обновлений
            }

            log.debug("MAX Admin: Received {} updates", updates.size());

            for (JsonNode update : updates) {
                try {
                    processUpdate(update);
                } catch (Exception e) {
                    log.error("Error processing MAX update: {}", e.getMessage(), e);
                }
            }

        } catch (Exception e) {
            log.error("Error parsing MAX updates: {}", e.getMessage(), e);
        }
    }

    /**
     * Обработка одного обновления
     */
    private void processUpdate(JsonNode update) {
        String updateType = update.has("update_type") ? update.get("update_type").asText() : null;

        if (updateType == null) {
            log.warn("MAX Admin: Update without update_type: {}", update);
            return;
        }

        // Логируем весь update для отладки
        log.info("MAX Admin: Processing update type: {}, data: {}", updateType, update.toString());

        switch (updateType) {
            case "message_created":
                handleMessageCreated(update);
                break;
            case "message_callback":
                handleMessageCallback(update);
                break;
            case "bot_started":
                handleBotStarted(update);
                break;
            case "message_chat_created":
                log.info("MAX Admin: Bot added to chat");
                break;
            default:
                // Логируем все неизвестные типы - возможно это callback!
                log.info("MAX Admin: ⚠️ Unknown update_type '{}', full update: {}", updateType, update.toString());
                // Пробуем обработать как callback если есть callback поле
                if (update.has("callback")) {
                    log.info("MAX Admin: Found callback in unknown update, processing...");
                    handleMessageCallback(update);
                }
        }
    }

    /**
     * Обработка нового сообщения
     *
     * Структура MAX API:
     * {
     *   "update_type": "message_created",
     *   "message": {
     *     "sender": { "user_id": 123, "first_name": "Name" },
     *     "body": { "text": "Hello" }
     *   }
     * }
     */
    private void handleMessageCreated(JsonNode update) {
        try {
            JsonNode message = update.path("message");
            JsonNode sender = message.path("sender");
            JsonNode body = message.path("body");

            Long userId = sender.has("user_id") ? sender.get("user_id").asLong() : null;
            String username = sender.has("username") ? sender.get("username").asText() : null;
            // Имя может быть в first_name или name
            String firstName = sender.has("first_name") ? sender.get("first_name").asText() :
                              (sender.has("name") ? sender.get("name").asText() : null);
            // Текст в body.text, не в text напрямую
            String messageText = body.has("text") ? body.get("text").asText() : "";

            if (userId == null) {
                log.warn("MAX Admin: Message without user_id");
                return;
            }

            log.info("MAX Admin: Received message from userId={}, username={}, text={}",
                    userId, username, messageText.substring(0, Math.min(50, messageText.length())));

            callbackHandler.handleMessage(userId, messageText, username, firstName);

        } catch (Exception e) {
            log.error("MAX Admin: Error processing message_created: {}", e.getMessage(), e);
        }
    }

    /**
     * Обработка callback от inline кнопки
     *
     * Структура MAX API:
     * {
     *   "update_type": "message_callback",
     *   "callback": {
     *     "sender": { "user_id": 123 },
     *     "message_id": 456,
     *     "payload": "max_order_1_CONFIRMED"
     *   }
     * }
     */
    private void handleMessageCallback(JsonNode update) {
        try {
            JsonNode callback = update.path("callback");
            JsonNode sender = callback.path("sender");

            Long userId = sender.has("user_id") ? sender.get("user_id").asLong() : null;
            Long messageId = callback.has("message_id") ? callback.get("message_id").asLong() : null;
            // payload может быть в payload или callback_data
            String callbackData = callback.has("payload") ? callback.get("payload").asText() :
                                 (callback.has("callback_data") ? callback.get("callback_data").asText() : null);

            if (userId == null || callbackData == null) {
                log.warn("MAX Admin: Callback without required data: userId={}, data={}", userId, callbackData);
                return;
            }

            log.info("MAX Admin: Received callback from userId={}, messageId={}, data={}",
                    userId, messageId, callbackData);

            callbackHandler.handleCallback(userId, messageId, callbackData);

        } catch (Exception e) {
            log.error("MAX Admin: Error processing message_callback: {}", e.getMessage(), e);
        }
    }

    /**
     * Обработка запуска бота (/start)
     *
     * Структура MAX API:
     * {
     *   "update_type": "bot_started",
     *   "initiator": {
     *     "user": { "user_id": 123, "first_name": "Name" }
     *   }
     * }
     * Или:
     * {
     *   "update_type": "bot_started",
     *   "user": { "user_id": 123 }
     * }
     */
    private void handleBotStarted(JsonNode update) {
        try {
            // Пробуем разные варианты структуры
            JsonNode user = update.path("user");
            if (user.isMissingNode() || user.isNull()) {
                JsonNode initiator = update.path("initiator");
                user = initiator.path("user");
            }

            Long userId = user.has("user_id") ? user.get("user_id").asLong() : null;
            String username = user.has("username") ? user.get("username").asText() : null;
            // Имя может быть в first_name, name или last_name
            String firstName = user.has("first_name") ? user.get("first_name").asText() :
                              (user.has("name") ? user.get("name").asText() : null);

            if (userId == null) {
                log.warn("MAX Admin: Bot started without user_id");
                return;
            }

            log.info("MAX Admin: Bot started by userId={}, username={}, firstName={}", userId, username, firstName);

            // Отправляем приветственное сообщение через handler
            callbackHandler.handleCommand("/start", userId, username, firstName);

        } catch (Exception e) {
            log.error("MAX Admin: Error processing bot_started: {}", e.getMessage(), e);
        }
    }
}
