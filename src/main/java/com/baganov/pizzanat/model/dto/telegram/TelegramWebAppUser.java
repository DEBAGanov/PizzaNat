/**
 * @file: TelegramWebAppUser.java
 * @description: DTO для пользователя Telegram WebApp
 * @dependencies: -
 * @created: 2025-01-23
 */
package com.baganov.pizzanat.model.dto.telegram;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class TelegramWebAppUser {
    
    @NotNull(message = "id пользователя обязателен")
    private Long id;
    
    @JsonProperty("first_name")
    private String firstName;
    
    @JsonProperty("last_name")
    private String lastName;
    
    private String username;
    
    @JsonProperty("language_code")
    private String languageCode;
    
    @JsonProperty("is_premium")
    private Boolean isPremium;
    
    @JsonProperty("photo_url")
    private String photoUrl;
    
    @JsonProperty("phone_number")
    private String phoneNumber;
}
