package com.baganov.pizzanat.model.dto.gateway;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * DTO для запроса отправки верификационного сообщения через Telegram Gateway
 * Согласно документации: https://core.telegram.org/gateway/api
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SendVerificationRequest {

    /**
     * Номер телефона в формате E.164
     */
    @JsonProperty("phone_number")
    private String phoneNumber;

    /**
     * ID предыдущего запроса от checkSendAbility (делает запрос бесплатным)
     */
    @JsonProperty("request_id")
    private String requestId;

    /**
     * Username канала Telegram для отправки (должен быть верифицирован)
     */
    @JsonProperty("sender_username")
    private String senderUsername;

    /**
     * Верификационный код (если хотите установить сами)
     */
    private String code;

    /**
     * Длина кода, если Telegram генерирует его (4-8)
     */
    @JsonProperty("code_length")
    private Integer codeLength;

    /**
     * URL для получения отчетов о доставке
     */
    @JsonProperty("callback_url")
    private String callbackUrl;

    /**
     * Пользовательские данные (0-128 байт)
     */
    private String payload;

    /**
     * Время жизни сообщения в секундах (30-3600)
     */
    private Integer ttl;
}