package com.baganov.pizzanat.model.dto.gateway;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Ответ от Telegram Gateway API для проверки статуса верификации
 * Согласно документации: https://core.telegram.org/gateway/api
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class VerificationStatusResponse {

    /**
     * Статус запроса (true если успешно)
     */
    private boolean ok;

    /**
     * Результат запроса (если ok = true)
     */
    private RequestStatusResponse.RequestStatus result;

    /**
     * Описание ошибки (если ok = false)
     */
    private String error;
}