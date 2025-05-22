/**
 * @file: TestAutoConfigureSecurityConfig.java
 * @description: Автоматическая конфигурация безопасности для тестов
 * @dependencies: Spring Security
 * @created: 2025-05-23
 */
package com.baganov.pizzanat.config;

import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.context.annotation.Profile;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;

@TestConfiguration
@Profile("test")
@AutoConfigureMockMvc(addFilters = false) // Отключаем все фильтры Spring Security для тестов
public class TestAutoConfigureSecurityConfig {

    @Bean
    @Primary
    public SecurityFilterChain testSecurityFilterChain(HttpSecurity http) throws Exception {
        return http
                .csrf(AbstractHttpConfigurer::disable)
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers("/**").permitAll())
                .build();
    }
}