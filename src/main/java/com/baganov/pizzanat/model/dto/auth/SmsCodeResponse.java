package com.baganov.pizzanat.model.dto.auth;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonInclude;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * DTO для ответа при отправке SMS кода.
 * Следует принципу Interface Segregation из SOLID.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
@Schema(description = "Ответ на запрос отправки SMS кода")
public class SmsCodeResponse {

    @Schema(description = "Успешность операции", example = "true")
    private boolean success;

    @Schema(description = "Сообщение о результате", example = "SMS код отправлен")
    private String message;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    @Schema(description = "Время истечения кода", example = "2025-01-15 14:30:00")
    private LocalDateTime expiresAt;

    @Schema(description = "Длина SMS кода", example = "4")
    private Integer codeLength;

    @Schema(description = "Время до следующей попытки в секундах", example = "300")
    private Long retryAfterSeconds;

    @Schema(description = "Маскированный номер телефона", example = "+7 (912) ***-**-89")
    private String maskedPhoneNumber;

    /**
     * Создание успешного ответа
     */
    public static SmsCodeResponse success(LocalDateTime expiresAt, Integer codeLength, String maskedPhoneNumber) {
        return SmsCodeResponse.builder()
                .success(true)
                .message("SMS код отправлен")
                .expiresAt(expiresAt)
                .codeLength(codeLength)
                .maskedPhoneNumber(maskedPhoneNumber)
                .build();
    }

    /**
     * Создание ответа с ошибкой
     */
    public static SmsCodeResponse error(String message) {
        return SmsCodeResponse.builder()
                .success(false)
                .message(message)
                .build();
    }

    /**
     * Создание ответа при превышении rate limit
     */
    public static SmsCodeResponse rateLimitExceeded(Long retryAfterSeconds) {
        return SmsCodeResponse.builder()
                .success(false)
                .message("Слишком много запросов. Повторите через некоторое время")
                .retryAfterSeconds(retryAfterSeconds)
                .build();
    }
}