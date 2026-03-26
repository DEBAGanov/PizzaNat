package com.baganov.pizzanat.model.dto.max;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * DTO пользователя MAX WebApp
 *
 * Формат данных от MAX:
 * {
 *   "id": 400,
 *   "first_name": "Вася",
 *   "last_name": "",
 *   "username": null,
 *   "language_code": "ru",
 *   "photo_url": null
 * }
 *
 * Документация: https://dev.max.ru/docs/webapps/validation
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class MaxWebAppUser {

    /**
     * Уникальный идентификатор пользователя в MAX
     */
    @JsonProperty("id")
    private Long id;

    /**
     * Имя пользователя
     */
    @JsonProperty("first_name")
    private String firstName;

    /**
     * Фамилия пользователя (может быть пустой)
     */
    @JsonProperty("last_name")
    private String lastName;

    /**
     * Username пользователя (может отсутствовать)
     */
    @JsonProperty("username")
    private String username;

    /**
     * Код языка пользователя (ru, en и т.д.)
     */
    @JsonProperty("language_code")
    private String languageCode;

    /**
     * URL фото профиля (может отсутствовать)
     */
    @JsonProperty("photo_url")
    private String photoUrl;

    /**
     * Получить полное имя пользователя
     */
    public String getFullName() {
        if (firstName == null || firstName.isEmpty()) {
            return username != null ? username : "Пользователь MAX";
        }
        if (lastName == null || lastName.isEmpty()) {
            return firstName;
        }
        return firstName + " " + lastName;
    }

    /**
     * Получить отображаемое имя
     */
    public String getDisplayName() {
        if (username != null && !username.isEmpty()) {
            return "@" + username;
        }
        return getFullName();
    }
}
