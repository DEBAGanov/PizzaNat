package com.baganov.pizzanat.controller;

import com.baganov.pizzanat.service.TelegramGatewayService;
import com.baganov.pizzanat.model.dto.gateway.RequestStatusResponse;
import com.baganov.pizzanat.model.dto.gateway.VerificationStatusResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * Контроллер для работы с Telegram Gateway API
 * Альтернатива SMS аутентификации через Telegram
 * Согласно документации: https://core.telegram.org/gateway/api
 */
@Slf4j
@RestController
@RequestMapping("/api/v1/auth/telegram-gateway")
@RequiredArgsConstructor
@ConditionalOnProperty(value = "telegram.gateway.enabled", havingValue = "true")
@Tag(name = "Telegram Gateway Authentication", description = "API для аутентификации через Telegram Gateway (верификационные коды)")
public class TelegramGatewayController {

    private final TelegramGatewayService telegramGatewayService;

    @PostMapping("/send-code")
    @Operation(summary = "Отправка верификационного кода через Telegram Gateway", description = "Отправляет верификационный код на указанный номер телефона через Telegram. Альтернатива SMS.")
    @ApiResponse(responseCode = "200", description = "Код успешно отправлен", content = @Content(mediaType = "application/json", examples = @ExampleObject(value = """
            {
                "success": true,
                "requestId": "gateway_req_abc123",
                "cost": 0.05,
                "expiresAt": "2024-12-20T15:30:00Z",
                "message": "Верификационный код отправлен через Telegram"
            }
            """)))
    @ApiResponse(responseCode = "400", description = "Неверный формат номера телефона", content = @Content(mediaType = "application/json", examples = @ExampleObject(value = """
            {
                "success": false,
                "error": "INVALID_PHONE_NUMBER",
                "message": "Неверный формат номера телефона"
            }
            """)))
    public ResponseEntity<?> sendVerificationCode(
            @Parameter(description = "Номер телефона в формате +7XXXXXXXXXX", example = "+79991234567") @RequestParam String phoneNumber,

            @Parameter(description = "Длина кода (4-8 символов)", example = "4") @RequestParam(defaultValue = "4") Integer codeLength,

            @Parameter(description = "Пользовательские данные для отслеживания", example = "auth_session_123") @RequestParam(required = false) String payload) {

        try {
            log.info("Запрос отправки кода через Telegram Gateway на номер: {}",
                    phoneNumber.replaceAll("(\\d{1,3})(\\d{3})(\\d{3})(\\d+)", "$1***$2***$4"));

            // Валидация номера телефона
            if (!isValidPhoneNumber(phoneNumber)) {
                return ResponseEntity.badRequest().body(Map.of(
                        "success", false,
                        "error", "INVALID_PHONE_NUMBER",
                        "message", "Неверный формат номера телефона"));
            }

            // Валидация длины кода
            if (codeLength < 4 || codeLength > 8) {
                return ResponseEntity.badRequest().body(Map.of(
                        "success", false,
                        "error", "INVALID_CODE_LENGTH",
                        "message", "Длина кода должна быть от 4 до 8 символов"));
            }

            RequestStatusResponse response = telegramGatewayService.sendVerificationCode(phoneNumber, codeLength,
                    payload);

            if (response.isOk() && response.getResult() != null) {
                return ResponseEntity.ok(Map.of(
                        "success", true,
                        "requestId", response.getResult().getRequestId(),
                        "cost", response.getResult().getRequestCost(),
                        "message", "Верификационный код отправлен через Telegram"));
            } else {
                return ResponseEntity.badRequest().body(Map.of(
                        "success", false,
                        "error", "GATEWAY_ERROR",
                        "message", "Ошибка отправки кода: " + response.getError()));
            }

        } catch (Exception e) {
            log.error("Ошибка отправки кода через Gateway: {}", e.getMessage(), e);
            return ResponseEntity.internalServerError().body(Map.of(
                    "success", false,
                    "error", "INTERNAL_ERROR",
                    "message", "Внутренняя ошибка сервера"));
        }
    }

    @PostMapping("/verify-code")
    @Operation(summary = "Проверка верификационного кода из Telegram Gateway", description = "Проверяет правильность введенного кода и выполняет аутентификацию пользователя")
    @ApiResponse(responseCode = "200", description = "Код корректный, пользователь аутентифицирован", content = @Content(mediaType = "application/json", examples = @ExampleObject(value = """
            {
                "success": true,
                "token": "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9...",
                "user": {
                    "id": 123,
                    "phoneNumber": "+79991234567",
                    "firstName": "Иван",
                    "lastName": "Иванов",
                    "isPhoneVerified": true
                }
            }
            """)))
    public ResponseEntity<?> verifyCode(
            @Parameter(description = "ID запроса от sendVerificationCode", example = "gateway_req_abc123") @RequestParam String requestId,

            @Parameter(description = "Код, введенный пользователем", example = "1234") @RequestParam String code) {

        try {
            log.info("Проверка кода Gateway для request ID: {}", requestId);

            VerificationStatusResponse response = telegramGatewayService.checkVerificationStatus(requestId, code);

            if (response.isOk() && response.getResult() != null) {
                String status = response.getResult().getVerificationStatus().getStatus();

                switch (status) {
                    case "code_valid":
                        // Код правильный - создаем/находим пользователя и выдаем JWT
                        // TODO: Реализовать логику создания пользователя и JWT
                        return ResponseEntity.ok(Map.of(
                                "success", true,
                                "message", "Код подтвержден",
                                "status", "VERIFIED"
                        // "token", jwtToken,
                        // "user", userData
                        ));

                    case "code_invalid":
                        return ResponseEntity.badRequest().body(Map.of(
                                "success", false,
                                "error", "INVALID_CODE",
                                "message", "Неверный код"));

                    case "code_max_attempts_exceeded":
                        return ResponseEntity.badRequest().body(Map.of(
                                "success", false,
                                "error", "MAX_ATTEMPTS_EXCEEDED",
                                "message", "Превышено максимальное количество попыток"));

                    case "expired":
                        return ResponseEntity.badRequest().body(Map.of(
                                "success", false,
                                "error", "CODE_EXPIRED",
                                "message", "Код истек"));

                    default:
                        return ResponseEntity.badRequest().body(Map.of(
                                "success", false,
                                "error", "UNKNOWN_STATUS",
                                "message", "Неизвестный статус верификации: " + status));
                }
            } else {
                return ResponseEntity.badRequest().body(Map.of(
                        "success", false,
                        "error", "GATEWAY_ERROR",
                        "message", "Ошибка проверки кода: " + response.getError()));
            }

        } catch (Exception e) {
            log.error("Ошибка проверки кода Gateway: {}", e.getMessage(), e);
            return ResponseEntity.internalServerError().body(Map.of(
                    "success", false,
                    "error", "INTERNAL_ERROR",
                    "message", "Внутренняя ошибка сервера"));
        }
    }

    @GetMapping("/status/{requestId}")
    @Operation(summary = "Проверка статуса верификационного сообщения", description = "Получение текущего статуса доставки и верификации сообщения")
    public ResponseEntity<?> checkStatus(
            @Parameter(description = "ID запроса верификации", example = "gateway_req_abc123") @PathVariable String requestId) {

        try {
            VerificationStatusResponse response = telegramGatewayService.checkVerificationStatus(requestId, null);

            if (response.isOk() && response.getResult() != null) {
                return ResponseEntity.ok(Map.of(
                        "success", true,
                        "requestId", requestId,
                        "deliveryStatus", response.getResult().getDeliveryStatus(),
                        "verificationStatus", response.getResult().getVerificationStatus()));
            } else {
                return ResponseEntity.badRequest().body(Map.of(
                        "success", false,
                        "error", "REQUEST_NOT_FOUND",
                        "message", "Запрос не найден или истек"));
            }

        } catch (Exception e) {
            log.error("Ошибка получения статуса Gateway: {}", e.getMessage(), e);
            return ResponseEntity.internalServerError().body(Map.of(
                    "success", false,
                    "error", "INTERNAL_ERROR",
                    "message", "Внутренняя ошибка сервера"));
        }
    }

    @DeleteMapping("/revoke/{requestId}")
    @Operation(summary = "Отзыв верификационного сообщения", description = "Отзывает отправленное сообщение (если оно еще не доставлено/прочитано)")
    public ResponseEntity<?> revokeMessage(
            @Parameter(description = "ID запроса для отзыва", example = "gateway_req_abc123") @PathVariable String requestId) {

        try {
            boolean revoked = telegramGatewayService.revokeVerificationMessage(requestId);

            if (revoked) {
                return ResponseEntity.ok(Map.of(
                        "success", true,
                        "message", "Запрос на отзыв сообщения принят"));
            } else {
                return ResponseEntity.badRequest().body(Map.of(
                        "success", false,
                        "error", "REVOKE_FAILED",
                        "message", "Не удалось отозвать сообщение (возможно, уже доставлено)"));
            }

        } catch (Exception e) {
            log.error("Ошибка отзыва сообщения Gateway: {}", e.getMessage(), e);
            return ResponseEntity.internalServerError().body(Map.of(
                    "success", false,
                    "error", "INTERNAL_ERROR",
                    "message", "Внутренняя ошибка сервера"));
        }
    }

    @GetMapping("/test")
    @Operation(summary = "Health check для Telegram Gateway API", description = "Проверка работоспособности Gateway сервиса")
    public ResponseEntity<?> healthCheck() {
        return ResponseEntity.ok(Map.of(
                "status", "OK",
                "service", "Telegram Gateway API",
                "timestamp", System.currentTimeMillis()));
    }

    /**
     * Валидация номера телефона в формате E.164
     */
    private boolean isValidPhoneNumber(String phoneNumber) {
        if (phoneNumber == null || phoneNumber.trim().isEmpty()) {
            return false;
        }
        // Простая проверка формата E.164: +[1-9][0-9]{6,14}
        return phoneNumber.matches("^\\+[1-9]\\d{6,14}$");
    }
}