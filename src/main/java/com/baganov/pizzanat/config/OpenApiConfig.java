/**
 * @file: OpenApiConfig.java
 * @description: Конфигурация OpenAPI и Swagger UI
 * @dependencies: SpringDoc OpenAPI
 * @created: 2025-05-23
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

import java.util.Arrays;

@Configuration
public class OpenApiConfig {

    private static final String BEARER_AUTH = "bearer-jwt";

    @Bean
    public OpenAPI customOpenAPI() {
        return new OpenAPI()
                .openapi("3.0.1")
                .info(apiInfo())
                .servers(Arrays.asList(
                        new Server()
                                .url("/")
                                .description("Текущий сервер")))
                .addSecurityItem(new SecurityRequirement().addList(BEARER_AUTH))
                .components(
                        new Components()
                                .addSecuritySchemes(BEARER_AUTH,
                                        new SecurityScheme()
                                                .name("Authorization")
                                                .type(SecurityScheme.Type.HTTP)
                                                .scheme("bearer")
                                                .bearerFormat("JWT")
                                                .in(SecurityScheme.In.HEADER)
                                                .description("JWT Authorization header using the Bearer scheme.")));
    }

    private Info apiInfo() {
        return new Info()
                .title("PizzaNat API")
                .description("API для сервиса заказа пиццы PizzaNat")
                .version("1.0.0")
                .contact(new Contact()
                        .name("Baganov")
                        .url("https://pizzanat.com")
                        .email("support@pizzanat.com"))
                .license(new License()
                        .name("Apache 2.0")
                        .url("https://www.apache.org/licenses/LICENSE-2.0.html"));
    }
}