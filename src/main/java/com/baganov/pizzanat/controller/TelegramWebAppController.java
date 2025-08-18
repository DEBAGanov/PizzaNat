/**
 * @file: TelegramWebAppController.java
 * @description: Контроллер для обработки Telegram Mini App авторизации и функций
 * @dependencies: TelegramWebAppService, AuthService, JwtService
 * @created: 2025-01-23
 */
package com.baganov.pizzanat.controller;

import com.baganov.pizzanat.model.dto.auth.AuthResponse;
import com.baganov.pizzanat.model.dto.telegram.TelegramWebAppAuthRequest;
import com.baganov.pizzanat.model.dto.telegram.TelegramWebAppEnhancedAuthRequest;
import com.baganov.pizzanat.service.TelegramWebAppService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import jakarta.validation.Valid;

@Slf4j
@RestController
@RequestMapping("/api/v1/telegram-webapp")
@RequiredArgsConstructor
@Tag(name = "Telegram WebApp", description = "API для Telegram Mini App")
public class TelegramWebAppController {

    private final TelegramWebAppService telegramWebAppService;

    @PostMapping("/auth")
    @Operation(summary = "Авторизация через Telegram WebApp initData")
    public ResponseEntity<AuthResponse> authenticateWebApp(
            @Valid @RequestBody TelegramWebAppAuthRequest request) {
        
        log.info("Получен запрос авторизации Telegram WebApp");
        
        try {
            AuthResponse response = telegramWebAppService.authenticateUser(request.getInitDataRaw());
            log.info("Пользователь {} успешно авторизован через Telegram WebApp", 
                    response.getUserId());
            return ResponseEntity.ok(response);
            
        } catch (Exception e) {
            log.error("Ошибка авторизации Telegram WebApp: {}", e.getMessage(), e);
            return ResponseEntity.badRequest().build();
        }
    }

    @PostMapping("/enhanced-auth")
    @Operation(summary = "Расширенная авторизация через Telegram WebApp с номером телефона")
    public ResponseEntity<AuthResponse> enhancedAuthenticateWebApp(
            @Valid @RequestBody TelegramWebAppEnhancedAuthRequest request) {
        
        log.info("Получен запрос расширенной авторизации Telegram WebApp с номером телефона");
        
        try {
            AuthResponse response = telegramWebAppService.enhancedAuthenticateUser(
                request.getInitDataRaw(), 
                request.getPhoneNumber(),
                request.getDeviceId()
            );
            log.info("Пользователь {} успешно авторизован через расширенную Telegram WebApp авторизацию", 
                    response.getUserId());
            return ResponseEntity.ok(response);
            
        } catch (Exception e) {
            log.error("Ошибка расширенной авторизации Telegram WebApp: {}", e.getMessage(), e);
            return ResponseEntity.badRequest().build();
        }
    }

    @PostMapping("/validate-init-data")
    @Operation(summary = "Валидация initData от Telegram WebApp")
    public ResponseEntity<Boolean> validateInitData(
            @Valid @RequestBody TelegramWebAppAuthRequest request) {
        
        log.debug("Валидация initData от Telegram WebApp");
        
        try {
            boolean isValid = telegramWebAppService.validateInitDataRaw(request.getInitDataRaw());
            return ResponseEntity.ok(isValid);
            
        } catch (Exception e) {
            log.error("Ошибка валидации initData: {}", e.getMessage());
            return ResponseEntity.ok(false);
        }
    }

    @GetMapping("/user-info")
    @Operation(summary = "Получение информации о текущем пользователе WebApp")
    public ResponseEntity<Object> getCurrentUserInfo(
            @Parameter(hidden = true) @RequestHeader(value = "Authorization", required = false) String authHeader) {
        
        // Этот эндпоинт будет использоваться для получения данных пользователя
        // после успешной авторизации через WebApp
        return ResponseEntity.ok().build();
    }
}
