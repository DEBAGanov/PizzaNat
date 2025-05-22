/**
 * @file: ErrorResponse.java
 * @description: Модель для ответа с информацией об ошибке
 * @dependencies: None
 * @created: 2025-05-23
 */
package com.baganov.pizzanat.exception;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class ErrorResponse {
    private int status;
    private String message;
    private long timestamp;
}