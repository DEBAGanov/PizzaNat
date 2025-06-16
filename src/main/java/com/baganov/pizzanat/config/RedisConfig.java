package com.baganov.pizzanat.config;

import org.springframework.cache.annotation.EnableCaching;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.data.redis.cache.RedisCacheConfiguration;
import org.springframework.data.redis.cache.RedisCacheManager;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.serializer.GenericJackson2JsonRedisSerializer;
import org.springframework.data.redis.serializer.RedisSerializationContext;
import org.springframework.data.redis.serializer.StringRedisSerializer;

import java.time.Duration;
import java.util.HashMap;
import java.util.Map;

@Configuration
@EnableCaching
@Profile({ "dev" }) // Только для prod профиля (Redis недоступен в dev)
public class RedisConfig {

        @Bean
        public RedisCacheManager cacheManager(RedisConnectionFactory connectionFactory) {
                RedisCacheConfiguration defaultConfig = RedisCacheConfiguration.defaultCacheConfig()
                                .entryTtl(Duration.ofMinutes(10))
                                .serializeKeysWith(RedisSerializationContext.SerializationPair
                                                .fromSerializer(new StringRedisSerializer()))
                                .serializeValuesWith(RedisSerializationContext.SerializationPair
                                                .fromSerializer(new GenericJackson2JsonRedisSerializer()))
                                .disableCachingNullValues();

                Map<String, RedisCacheConfiguration> cacheConfigurations = new HashMap<>();

                // Кэш для каталога продуктов - 30 минут
                cacheConfigurations.put("products", defaultConfig.entryTtl(Duration.ofMinutes(30)));
                cacheConfigurations.put("categories", defaultConfig.entryTtl(Duration.ofMinutes(30)));

                // Кэш для заказов - 5 минут
                cacheConfigurations.put("orderDetails", defaultConfig.entryTtl(Duration.ofMinutes(5)));
                cacheConfigurations.put("userOrders", defaultConfig.entryTtl(Duration.ofMinutes(5)));
                cacheConfigurations.put("allOrders", defaultConfig.entryTtl(Duration.ofMinutes(5)));

                return RedisCacheManager.builder(connectionFactory)
                                .cacheDefaults(defaultConfig)
                                .withInitialCacheConfigurations(cacheConfigurations)
                                .build();
        }
}