/**
 * @file: ValidationErrorResponse.java
 * @description: Ответ с деталями ошибок валидации
 * @dependencies: None
 * @created: 2025-05-31
 */
package com.baganov.pizzanat.exception;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.util.Map;

@Data
@EqualsAndHashCode(callSuper = true)
@AllArgsConstructor
public class ValidationErrorResponse extends ErrorResponse {

    private Map<String, String> fieldErrors;

    public ValidationErrorResponse(int status, String message, long timestamp, Map<String, String> fieldErrors) {
        super(status, message, timestamp);
        this.fieldErrors = fieldErrors;
    }
}