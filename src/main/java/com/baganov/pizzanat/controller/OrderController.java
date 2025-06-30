package com.baganov.pizzanat.controller;

import com.baganov.pizzanat.model.dto.order.CreateOrderRequest;
import com.baganov.pizzanat.model.dto.order.OrderDTO;
import com.baganov.pizzanat.model.dto.payment.PaymentUrlResponse;
import com.baganov.pizzanat.service.OrderService;
import com.baganov.pizzanat.service.UserService;
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
    private final UserService userService;

    @PostMapping
    @Operation(summary = "Создание заказа", security = @SecurityRequirement(name = "bearerAuth"))
    public ResponseEntity<OrderDTO> createOrder(
            @Valid @RequestBody CreateOrderRequest request,
            HttpServletRequest httpRequest,
            Authentication authentication) {

        Integer userId = getUserId(authentication);

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

        Integer userId = getUserId(authentication);

        OrderDTO order = orderService.getOrderById(orderId, userId);
        return ResponseEntity.ok(order);
    }

    @GetMapping("/{orderId}/payment-url")
    @Operation(summary = "Получение URL для оплаты заказа", description = "Создает и возвращает URL для перенаправления на страницу оплаты", security = @SecurityRequirement(name = "bearerAuth"))
    public ResponseEntity<PaymentUrlResponse> getPaymentUrl(
            @Parameter(description = "ID заказа", required = true) @PathVariable Integer orderId,
            Authentication authentication) {

        Integer userId = getUserId(authentication);

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

        Integer userId = getUserId(authentication);

        PageRequest pageRequest = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "createdAt"));
        Page<OrderDTO> orders = orderService.getUserOrders(userId, pageRequest);

        return ResponseEntity.ok(orders);
    }

    private Integer getUserId(Authentication authentication) {
        log.debug("getUserId: authentication={}", authentication);
        if (authentication != null) {
            log.debug("getUserId: isAuthenticated={}, principal type={}, principal={}",
                    authentication.isAuthenticated(),
                    authentication.getPrincipal().getClass().getName(),
                    authentication.getPrincipal());
        }

        if (authentication != null && authentication.isAuthenticated()) {
            try {
                log.debug("Authentication principal type: {}", authentication.getPrincipal().getClass().getName());

                // Проверяем, является ли principal User объектом
                if (authentication.getPrincipal() instanceof com.baganov.pizzanat.entity.User) {
                    return ((com.baganov.pizzanat.entity.User) authentication.getPrincipal()).getId();
                }

                // Если это UserDetails, получаем username и ищем пользователя
                if (authentication
                        .getPrincipal() instanceof org.springframework.security.core.userdetails.UserDetails) {
                    String username = ((org.springframework.security.core.userdetails.UserDetails) authentication
                            .getPrincipal()).getUsername();
                    log.debug("Principal is UserDetails with username: {}", username);

                    // Получаем User по username
                    try {
                        com.baganov.pizzanat.entity.User user = userService.getUserByUsername(username);
                        log.debug("Found user by username: {}, id: {}", username, user.getId());
                        return user.getId();
                    } catch (Exception e) {
                        log.warn("Failed to find user by username: {}", username, e);
                        return null;
                    }
                }

                log.warn("Unknown principal type: {}", authentication.getPrincipal().getClass().getName());
                return null;
            } catch (Exception e) {
                log.warn("Failed to get user ID from authentication", e);
            }
        }
        log.debug("getUserId returning null: authentication={}, isAuthenticated={}",
                authentication != null, authentication != null ? authentication.isAuthenticated() : false);
        return null;
    }
}