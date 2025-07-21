/**
 * @file: CustomerDto.java
 * @description: DTO для данных покупателя в чеке ЮКассы
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
 * Данные покупателя для чека
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CustomerDto {
    
    /**
     * Полное имя покупателя (обязательное поле для чека)
     */
    @JsonProperty("full_name")
    private String fullName;
    
    /**
     * Номер телефона покупателя (обязательное поле)
     * Формат: +7XXXXXXXXXX
     */
    @JsonProperty("phone")
    private String phone;
    
    /**
     * Email покупателя (опциональное поле)
     */
    @JsonProperty("email")
    private String email;
} 