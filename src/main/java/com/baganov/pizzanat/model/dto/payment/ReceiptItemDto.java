/**
 * @file: ReceiptItemDto.java
 * @description: DTO для товарной позиции в чеке ЮКассы
 * @dependencies: Jackson, Lombok, AmountDto
 * @created: 2025-01-19
 */
package com.baganov.pizzanat.model.dto.payment;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Товарная позиция в чеке
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ReceiptItemDto {
    
    /**
     * Название товара
     * Максимальная длина: 128 символов
     */
    @JsonProperty("description")
    private String description;
    
    /**
     * Количество товара
     * Формат: строка с числом, например "2.00"
     */
    @JsonProperty("quantity")
    private String quantity;
    
    /**
     * Стоимость товара
     */
    @JsonProperty("amount")
    private AmountDto amount;
    
    /**
     * Ставка НДС
     * 1 - НДС 0%
     * 2 - НДС 10%
     * 3 - НДС 20%
     * 4 - НДС не облагается
     */
    @JsonProperty("vat_code")
    @Builder.Default
    private Integer vatCode = 1; // По умолчанию 0% НДС для доставки еды
    
    /**
     * Признак способа расчета
     */
    @JsonProperty("payment_subject")
    @Builder.Default
    private String paymentSubject = "commodity"; // товар
    
    /**
     * Признак предмета расчета  
     */
    @JsonProperty("payment_mode")  
    @Builder.Default
    private String paymentMode = "full_payment"; // полный расчет
} 