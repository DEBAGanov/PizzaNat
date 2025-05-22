/**
 * @file: AuthControllerTest.java
 * @description: Тесты для AuthController
 * @dependencies: Spring Boot Test, JWT
 * @created: 2025-05-22
 */
package com.baganov.pizzanat.integration;

import com.baganov.pizzanat.model.dto.auth.AuthRequest;
import com.baganov.pizzanat.model.dto.auth.RegisterRequest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.result.MockMvcResultMatchers;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultHandlers.print;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

public class AuthControllerTest extends BaseIntegrationTest {

    @Test
    @DisplayName("Проверка доступности API аутентификации")
    public void testAuthEndpoint() throws Exception {
        mockMvc.perform(get("/api/v1/auth/test"))
                .andDo(print())
                .andExpect(status().isOk())
                .andExpect(MockMvcResultMatchers.content().string("API аутентификации доступно"));
    }

    @Test
    @DisplayName("Успешная регистрация нового пользователя")
    public void testRegisterUser() throws Exception {
        RegisterRequest registerRequest = new RegisterRequest();
        registerRequest.setUsername("newuser@test.com");
        registerRequest.setEmail("newuser@test.com");
        registerRequest.setPassword("password123");
        registerRequest.setFirstName("Тест");
        registerRequest.setLastName("Тестов");
        registerRequest.setPhone("+79001234567");

        mockMvc.perform(post("/api/v1/auth/register")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(registerRequest)))
                .andDo(print())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.token").exists())
                .andExpect(jsonPath("$.username").value("newuser@test.com"));
    }

    @Test
    @DisplayName("Успешная аутентификация пользователя")
    public void testLoginUser() throws Exception {
        // Сначала регистрируем пользователя
        RegisterRequest registerRequest = new RegisterRequest();
        registerRequest.setUsername("logintest@test.com");
        registerRequest.setEmail("logintest@test.com");
        registerRequest.setPassword("password123");
        registerRequest.setFirstName("Тест");
        registerRequest.setLastName("Тестов");
        registerRequest.setPhone("+79001234568");

        mockMvc.perform(post("/api/v1/auth/register")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(registerRequest)))
                .andExpect(status().isOk());

        // Затем пытаемся войти
        AuthRequest authRequest = new AuthRequest();
        authRequest.setUsername("logintest@test.com");
        authRequest.setPassword("password123");

        mockMvc.perform(post("/api/v1/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(authRequest)))
                .andDo(print())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.token").exists())
                .andExpect(jsonPath("$.username").value("logintest@test.com"));
    }

    @Test
    @DisplayName("Ошибка аутентификации при неверном пароле")
    public void testLoginWithWrongPassword() throws Exception {
        // Сначала регистрируем пользователя
        RegisterRequest registerRequest = new RegisterRequest();
        registerRequest.setUsername("wrongpass@test.com");
        registerRequest.setEmail("wrongpass@test.com");
        registerRequest.setPassword("password123");
        registerRequest.setFirstName("Тест");
        registerRequest.setLastName("Тестов");
        registerRequest.setPhone("+79001234569");

        mockMvc.perform(post("/api/v1/auth/register")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(registerRequest)))
                .andExpect(status().isOk());

        // Затем пытаемся войти с неверным паролем
        AuthRequest authRequest = new AuthRequest();
        authRequest.setUsername("wrongpass@test.com");
        authRequest.setPassword("wrongpassword");

        mockMvc.perform(post("/api/v1/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(authRequest)))
                .andDo(print())
                .andExpect(status().isForbidden());
    }
}