/**
 * @file: HomeController.java
 * @description: Контроллер для проверки состояния сервиса и перенаправления на Swagger
 * @dependencies: Spring Web
 * @created: 2025-06-10
 */
package com.baganov.pizzanat.controller;

import io.swagger.v3.oas.annotations.Hidden;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.HashMap;
import java.util.Map;

/**
 * Контроллер для проверки состояния сервиса и навигации
 */
@RestController
@Tag(name = "System", description = "Системные эндпоинты")
public class HomeController {

    /**
     * Перенаправление с корневого пути на Swagger UI
     */
    @GetMapping("/")
    @Hidden
    public ResponseEntity<Void> redirectToSwagger() {
        HttpHeaders headers = new HttpHeaders();
        headers.add("Location", "/swagger-ui.html");
        return ResponseEntity.status(302).headers(headers).build();
    }

    /**
     * Обработчик для проверки состояния API
     * 
     * @return информация о сервисе
     */
    @GetMapping("/api/health")
    @Operation(summary = "Проверка состояния сервиса", description = "Возвращает информацию о состоянии API")
    public ResponseEntity<Map<String, String>> health() {
        Map<String, String> response = new HashMap<>();
        response.put("name", "PizzaNat API");
        response.put("version", "1.0.0");
        response.put("status", "running");
        response.put("swagger", "/swagger-ui.html");

        return ResponseEntity.ok(response);
    }
}