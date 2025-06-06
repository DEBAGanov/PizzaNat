package com.baganov.pizzanat.controller;

import com.baganov.pizzanat.model.dto.telegram.TelegramUpdate;
import com.baganov.pizzanat.service.TelegramWebhookService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

/**
 * Webhook контроллер для обработки обновлений от Telegram Bot API.
 * Следует принципу Single Responsibility из SOLID.
 */
@Slf4j
@RestController
@RequestMapping("/api/v1/telegram")
@RequiredArgsConstructor
@Tag(name = "Telegram Webhook", description = "Webhook для обработки обновлений от Telegram Bot API")
public class TelegramWebhookController {

    private final TelegramWebhookService telegramWebhookService;

    @PostMapping("/webhook")
    @Operation(summary = "Webhook для обработки Telegram updates", description = "Принимает обновления от Telegram Bot API и обрабатывает команды аутентификации")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Update обработан успешно", content = @Content(mediaType = "application/json", examples = @ExampleObject(value = """
                    {
                        "status": "OK"
                    }
                    """))),
            @ApiResponse(responseCode = "400", description = "Некорректные данные update", content = @Content(mediaType = "application/json", examples = @ExampleObject(value = """
                    {
                        "error": "Invalid update data"
                    }
                    """)))
    })
    public ResponseEntity<Object> handleWebhook(@RequestBody TelegramUpdate update) {
        try {
            log.debug("Получен Telegram webhook update: {}", update.getUpdateId());

            telegramWebhookService.processUpdate(update);

            return ResponseEntity.ok(java.util.Map.of("status", "OK"));

        } catch (Exception e) {
            log.error("Ошибка при обработке Telegram webhook: {}", e.getMessage(), e);
            return ResponseEntity.badRequest().body(
                    java.util.Map.of("error", "Invalid update data"));
        }
    }

    @GetMapping("/webhook/info")
    @Operation(summary = "Информация о webhook", description = "Возвращает информацию о настройке webhook для Telegram бота")
    @ApiResponse(responseCode = "200", description = "Информация о webhook", content = @Content(mediaType = "application/json", schema = @Schema(implementation = Object.class), examples = @ExampleObject(value = """
            {
                "webhookUrl": "https://your-domain.com/api/v1/telegram/webhook",
                "isActive": true,
                "lastUpdate": "2025-01-15T14:30:00"
            }
            """)))
    public ResponseEntity<Object> getWebhookInfo() {
        try {
            Object webhookInfo = telegramWebhookService.getWebhookInfo();
            return ResponseEntity.ok(webhookInfo);
        } catch (Exception e) {
            log.error("Ошибка при получении информации о webhook: {}", e.getMessage(), e);
            return ResponseEntity.ok(java.util.Map.of(
                    "error", "Unable to get webhook info",
                    "message", e.getMessage()));
        }
    }

    @PostMapping("/webhook/register")
    @Operation(summary = "Регистрация webhook", description = "Регистрирует webhook URL в Telegram Bot API")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Webhook зарегистрирован успешно", content = @Content(mediaType = "application/json", examples = @ExampleObject(value = """
                    {
                        "success": true,
                        "message": "Webhook registered successfully"
                    }
                    """))),
            @ApiResponse(responseCode = "400", description = "Ошибка регистрации webhook", content = @Content(mediaType = "application/json", examples = @ExampleObject(value = """
                    {
                        "success": false,
                        "message": "Failed to register webhook"
                    }
                    """)))
    })
    public ResponseEntity<Object> registerWebhook() {
        try {
            boolean success = telegramWebhookService.registerWebhook();

            if (success) {
                return ResponseEntity.ok(java.util.Map.of(
                        "success", true,
                        "message", "Webhook registered successfully"));
            } else {
                return ResponseEntity.badRequest().body(java.util.Map.of(
                        "success", false,
                        "message", "Failed to register webhook"));
            }
        } catch (Exception e) {
            log.error("Ошибка при регистрации webhook: {}", e.getMessage(), e);
            return ResponseEntity.badRequest().body(java.util.Map.of(
                    "success", false,
                    "message", e.getMessage()));
        }
    }

    @DeleteMapping("/webhook")
    @Operation(summary = "Удаление webhook", description = "Удаляет webhook из Telegram Bot API")
    @ApiResponse(responseCode = "200", description = "Webhook удален успешно", content = @Content(mediaType = "application/json", examples = @ExampleObject(value = """
            {
                "success": true,
                "message": "Webhook deleted successfully"
            }
            """)))
    public ResponseEntity<Object> deleteWebhook() {
        try {
            boolean success = telegramWebhookService.deleteWebhook();

            return ResponseEntity.ok(java.util.Map.of(
                    "success", success,
                    "message", success ? "Webhook deleted successfully" : "Failed to delete webhook"));
        } catch (Exception e) {
            log.error("Ошибка при удалении webhook: {}", e.getMessage(), e);
            return ResponseEntity.ok(java.util.Map.of(
                    "success", false,
                    "message", e.getMessage()));
        }
    }
}