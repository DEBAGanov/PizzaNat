/**
 * @file: RestTemplateConfig.java
 * @description: Конфигурация RestTemplate для HTTP клиентов
 * @dependencies: Spring Web, Apache HttpClient 5
 * @created: 2023-11-01
 */
package com.baganov.pizzanat.config;

import org.springframework.boot.autoconfigure.http.HttpMessageConvertersAutoConfiguration;
import org.springframework.boot.autoconfigure.http.client.HttpClientAutoConfiguration;
import org.springframework.boot.autoconfigure.ImportAutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestTemplate;

import java.time.Duration;

/**
 * Конфигурация RestTemplate для HTTP клиентов с настройками таймаутов
 */
@Configuration
@ImportAutoConfiguration(exclude = {
        HttpClientAutoConfiguration.class,
        HttpMessageConvertersAutoConfiguration.class
})
public class RestTemplateConfig {

    /**
     * Создает RestTemplate с настроенными таймаутами и обработкой ошибок
     *
     * @return настроенный RestTemplate
     */
    @Bean
    @ConditionalOnMissingBean
    public RestTemplate restTemplate() {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(5000);
        factory.setReadTimeout(10000);

        return new RestTemplate(factory);
    }
}