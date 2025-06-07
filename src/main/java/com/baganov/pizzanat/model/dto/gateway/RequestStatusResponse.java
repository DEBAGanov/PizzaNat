package com.baganov.pizzanat.model.dto.gateway;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Ответ от Telegram Gateway API для запросов отправки верификационного
 * сообщения
 * Согласно документации: https://core.telegram.org/gateway/api
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RequestStatusResponse {

    /**
     * Статус запроса (true если успешно)
     */
    private boolean ok;

    /**
     * Результат запроса (если ok = true)
     */
    private RequestStatus result;

    /**
     * Описание ошибки (если ok = false)
     */
    private String error;

    /**
     * Объект RequestStatus согласно документации
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class RequestStatus {

        /**
         * Уникальный идентификатор запроса верификации
         */
        @JsonProperty("request_id")
        private String requestId;

        /**
         * Номер телефона в формате E.164
         */
        @JsonProperty("phone_number")
        private String phoneNumber;

        /**
         * Стоимость запроса
         */
        @JsonProperty("request_cost")
        private Double requestCost;

        /**
         * Возврат платы за запрос (если сообщение не доставлено)
         */
        @JsonProperty("is_refunded")
        private Boolean isRefunded;

        /**
         * Оставшийся баланс в кредитах
         */
        @JsonProperty("remaining_balance")
        private Double remainingBalance;

        /**
         * Статус доставки сообщения
         */
        @JsonProperty("delivery_status")
        private DeliveryStatus deliveryStatus;

        /**
         * Статус верификации
         */
        @JsonProperty("verification_status")
        private VerificationStatus verificationStatus;

        /**
         * Пользовательские данные
         */
        private String payload;
    }

    /**
     * Статус доставки сообщения
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class DeliveryStatus {

        /**
         * Статус: sent, delivered, read, expired, revoked
         */
        private String status;

        /**
         * Время последнего обновления статуса (Unix timestamp)
         */
        @JsonProperty("updated_at")
        private Long updatedAt;
    }

    /**
     * Статус верификации кода
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class VerificationStatus {

        /**
         * Статус: code_valid, code_invalid, code_max_attempts_exceeded, expired
         */
        private String status;

        /**
         * Время обновления статуса (Unix timestamp)
         */
        @JsonProperty("updated_at")
        private Long updatedAt;

        /**
         * Код, введенный пользователем
         */
        @JsonProperty("code_entered")
        private String codeEntered;
    }
}