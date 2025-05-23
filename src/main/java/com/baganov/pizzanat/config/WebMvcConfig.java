/**
 * @file: WebMvcConfig.java
 * @description: Конфигурация MVC для статических ресурсов
 * @dependencies: Spring Web
 * @created: 2025-05-24
 * @updated: 2025-05-26
 */
package com.baganov.pizzanat.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.ViewControllerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * Конфигурация WebMvc для настройки доступа к статическим ресурсам
 */
@Configuration
public class WebMvcConfig implements WebMvcConfigurer {

        /**
         * Настраивает обработчики статических ресурсов
         *
         * @param registry реестр обработчиков ресурсов
         */
        @Override
        public void addResourceHandlers(ResourceHandlerRegistry registry) {
                // Конфигурация для статических ресурсов приложения
                registry.addResourceHandler("/static/**")
                                .addResourceLocations("classpath:/static/");
        }

        /**
         * Добавляет перенаправление с корневого URL на Swagger UI
         */
        // @Override
        // public void addViewControllers(ViewControllerRegistry registry) {
        // registry.addRedirectViewController("/", "/swagger-ui.html");
        // registry.addRedirectViewController("/swagger", "/swagger-ui.html");
        // }
}