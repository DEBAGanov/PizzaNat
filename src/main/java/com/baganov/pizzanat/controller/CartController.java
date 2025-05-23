package com.baganov.pizzanat.controller;

import com.baganov.pizzanat.model.dto.cart.AddToCartRequest;
import com.baganov.pizzanat.model.dto.cart.CartDTO;
import com.baganov.pizzanat.model.dto.cart.UpdateCartItemRequest;
import com.baganov.pizzanat.service.CartService;
import com.baganov.pizzanat.service.UserService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@Slf4j
@RestController
@RequestMapping("/api/v1/cart")
@RequiredArgsConstructor
@Tag(name = "Cart", description = "API для работы с корзиной")
public class CartController {

    private final CartService cartService;
    private final UserService userService;
    private static final String SESSION_ID_COOKIE = "CART_SESSION_ID";

    @GetMapping
    @Operation(summary = "Получение корзины")
    public ResponseEntity<CartDTO> getCart(
            HttpServletRequest request,
            Authentication authentication) {

        Integer userId = getUserId(authentication);
        String sessionId = getOrCreateSessionId(request);

        log.debug("getCart: userId={}, sessionId={}, authentication={}", userId, sessionId,
                authentication != null ? authentication.getName() : "null");

        CartDTO cart = cartService.getCart(sessionId, userId);
        return ResponseEntity.ok(cart);
    }

    @PostMapping("/items")
    @Operation(summary = "Добавление товара в корзину")
    public ResponseEntity<CartDTO> addToCart(
            @Valid @RequestBody AddToCartRequest addToCartRequest,
            HttpServletRequest request,
            Authentication authentication) {

        Integer userId = getUserId(authentication);
        String sessionId = getOrCreateSessionId(request);

        log.debug("addToCart: userId={}, sessionId={}, authentication={}", userId, sessionId,
                authentication != null ? authentication.getName() : "null");

        CartDTO cart = cartService.addToCart(
                sessionId,
                userId,
                addToCartRequest.getProductId(),
                addToCartRequest.getQuantity());

        return ResponseEntity.ok(cart);
    }

    @PutMapping("/items/{productId}")
    @Operation(summary = "Обновление количества товара в корзине")
    public ResponseEntity<CartDTO> updateCartItem(
            @Parameter(description = "ID продукта", required = true) @PathVariable Integer productId,
            @Valid @RequestBody UpdateCartItemRequest updateRequest,
            HttpServletRequest request,
            Authentication authentication) {

        Integer userId = getUserId(authentication);
        String sessionId = getOrCreateSessionId(request);

        CartDTO cart = cartService.updateCartItem(sessionId, userId, productId, updateRequest.getQuantity());
        return ResponseEntity.ok(cart);
    }

    @DeleteMapping("/items/{productId}")
    @Operation(summary = "Удаление товара из корзины")
    public ResponseEntity<CartDTO> removeFromCart(
            @Parameter(description = "ID продукта", required = true) @PathVariable Integer productId,
            HttpServletRequest request,
            Authentication authentication) {

        Integer userId = getUserId(authentication);
        String sessionId = getOrCreateSessionId(request);

        CartDTO cart = cartService.removeFromCart(sessionId, userId, productId);
        return ResponseEntity.ok(cart);
    }

    @DeleteMapping
    @Operation(summary = "Очистка корзины")
    public ResponseEntity<Void> clearCart(
            HttpServletRequest request,
            Authentication authentication) {

        Integer userId = getUserId(authentication);
        String sessionId = getOrCreateSessionId(request);

        cartService.clearCart(sessionId, userId);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/merge")
    @Operation(summary = "Объединение анонимной корзины с корзиной пользователя", security = @SecurityRequirement(name = "bearerAuth"))
    public ResponseEntity<CartDTO> mergeCart(
            HttpServletRequest request,
            Authentication authentication) {

        if (authentication == null) {
            return ResponseEntity.badRequest().build();
        }

        Integer userId = getUserId(authentication);
        String sessionId = getSessionId(request);

        if (sessionId != null && userId != null) {
            cartService.mergeAnonymousCartWithUserCart(sessionId, userId);
            return ResponseEntity.ok(cartService.getCart(null, userId));
        }

        return ResponseEntity.badRequest().build();
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
                if (authentication.getPrincipal() instanceof com.baganov.pizzanat.model.entity.User) {
                    return ((com.baganov.pizzanat.model.entity.User) authentication.getPrincipal()).getId();
                }

                // Если это UserDetails, получаем username и ищем пользователя
                if (authentication
                        .getPrincipal() instanceof org.springframework.security.core.userdetails.UserDetails) {
                    String username = ((org.springframework.security.core.userdetails.UserDetails) authentication
                            .getPrincipal()).getUsername();
                    log.debug("Principal is UserDetails with username: {}", username);

                    // Получаем User по username
                    try {
                        com.baganov.pizzanat.model.entity.User user = userService.getUserByUsername(username);
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

    private String getSessionId(HttpServletRequest request) {
        Cookie[] cookies = request.getCookies();
        if (cookies != null) {
            for (Cookie cookie : cookies) {
                if (SESSION_ID_COOKIE.equals(cookie.getName())) {
                    return cookie.getValue();
                }
            }
        }
        return null;
    }

    private String getOrCreateSessionId(HttpServletRequest request) {
        String sessionId = getSessionId(request);
        return sessionId != null ? sessionId : UUID.randomUUID().toString();
    }
}