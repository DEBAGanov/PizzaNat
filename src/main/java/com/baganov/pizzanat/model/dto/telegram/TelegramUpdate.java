package com.baganov.pizzanat.model.dto.telegram;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * DTO для Telegram webhook updates.
 * Представляет входящие обновления от Telegram Bot API.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "Telegram webhook update")
public class TelegramUpdate {

    @Schema(description = "ID обновления", example = "123456789")
    @JsonProperty("update_id")
    private Long updateId;

    @Schema(description = "Сообщение от пользователя")
    private TelegramMessage message;

    @Schema(description = "Callback query от inline кнопки")
    @JsonProperty("callback_query")
    private TelegramCallbackQuery callbackQuery;

    /**
     * DTO для сообщения Telegram
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    @Schema(description = "Telegram сообщение")
    public static class TelegramMessage {

        @Schema(description = "ID сообщения", example = "123")
        @JsonProperty("message_id")
        private Long messageId;

        @Schema(description = "Отправитель сообщения")
        private TelegramUserData from;

        @Schema(description = "Чат, в котором отправлено сообщение")
        private TelegramChat chat;

        @Schema(description = "Текст сообщения", example = "/start tg_auth_abc123")
        private String text;

        @Schema(description = "Дата отправки сообщения (Unix timestamp)")
        private Long date;

        @Schema(description = "Контактные данные (при отправке контакта)")
        private TelegramContact contact;

        /**
         * Проверяет, содержит ли сообщение контактные данные
         */
        public boolean hasContact() {
            return contact != null;
        }
    }

    /**
     * DTO для чата Telegram
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    @JsonIgnoreProperties(ignoreUnknown = true)
    @Schema(description = "Telegram чат")
    public static class TelegramChat {

        @Schema(description = "ID чата", example = "123456789")
        private Long id;

        @Schema(description = "Тип чата", example = "private")
        private String type;

        @Schema(description = "Username чата (для групп)", example = "my_group")
        private String username;

        @Schema(description = "Название чата", example = "My Group")
        private String title;
    }

    /**
     * DTO для callback query (inline кнопки)
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    @Schema(description = "Telegram callback query")
    public static class TelegramCallbackQuery {

        @Schema(description = "ID callback query", example = "abc123")
        private String id;

        @Schema(description = "Пользователь, нажавший кнопку")
        private TelegramUserData from;

        @Schema(description = "Сообщение с inline кнопкой")
        private TelegramMessage message;

        @Schema(description = "Данные callback кнопки", example = "confirm_auth_abc123")
        private String data;
    }

    /**
     * DTO для контактных данных Telegram
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    @Schema(description = "Telegram контакт")
    public static class TelegramContact {

        @Schema(description = "Номер телефона", example = "+79161234567")
        @JsonProperty("phone_number")
        private String phoneNumber;

        @Schema(description = "Имя контакта", example = "Владимир")
        @JsonProperty("first_name")
        private String firstName;

        @Schema(description = "Фамилия контакта", example = "Баганов")
        @JsonProperty("last_name")
        private String lastName;

        @Schema(description = "Telegram ID пользователя (если контакт принадлежит пользователю Telegram)")
        @JsonProperty("user_id")
        private Long userId;

        @Schema(description = "Дополнительная информация о контакте")
        private String vcard;

        /**
         * Получает полное имя контакта
         */
        public String getFullName() {
            StringBuilder name = new StringBuilder();
            if (firstName != null && !firstName.trim().isEmpty()) {
                name.append(firstName.trim());
            }
            if (lastName != null && !lastName.trim().isEmpty()) {
                if (name.length() > 0) {
                    name.append(" ");
                }
                name.append(lastName.trim());
            }
            return name.length() > 0 ? name.toString() : null;
        }

        /**
         * Проверяет, принадлежит ли контакт самому пользователю
         */
        public boolean isOwnContact(Long telegramUserId) {
            return userId != null && userId.equals(telegramUserId);
        }
    }

    /**
     * Проверяет, содержит ли update сообщение
     */
    public boolean hasMessage() {
        return message != null;
    }

    /**
     * Проверяет, содержит ли update callback query
     */
    public boolean hasCallbackQuery() {
        return callbackQuery != null;
    }

    /**
     * Получает пользователя из update (из сообщения или callback)
     */
    public TelegramUserData getUser() {
        if (hasMessage() && message.getFrom() != null) {
            return message.getFrom();
        } else if (hasCallbackQuery() && callbackQuery.getFrom() != null) {
            return callbackQuery.getFrom();
        }
        return null;
    }

    /**
     * Получает ID чата из update
     */
    public Long getChatId() {
        if (hasMessage() && message.getChat() != null) {
            return message.getChat().getId();
        } else if (hasCallbackQuery() && callbackQuery.getMessage() != null
                && callbackQuery.getMessage().getChat() != null) {
            return callbackQuery.getMessage().getChat().getId();
        }
        return null;
    }
}