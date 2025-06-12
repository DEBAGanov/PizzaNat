/**
 * @file: UserController.java
 * @description: Контроллер для работы с профилем пользователя
 * @dependencies: Spring Security, UserService
 * @created: 2025-01-11
 */
package com.baganov.pizzanat.controller;

import com.baganov.pizzanat.entity.User;
import com.baganov.pizzanat.model.dto.user.UserProfileResponse;
import com.baganov.pizzanat.service.UserService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Slf4j
@RestController
@RequestMapping("/api/v1/user")
@RequiredArgsConstructor
@Tag(name = "User Profile", description = "API для работы с профилем пользователя")
public class UserController {

    private final UserService userService;

    @GetMapping("/profile")
    @Operation(summary = "Получение профиля текущего пользователя", description = "Возвращает данные профиля аутентифицированного пользователя", security = @SecurityRequirement(name = "bearerAuth"))
    public ResponseEntity<UserProfileResponse> getCurrentUserProfile(Authentication authentication) {
        if (authentication == null) {
            return ResponseEntity.status(401).build();
        }

        User user = (User) authentication.getPrincipal();
        log.info("Запрос профиля пользователя: {}", user.getUsername());

        UserProfileResponse profile = UserProfileResponse.builder()
                .id(user.getId())
                .username(user.getUsername())
                .email(user.getEmail())
                .firstName(user.getFirstName())
                .lastName(user.getLastName())
                .phone(user.getPhone())
                .phoneNumber(user.getPhoneNumber())
                .isPhoneVerified(user.getIsPhoneVerified())
                .telegramId(user.getTelegramId())
                .telegramUsername(user.getTelegramUsername())
                .isTelegramVerified(user.getIsTelegramVerified())
                .isActive(user.isActive())
                .createdAt(user.getCreatedAt())
                .updatedAt(user.getUpdatedAt())
                .displayName(user.getDisplayName())
                .primaryIdentifier(user.getPrimaryIdentifier())
                .hasVerifiedAuthentication(user.hasVerifiedAuthentication())
                .build();

        return ResponseEntity.ok(profile);
    }

    @GetMapping("/me")
    @Operation(summary = "Получение профиля текущего пользователя (альтернативный endpoint)", description = "Возвращает данные профиля аутентифицированного пользователя", security = @SecurityRequirement(name = "bearerAuth"))
    public ResponseEntity<UserProfileResponse> getCurrentUser(Authentication authentication) {
        return getCurrentUserProfile(authentication);
    }
}