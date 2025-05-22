/**
 * @file: MinioClientConfig.java
 * @description: Конфигурация клиента MinIO с проксированием URL для внешнего доступа
 * @dependencies: MinIO
 * @created: 2025-05-23
 */
package com.baganov.pizzanat.config;

import io.minio.MinioClient;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;

@Slf4j
@Configuration
public class MinioClientConfig {

    @Value("${s3.endpoint}")
    private String s3Endpoint;

    @Value("${s3.public-url:#{null}}")
    private String s3PublicUrl;

    /**
     * Перезаписывает URL для presignedUrl в StorageService, заменяя внутренний URL
     * на публичный
     * 
     * @return функция трансформации URL
     */
    @Bean
    public UrlTransformer minioUrlTransformer() {
        if (s3PublicUrl != null && !s3PublicUrl.isEmpty()) {
            log.info("Настройка проксирования MinIO URL: {} -> {}", s3Endpoint, s3PublicUrl);
            return url -> {
                // Заменяем базовый URL и сохраняем путь и параметры запроса
                String result = url.replace(s3Endpoint, s3PublicUrl);
                log.debug("Трансформирован URL: {} -> {}", url, result);
                return result;
            };
        }

        log.info("Проксирование MinIO URL отключено, s3.public-url не задан");
        return url -> url; // без изменений
    }

    /**
     * Функциональный интерфейс для трансформации URL
     */
    @FunctionalInterface
    public interface UrlTransformer {
        String transform(String url);
    }
}