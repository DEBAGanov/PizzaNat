/**
 * @file: SwaggerConfig.java
 * @description: Конфигурация SpringDoc для Swagger UI
 * @dependencies: springdoc-openapi
 * @created: 2025-05-24
 */
package com.baganov.pizzanat.config;

import org.springdoc.core.models.GroupedOpenApi;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Конфигурация Swagger UI с использованием SpringDoc
 */
@Configuration
public class SwaggerConfig {

        /**
         * Группировка API для публичных эндпоинтов
         */
        @Bean
        public GroupedOpenApi publicApi() {
                return GroupedOpenApi.builder()
                                .group("public-api")
                                .pathsToMatch("/api/v1/public/**", "/api/v1/auth/**")
                                .packagesToScan("com.baganov.pizzanat.controller")
                                .displayName("Публичное API")
                                .build();
        }

        /**
         * Группировка API для клиентских эндпоинтов
         */
        @Bean
        public GroupedOpenApi clientApi() {
                return GroupedOpenApi.builder()
                                .group("client-api")
                                .pathsToMatch("/api/v1/cart/**", "/api/v1/order/**", "/api/v1/user/**")
                                .packagesToScan("com.baganov.pizzanat.controller")
                                .displayName("Клиентское API")
                                .build();
        }

        /**
         * Группировка API для административных эндпоинтов
         */
        @Bean
        public GroupedOpenApi adminApi() {
                return GroupedOpenApi.builder()
                                .group("admin-api")
                                .pathsToMatch("/api/v1/admin/**")
                                .packagesToScan("com.baganov.pizzanat.controller")
                                .displayName("Административное API")
                                .build();
        }

        /**
         * Группировка API для всех эндпоинтов (все API)
         */
        @Bean
        public GroupedOpenApi allApi() {
                return GroupedOpenApi.builder()
                                .group("all-api")
                                .pathsToMatch("/api/v1/**")
                                .packagesToScan("com.baganov.pizzanat.controller")
                                .displayName("Все API")
                                .build();
        }
}