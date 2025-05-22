/**
 * @file: RestTemplateConfig.java
 * @description: Конфигурация RestTemplate для HTTP клиентов
 * @dependencies: Spring Web
 * @created: 2023-11-01
 */
package com.baganov.pizzanat.config;

import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.ClientHttpRequestFactory;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestTemplate;

import java.time.Duration;

/**
 * Конфигурация RestTemplate для HTTP клиентов с настройками таймаутов
 */
@Configuration
public class RestTemplateConfig {

    /**
     * Создает RestTemplate с настроенными таймаутами и обработкой ошибок
     *
     * @return настроенный RestTemplate
     */
    @Bean
    public RestTemplate restTemplate() {
        return new RestTemplateBuilder()
                .requestFactory(this::clientHttpRequestFactory)
                .setConnectTimeout(Duration.ofSeconds(5))
                .setReadTimeout(Duration.ofSeconds(10))
                .build();
    }

    /**
     * Создает фабрику для HTTP запросов с настроенными таймаутами
     *
     * @return настроенная фабрика HTTP запросов
     */
    private ClientHttpRequestFactory clientHttpRequestFactory() {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(5000); // 5 секунд
        factory.setReadTimeout(10000); // 10 секунд
        factory.setBufferRequestBody(false); // Не буферизовать тело запроса для больших запросов
        return factory;
    }
}