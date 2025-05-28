package com.baganov.pizzanat.controller;

import com.baganov.pizzanat.model.dto.order.CreateOrderRequest;
import com.baganov.pizzanat.model.dto.order.OrderDTO;
import com.baganov.pizzanat.model.dto.payment.PaymentUrlResponse;
import com.baganov.pizzanat.service.OrderService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

@Slf4j
@RestController
@RequestMapping("/api/v1/orders")
@RequiredArgsConstructor
@Tag(name = "Orders", description = "API для работы с заказами")
public class OrderController {

    private final OrderService orderService;

    @PostMapping
    @Operation(summary = "Создание заказа", security = @SecurityRequirement(name = "bearerAuth"))
    public ResponseEntity<OrderDTO> createOrder(
            @Valid @RequestBody CreateOrderRequest request,
            HttpServletRequest httpRequest,
            Authentication authentication) {

        Integer userId = authentication != null
                ? ((com.baganov.pizzanat.entity.User) authentication.getPrincipal()).getId()
                : null;

        String sessionId = null;
        if (userId == null) {
            // Получаем сессию анонимного пользователя
            jakarta.servlet.http.Cookie[] cookies = httpRequest.getCookies();
            if (cookies != null) {
                for (jakarta.servlet.http.Cookie cookie : cookies) {
                    if ("CART_SESSION_ID".equals(cookie.getName())) {
                        sessionId = cookie.getValue();
                        break;
                    }
                }
            }
        }

        OrderDTO order = orderService.createOrder(userId, sessionId, request);
        return ResponseEntity.ok(order);
    }

    @GetMapping("/{orderId}")
    @Operation(summary = "Получение заказа по ID", security = @SecurityRequirement(name = "bearerAuth"))
    public ResponseEntity<OrderDTO> getOrderById(
            @Parameter(description = "ID заказа", required = true) @PathVariable Integer orderId,
            Authentication authentication) {

        Integer userId = authentication != null
                ? ((com.baganov.pizzanat.entity.User) authentication.getPrincipal()).getId()
                : null;

        OrderDTO order = orderService.getOrderById(orderId, userId);
        return ResponseEntity.ok(order);
    }

    @GetMapping("/{orderId}/payment-url")
    @Operation(summary = "Получение URL для оплаты заказа", description = "Создает и возвращает URL для перенаправления на страницу оплаты", security = @SecurityRequirement(name = "bearerAuth"))
    public ResponseEntity<PaymentUrlResponse> getPaymentUrl(
            @Parameter(description = "ID заказа", required = true) @PathVariable Integer orderId,
            Authentication authentication) {

        Integer userId = authentication != null
                ? ((com.baganov.pizzanat.entity.User) authentication.getPrincipal()).getId()
                : null;

        PaymentUrlResponse paymentUrlResponse = orderService.createPaymentUrl(orderId, userId);
        return ResponseEntity.ok(paymentUrlResponse);
    }

    @GetMapping
    @Operation(summary = "Получение списка заказов пользователя", security = @SecurityRequirement(name = "bearerAuth"))
    public ResponseEntity<Page<OrderDTO>> getUserOrders(
            @Parameter(description = "Номер страницы") @RequestParam(defaultValue = "0") int page,
            @Parameter(description = "Размер страницы") @RequestParam(defaultValue = "10") int size,
            Authentication authentication) {

        if (authentication == null) {
            return ResponseEntity.status(401).build();
        }

        Integer userId = ((com.baganov.pizzanat.entity.User) authentication.getPrincipal()).getId();

        PageRequest pageRequest = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "createdAt"));
        Page<OrderDTO> orders = orderService.getUserOrders(userId, pageRequest);

        return ResponseEntity.ok(orders);
    }
}