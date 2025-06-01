/**
 * @file: JacksonConfig.java
 * @description: Конфигурация Jackson для корректного форматирования JSON
 * @dependencies: Spring Web, Jackson, JSR310
 * @created: 2025-05-31
 */
package com.baganov.pizzanat.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;

@Configuration
public class JacksonConfig {

    @Bean
    @Primary
    public ObjectMapper objectMapper() {
        ObjectMapper mapper = new ObjectMapper();

        // Регистрируем модуль для работы с Java 8 Time API
        mapper.registerModule(new JavaTimeModule());

        // Отключаем сериализацию дат как timestamps
        mapper.disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);

        // Настройки для корректной работы
        mapper.disable(SerializationFeature.FAIL_ON_EMPTY_BEANS);

        return mapper;
    }
}