/**
 * @file: UserProfileResponse.java
 * @description: DTO для ответа с данными профиля пользователя
 * @dependencies: Jackson, Swagger, Lombok
 * @created: 2025-01-11
 */
package com.baganov.pizzanat.model.dto.user;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "Данные профиля пользователя")
public class UserProfileResponse {

    @Schema(description = "ID пользователя", example = "1")
    private Integer id;

    @Schema(description = "Имя пользователя", example = "user123")
    private String username;

    @Schema(description = "Email пользователя", example = "user@example.com")
    private String email;

    @Schema(description = "Имя", example = "Иван")
    @JsonProperty("first_name")
    private String firstName;

    @Schema(description = "Фамилия", example = "Иванов")
    @JsonProperty("last_name")
    private String lastName;

    @Schema(description = "Номер телефона (основное поле)", example = "+79161234567")
    private String phone;

    @Schema(description = "Номер телефона для SMS аутентификации", example = "+79161234567")
    @JsonProperty("phone_number")
    private String phoneNumber;

    @Schema(description = "Подтвержден ли номер телефона через SMS", example = "true")
    @JsonProperty("is_phone_verified")
    private Boolean isPhoneVerified;

    @Schema(description = "Telegram ID пользователя", example = "123456789")
    @JsonProperty("telegram_id")
    private Long telegramId;

    @Schema(description = "Telegram username", example = "john_doe")
    @JsonProperty("telegram_username")
    private String telegramUsername;

    @Schema(description = "Подтвержден ли Telegram", example = "true")
    @JsonProperty("is_telegram_verified")
    private Boolean isTelegramVerified;

    @Schema(description = "Активен ли пользователь", example = "true")
    @JsonProperty("is_active")
    private Boolean isActive;

    @Schema(description = "Дата создания", example = "2025-01-11T10:30:00")
    @JsonProperty("created_at")
    private LocalDateTime createdAt;

    @Schema(description = "Дата обновления", example = "2025-01-11T10:30:00")
    @JsonProperty("updated_at")
    private LocalDateTime updatedAt;

    @Schema(description = "Отображаемое имя пользователя", example = "Иван Иванов")
    @JsonProperty("display_name")
    private String displayName;

    @Schema(description = "Основной идентификатор пользователя", example = "user@example.com")
    @JsonProperty("primary_identifier")
    private String primaryIdentifier;

    @Schema(description = "Есть ли у пользователя подтвержденный способ аутентификации", example = "true")
    @JsonProperty("has_verified_authentication")
    private Boolean hasVerifiedAuthentication;
}