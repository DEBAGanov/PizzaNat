/**
 * @file: OpenApiConfig.java
 * @description: Простая стандартная конфигурация OpenAPI и Swagger UI
 * @dependencies: SpringDoc OpenAPI
 * @created: 2025-06-10
 */
package com.baganov.pizzanat.config;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.info.License;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import io.swagger.v3.oas.models.servers.Server;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.List;

/**
 * Простая стандартная конфигурация OpenAPI 3.0
 * Использует только базовые настройки SpringDoc без кастомизации
 */
@Configuration
public class OpenApiConfig {

        @Bean
        public OpenAPI pizzaNatOpenAPI() {
                return new OpenAPI()
                                .openapi("3.0.1")
                                .info(new Info()
                                                .title("Application API")
                                                .description("The project to support your fit")
                                                .version("0.0.1-SNAPSHOT")
                                                .contact(new Contact()
                                                                .name("Contact Roman"))
                                                .license(new License()
                                                                .name("Apache 2.0")
                                                                .url("https://www.apache.org/licenses/LICENSE-2.0.html")))
                                .servers(List.of(
                                                new Server().url("http://localhost:8080")
                                                                .description("Dev service")))
                                .addSecurityItem(new SecurityRequirement().addList("bearerAuth"))
                                .components(new Components()
                                                .addSecuritySchemes("bearerAuth", new SecurityScheme()
                                                                .type(SecurityScheme.Type.HTTP)
                                                                .scheme("bearer")
                                                                .bearerFormat("JWT")
                                                                .description("JWT Authorization header")));
        }
}