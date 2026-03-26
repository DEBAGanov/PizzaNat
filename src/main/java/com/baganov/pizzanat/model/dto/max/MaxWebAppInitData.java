package com.baganov.pizzanat.model.dto.max;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * DTO для парсинга initData от MAX WebApp
 *
 * Формат query string:
 * user=%7B%22id%22%3A400%2C...%7D&auth_date=1733485316394&query_id=xxx&hash=abc123
 *
 * Документация: https://dev.max.ru/docs/webapps/validation
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class MaxWebAppInitData {

    /**
     * Уникальный идентификатор запроса
     */
    @JsonProperty("query_id")
    private String queryId;

    /**
     * Данные пользователя в JSON формате
     * Парсится отдельно в MaxWebAppUser
     */
    @JsonProperty("user")
    private String userJson;

    /**
     * Время авторизации (Unix timestamp в миллисекундах)
     * Важно: MAX использует миллисекунды, Telegram - секунды
     */
    @JsonProperty("auth_date")
    private Long authDate;

    /**
     * HMAC-SHA256 подпись данных
     */
    @JsonProperty("hash")
    private String hash;

    /**
     * Стартовый параметр из диплинка
     * https://max.ru/botname?startapp=param
     */
    @JsonProperty("start_param")
    private String startParam;

    /**
     * Тип чата (DIALOG, CHAT)
     */
    @JsonProperty("chat_type")
    private String chatType;

    /**
     * Идентификатор экземпляра чата
     */
    @JsonProperty("chat_instance")
    private String chatInstance;

    /**
     * Распарсенный пользователь
     * Заполняется после парсинга userJson
     */
    private MaxWebAppUser user;

    /**
     * Проверить актуальность данных (не старше 24 часов)
     */
    public boolean isExpired() {
        if (authDate == null) {
            return true;
        }

        // MAX использует миллисекунды, конвертируем в секунды
        long authTimeSeconds = authDate / 1000;
        long currentTimeSeconds = System.currentTimeMillis() / 1000;

        // 24 часа = 86400 секунд
        return (currentTimeSeconds - authTimeSeconds) > 86400;
    }
}
