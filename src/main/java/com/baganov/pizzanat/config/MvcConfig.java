/**
 * @file: MvcConfig.java
 * @description: Конфигурация MVC для корректного форматирования JSON
 * @dependencies: Spring Web, Jackson
 * @created: 2025-05-23
 */
package com.baganov.pizzanat.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.converter.HttpMessageConverter;
import org.springframework.http.converter.StringHttpMessageConverter;
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import java.nio.charset.StandardCharsets;
import java.util.List;

@Configuration
public class MvcConfig implements WebMvcConfigurer {

    /**
     * Настройка конвертеров HTTP-сообщений
     */
    @Override
    public void configureMessageConverters(List<HttpMessageConverter<?>> converters) {
        // Удаляем стандартные конвертеры
        converters.clear();

        // Добавляем конвертер с правильной кодировкой для строк
        StringHttpMessageConverter stringConverter = new StringHttpMessageConverter(StandardCharsets.UTF_8);
        stringConverter.setWriteAcceptCharset(false);
        converters.add(stringConverter);

        // Добавляем Jackson конвертер для JSON
        converters.add(new MappingJackson2HttpMessageConverter(new ObjectMapper()));
    }

    /**
     * Конфигурация StringHttpMessageConverter с отключенным маркером BOM
     */
    @Bean
    public StringHttpMessageConverter stringHttpMessageConverter() {
        StringHttpMessageConverter converter = new StringHttpMessageConverter(StandardCharsets.UTF_8);
        converter.setWriteAcceptCharset(false); // Отключаем добавление маркера BOM/символа %
        return converter;
    }
}