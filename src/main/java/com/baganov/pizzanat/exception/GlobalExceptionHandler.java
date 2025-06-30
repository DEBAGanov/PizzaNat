/**
 * @file: GlobalExceptionHandler.java
 * @description: Глобальный обработчик исключений
 * @dependencies: Spring Web
 * @created: 2025-05-23
 */
package com.baganov.pizzanat.exception;

import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.context.request.WebRequest;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonMappingException;
import com.fasterxml.jackson.databind.exc.InvalidFormatException;
import com.fasterxml.jackson.databind.exc.MismatchedInputException;

import jakarta.validation.ValidationException;
import jakarta.validation.ConstraintViolationException;
import org.springframework.validation.FieldError;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;

@Slf4j
@ControllerAdvice
public class GlobalExceptionHandler {

    /**
     * Обработчик ошибок аутентификации
     */
    @ExceptionHandler(BadCredentialsException.class)
    public ResponseEntity<ErrorResponse> handleBadCredentialsException(BadCredentialsException ex) {
        log.warn("Ошибка аутентификации: {}", ex.getMessage());
        ErrorResponse error = new ErrorResponse(
                HttpStatus.UNAUTHORIZED.value(),
                ex.getMessage(),
                System.currentTimeMillis());
        return new ResponseEntity<>(error, HttpStatus.UNAUTHORIZED);
    }

    /**
     * Обработчик ошибок JSON парсинга (для webhook диагностики)
     */
    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<Object> handleJsonParsingError(
            HttpMessageNotReadableException ex, WebRequest request) {

        String requestUrl = request.getDescription(false);
        log.error("JSON PARSING ERROR: URL: {}, Message: {}", requestUrl, ex.getMessage(), ex);

        String errorMessage = "Ошибка парсинга JSON";

        // Детальная информация об ошибке JSON для webhook
        Throwable cause = ex.getCause();
        if (cause instanceof JsonProcessingException jsonEx) {
            log.error("JSON Processing Exception: {}", jsonEx.getOriginalMessage());
            errorMessage = "Ошибка обработки JSON: " + jsonEx.getOriginalMessage();
            
            // Специальная обработка для UTF-8 ошибок
            if (jsonEx.getOriginalMessage().contains("Invalid UTF-8")) {
                log.warn("UTF-8 кодировка проблема, возможно некорректные данные в запросе");
                errorMessage = "Ошибка кодировки данных. Проверьте корректность UTF-8 символов в запросе";
            }
        } else if (cause instanceof InvalidFormatException formatEx) {
            log.error("Invalid Format Exception: {}", formatEx.getOriginalMessage());
            errorMessage = "Неверный формат данных: " + formatEx.getOriginalMessage();
        } else if (cause instanceof MismatchedInputException inputEx) {
            log.error("Mismatched Input Exception: {}", inputEx.getOriginalMessage());
            errorMessage = "Несоответствие входных данных: " + inputEx.getOriginalMessage();
        }

        // Для Telegram webhook возвращаем OK статус с ошибкой
        if (requestUrl.contains("/telegram/webhook")) {
            return ResponseEntity.ok(Map.of(
                    "status", "OK",
                    "processed", false,
                    "error", errorMessage,
                    "timestamp", LocalDateTime.now().toString()));
        }

        // Для ЮКасса webhook тоже возвращаем OK статус
        if (requestUrl.contains("/payments/yookassa/webhook")) {
            return ResponseEntity.ok(Map.of(
                    "status", "error",
                    "message", "Failed to process webhook: " + errorMessage,
                    "timestamp", LocalDateTime.now().toString()));
        }

        // Для остальных эндпоинтов используем стандартную обработку ошибок
        ErrorResponse error = new ErrorResponse(
                HttpStatus.BAD_REQUEST.value(),
                errorMessage,
                System.currentTimeMillis());
        return new ResponseEntity<>(error, HttpStatus.BAD_REQUEST);
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<ErrorResponse> handleIllegalArgumentException(IllegalArgumentException ex) {
        log.error("Ошибка запроса: {}", ex.getMessage());
        ErrorResponse error = new ErrorResponse(
                HttpStatus.NOT_FOUND.value(),
                ex.getMessage(),
                System.currentTimeMillis());
        return new ResponseEntity<>(error, HttpStatus.NOT_FOUND);
    }

    @ExceptionHandler(ValidationException.class)
    public ResponseEntity<ErrorResponse> handleValidationException(ValidationException ex) {
        log.error("Ошибка валидации: {}", ex.getMessage());
        ErrorResponse error = new ErrorResponse(
                HttpStatus.BAD_REQUEST.value(),
                ex.getMessage(),
                System.currentTimeMillis());
        return new ResponseEntity<>(error, HttpStatus.BAD_REQUEST);
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ValidationErrorResponse> handleMethodArgumentNotValid(MethodArgumentNotValidException ex) {
        log.error("Ошибка валидации полей: {}", ex.getMessage());

        Map<String, String> errors = new HashMap<>();
        ex.getBindingResult().getAllErrors().forEach((error) -> {
            String fieldName = ((FieldError) error).getField();
            String errorMessage = error.getDefaultMessage();
            errors.put(fieldName, errorMessage);
        });

        ValidationErrorResponse errorResponse = new ValidationErrorResponse(
                HttpStatus.BAD_REQUEST.value(),
                "Ошибка валидации полей",
                System.currentTimeMillis(),
                errors);

        return new ResponseEntity<>(errorResponse, HttpStatus.BAD_REQUEST);
    }

    @ExceptionHandler(ConstraintViolationException.class)
    public ResponseEntity<ErrorResponse> handleConstraintViolation(ConstraintViolationException ex) {
        log.error("Ошибка ограничений: {}", ex.getMessage());

        StringBuilder message = new StringBuilder("Ошибки валидации: ");
        ex.getConstraintViolations().forEach(violation -> message.append(violation.getPropertyPath()).append(" ")
                .append(violation.getMessage()).append("; "));

        ErrorResponse error = new ErrorResponse(
                HttpStatus.BAD_REQUEST.value(),
                message.toString(),
                System.currentTimeMillis());
        return new ResponseEntity<>(error, HttpStatus.BAD_REQUEST);
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<Object> handleGenericException(Exception ex, WebRequest request) {
        log.error("GENERIC ERROR: URL: {}, Exception: {}, Message: {}",
                request.getDescription(false), ex.getClass().getSimpleName(), ex.getMessage(), ex);

        // Для Telegram webhook возвращаем OK статус с ошибкой
        if (request.getDescription(false).contains("/telegram/webhook")) {
            return ResponseEntity.ok(Map.of(
                    "status", "OK",
                    "processed", false,
                    "error", ex.getMessage() != null ? ex.getMessage() : "Внутренняя ошибка сервера",
                    "timestamp", LocalDateTime.now().toString()));
        }

        // Для остальных эндпоинтов используем стандартную обработку ошибок
        ErrorResponse error = new ErrorResponse(
                HttpStatus.INTERNAL_SERVER_ERROR.value(),
                "Внутренняя ошибка сервера",
                System.currentTimeMillis());
        return new ResponseEntity<>(error, HttpStatus.INTERNAL_SERVER_ERROR);
    }
}