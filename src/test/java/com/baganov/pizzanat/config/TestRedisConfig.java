/**
 * @file: TestRedisConfig.java
 * @description: Тестовая конфигурация Redis
 * @dependencies: Spring Boot Test
 * @created: 2025-05-22
 */
package com.baganov.pizzanat.config;

import org.mockito.Mockito;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.cache.CacheManager;
import org.springframework.cache.concurrent.ConcurrentMapCacheManager;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.data.redis.connection.RedisConnection;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.core.RedisTemplate;

@TestConfiguration
public class TestRedisConfig {

    @Bean
    @Primary
    public CacheManager cacheManager() {
        // Используем локальный кэш вместо Redis
        return new ConcurrentMapCacheManager(
                "products", "categories", "orderDetails", "userOrders", "allOrders");
    }

    @Bean
    @Primary
    public RedisConnectionFactory redisConnectionFactory() {
        // Создаем мок для RedisConnectionFactory
        RedisConnectionFactory mockFactory = Mockito.mock(RedisConnectionFactory.class);
        RedisConnection mockConnection = Mockito.mock(RedisConnection.class);

        // Настраиваем заглушку, чтобы не выбрасывала исключение при попытке соединения
        Mockito.when(mockFactory.getConnection()).thenReturn(mockConnection);

        return mockFactory;
    }

    @Bean
    @Primary
    public RedisTemplate<String, Object> redisTemplate(RedisConnectionFactory connectionFactory) {
        // Создаем шаблон с нашей заглушкой
        RedisTemplate<String, Object> template = new RedisTemplate<>();
        template.setConnectionFactory(connectionFactory);
        return template;
    }
}