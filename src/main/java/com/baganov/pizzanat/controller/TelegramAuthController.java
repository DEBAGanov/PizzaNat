package com.baganov.pizzanat.controller;

import com.baganov.pizzanat.model.dto.telegram.InitTelegramAuthRequest;
import com.baganov.pizzanat.model.dto.telegram.TelegramAuthResponse;
import com.baganov.pizzanat.model.dto.telegram.TelegramStatusResponse;
import com.baganov.pizzanat.service.TelegramAuthService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

/**
 * REST контроллер для Telegram аутентификации.
 * Следует принципам SOLID - Single Responsibility, Open/Closed.
 */
@Slf4j
@RestController
@RequestMapping("/api/v1/auth/telegram")
@RequiredArgsConstructor
@Tag(name = "Telegram Authentication", description = "API для аутентификации через Telegram")
public class TelegramAuthController {

    private final TelegramAuthService telegramAuthService;

    @PostMapping("/init")
    @Operation(summary = "Инициализация Telegram аутентификации", description = "Создает токен аутентификации и возвращает ссылку на Telegram бот для подтверждения")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Токен успешно создан", content = @Content(mediaType = "application/json", schema = @Schema(implementation = TelegramAuthResponse.class), examples = @ExampleObject(name = "Успешный ответ", value = """
                    {
                        "success": true,
                        "authToken": "tg_auth_abc123xyz789",
                        "telegramBotUrl": "https://t.me/pizzanat_auth_bot?start=tg_auth_abc123xyz789",
                        "expiresAt": "2025-01-15T14:30:00",
                        "message": "Перейдите по ссылке для подтверждения аутентификации в Telegram"
                    }
                    """))),
            @ApiResponse(responseCode = "429", description = "Превышен лимит запросов", content = @Content(mediaType = "application/json", examples = @ExampleObject(name = "Rate limit", value = """
                    {
                        "success": false,
                        "message": "Слишком много попыток. Попробуйте позже"
                    }
                    """))),
            @ApiResponse(responseCode = "503", description = "Telegram аутентификация недоступна", content = @Content(mediaType = "application/json", examples = @ExampleObject(name = "Сервис недоступен", value = """
                    {
                        "success": false,
                        "message": "Telegram аутентификация недоступна"
                    }
                    """)))
    })
    public ResponseEntity<TelegramAuthResponse> initAuth(
            @Valid @RequestBody InitTelegramAuthRequest request) {

        log.info("Запрос инициализации Telegram аутентификации для устройства: {}", request.getDeviceId());

        TelegramAuthResponse response = telegramAuthService.initAuth(request.getDeviceId());

        if (response.isSuccess()) {
            log.info("Telegram аутентификация инициализирована: {}", response.getAuthToken());
            return ResponseEntity.ok(response);
        } else {
            log.warn("Ошибка инициализации Telegram аутентификации: {}", response.getMessage());

            // Определяем HTTP статус по типу ошибки
            if (response.getMessage().contains("много попыток")) {
                return ResponseEntity.status(429).body(response);
            } else if (response.getMessage().contains("недоступна")) {
                return ResponseEntity.status(503).body(response);
            } else {
                return ResponseEntity.badRequest().body(response);
            }
        }
    }

    @GetMapping("/status/{authToken}")
    @Operation(summary = "Проверка статуса Telegram аутентификации", description = "Проверяет текущий статус токена аутентификации и возвращает JWT токен при успешном подтверждении")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Статус получен успешно", content = @Content(mediaType = "application/json", schema = @Schema(implementation = TelegramStatusResponse.class), examples = {
                    @ExampleObject(name = "Ожидание подтверждения", value = """
                            {
                                "success": true,
                                "status": "PENDING",
                                "message": "Ожидание подтверждения в Telegram"
                            }
                            """),
                    @ExampleObject(name = "Подтверждено", value = """
                            {
                                "success": true,
                                "status": "CONFIRMED",
                                "message": "Аутентификация успешно подтверждена",
                                "authData": {
                                    "token": "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9...",
                                    "username": "john_doe",
                                    "email": null,
                                    "firstName": "Иван",
                                    "lastName": "Иванов"
                                }
                            }
                            """),
                    @ExampleObject(name = "Истек", value = """
                            {
                                "success": false,
                                "status": "EXPIRED",
                                "message": "Токен аутентификации истек. Попробуйте снова"
                            }
                            """)
            })),
            @ApiResponse(responseCode = "400", description = "Некорректный токен", content = @Content(mediaType = "application/json", examples = @ExampleObject(name = "Некорректный токен", value = """
                    {
                        "success": false,
                        "message": "Некорректный токен"
                    }
                    """)))
    })
    public ResponseEntity<TelegramStatusResponse> checkStatus(
            @Parameter(description = "Токен аутентификации", example = "tg_auth_abc123xyz789") @PathVariable String authToken) {

        log.debug("Проверка статуса Telegram токена: {}", authToken);

        TelegramStatusResponse response = telegramAuthService.checkAuthStatus(authToken);

        if (response.isSuccess()) {
            return ResponseEntity.ok(response);
        } else {
            return ResponseEntity.badRequest().body(response);
        }
    }

    @GetMapping("/test")
    @Operation(summary = "Проверка работоспособности Telegram аутентификации", description = "Простой health check для проверки доступности сервиса")
    @ApiResponse(responseCode = "200", description = "Сервис работает", content = @Content(mediaType = "application/json", examples = @ExampleObject(value = """
            {
                "status": "OK",
                "service": "Telegram Authentication",
                "timestamp": "2025-01-15T14:30:00"
            }
            """)))
    public ResponseEntity<Object> healthCheck() {
        try {
            // Проверяем базовую доступность сервиса
            log.debug("Telegram auth health check запрошен");

            // Получаем информацию о конфигурации без вызова зависимостей
            java.util.Map<String, Object> response = new java.util.HashMap<>();
            response.put("status", "OK");
            response.put("service", "Telegram Authentication");
            response.put("timestamp", java.time.LocalDateTime.now());

            // Добавляем минимальную диагностическую информацию
            try {
                response.put("serviceAvailable", telegramAuthService != null);
            } catch (Exception e) {
                response.put("serviceAvailable", false);
                response.put("serviceError", e.getMessage());
            }

            log.debug("Telegram auth health check успешно завершен");
            return ResponseEntity.ok(response);

        } catch (Exception e) {
            log.error("Ошибка в Telegram auth health check: {}", e.getMessage(), e);
            return ResponseEntity.status(500).body(java.util.Map.of(
                    "status", "ERROR",
                    "service", "Telegram Authentication",
                    "error", e.getMessage(),
                    "timestamp", java.time.LocalDateTime.now()));
        }
    }
}