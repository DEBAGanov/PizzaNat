/**
 * @file: PaymentUrlResponse.java
 * @description: DTO для ответа с URL оплаты
 * @dependencies: -
 * @created: 2023-11-01
 */
package com.baganov.pizzanat.model.dto.payment;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * DTO для возврата URL оплаты
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class PaymentUrlResponse {

    /**
     * URL для перенаправления на страницу оплаты
     */
    private String paymentUrl;
}