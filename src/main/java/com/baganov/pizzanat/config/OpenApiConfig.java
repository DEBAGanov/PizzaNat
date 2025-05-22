/**
 * @file: OpenApiConfig.java
 * @description: Конфигурация OpenAPI для документации API
 * @dependencies: springdoc-openapi
 * @created: 2025-05-22
 */
package com.baganov.pizzanat.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.info.License;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.servers.Server;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.Arrays;
import java.util.List;

/**
 * Конфигурация OpenAPI для генерации документации API
 */
@Configuration
public class OpenApiConfig {

    @Value("${spring.application.name:PizzaNat}")
    private String applicationName;

    /**
     * Создает основную конфигурацию OpenAPI
     *
     * @return OpenAPI объект с настройками
     */
    @Bean
    public OpenAPI openAPI() {
        return new OpenAPI()
                .info(new Info()
                        .title(applicationName + " API")
                        .description("API для сервиса заказа пиццы")
                        .version("1.0.0")
                        .contact(new Contact()
                                .name("PizzaNat Team")
                                .email("support@pizzanat.com")
                                .url("https://pizzanat.com"))
                        .license(new License()
                                .name("Apache 2.0")
                                .url("https://www.apache.org/licenses/LICENSE-2.0.html")))
                .servers(getServers())
                .components(new Components()
                        .addSecuritySchemes("bearerAuth",
                                new SecurityScheme()
                                        .type(SecurityScheme.Type.HTTP)
                                        .scheme("bearer")
                                        .bearerFormat("JWT")
                                        .description("JWT токен авторизации. Введите токен без префикса 'Bearer '")))
                .addSecurityItem(new SecurityRequirement().addList("bearerAuth"));
    }

    /**
     * Создает список серверов для документации
     *
     * @return список серверов
     */
    private List<Server> getServers() {
        Server localServer = new Server()
                .url("http://localhost:8080")
                .description("Локальный сервер");

        Server devServer = new Server()
                .url("http://localhost:80")
                .description("Docker Dev сервер");

        return Arrays.asList(localServer, devServer);
    }
}