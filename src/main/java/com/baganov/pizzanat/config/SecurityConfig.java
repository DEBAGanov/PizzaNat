/**
 * @file: SecurityConfig.java
 * @description: Настройка безопасности приложения
 * @dependencies: Spring Security
 * @created: 2025-05-24
 */
package com.baganov.pizzanat.config;

import com.baganov.pizzanat.security.JwtAuthenticationFilter;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Lazy;
import org.springframework.context.annotation.Profile;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.AuthenticationProvider;
import org.springframework.security.authentication.dao.DaoAuthenticationProvider;
import org.springframework.security.config.annotation.authentication.configuration.AuthenticationConfiguration;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpMethod;

import java.util.Arrays;
import java.util.List;

@Configuration
@EnableWebSecurity
@Profile("!test")
public class SecurityConfig {

    private static final Logger log = LoggerFactory.getLogger(SecurityConfig.class);

    private final JwtAuthenticationFilter jwtAuthFilter;
    private final UserDetailsService userDetailsService;
    private final PasswordEncoder passwordEncoder;

    @Value("${app.security.disable-jwt-auth:false}")
    private boolean disableJwtAuth;

    public SecurityConfig(@Lazy JwtAuthenticationFilter jwtAuthFilter,
            UserDetailsService userDetailsService,
            PasswordEncoder passwordEncoder) {
        this.jwtAuthFilter = jwtAuthFilter;
        this.userDetailsService = userDetailsService;
        this.passwordEncoder = passwordEncoder;
    }

    /**
     * Массив URL-адресов, на которые можно делать запросы без аутентификации
     */
    private static final String[] AUTH_WHITELIST = {
            // Root path
            "/",
            // Auth endpoints (включая новые SMS и Telegram)
            "/api/v1/auth/**",
            "/api/v1/auth/sms/**",
            "/api/v1/auth/telegram/**",
            // Telegram webhook endpoints
            "/api/v1/telegram/**",
            // Public API
            "/api/v1/public/**",
            // Actuator
            "/actuator/**",
            // Health check
            "/health",
            "/api/health",
            "/api/status",
            // Categories (только GET)
            "/api/v1/categories",
            "/api/v1/categories/*",
            // Products (только GET)
            "/api/v1/products",
            "/api/v1/products/*",
            "/api/v1/products/category/*",
            "/api/v1/products/special-offers",
            "/api/v1/products/search",
            // Delivery Locations (только GET для Android приложения)
            "/api/v1/delivery-locations",
            "/api/v1/delivery-locations/*",
            // В dev режиме разрешаем все
            "/api/v1/cart",
            "/api/v1/cart/**",
            "/api/v1/orders",
            "/api/v1/orders/**",
            "/api/v1/admin/**",
            "/debug/**"
    };

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        log.debug("Configuring security with public URLs: {}", Arrays.toString(AUTH_WHITELIST));
        log.info("Режим без проверки JWT: {}", disableJwtAuth);

        if (disableJwtAuth) {
            return http
                    .csrf(AbstractHttpConfigurer::disable)
                    .cors(cors -> cors.configurationSource(corsConfigurationSource()))
                    .authorizeHttpRequests(auth -> auth
                            .anyRequest().permitAll()) // Разрешаем все запросы в dev режиме
                    .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                    .build();
        } else {
            return http
                    .csrf(AbstractHttpConfigurer::disable)
                    .cors(cors -> cors.configurationSource(corsConfigurationSource()))
                    .authorizeHttpRequests(auth -> auth
                            .requestMatchers(AUTH_WHITELIST).permitAll()
                            .requestMatchers(HttpMethod.GET, "/api/v1/categories/**").permitAll()
                            .requestMatchers(HttpMethod.GET, "/api/v1/pizzas/**").permitAll()
                            .requestMatchers(HttpMethod.GET, "/api/v1/orders").permitAll()
                            .requestMatchers(HttpMethod.GET, "/api/v1/orders/**").permitAll()
                            .anyRequest().authenticated())
                    .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                    .authenticationProvider(authenticationProvider())
                    .addFilterBefore(jwtAuthFilter, UsernamePasswordAuthenticationFilter.class)
                    .build();
        }
    }

    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration configuration = new CorsConfiguration();
        configuration.setAllowedOriginPatterns(List.of("*")); // Используем patterns вместо origins для поддержки *
        configuration.setAllowedMethods(Arrays.asList("GET", "POST", "PUT", "DELETE", "OPTIONS"));
        configuration.setAllowedHeaders(List.of("*"));
        configuration.setExposedHeaders(List.of("Authorization", "Content-Type"));
        configuration.setAllowCredentials(true); // Разрешаем credentials для JWT

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", configuration);
        return source;
    }

    @Bean
    public AuthenticationProvider authenticationProvider() {
        DaoAuthenticationProvider authProvider = new DaoAuthenticationProvider();
        authProvider.setUserDetailsService(userDetailsService);
        authProvider.setPasswordEncoder(passwordEncoder);
        return authProvider;
    }

    @Bean
    public AuthenticationManager authenticationManager(AuthenticationConfiguration config) throws Exception {
        return config.getAuthenticationManager();
    }

}

/**
 * Конфигурация для продакшн режима с включенной method security
 */
@Configuration
@EnableMethodSecurity
@Profile("prod")
class ProductionSecurityConfig {
}

/**
 * Конфигурация для dev режима с отключенной method security
 */
@Configuration
@Profile("dev")
class DevelopmentSecurityConfig {
    // Method security отключена для dev режима
}