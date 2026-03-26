package com.baganov.pizzanat.controller;

import com.baganov.pizzanat.model.dto.auth.AuthResponse;
import com.baganov.pizzanat.model.dto.max.MaxWebAppAuthRequest;
import com.baganov.pizzanat.model.dto.max.MaxWebAppValidateRequest;
import com.baganov.pizzanat.service.MaxWebAppService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

/**
 * REST API контроллер для MAX Mini App
 *
 * Обеспечивает:
 * - Авторизацию пользователей через MAX WebApp initData
 * - Валидацию MAX initData
 *
 * Документация MAX: https://dev.max.ru/docs/webapps/introduction
 */
@Slf4j
@RestController
@RequestMapping("/api/v1/max-webapp")
@RequiredArgsConstructor
@Tag(name = "MAX WebApp", description = "API для MAX Mini App авторизации")
public class MaxWebAppController {

    private final MaxWebAppService maxWebAppService;

    /**
     * Авторизация через MAX WebApp initData
     *
     * @param request запрос с initData
     * @return AuthResponse с JWT токеном
     */
    @PostMapping("/auth")
    @Operation(
            summary = "Авторизация через MAX WebApp initData",
            description = "Принимает initData от MAX Mini App, валидирует и возвращает JWT токен"
    )
    public ResponseEntity<AuthResponse> authenticate(
            @Valid @RequestBody MaxWebAppAuthRequest request) {

        log.info("Получен запрос авторизации MAX WebApp");

        try {
            AuthResponse response = maxWebAppService.authenticateUser(request);
            log.info("Пользователь {} успешно авторизован через MAX WebApp",
                    response.getUserId());
            return ResponseEntity.ok(response);

        } catch (IllegalArgumentException e) {
            log.warn("Ошибка авторизации MAX WebApp: {}", e.getMessage());
            return ResponseEntity.badRequest().build();
        } catch (Exception e) {
            log.error("Ошибка авторизации MAX WebApp: {}", e.getMessage(), e);
            return ResponseEntity.internalServerError().build();
        }
    }

    /**
     * Валидация MAX initData
     *
     * @param request запрос с initData
     * @return true если валидация успешна
     */
    @PostMapping("/validate")
    @Operation(
            summary = "Валидация MAX initData",
            description = "Проверяет подлинность данных от MAX Mini App"
    )
    public ResponseEntity<Boolean> validateInitData(
            @Valid @RequestBody MaxWebAppValidateRequest request) {

        log.debug("Валидация MAX initData");

        try {
            boolean isValid = maxWebAppService.validateInitData(request.getInitDataRaw());
            return ResponseEntity.ok(isValid);

        } catch (Exception e) {
            log.error("Ошибка валидации MAX initData: {}", e.getMessage());
            return ResponseEntity.ok(false);
        }
    }

    /**
     * Получение информации о текущем пользователе
     *
     * @param authHeader заголовок авторизации
     * @return информация о пользователе
     */
    @GetMapping("/user-info")
    @Operation(
            summary = "Получение информации о текущем пользователе MAX",
            description = "Возвращает данные авторизованного пользователя"
    )
    public ResponseEntity<Object> getCurrentUserInfo(
            @RequestHeader(value = "Authorization", required = false) String authHeader) {

        // Этот эндпоинт будет использоваться для получения данных пользователя
        // после успешной авторизации через WebApp
        return ResponseEntity.ok().build();
    }
}
