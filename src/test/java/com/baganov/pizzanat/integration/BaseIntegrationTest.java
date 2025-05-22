/**
 * @file: BaseIntegrationTest.java
 * @description: Базовый класс для интеграционных тестов
 * @dependencies: Spring Boot Test, JWT
 * @created: 2025-05-22
 */
package com.baganov.pizzanat.integration;

import com.baganov.pizzanat.config.*;
import com.baganov.pizzanat.security.JwtService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.util.Collections;

/**
 * Базовый класс для всех интеграционных тестов API
 */
@SpringBootTest
@AutoConfigureMockMvc(addFilters = false) // Отключаем все фильтры безопасности для тестов
@ActiveProfiles("test")
@Import({ TestConfig.class, TestRedisConfig.class, TestMailConfig.class, TestS3Config.class })
public abstract class BaseIntegrationTest {

    @Autowired
    protected MockMvc mockMvc;

    @Autowired
    protected ObjectMapper objectMapper;

    @Autowired
    protected JwtService jwtService;

    protected String userToken;
    protected String adminToken;

    /**
     * Создание JWT токенов для тестирования
     */
    @BeforeEach
    void setUp() {
        // Создаем тестовые токены для пользователя и админа
        UserDetails userDetails = new User(
                "user@example.com",
                "password",
                Collections.singletonList(new SimpleGrantedAuthority("ROLE_USER")));

        UserDetails adminDetails = new User(
                "admin@example.com",
                "admin",
                Collections.singletonList(new SimpleGrantedAuthority("ROLE_ADMIN")));

        userToken = jwtService.generateToken(userDetails);
        adminToken = jwtService.generateToken(adminDetails);
    }

    /**
     * Получение заголовка авторизации с JWT токеном
     *
     * @param token JWT токен
     * @return строка заголовка авторизации
     */
    protected String getBearerToken(String token) {
        return "Bearer " + token;
    }
}