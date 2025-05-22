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
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@Slf4j
@RestController
@RequestMapping("/api/v1/admin/orders")
@RequiredArgsConstructor
@PreAuthorize("hasRole('ADMIN')")
@Tag(name = "Admin Orders", description = "API для администрирования заказов")
public class AdminOrderController {

    private final OrderService orderService;

    @GetMapping
    @Operation(summary = "Получение всех заказов", security = @SecurityRequirement(name = "bearerAuth"))
    public ResponseEntity<Page<OrderDTO>> getAllOrders(
            @Parameter(description = "Номер страницы") @RequestParam(defaultValue = "0") int page,
            @Parameter(description = "Размер страницы") @RequestParam(defaultValue = "10") int size,
            @Parameter(description = "Сортировка (createdAt, updatedAt, status)") @RequestParam(defaultValue = "createdAt") String sort,
            @Parameter(description = "Направление сортировки") @RequestParam(defaultValue = "DESC") String direction) {
        Sort.Direction sortDirection = Sort.Direction.fromString(direction);
        PageRequest pageRequest = PageRequest.of(page, size, Sort.by(sortDirection, sort));

        Page<OrderDTO> orders = orderService.getAllOrders(pageRequest);
        return ResponseEntity.ok(orders);
    }

    @GetMapping("/{orderId}")
    @Operation(summary = "Получение заказа по ID", security = @SecurityRequirement(name = "bearerAuth"))
    public ResponseEntity<OrderDTO> getOrderById(
            @Parameter(description = "ID заказа", required = true) @PathVariable Integer orderId) {
        OrderDTO order = orderService.getOrderById(orderId, null);
        return ResponseEntity.ok(order);
    }

    @PutMapping("/{orderId}/status")
    @Operation(summary = "Обновление статуса заказа", security = @SecurityRequirement(name = "bearerAuth"))
    public ResponseEntity<OrderDTO> updateOrderStatus(
            @Parameter(description = "ID заказа", required = true) @PathVariable Integer orderId,
            @Valid @RequestBody UpdateOrderStatusRequest request) {
        OrderDTO order = orderService.updateOrderStatus(orderId, request.getStatus());
        return ResponseEntity.ok(order);
    }
}