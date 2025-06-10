/**
 * @file: AdminOrderController.java
 * @description: Административный API для управления заказами
 * @dependencies: Spring Web, Spring Security
 * @created: 2025-05-31
 */
package com.baganov.pizzanat.controller;

import com.baganov.pizzanat.model.dto.order.OrderDTO;
import com.baganov.pizzanat.model.dto.order.UpdateOrderStatusRequest;
import com.baganov.pizzanat.service.OrderService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@Slf4j
@RestController
@RequestMapping("/api/v1/admin/orders")
@RequiredArgsConstructor
@Tag(name = "Admin Orders", description = "API для администрирования заказов")
public class AdminOrderController {

    private final OrderService orderService;

    @GetMapping
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Получение всех заказов", security = @SecurityRequirement(name = "bearerAuth"))
    public ResponseEntity<Page<OrderDTO>> getAllOrders(
            @PageableDefault(size = 20) Pageable pageable) {
        log.info("Администратор запрашивает список всех заказов");
        Page<OrderDTO> orders = orderService.getAllOrders(pageable);
        return ResponseEntity.ok(orders);
    }

    @GetMapping("/{orderId}")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Получение заказа по ID", security = @SecurityRequirement(name = "bearerAuth"))
    public ResponseEntity<OrderDTO> getOrderById(
            @Parameter(description = "ID заказа", required = true) @PathVariable Integer orderId) {
        log.info("Администратор запрашивает заказ с ID: {}", orderId);
        OrderDTO order = orderService.getOrderById(orderId, null); // null - административный доступ
        return ResponseEntity.ok(order);
    }

    @PutMapping("/{orderId}/status")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Обновление статуса заказа (с Telegram уведомлением)", description = "Обновляет статус заказа. Поддерживаемые статусы: PENDING, CONFIRMED, PREPARING, READY, DELIVERING, DELIVERED, CANCELLED", security = @SecurityRequirement(name = "bearerAuth"))
    public ResponseEntity<OrderDTO> updateOrderStatus(
            @Parameter(description = "ID заказа", required = true) @PathVariable Integer orderId,
            @Valid @RequestBody UpdateOrderStatusRequest request) {
        log.info("Администратор изменяет статус заказа {} на '{}'", orderId, request.getStatusName());
        OrderDTO order = orderService.updateOrderStatus(orderId, request.getStatusName());
        return ResponseEntity.ok(order);
    }
}