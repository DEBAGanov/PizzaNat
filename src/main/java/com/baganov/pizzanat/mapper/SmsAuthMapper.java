package com.baganov.pizzanat.mapper;

import com.baganov.pizzanat.model.dto.auth.AuthResponse;
import com.baganov.pizzanat.model.dto.auth.SmsCodeResponse;
import com.baganov.pizzanat.service.SmsAuthService;
import com.baganov.pizzanat.util.PhoneNumberValidator;
import org.springframework.stereotype.Component;

import java.time.Duration;

/**
 * Mapper для конвертации между внутренними классами SmsAuthService и публичными
 * DTO.
 * Следует принципу Single Responsibility из SOLID.
 */
@Component
public class SmsAuthMapper {

    private final PhoneNumberValidator phoneValidator;

    public SmsAuthMapper(PhoneNumberValidator phoneValidator) {
        this.phoneValidator = phoneValidator;
    }

    /**
     * Конвертирует внутренний SmsCodeResponse в публичный DTO
     */
    public SmsCodeResponse toDto(SmsAuthService.SmsCodeResponse serviceResponse, String originalPhoneNumber) {
        if (!serviceResponse.isSuccess()) {
            if (serviceResponse.getRetryAfter() != null) {
                return SmsCodeResponse.rateLimitExceeded(serviceResponse.getRetryAfter().getSeconds());
            }
            return SmsCodeResponse.error(serviceResponse.getMessage());
        }

        String maskedPhone = phoneValidator.maskForLogging(originalPhoneNumber);
        return SmsCodeResponse.success(
                serviceResponse.getExpiresAt(),
                serviceResponse.getCodeLength(),
                maskedPhone);
    }

    /**
     * Конвертирует внутренний AuthResponse в публичный DTO
     */
    public AuthResponse toDto(SmsAuthService.AuthResponse serviceResponse) {
        if (!serviceResponse.isSuccess()) {
            // Для неуспешных ответов возвращаем базовый AuthResponse с ошибкой
            return AuthResponse.builder()
                    .token(null)
                    .username(null)
                    .email(null)
                    .firstName(null)
                    .lastName(null)
                    .build();
        }

        SmsAuthService.UserInfo user = serviceResponse.getUser();
        return AuthResponse.builder()
                .token(serviceResponse.getToken())
                .username(user.getPhoneNumber()) // Используем номер телефона как username
                .email(user.getEmail())
                .firstName(user.getFirstName())
                .lastName(user.getLastName())
                .build();
    }
}