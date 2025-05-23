package com.baganov.pizzanat.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationContext;
import org.springframework.lang.NonNull;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

@Slf4j
@Component
@RequiredArgsConstructor
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private final JwtService jwtService;
    private UserDetailsService userDetailsService;
    private final ApplicationContext applicationContext;

    @Override
    protected void doFilterInternal(
            @NonNull HttpServletRequest request,
            @NonNull HttpServletResponse response,
            @NonNull FilterChain filterChain) throws ServletException, IOException {

        final String requestURI = request.getRequestURI();
        log.debug("JwtAuthenticationFilter: Запрос к URI: {}, метод: {}", requestURI, request.getMethod());

        final String authHeader = request.getHeader("Authorization");
        log.debug("JwtAuthenticationFilter: Заголовок Authorization: {}", authHeader);

        final String jwt;
        final String username;

        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            log.debug("JwtAuthenticationFilter: Заголовок Authorization отсутствует или неверного формата для URI: {}",
                    requestURI);
            filterChain.doFilter(request, response);
            return;
        }

        jwt = authHeader.substring(7);
        log.debug("JwtAuthenticationFilter: Извлечен JWT токен для URI: {}", requestURI);

        try {
            username = jwtService.extractUsername(jwt);
            log.debug("JwtAuthenticationFilter: Извлечено имя пользователя из токена: {} для URI: {}", username,
                    requestURI);

            if (username != null && SecurityContextHolder.getContext().getAuthentication() == null) {
                UserDetails userDetails = getUserDetailsService().loadUserByUsername(username);
                log.debug("JwtAuthenticationFilter: Загружены данные пользователя: {}, роли: {} для URI: {}",
                        userDetails.getUsername(),
                        userDetails.getAuthorities(), requestURI);

                if (jwtService.isTokenValid(jwt, userDetails)) {
                    UsernamePasswordAuthenticationToken authToken = new UsernamePasswordAuthenticationToken(
                            userDetails,
                            null,
                            userDetails.getAuthorities());
                    authToken.setDetails(
                            new WebAuthenticationDetailsSource().buildDetails(request));
                    SecurityContextHolder.getContext().setAuthentication(authToken);
                    log.debug("JwtAuthenticationFilter: Пользователь {} успешно аутентифицирован для URI: {}", username,
                            requestURI);
                } else {
                    log.debug("JwtAuthenticationFilter: Токен недействителен для пользователя {} для URI: {}", username,
                            requestURI);
                }
            } else if (username != null) {
                log.debug("JwtAuthenticationFilter: Authentication уже установлен для пользователя {} URI: {}",
                        username, requestURI);
            }
        } catch (Exception e) {
            log.error("JwtAuthenticationFilter: Ошибка при обработке JWT токена для URI {}: {}", requestURI,
                    e.getMessage());
        }

        filterChain.doFilter(request, response);
    }

    private UserDetailsService getUserDetailsService() {
        if (userDetailsService == null) {
            userDetailsService = applicationContext.getBean(UserDetailsService.class);
        }
        return userDetailsService;
    }
}