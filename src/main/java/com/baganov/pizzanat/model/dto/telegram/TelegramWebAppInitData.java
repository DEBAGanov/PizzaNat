/**
 * @file: TelegramWebAppInitData.java
 * @description: DTO для данных инициализации Telegram WebApp
 * @dependencies: TelegramWebAppUser
 * @created: 2025-01-23
 */
package com.baganov.pizzanat.model.dto.telegram;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class TelegramWebAppInitData {
    
    @JsonProperty("query_id")
    private String queryId;
    
    @Valid
    private TelegramWebAppUser user;
    
    @JsonProperty("auth_date")
    private Long authDate;
    
    @NotBlank(message = "hash обязателен для валидации")
    private String hash;
    
    @JsonProperty("start_param")
    private String startParam;
    
    @JsonProperty("chat_type")
    private String chatType;
    
    @JsonProperty("chat_instance")
    private String chatInstance;
}
