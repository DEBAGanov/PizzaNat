/**
 * @file: SwaggerController.java
 * @description: Контроллер для обработки запросов к Swagger UI
 * @dependencies: Spring Web, SpringDoc OpenAPI
 * @created: 2025-05-24
 */
package com.baganov.pizzanat.controller;

import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.nio.charset.StandardCharsets;

/**
 * Контроллер для обработки запросов к Swagger UI
 */
@RestController
public class SwaggerController {

    /**
     * Перенаправление с корневого пути на Swagger UI
     */
    @GetMapping("/")
    public ResponseEntity<Void> redirectToSwagger() {
        HttpHeaders headers = new HttpHeaders();
        headers.add("Location", "/swagger-ui/index.html");
        return ResponseEntity.status(302).headers(headers).build();
    }

    /**
     * Альтернативный путь для доступа к Swagger UI
     */
    @GetMapping("/swagger")
    public ResponseEntity<Void> redirectToSwaggerAlternative() {
        HttpHeaders headers = new HttpHeaders();
        headers.add("Location", "/swagger-ui/index.html");
        return ResponseEntity.status(302).headers(headers).build();
    }

    /**
     * Страница справки по API
     */
    @GetMapping(value = "/api-help", produces = MediaType.TEXT_HTML_VALUE)
    public ResponseEntity<String> apiHelp() {
        String html = """
                <!DOCTYPE html>
                <html>
                <head>
                    <title>PizzaNat API - Справка</title>
                    <style>
                        body { font-family: Arial, sans-serif; margin: 20px; }
                        h1 { color: #333; }
                        .endpoint { background-color: #f5f5f5; padding: 10px; margin: 10px 0; border-radius: 5px; }
                        .method { font-weight: bold; color: #007bff; }
                        .status { padding: 5px 10px; margin: 5px 0; border-radius: 3px; }
                        .success { background-color: #d4edda; color: #155724; }
                        .info { background-color: #d1ecf1; color: #0c5460; }
                    </style>
                </head>
                <body>
                    <h1>PizzaNat API - Справка</h1>
                    <p>Добро пожаловать в API сервиса заказа пиццы PizzaNat!</p>

                    <div class="status success">✅ Swagger UI полностью настроен и функционален</div>
                    <div class="status info">📊 Доступно 24 эндпоинта в 4 группах API</div>

                    <h2>Доступные ресурсы:</h2>
                    <div class="endpoint">
                        <span class="method">GET</span> <a href="/swagger-ui/index.html" target="_blank">/swagger-ui/index.html</a> - 🎯 Интерактивная документация API
                    </div>
                    <div class="endpoint">
                        <span class="method">GET</span> <a href="/v3/api-docs/swagger-config" target="_blank">/v3/api-docs/swagger-config</a> - ⚙️ Конфигурация Swagger
                    </div>
                    <div class="endpoint">
                        <span class="method">GET</span> <a href="/v3/api-docs" target="_blank">/v3/api-docs</a> - 📋 OpenAPI спецификация в JSON формате
                    </div>
                    <div class="endpoint">
                        <span class="method">GET</span> <a href="/api/health" target="_blank">/api/health</a> - 💚 Проверка состояния сервиса
                    </div>

                    <h2>Группы API в Swagger UI:</h2>
                    <ul>
                        <li><strong>Все API</strong> (24 эндпоинта) - Полная документация всех endpoint'ов</li>
                        <li><strong>Публичное API</strong> - Регистрация и аутентификация</li>
                        <li><strong>Клиентское API</strong> - Корзина, заказы, пользователи</li>
                        <li><strong>Административное API</strong> - Управление заказами</li>
                    </ul>

                    <h2>Основные категории эндпоинтов:</h2>
                    <ul>
                        <li>🔐 <strong>Аутентификация</strong>: /api/v1/auth/*</li>
                        <li>📂 <strong>Категории</strong>: /api/v1/categories/*</li>
                        <li>🍕 <strong>Продукты</strong>: /api/v1/products/*</li>
                        <li>🛒 <strong>Корзина</strong>: /api/v1/cart/*</li>
                        <li>📋 <strong>Заказы</strong>: /api/v1/orders/*</li>
                        <li>💳 <strong>Платежи</strong>: /api/v1/payments/*</li>
                        <li>⚡ <strong>Админ-панель</strong>: /api/v1/admin/*</li>
                    </ul>

                    <p><strong>👉 Для начала работы с API перейдите в <a href="/swagger-ui/index.html" target="_blank">Swagger UI</a>.</strong></p>

                    <h3>Быстрые ссылки для проверки:</h3>
                    <ul>
                        <li><a href="/v3/api-docs/all-api" target="_blank">Документация "Все API"</a></li>
                        <li><a href="/v3/api-docs/public-api" target="_blank">Документация "Публичное API"</a></li>
                        <li><a href="/api/v1/categories" target="_blank">Тест API: Список категорий</a></li>
                        <li><a href="/api/v1/products" target="_blank">Тест API: Список продуктов</a></li>
                    </ul>
                </body>
                </html>
                """;

        return ResponseEntity.ok()
                .contentType(MediaType.TEXT_HTML)
                .header(HttpHeaders.CONTENT_ENCODING, StandardCharsets.UTF_8.name())
                .body(html);
    }
}