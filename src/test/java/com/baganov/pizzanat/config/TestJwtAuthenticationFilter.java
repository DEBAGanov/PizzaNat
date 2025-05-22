/**
 * @file: TestJwtAuthenticationFilter.java
 * @description: Заглушка для JwtAuthenticationFilter для тестирования
 * @dependencies: Spring Security
 * @created: 2025-05-23
 */
package com.baganov.pizzanat.config;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.context.annotation.Primary;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

@Component
@Primary
@Profile("test")
public class TestJwtAuthenticationFilter extends OncePerRequestFilter {

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain) throws ServletException, IOException {
        // Просто пропускаем все запросы без проверки JWT
        filterChain.doFilter(request, response);
    }
}