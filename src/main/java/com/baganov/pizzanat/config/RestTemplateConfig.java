/**
 * @file: RestTemplateConfig.java
 * @description: Конфигурация RestTemplate для HTTP клиентов
 * @dependencies: Spring Web
 * @created: 2023-11-01
 */
package com.baganov.pizzanat.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestTemplate;

/**
 * Конфигурация RestTemplate для HTTP клиентов с настройками таймаутов
 */
@Configuration
public class RestTemplateConfig {

    /**
     * Создает RestTemplate с настроенными таймаутами для платежной системы
     * Robokassa
     *
     * @return настроенный RestTemplate
     */
    @Bean("restTemplate")
    public RestTemplate restTemplate() {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(5000);
        factory.setReadTimeout(10000);

        return new RestTemplate(factory);
    }
}