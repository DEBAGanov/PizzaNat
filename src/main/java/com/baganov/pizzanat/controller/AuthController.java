package com.baganov.pizzanat.controller;

import com.baganov.pizzanat.model.dto.auth.AuthRequest;
import com.baganov.pizzanat.model.dto.auth.AuthResponse;
import com.baganov.pizzanat.model.dto.auth.RegisterRequest;
import com.baganov.pizzanat.service.AuthService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Slf4j
@RestController
@RequestMapping("/api/v1/auth")
@RequiredArgsConstructor
@Tag(name = "Authentication", description = "API для аутентификации и регистрации пользователей")
public class AuthController {

    private final AuthService authService;

    @GetMapping("/test")
    public ResponseEntity<String> test() {
        log.info("Тестовый запрос к /api/v1/auth/test выполнен успешно");
        return ResponseEntity.ok("API аутентификации доступно");
    }

    @PostMapping("/register")
    @Operation(summary = "Регистрация нового пользователя")
    public ResponseEntity<AuthResponse> register(@Valid @RequestBody RegisterRequest request) {
        log.info("Registering new user: {}", request.getUsername());
        try {
            AuthResponse response = authService.register(request);
            log.info("Пользователь {} успешно зарегистрирован", request.getUsername());
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            log.error("Ошибка при регистрации пользователя {}: {}", request.getUsername(), e.getMessage());
            throw e;
        }
    }

    @PostMapping("/login")
    @Operation(summary = "Аутентификация пользователя")
    public ResponseEntity<AuthResponse> authenticate(@Valid @RequestBody AuthRequest request) {
        log.info("Попытка аутентификации пользователя: {}", request.getUsername());
        try {
            AuthResponse response = authService.authenticate(request);
            log.info("Пользователь {} успешно аутентифицирован", request.getUsername());
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            log.error("Ошибка при аутентификации пользователя {}: {}", request.getUsername(), e.getMessage());
            throw e;
        }
    }
}