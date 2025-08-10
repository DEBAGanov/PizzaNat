/**
 * @file: WebConfig.java
 * @description: Веб-конфигурация приложения с CORS настройками
 * @dependencies: Spring Web MVC
 * @created: 2025-05-24
 */
package com.baganov.pizzanat.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
public class WebConfig implements WebMvcConfigurer {

    @Value("${app.cors.allowed-origins:https://pizzanat.ru,https://www.pizzanat.ru,https://api.pizzanat.ru,http://localhost:5173,http://localhost:3000,http://localhost:8080,https://api.dimbopizza.ru,https://dimbopizza.ru,https://dimbopizza.ru/*}")
    private String[] allowedOrigins;

    @Override
    public void addCorsMappings(CorsRegistry registry) {
        registry.addMapping("/**")
                .allowedOrigins(allowedOrigins)
                .allowedMethods("GET", "POST", "PUT", "DELETE", "OPTIONS", "PATCH")
                .allowedHeaders("Authorization", "Content-Type", "X-Requested-With", "Accept", "Origin", "X-Auth-Token",
                        "X-Client-Type", "X-Client-Version")
                .exposedHeaders("Authorization", "Content-Type", "X-Total-Count")
                .allowCredentials(true)
                .maxAge(3600);
    }

    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        // Статические ресурсы Mini App
        registry.addResourceHandler("/miniapp/**")
                .addResourceLocations("classpath:/static/miniapp/")
                .setCachePeriod(3600); // 1 час кеширования
        
        // Обычные статические ресурсы
        registry.addResourceHandler("/static/**")
                .addResourceLocations("classpath:/static/")
                .setCachePeriod(3600);
    }
}
