/**
 * @file: TestS3Config.java
 * @description: Тестовая конфигурация для S3/MinIO
 * @dependencies: Spring Boot Test
 * @created: 2025-05-22
 */
package com.baganov.pizzanat.config;

import com.baganov.pizzanat.config.MinioClientConfig.UrlTransformer;
import com.baganov.pizzanat.service.StorageService;
import com.baganov.pizzanat.util.ImageUploader;
import io.minio.MinioClient;
import org.mockito.Mockito;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

@Component
@Profile("test")
public class TestS3Config {

    @Bean
    @Primary
    public StorageService storageService() {
        StorageService mockStorageService = Mockito.mock(StorageService.class);
        // Настраиваем мок для возврата пустой строки или URL по умолчанию
        Mockito.when(mockStorageService.getPresignedUrl(Mockito.anyString(), Mockito.anyInt()))
                .thenReturn("http://localhost:9000/test-bucket/test-image.jpg");
        return mockStorageService;
    }

    @Bean
    @Primary
    public MinioClient minioClient() {
        // Создаем мок для MinioClient
        return Mockito.mock(MinioClient.class);
    }

    @Bean
    @Primary
    public UrlTransformer urlTransformer() {
        // Возвращаем функцию, которая не изменяет URL
        return url -> url;
    }

    @Bean
    @Primary
    public ImageUploader imageUploader() {
        // Создаем мок для ImageUploader
        ImageUploader mockImageUploader = Mockito.mock(ImageUploader.class);

        // Заглушка для метода syncProductData
        Mockito.doNothing().when(mockImageUploader).syncProductData();

        return mockImageUploader;
    }
}