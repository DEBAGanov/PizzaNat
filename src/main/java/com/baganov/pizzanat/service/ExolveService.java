package com.baganov.pizzanat.service;

import com.baganov.pizzanat.config.ExolveConfig;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.*;
import org.springframework.retry.annotation.Backoff;
import org.springframework.retry.annotation.Retryable;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

import java.util.concurrent.CompletableFuture;

/**
 * Сервис для отправки SMS через Exolve API.
 * Следует принципу Single Responsibility из SOLID.
 */
@Service
public class ExolveService {

    private static final Logger logger = LoggerFactory.getLogger(ExolveService.class);

    private final RestTemplate exolveRestTemplate;
    private final ExolveConfig exolveConfig;

    public ExolveService(@Qualifier("exolveRestTemplate") RestTemplate exolveRestTemplate,
            ExolveConfig exolveConfig) {
        this.exolveRestTemplate = exolveRestTemplate;
        this.exolveConfig = exolveConfig;
    }

    /**
     * Асинхронная отправка SMS
     * 
     * @param phoneNumber номер телефона в формате +7XXXXXXXXXX
     * @param message     текст сообщения
     * @return CompletableFuture с результатом отправки
     */
    @Async
    public CompletableFuture<Boolean> sendSmsAsync(String phoneNumber, String message) {
        try {
            boolean result = sendSms(phoneNumber, message);
            return CompletableFuture.completedFuture(result);
        } catch (Exception e) {
            logger.error("Ошибка асинхронной отправки SMS на номер {}: {}", phoneNumber, e.getMessage());
            return CompletableFuture.completedFuture(false);
        }
    }

    /**
     * Синхронная отправка SMS с повторными попытками
     * 
     * @param phoneNumber номер телефона в формате +7XXXXXXXXXX
     * @param message     текст сообщения
     * @return true если SMS отправлено успешно
     */
    @Retryable(value = { RestClientException.class }, maxAttempts = 3, backoff = @Backoff(delay = 1000, multiplier = 2))
    public boolean sendSms(String phoneNumber, String message) {
        if (!exolveConfig.isConfigured()) {
            logger.warn("Exolve API не настроен, SMS не будет отправлено");
            return false;
        }

        try {
            // Нормализуем номер телефона для Exolve API (формат: 79XXXXXXXXX)
            String normalizedPhone = normalizePhoneForExolve(phoneNumber);
            String normalizedSender = normalizePhoneForExolve(exolveConfig.getExolveSenderName());

            ExolveRequest request = new ExolveRequest(
                    normalizedSender,
                    normalizedPhone,
                    message);

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            headers.setBearerAuth(exolveConfig.getExolveApiKey());

            HttpEntity<ExolveRequest> entity = new HttpEntity<>(request, headers);

            logger.debug("Отправка SMS на номер {} (нормализован: {}) через Exolve API", phoneNumber, normalizedPhone);

            ResponseEntity<ExolveResponse> response = exolveRestTemplate.exchange(
                    "/SendSMS",
                    HttpMethod.POST,
                    entity,
                    ExolveResponse.class);

            if (response.getStatusCode() == HttpStatus.OK && response.getBody() != null) {
                ExolveResponse exolveResponse = response.getBody();
                logger.info("SMS успешно отправлено на номер {}. Response: {}", phoneNumber, exolveResponse);
                return exolveResponse.isSuccess();
            } else {
                logger.error("Ошибка отправки SMS на номер {}. HTTP Status: {}", phoneNumber, response.getStatusCode());
                return false;
            }

        } catch (RestClientException e) {
            logger.error("Ошибка соединения с Exolve API при отправке SMS на номер {}: {}", phoneNumber,
                    e.getMessage());
            throw e; // Для срабатывания @Retryable
        } catch (Exception e) {
            logger.error("Неожиданная ошибка при отправке SMS на номер {}: {}", phoneNumber, e.getMessage(), e);
            return false;
        }
    }

    /**
     * Проверка доступности сервиса Exolve
     * 
     * @return true если сервис доступен
     */
    public boolean isServiceAvailable() {
        if (!exolveConfig.isConfigured()) {
            return false;
        }

        try {
            // Простая проверка доступности API (можно отправить тестовый запрос)
            ResponseEntity<String> response = exolveRestTemplate.getForEntity("/", String.class);
            return response.getStatusCode().is2xxSuccessful();
        } catch (Exception e) {
            logger.warn("Exolve API недоступен: {}", e.getMessage());
            return false;
        }
    }

    /**
     * Генерация текста SMS сообщения с кодом
     * 
     * @param code 4-значный код подтверждения
     * @return отформатированное сообщение
     */
    public String generateSmsMessage(String code) {
        return String.format("Ваш код для входа в PizzaNat: %s. Не сообщайте его никому!", code);
    }

    /**
     * Нормализует номер телефона для Exolve API
     * Убирает '+' и оставляет только цифры, начинающиеся с '79'
     * 
     * @param phoneNumber номер в формате +7XXXXXXXXXX или 7XXXXXXXXXX
     * @return номер в формате 79XXXXXXXXX
     */
    private String normalizePhoneForExolve(String phoneNumber) {
        if (phoneNumber == null) {
            return null;
        }

        // Убираем все символы кроме цифр
        String digitsOnly = phoneNumber.replaceAll("[^0-9]", "");

        // Если номер начинается с '8', заменяем на '7'
        if (digitsOnly.startsWith("8")) {
            digitsOnly = "7" + digitsOnly.substring(1);
        }

        // Если номер начинается с '7', оставляем как есть
        if (digitsOnly.startsWith("7") && digitsOnly.length() == 11) {
            return digitsOnly;
        }

        // Если номер имеет 10 цифр (без '7' в начале), добавляем '7'
        if (digitsOnly.length() == 10) {
            return "7" + digitsOnly;
        }

        logger.warn("Неожиданный формат номера телефона для Exolve: {}", phoneNumber);
        return digitsOnly;
    }

    // === DTO классы для Exolve API ===

    /**
     * Запрос к Exolve API
     */
    public static class ExolveRequest {
        @JsonProperty("number")
        private String senderName;

        @JsonProperty("destination")
        private String destination;

        @JsonProperty("text")
        private String text;

        public ExolveRequest() {
        }

        public ExolveRequest(String senderName, String destination, String text) {
            this.senderName = senderName;
            this.destination = destination;
            this.text = text;
        }

        // Геттеры и сеттеры
        public String getSenderName() {
            return senderName;
        }

        public void setSenderName(String senderName) {
            this.senderName = senderName;
        }

        public String getDestination() {
            return destination;
        }

        public void setDestination(String destination) {
            this.destination = destination;
        }

        public String getText() {
            return text;
        }

        public void setText(String text) {
            this.text = text;
        }

        @Override
        public String toString() {
            return String.format("ExolveRequest{senderName='%s', destination='%s', textLength=%d}",
                    senderName, destination, text != null ? text.length() : 0);
        }
    }

    /**
     * Ответ от Exolve API
     */
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class ExolveResponse {
        @JsonProperty("success")
        private Boolean success;

        @JsonProperty("message")
        private String message;

        @JsonProperty("error")
        private String error;

        @JsonProperty("message_id")
        private String messageId;

        public ExolveResponse() {
        }

        public Boolean isSuccess() {
            // Если есть messageId, значит SMS отправлено успешно
            // Если есть explicit success=true, тоже считаем успешным
            // Если есть error, считаем неуспешным
            if (error != null && !error.trim().isEmpty()) {
                return false;
            }

            if (messageId != null && !messageId.trim().isEmpty()) {
                return true;
            }

            return success != null && success;
        }

        // Геттеры и сеттеры
        public Boolean getSuccess() {
            return success;
        }

        public void setSuccess(Boolean success) {
            this.success = success;
        }

        public String getMessage() {
            return message;
        }

        public void setMessage(String message) {
            this.message = message;
        }

        public String getError() {
            return error;
        }

        public void setError(String error) {
            this.error = error;
        }

        public String getMessageId() {
            return messageId;
        }

        public void setMessageId(String messageId) {
            this.messageId = messageId;
        }

        @Override
        public String toString() {
            return String.format("ExolveResponse{success=%s, message='%s', error='%s', messageId='%s'}",
                    success, message, error, messageId);
        }
    }
}