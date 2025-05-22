/**
 * @file: JacksonConfig.java
 * @description: Конфигурация Jackson для корректного форматирования JSON
 * @dependencies: Spring Web, Jackson
 * @created: 2025-05-23
 */
package com.baganov.pizzanat.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;
import org.springframework.http.converter.HttpMessageConverter;

import java.util.List;

@Configuration
public class JacksonConfig implements WebMvcConfigurer {

    /**
     * Создаем экземпляр ObjectMapper с настройками для корректного форматирования
     */
    @Bean
    public ObjectMapper objectMapper() {
        ObjectMapper objectMapper = new ObjectMapper();
        // Дополнительные настройки, если потребуются
        return objectMapper;
    }

    /**
     * Регистрируем настроенный конвертер JSON в списке конвертеров HTTP сообщений
     */
    @Override
    public void configureMessageConverters(List<HttpMessageConverter<?>> converters) {
        MappingJackson2HttpMessageConverter converter = new MappingJackson2HttpMessageConverter(objectMapper());

        // Убираем конвертер по умолчанию и добавляем наш
        converters.removeIf(c -> c instanceof MappingJackson2HttpMessageConverter);
        converters.add(0, converter);
    }
}