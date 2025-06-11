package com.baganov.pizzanat.model.dto.telegram;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * DTO для данных пользователя Telegram.
 * Следует принципу Interface Segregation из SOLID.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "Данные пользователя Telegram")
public class TelegramUserData {

    @Schema(description = "ID пользователя в Telegram", example = "123456789", required = true)
    private Long id;

    @Schema(description = "Username пользователя в Telegram (без @)", example = "john_doe")
    private String username;

    @Schema(description = "Имя пользователя в Telegram", example = "Иван")
    @JsonProperty("first_name")
    private String firstName;

    @Schema(description = "Фамилия пользователя в Telegram", example = "Иванов")
    @JsonProperty("last_name")
    private String lastName;

    @Schema(description = "Номер телефона пользователя", example = "+79161234567")
    private String phoneNumber;

    /**
     * Получить полное имя пользователя
     *
     * @return полное имя или username если имя не указано
     */
    public String getFullName() {
        if (firstName != null && lastName != null) {
            return firstName + " " + lastName;
        } else if (firstName != null) {
            return firstName;
        } else if (username != null) {
            return "@" + username;
        } else {
            return "Telegram User";
        }
    }

    /**
     * Получить отображаемое имя для UI
     *
     * @return имя для отображения
     */
    public String getDisplayName() {
        if (firstName != null) {
            return firstName;
        } else if (username != null) {
            return "@" + username;
        } else {
            return "Пользователь #" + id;
        }
    }

    /**
     * Проверить, есть ли у пользователя имя
     *
     * @return true если есть имя или фамилия
     */
    public boolean hasName() {
        return (firstName != null && !firstName.trim().isEmpty()) ||
                (lastName != null && !lastName.trim().isEmpty());
    }
}