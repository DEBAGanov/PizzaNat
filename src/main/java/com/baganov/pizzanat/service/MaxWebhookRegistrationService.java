/**
 * @file: MaxWebhookRegistrationService.java
 * @description: Сервис для регистрации webhook в MAX API
 * @dependencies: MaxBotConfig, RestTemplate
 * @created: 2026-03-27
 */
package com.baganov.pizzanat.service;

import com.baganov.pizzanat.config.MaxBotConfig;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.util.HashMap;
import java.util.Map;

/**
 * Сервис для автоматической регистрации webhook в MAX API
 *
 * MAX API требует явной регистрации webhook URL через API.
 * Документация: https://dev.max.ru/docs/api/bot-apis
 *
 * Возможные endpoint'ы для регистрации webhook:
 * 1. POST /bots/{token}/subscriptions
 * 2. POST /bots/{token}/webhook
 * 3. Через кабинет разработчика MAX
 */
@Slf4j
@Service
@RequiredArgsConstructor
@ConditionalOnProperty(name = "max.bot.admin-enabled", havingValue = "true", matchIfMissing = true)
public class MaxWebhookRegistrationService {

    private final MaxBotConfig maxBotConfig;
    private final RestTemplate restTemplate;

    private static final String MAX_API_BASE_URL = "https://api.max.ru";

    /**
     * Регистрация webhook при запуске приложения
     */
    @EventListener(ApplicationReadyEvent.class)
    public void registerWebhookOnStartup() {
        String webhookUrl = maxBotConfig.getAdminWebhookUrl();
        String adminBotToken = maxBotConfig.getAdminBotToken();

        if (adminBotToken == null || adminBotToken.isEmpty()) {
            log.warn("MAX Admin Bot: Токен не настроен, пропускаем регистрацию webhook");
            log.info("MAX Admin Bot: Укажите MAX_ADMIN_BOT_TOKEN в переменных окружения");
            return;
        }

        if (webhookUrl == null || webhookUrl.isEmpty()) {
            log.warn("MAX Admin Bot: Webhook URL не настроен (max.bot.admin-webhook-url). " +
                    "Бот не будет получать события от MAX.");
            log.info("MAX Admin Bot: Для работы бота укажите переменную окружения MAX_ADMIN_WEBHOOK_URL");
            log.info("MAX Admin Bot: Пример: MAX_ADMIN_WEBHOOK_URL=https://api.dimbopizza.ru/max-admin/");
            return;
        }

        log.info("=".repeat(60));
        log.info("MAX Admin Bot: НАСТРОЙКА WEBHOOK");
        log.info("MAX Admin Bot: Token: {}...", adminBotToken.substring(0, Math.min(10, adminBotToken.length())));
        log.info("MAX Admin Bot: Webhook URL: {}", webhookUrl);
        log.info("=".repeat(60));

        try {
            // Пробуем разные методы регистрации
            boolean registered = tryRegisterWebhook(adminBotToken, webhookUrl);

            if (registered) {
                log.info("✅ MAX Admin Bot: Webhook успешно зарегистрирован");
            } else {
                log.warn("⚠️ MAX Admin Bot: Не удалось автоматически зарегистрировать webhook через API");
                log.info("MAX Admin Bot: Возможно, webhook нужно настроить вручную через кабинет MAX:");
                log.info("MAX Admin Bot: 1. Войдите в https://dev.max.ru/");
                log.info("MAX Admin Bot: 2. Выберите бота: {}", maxBotConfig.getAdminBotUsername());
                log.info("MAX Admin Bot: 3. Укажите webhook URL: {}", webhookUrl);
            }

            // Проверяем текущие подписки
            checkSubscriptions(adminBotToken);

        } catch (Exception e) {
            log.error("MAX Admin Bot: Ошибка при регистрации webhook: {}", e.getMessage(), e);
        }
    }

    /**
     * Попытка регистрации webhook разными методами
     */
    private boolean tryRegisterWebhook(String botToken, String webhookUrl) {
        // Метод 1: /subscriptions endpoint
        if (trySubscriptionsEndpoint(botToken, webhookUrl)) {
            return true;
        }

        // Метод 2: /webhook endpoint
        if (tryWebhookEndpoint(botToken, webhookUrl)) {
            return true;
        }

        // Метод 3: /setWebhook endpoint (как в Telegram)
        if (trySetWebhookEndpoint(botToken, webhookUrl)) {
            return true;
        }

        return false;
    }

    /**
     * Метод 1: Регистрация через /subscriptions
     */
    private boolean trySubscriptionsEndpoint(String botToken, String webhookUrl) {
        String url = String.format("%s/bots/%s/subscriptions", MAX_API_BASE_URL, botToken);

        Map<String, Object> body = new HashMap<>();
        body.put("url", webhookUrl);
        body.put("update_types", new String[]{
                "message_created",
                "message_callback",
                "bot_started",
                "message_chat_created"
        });

        try {
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);

            HttpEntity<Map<String, Object>> entity = new HttpEntity<>(body, headers);

            log.debug("MAX Admin Bot: Попытка регистрации через /subscriptions: {}", url);
            var response = restTemplate.postForEntity(url, entity, String.class);

            if (response.getStatusCode().is2xxSuccessful()) {
                log.info("MAX Admin Bot: Успешно через /subscriptions: {}", response.getBody());
                return true;
            } else {
                log.debug("MAX Admin Bot: /subscriptions вернул статус: {}", response.getStatusCode());
            }

        } catch (Exception e) {
            log.debug("MAX Admin Bot: /subscriptions не доступен: {}", e.getMessage());
        }

        return false;
    }

    /**
     * Метод 2: Регистрация через /webhook
     */
    private boolean tryWebhookEndpoint(String botToken, String webhookUrl) {
        String url = String.format("%s/bots/%s/webhook", MAX_API_BASE_URL, botToken);

        Map<String, Object> body = new HashMap<>();
        body.put("url", webhookUrl);

        try {
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);

            HttpEntity<Map<String, Object>> entity = new HttpEntity<>(body, headers);

            log.debug("MAX Admin Bot: Попытка регистрации через /webhook: {}", url);
            var response = restTemplate.postForEntity(url, entity, String.class);

            if (response.getStatusCode().is2xxSuccessful()) {
                log.info("MAX Admin Bot: Успешно через /webhook: {}", response.getBody());
                return true;
            } else {
                log.debug("MAX Admin Bot: /webhook вернул статус: {}", response.getStatusCode());
            }

        } catch (Exception e) {
            log.debug("MAX Admin Bot: /webhook не доступен: {}", e.getMessage());
        }

        return false;
    }

    /**
     * Метод 3: Регистрация через /setWebhook (как в Telegram)
     */
    private boolean trySetWebhookEndpoint(String botToken, String webhookUrl) {
        String url = String.format("%s/bots/%s/setWebhook?url=%s", MAX_API_BASE_URL, botToken, webhookUrl);

        try {
            log.debug("MAX Admin Bot: Попытка регистрации через /setWebhook: {}", url);
            var response = restTemplate.getForEntity(url, String.class);

            if (response.getStatusCode().is2xxSuccessful()) {
                log.info("MAX Admin Bot: Успешно через /setWebhook: {}", response.getBody());
                return true;
            } else {
                log.debug("MAX Admin Bot: /setWebhook вернул статус: {}", response.getStatusCode());
            }

        } catch (Exception e) {
            log.debug("MAX Admin Bot: /setWebhook не доступен: {}", e.getMessage());
        }

        return false;
    }

    /**
     * Удаление регистрации webhook
     */
    public void unregisterWebhook(String botToken) {
        String url = String.format("%s/bots/%s/subscriptions", MAX_API_BASE_URL, botToken);

        try {
            restTemplate.delete(url);
            log.info("MAX Admin Bot: Webhook удален");
        } catch (Exception e) {
            log.error("MAX Admin Bot: Ошибка при удалении webhook: {}", e.getMessage());
        }
    }

    /**
     * Получение информации о текущих подписках
     */
    public void checkSubscriptions(String botToken) {
        // Пробуем разные endpoints для получения информации
        String[] endpoints = {"/subscriptions", "/webhook", "/getWebhook"};

        for (String endpoint : endpoints) {
            String url = String.format("%s/bots/%s%s", MAX_API_BASE_URL, botToken, endpoint);
            try {
                var response = restTemplate.exchange(url, HttpMethod.GET, null, String.class);
                if (response.getStatusCode().is2xxSuccessful() && response.getBody() != null && !response.getBody().isEmpty()) {
                    log.info("MAX Admin Bot: Информация от {}: {}", endpoint, response.getBody());
                    return;
                }
            } catch (Exception e) {
                log.debug("MAX Admin Bot: {} не доступен для GET: {}", endpoint, e.getMessage());
            }
        }

        log.debug("MAX Admin Bot: Не удалось получить информацию о подписках");
    }
}
