package com.baganov.pizzanat.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.HashMap;
import java.util.Map;

/**
 * Контроллер для проверки состояния сервиса
 */
@RestController
public class HomeController {

    /**
     * Обработчик для проверки состояния API
     * 
     * @return информация о сервисе
     */
    @GetMapping("/api/health")
    public ResponseEntity<Map<String, String>> home() {
        Map<String, String> response = new HashMap<>();
        response.put("name", "PizzaNat API");
        response.put("version", "1.0.0");
        response.put("status", "running");

        return ResponseEntity.ok(response);
    }
}