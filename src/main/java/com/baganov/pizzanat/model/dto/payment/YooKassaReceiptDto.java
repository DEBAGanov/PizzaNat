/**
 * @file: YooKassaReceiptDto.java
 * @description: Основной DTO для формирования чека ЮКассы согласно 54-ФЗ
 * @dependencies: Jackson для сериализации, CustomerDto, ReceiptItemDto
 * @created: 2025-01-19
 */
package com.baganov.pizzanat.model.dto.payment;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * Основной объект чека для ЮКассы
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class YooKassaReceiptDto {
    
    /**
     * Информация о покупателе
     */
    @JsonProperty("customer")
    private CustomerDto customer;
    
    /**
     * Список товаров в чеке
     */
    @JsonProperty("items")
    private List<ReceiptItemDto> items;
} 