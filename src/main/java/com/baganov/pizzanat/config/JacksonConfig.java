/**
 * @file: JacksonConfig.java
 * @description: Конфигурация Jackson для корректной сериализации Java 8 Time API
 * @dependencies: Jackson, Spring Boot
 * @created: 2025-01-29
 */
package com.baganov.pizzanat.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;

/**
 * Конфигурация Jackson для работы с Java 8 Time API и корректной обработки JSON
 */
@Configuration
public class JacksonConfig {

    /**
     * Настройка ObjectMapper для корректной работы с LocalDateTime и UTF-8
     */
    @Bean
    @Primary
    public ObjectMapper objectMapper() {
        ObjectMapper mapper = new ObjectMapper();

        // Регистрируем модуль для работы с Java 8 Time API
        mapper.registerModule(new JavaTimeModule());

        // Отключаем сериализацию дат как timestamp
        mapper.disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);

        // Игнорируем неизвестные поля при десериализации
        mapper.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);

        // Разрешаем пустые строки как null
        mapper.configure(DeserializationFeature.ACCEPT_EMPTY_STRING_AS_NULL_OBJECT, true);

        // Настройки для работы с UTF-8
        mapper.configure(DeserializationFeature.FAIL_ON_INVALID_SUBTYPE, false);

        return mapper;
    }
}