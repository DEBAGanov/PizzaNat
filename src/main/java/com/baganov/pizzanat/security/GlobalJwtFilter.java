/**
 * @file: GlobalJwtFilter.java
 * @description: Глобальный фильтр для обработки JWT токенов для всех запросов
 * @dependencies: Spring Security, JWT
 * @created: 2025-05-23
 */
package com.baganov.pizzanat.security;

import jakarta.servlet.Filter;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletRequest;
import jakarta.servlet.ServletResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationContext;
import org.springframework.core.annotation.Order;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;

import java.io.IOException;

@Slf4j
@Component
@RequiredArgsConstructor
@Order(-100) // Выполняется до Spring Security фильтров
public class GlobalJwtFilter implements Filter {

    private final JwtService jwtService;
    private final ApplicationContext applicationContext;
    private UserDetailsService userDetailsService;

    @Override
    public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain)
            throws IOException, ServletException {

        HttpServletRequest httpRequest = (HttpServletRequest) request;
        HttpServletResponse httpResponse = (HttpServletResponse) response;

        final String requestURI = httpRequest.getRequestURI();
        log.debug("GlobalJwtFilter: обработка запроса к URI: {}", requestURI);

        final String authHeader = httpRequest.getHeader("Authorization");

        if (authHeader != null && authHeader.startsWith("Bearer ")) {
            final String jwt = authHeader.substring(7);
            log.debug("GlobalJwtFilter: найден JWT токен для URI: {}", requestURI);

            try {
                final String username = jwtService.extractUsername(jwt);
                log.debug("GlobalJwtFilter: извлечено имя пользователя: {} для URI: {}", username, requestURI);

                if (username != null && SecurityContextHolder.getContext().getAuthentication() == null) {
                    UserDetails userDetails = getUserDetailsService().loadUserByUsername(username);
                    log.debug("GlobalJwtFilter: загружены данные пользователя: {} для URI: {}",
                            userDetails.getUsername(), requestURI);

                    if (jwtService.isTokenValid(jwt, userDetails)) {
                        UsernamePasswordAuthenticationToken authToken = new UsernamePasswordAuthenticationToken(
                                userDetails,
                                null,
                                userDetails.getAuthorities());
                        authToken.setDetails(new WebAuthenticationDetailsSource().buildDetails(httpRequest));
                        SecurityContextHolder.getContext().setAuthentication(authToken);
                        log.debug("GlobalJwtFilter: пользователь {} успешно аутентифицирован для URI: {}", username,
                                requestURI);
                    } else {
                        log.debug("GlobalJwtFilter: токен недействителен для пользователя {} для URI: {}", username,
                                requestURI);
                    }
                }
            } catch (Exception e) {
                log.error("GlobalJwtFilter: ошибка при обработке JWT токена для URI {}: {}", requestURI,
                        e.getMessage());
            }
        } else {
            log.debug("GlobalJwtFilter: JWT токен не найден для URI: {}", requestURI);
        }

        chain.doFilter(request, response);
    }

    private UserDetailsService getUserDetailsService() {
        if (userDetailsService == null) {
            userDetailsService = applicationContext.getBean(UserDetailsService.class);
        }
        return userDetailsService;
    }
}