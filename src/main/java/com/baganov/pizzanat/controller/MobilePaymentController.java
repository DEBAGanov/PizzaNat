/**
 * @file: MobilePaymentController.java
 * @description: Специальный REST API контроллер для интеграции платежей ЮKassa с мобильным приложением
 * @dependencies: YooKassaPaymentService, OrderService, SecurityContext
 * @created: 2025-01-26
 */
package com.baganov.pizzanat.controller;

import com.baganov.pizzanat.model.dto.payment.CreatePaymentRequest;
import com.baganov.pizzanat.model.dto.payment.PaymentResponse;
import com.baganov.pizzanat.model.dto.payment.SbpBankInfo;
import com.baganov.pizzanat.service.YooKassaPaymentService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import jakarta.validation.Valid;
import java.util.List;
import java.util.Map;

/**
 * REST API контроллер для интеграции платежей ЮKassa с мобильным приложением
 * Предоставляет упрощенный API специально адаптированный для Android/iOS
 */
@RestController
@RequestMapping("/api/v1/mobile/payments")
@RequiredArgsConstructor
@Slf4j
@Tag(name = "Mobile Payments", description = "API для интеграции платежей ЮKassa с мобильным приложением")
@ConditionalOnProperty(name = "yookassa.enabled", havingValue = "true")
public class MobilePaymentController {

    private final YooKassaPaymentService paymentService;

    /**
     * Создание платежа для мобильного приложения
     * Упрощенный endpoint с минимальными параметрами
     */
    @PostMapping("/create")
    @PreAuthorize("hasRole('USER')")
    @Operation(summary = "Создать платеж для мобильного приложения", description = "Создает новый платеж ЮKassa с упрощенными параметрами для мобильного приложения")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Платеж успешно создан"),
            @ApiResponse(responseCode = "400", description = "Некорректные данные запроса"),
            @ApiResponse(responseCode = "401", description = "Требуется аутентификация"),
            @ApiResponse(responseCode = "500", description = "Внутренняя ошибка сервера")
    })
    public ResponseEntity<Map<String, Object>> createMobilePayment(
            @Valid @RequestBody CreatePaymentRequest request) {

        log.info("Создание платежа для мобильного приложения. Заказ: {}, Сумма: {}",
                request.getOrderId(), request.getAmount());

        try {
            PaymentResponse payment = paymentService.createPayment(request);

            // Упрощенный ответ для мобильного приложения
            Map<String, Object> response = Map.of(
                    "success", true,
                    "paymentId", payment.getId(),
                    "paymentUrl", payment.getConfirmationUrl() != null ? payment.getConfirmationUrl() : "",
                    "status", payment.getStatus().toString(),
                    "amount", payment.getAmount(),
                    "currency", payment.getCurrency() != null ? payment.getCurrency() : "RUB",
                    "orderId", request.getOrderId(),
                    "description", "Заказ #" + request.getOrderId(),
                    "expiresAt", payment.getCreatedAt());

            log.info("Платеж для мобильного приложения создан успешно. ID: {}", payment.getId());
            return ResponseEntity.ok(response);

        } catch (Exception e) {
            log.error("Ошибка создания платежа для мобильного приложения: {}", e.getMessage(), e);

            Map<String, Object> errorResponse = Map.of(
                    "success", false,
                    "error", "Не удалось создать платеж",
                    "message", e.getMessage());

            return ResponseEntity.badRequest().body(errorResponse);
        }
    }

    /**
     * Получение статуса платежа для мобильного приложения
     */
    @GetMapping("/{paymentId}/status")
    @PreAuthorize("hasRole('USER')")
    @Operation(summary = "Получить статус платежа", description = "Возвращает текущий статус платежа в упрощенном формате для мобильного приложения")
    public ResponseEntity<Map<String, Object>> getPaymentStatus(
            @Parameter(description = "ID платежа") @PathVariable Long paymentId) {

        log.info("Запрос статуса платежа для мобильного приложения: {}", paymentId);

        try {
            PaymentResponse payment = paymentService.getPayment(paymentId);

            Map<String, Object> response = Map.of(
                    "success", true,
                    "paymentId", payment.getId(),
                    "status", payment.getStatus().toString(),
                    "paid", payment.isSuccessful(),
                    "amount", payment.getAmount(),
                    "orderId", payment.getOrderId(),
                    "updatedAt", payment.getCreatedAt());

            return ResponseEntity.ok(response);

        } catch (Exception e) {
            log.error("Ошибка получения статуса платежа {}: {}", paymentId, e.getMessage());

            Map<String, Object> errorResponse = Map.of(
                    "success", false,
                    "error", "Платеж не найден",
                    "message", e.getMessage());

            return ResponseEntity.badRequest().body(errorResponse);
        }
    }

    /**
     * Получение списка банков СБП для мобильного приложения
     */
    @GetMapping("/sbp/banks")
    @Operation(summary = "Получить список банков СБП", description = "Возвращает список банков, поддерживающих СБП, в формате для мобильного приложения")
    public ResponseEntity<Map<String, Object>> getSbpBanks() {
        log.info("Запрос списка банков СБП для мобильного приложения");

        try {
            List<SbpBankInfo> banks = paymentService.getSbpBanks();

            Map<String, Object> response = Map.of(
                    "success", true,
                    "banks", banks,
                    "count", banks.size());

            return ResponseEntity.ok(response);

        } catch (Exception e) {
            log.error("Ошибка получения списка банков СБП: {}", e.getMessage());

            Map<String, Object> errorResponse = Map.of(
                    "success", false,
                    "error", "Не удалось получить список банков",
                    "message", e.getMessage(),
                    "banks", List.of());

            return ResponseEntity.ok(errorResponse);
        }
    }

    /**
     * Отмена платежа для мобильного приложения
     */
    @PostMapping("/{paymentId}/cancel")
    @PreAuthorize("hasRole('USER')")
    @Operation(summary = "Отменить платеж", description = "Отменяет платеж в статусе ожидания для мобильного приложения")
    public ResponseEntity<Map<String, Object>> cancelPayment(
            @Parameter(description = "ID платежа") @PathVariable Long paymentId) {

        log.info("Отмена платежа для мобильного приложения: {}", paymentId);

        try {
            PaymentResponse payment = paymentService.cancelPayment(paymentId);

            Map<String, Object> response = Map.of(
                    "success", true,
                    "paymentId", payment.getId(),
                    "status", payment.getStatus().toString(),
                    "cancelled", true,
                    "message", "Платеж успешно отменен");

            return ResponseEntity.ok(response);

        } catch (Exception e) {
            log.error("Ошибка отмены платежа {}: {}", paymentId, e.getMessage());

            Map<String, Object> errorResponse = Map.of(
                    "success", false,
                    "error", "Не удалось отменить платеж",
                    "message", e.getMessage(),
                    "cancelled", false);

            return ResponseEntity.badRequest().body(errorResponse);
        }
    }

    /**
     * Проверка работоспособности платежной системы для мобильного приложения
     */
    @GetMapping("/health")
    @Operation(summary = "Проверка работоспособности", description = "Проверяет состояние платежной системы для мобильного приложения")
    public ResponseEntity<Map<String, Object>> healthCheck() {
        log.debug("Проверка работоспособности платежной системы для мобильного приложения");

        try {
            // Простая проверка доступности сервиса
            Map<String, Object> response = Map.of(
                    "success", true,
                    "status", "healthy",
                    "service", "YooKassa Mobile API",
                    "timestamp", System.currentTimeMillis());

            return ResponseEntity.ok(response);

        } catch (Exception e) {
            log.error("Ошибка проверки работоспособности: {}", e.getMessage());

            Map<String, Object> errorResponse = Map.of(
                    "success", false,
                    "status", "unhealthy",
                    "error", e.getMessage(),
                    "timestamp", System.currentTimeMillis());

            return ResponseEntity.status(500).body(errorResponse);
        }
    }
}