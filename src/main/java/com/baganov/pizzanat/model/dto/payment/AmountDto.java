/**
 * @file: AmountDto.java
 * @description: DTO для суммы в чеке ЮКассы
 * @dependencies: Jackson, Lombok
 * @created: 2025-01-19
 */
package com.baganov.pizzanat.model.dto.payment;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Сумма в чеке
 */
@Data
@Builder  
@NoArgsConstructor
@AllArgsConstructor
public class AmountDto {
    
    /**
     * Сумма в рублях и копейках
     * Формат: "100.00"
     */
    @JsonProperty("value")
    private String value;
    
    /**
     * Код валюты (RUB)
     */
    @JsonProperty("currency")
    @Builder.Default
    private String currency = "RUB";
} 