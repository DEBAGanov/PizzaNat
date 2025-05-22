package com.baganov.pizzanat.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.HashMap;
import java.util.Map;

/**
 * Контроллер для корневого пути
 */
@RestController
public class HomeController {

    /**
     * Обработчик для корневого пути
     * 
     * @return информация о сервисе
     */
    @GetMapping("/")
    public ResponseEntity<Map<String, String>> home() {
        Map<String, String> response = new HashMap<>();
        response.put("name", "PizzaNat API");
        response.put("version", "1.0.0");
        response.put("status", "running");

        return ResponseEntity.ok(response);
    }
}