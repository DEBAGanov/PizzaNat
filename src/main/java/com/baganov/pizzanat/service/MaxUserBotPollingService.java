/**
 * @file: MaxUserBotPollingService.java
 * @description: Сервис Long Polling для MAX User Bot (пользовательский бот)
 *               Аналогично Telegram боту @DIMBOpizzaBot
 *               - Авторизация при /start
 *               - Сохранение контактов (номер телефона)
 *               - Уведомления о статусе заказов
 *               - Меню с inline кнопками
 * @dependencies: MaxBotConfig, UserRepository
 * @created: 2026-03-28
 */
package com.baganov.pizzanat.service;

import com.baganov.pizzanat.config.MaxBotConfig;
import com.baganov.pizzanat.entity.User;
import com.baganov.pizzanat.repository.UserRepository;
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
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Long Polling сервис для MAX User Bot
 *
 * Функционал аналогичен Telegram боту @DIMBOpizzaBot:
 * - Авторизация при /start
 * - Сохранение контактов (номер телефона)
 * - Уведомления о статусе заказов
 * - Меню с inline кнопками
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class MaxUserBotPollingService {

    private final MaxBotConfig maxBotConfig;
    private final UserRepository userRepository;
    private final ObjectMapper objectMapper;

    // Создаем RestTemplate с длинным timeout для Long Polling (60 секунд)
    private final RestTemplate longPollingRestTemplate = createLongPollingRestTemplate();

    // RestTemplate для обычных запросов (отправка сообщений)
    private final RestTemplate restTemplate = new RestTemplate();

    private RestTemplate createLongPollingRestTemplate() {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(10000); // 10 секунд на подключение
        factory.setReadTimeout(60000);    // 60 секунд на чтение (Long Polling)
        return new RestTemplate(factory);
    }

    private final AtomicBoolean running = new AtomicBoolean(false);
    private final AtomicLong marker = new AtomicLong(0);

    // Хранение токенов авторизации для пользователей MAX
    private final Map<Long, String> userAuthTokens = new HashMap<>();

    private Thread pollingThread;

    private static final DateTimeFormatter TIME_FORMATTER = DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm");
    private static final String BOT_NAME = "id121603899498_bot"; // MAX User Bot username

    @PostConstruct
    public void startPolling() {
        if (!maxBotConfig.isEnabled()) {
            log.info("MAX User Bot polling disabled (enabled=false)");
            return;
        }

        if (maxBotConfig.getUserBotToken() == null || maxBotConfig.getUserBotToken().isEmpty()) {
            log.warn("MAX User Bot token not configured - polling not started");
            return;
        }

        running.set(true);

        pollingThread = new Thread(() -> {
            log.info("🔄 MAX User Bot Long Polling started");

            while (running.get()) {
                try {
                    pollUpdates();
                } catch (Exception e) {
                    log.error("MAX User Bot polling error: {}", e.getMessage());

                    try {
                        Thread.sleep(5000);
                    } catch (InterruptedException ie) {
                        Thread.currentThread().interrupt();
                        break;
                    }
                }
            }

            log.info("🛑 MAX User Bot Long Polling stopped");
        }, "max-user-bot-polling");

        pollingThread.setDaemon(true);
        pollingThread.start();
        log.info("✅ MAX User Bot polling thread started");
    }

    @PreDestroy
    public void stopPolling() {
        log.info("Stopping MAX User Bot polling...");
        running.set(false);
        if (pollingThread != null) {
            pollingThread.interrupt();
        }
    }

    /**
     * Выполнение Long Polling запроса к MAX API
     */
    private void pollUpdates() {
        try {
            String userBotToken = maxBotConfig.getUserBotToken();

            // Проверяем наличие токена
            if (userBotToken == null || userBotToken.isEmpty()) {
                log.warn("MAX User Bot token is not configured");
                Thread.sleep(10000); // Ждем перед повторной попыткой
                return;
            }

            String url = String.format("%s/updates?marker=%d&timeout=30",
                    maxBotConfig.getApiUrl(), marker.get());

            log.debug("MAX User: Polling updates with marker={}", marker.get());

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            headers.set("Authorization", userBotToken);

            HttpEntity<Void> entity = new HttpEntity<>(headers);

            // Используем RestTemplate с длинным timeout для Long Polling
            var response = longPollingRestTemplate.exchange(url, HttpMethod.GET, entity, String.class);

            if (response.getStatusCode().is2xxSuccessful() && response.getBody() != null) {
                processUpdates(response.getBody());
            }

        } catch (org.springframework.web.client.ResourceAccessException e) {
            // Timeout при Long Polling - это нормально, значит нет новых обновлений
            if (e.getMessage() != null && e.getMessage().contains("timed out")) {
                log.debug("MAX User: Long polling timeout - no new updates");
                return; // Просто возвращаемся, это нормальная ситуация
            }
            log.error("MAX User: Resource access error: {}", e.getMessage());
            throw new RuntimeException("Polling failed", e);

        } catch (Exception e) {
            log.error("MAX User: Error polling updates: {}", e.getMessage());

            // Если ошибка связана с маркером - сбрасываем
            if (e.getMessage() != null && e.getMessage().contains("marker")) {
                log.warn("MAX User: Resetting polling marker due to error");
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

            if (root.has("marker")) {
                marker.set(root.get("marker").asLong());
            }

            JsonNode updates = root.path("updates");

            if (!updates.isArray() || updates.isEmpty()) {
                return;
            }

            log.debug("MAX User: Received {} updates", updates.size());

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
            log.warn("MAX User: Update without update_type: {}", update);
            return;
        }

        log.info("MAX User: Processing update type: {}, data: {}", updateType, update.toString());

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
            default:
                log.debug("MAX User: Unknown update_type: {}", updateType);
        }
    }

    /**
     * Обработка нового сообщения
     */
    private void handleMessageCreated(JsonNode update) {
        try {
            JsonNode message = update.path("message");
            JsonNode sender = message.path("sender");
            JsonNode body = message.path("body");

            Long userId = sender.has("user_id") ? sender.get("user_id").asLong() : null;
            String username = sender.has("username") ? sender.get("username").asText() : null;
            String firstName = sender.has("first_name") ? sender.get("first_name").asText() :
                    (sender.has("name") ? sender.get("name").asText() : null);
            String lastName = sender.has("last_name") ? sender.get("last_name").asText() : null;
            String messageText = body.has("text") ? body.get("text").asText() : "";

            if (userId == null) {
                log.warn("MAX User: Message without user_id");
                return;
            }

            log.info("MAX User: Received message from userId={}, text={}", userId, messageText);

            // Обработка команд
            if (messageText.startsWith("/start")) {
                handleStartCommand(userId, username, firstName, lastName, messageText);
            } else if (messageText.equals("/help")) {
                handleHelpCommand(userId);
            } else if (messageText.equals("/menu")) {
                handleMenuCommand(userId);
            } else {
                handleUnknownCommand(userId);
            }

        } catch (Exception e) {
            log.error("MAX User: Error processing message_created: {}", e.getMessage(), e);
        }
    }

    /**
     * Обработка callback от inline кнопки
     */
    private void handleMessageCallback(JsonNode update) {
        try {
            JsonNode callback = update.path("callback");
            JsonNode user = callback.path("user");
            if (user.isMissingNode() || user.isNull()) {
                user = callback.path("sender");
            }

            Long userId = user.has("user_id") ? user.get("user_id").asLong() : null;
            String callbackData = callback.has("payload") ? callback.get("payload").asText() : null;

            if (userId == null || callbackData == null) {
                log.warn("MAX User: Callback without required data");
                return;
            }

            log.info("MAX User: Received callback from userId={}, data={}", userId, callbackData);

            // Обработка callback
            if (callbackData.startsWith("auth_")) {
                handleAuthCallback(userId, callbackData);
            } else if (callbackData.startsWith("order_status_")) {
                handleOrderStatusCallback(userId, callbackData);
            } else if (callbackData.startsWith("menu_")) {
                handleMenuCallback(userId);
            } else {
                log.warn("MAX User: Unknown callback data: {}", callbackData);
            }

        } catch (Exception e) {
            log.error("MAX User: Error processing message_callback: {}", e.getMessage(), e);
        }
    }

    /**
     * Обработка запуска бота
     */
    private void handleBotStarted(JsonNode update) {
        try {
            JsonNode user = update.path("user");
            if (user.isMissingNode() || user.isNull()) {
                JsonNode initiator = update.path("initiator");
                user = initiator.path("user");
            }

            Long userId = user.has("user_id") ? user.get("user_id").asLong() : null;
            String username = user.has("username") ? user.get("username").asText() : null;
            String firstName = user.has("first_name") ? user.get("first_name").asText() :
                    (user.has("name") ? user.get("name").asText() : null);
            String lastName = user.has("last_name") ? user.get("last_name").asText() : null;

            if (userId == null) {
                log.warn("MAX User: Bot started without user_id");
                return;
            }

            log.info("MAX User: Bot started by userId={}, username={}", userId, username);
            handleStartCommand(userId, username, firstName, lastName, "/start");
        } catch (Exception e) {
            log.error("MAX User: Error processing bot_started: {}", e.getMessage(), e);
        }
    }

    // ==================== КОМАНДЫ ====================

    /**
     * Обработка команды /start
     */
    private void handleStartCommand(Long userId, String username, String firstName, String lastName, String messageText) {
        // Парсим токен авторизации (если есть)
        String token = null;
        if (messageText.startsWith("/start ")) {
            token = messageText.substring("/start ".length()).trim();
        }

        // Генерируем токен авторизации
        String authToken = generateAuthToken();
        userAuthTokens.put(userId, authToken);

        // Сохраняем пользователя в базу (если новый)
        saveMaxUserToDatabase(userId, username, firstName, lastName);

        // Формируем полное имя для приветствия
        String fullName = (firstName != null ? firstName : "") +
                          (lastName != null && !lastName.isEmpty() ? " " + lastName : "");
        if (fullName.trim().isEmpty()) {
            fullName = "Друг";
        }

        // Отправляем приветственное сообщение с кнопками меню
        handleMenuCommand(userId, fullName.trim());
    }

    /**
     * Генерация токена авторизации
     */
    private String generateAuthToken() {
        return java.util.UUID.randomUUID().toString().replace("-", "").substring(0, 8);
    }

    /**
     * Сохранение пользователя MAX в базу данных
     */
    @Transactional
    public void saveMaxUserToDatabase(Long maxUserId, String maxUsername, String firstName, String lastName) {
        try {
            // Проверяем, существует ли пользователь с таким telegram ID (MAX ID)
            Optional<User> existingUser = userRepository.findByTelegramId(maxUserId);

            if (existingUser.isPresent()) {
                // Обновляем существующего пользователя
                User user = existingUser.get();
                user.setTelegramUsername(maxUsername);
                user.setIsTelegramVerified(true);
                if (firstName != null && !firstName.isEmpty()) {
                    user.setFirstName(firstName);
                }
                if (lastName != null && !lastName.isEmpty()) {
                    user.setLastName(lastName);
                }
                userRepository.save(user);
                log.info("MAX User: Updated existing user: maxUserId={}", maxUserId);
                return;
            }

            // Создаем нового пользователя
            User user = new User();
            user.setUsername("max_" + maxUserId);
            user.setPassword("");
            user.setFirstName(firstName);
            user.setLastName(lastName);
            user.setTelegramId(maxUserId);
            user.setTelegramUsername(maxUsername);
            user.setIsTelegramVerified(true);
            user.setActive(true);

            userRepository.save(user);
            log.info("MAX User: Created new user: maxUserId={}", maxUserId);

        } catch (Exception e) {
            log.error("MAX User: Error saving user: {}", e.getMessage(), e);
        }
    }

    /**
     * Обработка callback авторизации
     */
    private void handleAuthCallback(Long userId, String callbackData) {
        // Парсим токен из callback data: auth_{token}
        String[] parts = callbackData.split("_");
        if (parts.length != 2) {
            log.warn("MAX User: Invalid auth callback data: {}", callbackData);
            sendMessage(userId, "❌ Ошибка авторизации. Попробуйте снова.");
            return;
        }

        String token = parts[1];
        userAuthTokens.put(userId, token);

        // Отправляем сообщение об успешной авторизации
        sendMessage(userId, String.format(
                "✅ **Авторизация успешна!**\n\n" +
                        "👤 **Ваш ID:** %d\n" +
                        "🔑 **Токен:** `%s`\n\n" +
                        "📱 Используйте токен для входа на сайте или в приложении:\n" +
                        "🌐 [Открыть меню](https://max.ru/id121603899498_bot?startapp)\n\n" +
                        "📞 Для связи: +7 (902) 105 -34-34 ",
                token, token));
        log.info("MAX User: User {} authenticated with token: {}", userId, token);
    }

    /**
     * Обработка callback статуса заказа
     */
    private void handleOrderStatusCallback(Long userId, String callbackData) {
        // Парсим: order_status_{orderId}_{status}
        String[] parts = callbackData.split("_");
        if (parts.length != 3) {
            log.warn("MAX User: Invalid order status callback data: {}", callbackData);
            return;
        }

        try {
            Integer orderId = Integer.parseInt(parts[1]);
            String newStatus = parts[2];

            // Отправляем уведомление о статусе
            String statusMessage = String.format(
                    "📦 **Статус заказа #%d обновлен**\n\n" +
                            "Новый статус: %s\n" +
                            "Время: %s",
                    orderId,
                    getStatusDisplayName(newStatus),
                    LocalDateTime.now().format(TIME_FORMATTER));
            sendMessage(userId, statusMessage);
            log.info("MAX User: Order status notification sent for order #{}", orderId);

        } catch (Exception e) {
            log.error("MAX User: Error processing order status callback: {}", e.getMessage(), e);
        }
    }

    /**
     * Обработка callback меню
     */
    private void handleMenuCallback(Long userId) {
        handleMenuCommand(userId);
    }

    /**
     * Получить отображаемое название статуса
     */
    private String getStatusDisplayName(String status) {
        if (status == null) return "❓ Неизвестно";
        switch (status.toUpperCase()) {
            case "PENDING":
                return "🆕 Новый";
            case "CONFIRMED":
                return "✅ Подтвержден";
            case "PREPARING":
                return "👨‍🍳 Готовится";
            case "READY":
                return "🍕 Готов";
            case "DELIVERING":
                return "🚗 Доставляется";
            case "DELIVERED":
                return "✅ Доставлен";
            case "CANCELLED":
                return "❌ Отменен";
            default:
                return status;
        }
    }

    // ==================== МЕНЮ И КОМАНДЫ ====================

    /**
     * Обработка команды /menu
     */
    private void handleMenuCommand(Long userId, String userName) {
        String menuMessage = String.format("""
                👋 **Привет, %s!**

                🍕 **Димбо пицца - приложение для заказа пиццы в Волжске.**

                Доставка с 11:00 до 20:00.

                📞 тел. +7 (902) 105-34-34

                📍 Адрес: г. Волжск, ул. Шестакова 1б

                Выберите действие:
                """, userName);
        sendMenuWithButtons(userId, menuMessage);
    }

    /**
     * Обработка команды /menu (без имени)
     */
    private void handleMenuCommand(Long userId) {
        handleMenuCommand(userId, "Друг");
    }

    /**
     * Обработка команды /help
     */
    private void handleHelpCommand(Long userId) {
        String helpMessage = """
                🤖 **ДИМБО ПИЦЦА - Бот помощи**

                **Доступные команды:**
                `/start` - Начать работу с ботом
                `/menu` - Открыть меню
                `/help` - Показать эту справку

                **Функции:**
                • 🍕 Заказ пиццы через Mini App
                • 📱 Получение уведомлений о статусе заказов
                • 📞 Связь с поддержкой через +7 (902) 105 -34-34
                """;
        sendMessage(userId, helpMessage);
    }

    /**
     * Обработка неизвестной команды
     */
    private void handleUnknownCommand(Long userId) {
        sendMessage(userId, "❓ Неизвестная команда. Используйте /help для просмотра доступных команд.");
    }

    // ==================== ОТПРАВКА СООБЩЕНИЙ ====================

    /**
     * Отправка меню с inline кнопками
     */
    private void sendMenuWithButtons(Long userId, String message) {
        try {
            String userBotToken = maxBotConfig.getUserBotToken();
            String url = String.format("%s/messages?user_id=%d", maxBotConfig.getApiUrl(), userId);

            // Создаем inline кнопки
            List<Map<String, Object>> attachments = new ArrayList<>();
            List<List<Map<String, Object>>> buttonRows = new ArrayList<>();

            // Кнопка: Открыть меню (link тип для открытия URL)
            Map<String, Object> menuButton = new HashMap<>();
            menuButton.put("type", "link");
            menuButton.put("text", "🍕 Открыть меню");
            menuButton.put("url", "https://max.ru/id121603899498_bot?startapp");
            buttonRows.add(List.of(menuButton));

            // Кнопка: Связь с поддержкой (link тип для открытия URL)
            Map<String, Object> supportButton = new HashMap<>();
            supportButton.put("type", "link");
            supportButton.put("text", "📞 Связь с поддержкой");
            supportButton.put("url", "https://max.ru/u/f9LHodD0cOLntZ_-fT2vQ_TC2goPd1KD3E48k309fX_KUv4aGYawlXRscU8");
            buttonRows.add(List.of(supportButton));

            // Примечание: телефон указан в тексте сообщения - MAX не поддерживает tel: в кнопках

            Map<String, Object> attachment = new HashMap<>();
            attachment.put("type", "inline_keyboard");
            Map<String, Object> payload = new HashMap<>();
            payload.put("buttons", buttonRows);
            attachment.put("payload", payload);
            attachments.add(attachment);

            Map<String, Object> body = new HashMap<>();
            body.put("text", message);
            body.put("format", "markdown");
            body.put("attachments", attachments);

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            headers.set("Authorization", userBotToken);

            HttpEntity<Map<String, Object>> entity = new HttpEntity<>(body, headers);

            restTemplate.postForEntity(url, entity, String.class);
            log.debug("MAX User: Menu sent to userId={}", userId);

        } catch (Exception e) {
            log.error("MAX User: Error sending menu: {}", e.getMessage(), e);
        }
    }

    /**
     * Отправка простого сообщения
     */
    private void sendMessage(Long userId, String message) {
        try {
            String userBotToken = maxBotConfig.getUserBotToken();
            String url = String.format("%s/messages?user_id=%d", maxBotConfig.getApiUrl(), userId);

            Map<String, Object> body = new HashMap<>();
            body.put("text", message);
            body.put("format", "markdown");

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            headers.set("Authorization", userBotToken);

            HttpEntity<Map<String, Object>> entity = new HttpEntity<>(body, headers);

            restTemplate.postForEntity(url, entity, String.class);
            log.debug("MAX User: Message sent to userId={}", userId);

        } catch (Exception e) {
            log.error("MAX User: Error sending message: {}", e.getMessage(), e);
        }
    }
}
