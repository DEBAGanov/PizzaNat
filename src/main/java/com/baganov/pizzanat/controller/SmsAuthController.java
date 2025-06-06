package com.baganov.pizzanat.controller;

import com.baganov.pizzanat.mapper.SmsAuthMapper;
import com.baganov.pizzanat.model.dto.auth.AuthResponse;
import com.baganov.pizzanat.model.dto.auth.SendSmsCodeRequest;
import com.baganov.pizzanat.model.dto.auth.SmsCodeResponse;
import com.baganov.pizzanat.model.dto.auth.VerifySmsCodeRequest;
import com.baganov.pizzanat.service.SmsAuthService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

/**
 * Контроллер для SMS аутентификации.
 * Следует принципам SOLID - Single Responsibility, Dependency Inversion.
 */
@Slf4j
@RestController
@RequestMapping("/api/v1/auth/sms")
@RequiredArgsConstructor
@Validated
@Tag(name = "SMS Authentication", description = "API для аутентификации через SMS")
public class SmsAuthController {

    private final SmsAuthService smsAuthService;
    private final SmsAuthMapper smsAuthMapper;

    @PostMapping("/send-code")
    @Operation(summary = "Отправка SMS кода", description = "Отправляет 4-значный SMS код на указанный номер телефона для аутентификации")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "SMS код отправлен успешно", content = @Content(mediaType = "application/json", schema = @Schema(implementation = SmsCodeResponse.class), examples = @ExampleObject(value = """
                    {
                        "success": true,
                        "message": "SMS код отправлен",
                        "expiresAt": "2025-01-15 14:30:00",
                        "codeLength": 4,
                        "maskedPhoneNumber": "+7 (912) ***-**-89"
                    }
                    """))),
            @ApiResponse(responseCode = "400", description = "Некорректный формат номера телефона", content = @Content(mediaType = "application/json", schema = @Schema(implementation = SmsCodeResponse.class), examples = @ExampleObject(value = """
                    {
                        "success": false,
                        "message": "Некорректный формат номера телефона"
                    }
                    """))),
            @ApiResponse(responseCode = "429", description = "Превышен лимит запросов", content = @Content(mediaType = "application/json", schema = @Schema(implementation = SmsCodeResponse.class), examples = @ExampleObject(value = """
                    {
                        "success": false,
                        "message": "Слишком много запросов. Повторите через некоторое время",
                        "retryAfterSeconds": 300
                    }
                    """)))
    })
    public ResponseEntity<SmsCodeResponse> sendCode(@Valid @RequestBody SendSmsCodeRequest request) {
        log.info("Запрос на отправку SMS кода для номера: {}",
                request.getPhoneNumber().replaceAll("\\d(?=\\d{2})", "*"));

        try {
            SmsAuthService.SmsCodeResponse serviceResponse = smsAuthService.sendCode(request.getPhoneNumber());
            SmsCodeResponse response = smsAuthMapper.toDto(serviceResponse, request.getPhoneNumber());

            if (!response.isSuccess()) {
                HttpStatus status = response.getRetryAfterSeconds() != null ? HttpStatus.TOO_MANY_REQUESTS
                        : HttpStatus.BAD_REQUEST;

                log.warn("Ошибка отправки SMS кода: {}", response.getMessage());
                return ResponseEntity.status(status).body(response);
            }

            log.info("SMS код успешно отправлен");
            return ResponseEntity.ok(response);

        } catch (Exception e) {
            log.error("Неожиданная ошибка при отправке SMS кода: {}", e.getMessage(), e);
            SmsCodeResponse errorResponse = SmsCodeResponse.error("Внутренняя ошибка сервера");
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(errorResponse);
        }
    }

    @PostMapping("/verify-code")
    @Operation(summary = "Проверка SMS кода и аутентификация", description = "Проверяет SMS код и в случае успеха выполняет аутентификацию пользователя")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Аутентификация успешна", content = @Content(mediaType = "application/json", schema = @Schema(implementation = AuthResponse.class), examples = @ExampleObject(value = """
                    {
                        "token": "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9...",
                        "username": "+79123456789",
                        "email": null,
                        "firstName": null,
                        "lastName": null
                    }
                    """))),
            @ApiResponse(responseCode = "400", description = "Неверный код или некорректные данные", content = @Content(mediaType = "application/json", examples = @ExampleObject(value = """
                    {
                        "error": "Неверный или истекший код"
                    }
                    """))),
            @ApiResponse(responseCode = "429", description = "Превышен лимит попыток", content = @Content(mediaType = "application/json", examples = @ExampleObject(value = """
                    {
                        "error": "Слишком много попыток. Повторите позже"
                    }
                    """)))
    })
    public ResponseEntity<?> verifyCode(@Valid @RequestBody VerifySmsCodeRequest request) {
        log.info("Запрос на верификацию SMS кода для номера: {}",
                request.getPhoneNumber().replaceAll("\\d(?=\\d{2})", "*"));

        try {
            SmsAuthService.AuthResponse serviceResponse = smsAuthService.verifyCode(
                    request.getPhoneNumber(),
                    request.getCode());

            if (!serviceResponse.isSuccess()) {
                log.warn("Ошибка верификации SMS кода: {}", serviceResponse.getMessage());
                return ResponseEntity.badRequest()
                        .body(new ErrorResponse(serviceResponse.getMessage()));
            }

            AuthResponse response = smsAuthMapper.toDto(serviceResponse);
            log.info("SMS аутентификация успешна");
            return ResponseEntity.ok(response);

        } catch (Exception e) {
            log.error("Неожиданная ошибка при верификации SMS кода: {}", e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(new ErrorResponse("Внутренняя ошибка сервера"));
        }
    }

    @GetMapping("/test")
    @Operation(summary = "Тест SMS аутентификации", description = "Проверка доступности API SMS аутентификации")
    public ResponseEntity<String> test() {
        log.info("Тестовый запрос к SMS аутентификации");
        return ResponseEntity.ok("SMS аутентификация доступна");
    }

    /**
     * Внутренний класс для ответов с ошибками
     */
    @Schema(description = "Ответ с ошибкой")
    private static class ErrorResponse {
        @Schema(description = "Сообщение об ошибке")
        private final String error;

        public ErrorResponse(String error) {
            this.error = error;
        }

        public String getError() {
            return error;
        }
    }
}