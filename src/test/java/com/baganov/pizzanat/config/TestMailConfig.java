/**
 * @file: TestMailConfig.java
 * @description: Тестовая конфигурация для отправки почты
 * @dependencies: Spring Boot Test, JavaMail
 * @created: 2025-05-22
 */
package com.baganov.pizzanat.config;

import org.mockito.Mockito;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.JavaMailSenderImpl;

@TestConfiguration
public class TestMailConfig {

    @Bean
    @Primary
    public JavaMailSender javaMailSender() {
        // Используем мок вместо реального сервиса отправки почты
        return Mockito.mock(JavaMailSenderImpl.class);
    }
}