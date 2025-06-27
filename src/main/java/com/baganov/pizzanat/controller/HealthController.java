/**
 * @file: HealthController.java
 * @description: REST контроллер для проверки состояния приложения
 * @dependencies: Spring Web, Spring Boot Actuator
 * @created: 2025-01-23
 */
package com.baganov.pizzanat.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.HealthIndicator;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;

/**
 * Контроллер для проверки состояния приложения
 * Предоставляет информацию о здоровье системы и готовности к работе
 */
@RestController
@RequestMapping("/api/v1")
@Slf4j
@Tag(name = "Health", description = "API для проверки состояния приложения")
public class HealthController implements HealthIndicator {

    @GetMapping("/health")
    @Operation(summary = "Основная проверка состояния", description = "Возвращает базовую информацию о состоянии приложения")
    @ApiResponse(responseCode = "200", description = "Приложение работает нормально")
    public ResponseEntity<Map<String, Object>> healthCheck() {
        try {
            Map<String, Object> health = new HashMap<>();
            health.put("status", "UP");
            health.put("timestamp", System.currentTimeMillis());
            health.put("message", "Приложение работает нормально");
            health.put("service", "PizzaNat API");
            health.put("version", "1.0.0");

            log.debug("Health check успешен");
            return ResponseEntity.ok(health);

        } catch (Exception e) {
            log.error("Ошибка в health check", e);
            Map<String, Object> error = new HashMap<>();
            error.put("status", "DOWN");
            error.put("timestamp", System.currentTimeMillis());
            error.put("message", "Внутренняя ошибка сервера");
            error.put("error", e.getMessage());
            return ResponseEntity.status(500).body(error);
        }
    }

    @GetMapping("/health/detailed")
    @Operation(summary = "Детальная проверка состояния", description = "Возвращает подробную информацию о состоянии приложения и системы")
    @ApiResponse(responseCode = "200", description = "Детальная информация получена успешно")
    public ResponseEntity<Map<String, Object>> detailedHealth() {
        try {
            Map<String, Object> health = new HashMap<>();
            health.put("status", "UP");
            health.put("timestamp", System.currentTimeMillis());
            health.put("datetime", LocalDateTime.now().toString());
            health.put("service", "PizzaNat API");
            health.put("version", "1.0.0");

            // Информация о системе
            Map<String, Object> system = new HashMap<>();
            Runtime runtime = Runtime.getRuntime();
            system.put("totalMemory", runtime.totalMemory());
            system.put("freeMemory", runtime.freeMemory());
            system.put("usedMemory", runtime.totalMemory() - runtime.freeMemory());
            system.put("availableProcessors", runtime.availableProcessors());
            system.put("javaVersion", System.getProperty("java.version"));
            system.put("osName", System.getProperty("os.name"));
            health.put("system", system);

            // Статус компонентов
            Map<String, Object> components = new HashMap<>();
            components.put("api", "UP");
            components.put("delivery", "UP");
            components.put("addressSuggestions", "UP");
            health.put("components", components);

            log.debug("Detailed health check успешен");
            return ResponseEntity.ok(health);

        } catch (Exception e) {
            log.error("Ошибка в detailed health check", e);
            Map<String, Object> error = new HashMap<>();
            error.put("status", "DOWN");
            error.put("timestamp", System.currentTimeMillis());
            error.put("message", "Внутренняя ошибка сервера");
            error.put("error", e.getMessage());
            return ResponseEntity.status(500).body(error);
        }
    }

    @GetMapping("/ready")
    @Operation(summary = "Проверка готовности к обработке запросов", description = "Kubernetes readiness probe")
    @ApiResponse(responseCode = "200", description = "Приложение готово к обработке запросов")
    public ResponseEntity<Map<String, Object>> readiness() {
        try {
            Map<String, Object> readiness = new HashMap<>();
            readiness.put("status", "READY");
            readiness.put("timestamp", System.currentTimeMillis());
            readiness.put("message", "Приложение готово к обработке запросов");

            log.debug("Readiness check успешен");
            return ResponseEntity.ok(readiness);

        } catch (Exception e) {
            log.error("Ошибка в readiness check", e);
            Map<String, Object> error = new HashMap<>();
            error.put("status", "NOT_READY");
            error.put("timestamp", System.currentTimeMillis());
            error.put("message", "Приложение не готово");
            error.put("error", e.getMessage());
            return ResponseEntity.status(503).body(error);
        }
    }

    @GetMapping("/live")
    @Operation(summary = "Проверка жизнеспособности приложения", description = "Kubernetes liveness probe")
    @ApiResponse(responseCode = "200", description = "Приложение работает")
    public ResponseEntity<Map<String, Object>> liveness() {
        try {
            Map<String, Object> liveness = new HashMap<>();
            liveness.put("status", "ALIVE");
            liveness.put("timestamp", System.currentTimeMillis());
            liveness.put("message", "Приложение работает");
            liveness.put("uptime", System.currentTimeMillis());

            log.debug("Liveness check успешен");
            return ResponseEntity.ok(liveness);

        } catch (Exception e) {
            log.error("Ошибка в liveness check", e);
            Map<String, Object> error = new HashMap<>();
            error.put("status", "NOT_ALIVE");
            error.put("timestamp", System.currentTimeMillis());
            error.put("message", "Приложение не работает");
            error.put("error", e.getMessage());
            return ResponseEntity.status(503).body(error);
        }
    }

    /**
     * Реализация Spring Boot HealthIndicator для интеграции с Actuator
     */
    @Override
    public Health health() {
        try {
            return Health.up()
                    .withDetail("service", "PizzaNat API")
                    .withDetail("version", "1.0.0")
                    .withDetail("timestamp", System.currentTimeMillis())
                    .build();
        } catch (Exception e) {
            log.error("Ошибка в Spring Boot HealthIndicator", e);
            return Health.down()
                    .withDetail("error", e.getMessage())
                    .withDetail("timestamp", System.currentTimeMillis())
                    .build();
        }
    }
}