package com.baganov.pizzanat.config;

import io.minio.BucketExistsArgs;
import io.minio.MakeBucketArgs;
import io.minio.MinioClient;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.retry.annotation.Backoff;
import org.springframework.retry.annotation.EnableRetry;
import org.springframework.retry.annotation.Recover;
import org.springframework.retry.annotation.Retryable;

import java.net.ConnectException;

@Slf4j
@Configuration
@EnableRetry
@RequiredArgsConstructor
@Profile("!test")
public class MinioConfig {

    @Value("${s3.endpoint}")
    private String endpoint;

    @Value("${s3.access-key}")
    private String accessKey;

    @Value("${s3.secret-key}")
    private String secretKey;

    @Value("${s3.bucket}")
    private String bucket;

    /**
     * Создает и настраивает MinioClient с поддержкой повторных попыток
     */
    @Bean
    public MinioClient minioClient() {
        log.info("Инициализация MinIO клиента с параметрами: endpoint={}, bucket={}", endpoint, bucket);

        MinioClient minioClient = MinioClient.builder()
                .endpoint(endpoint)
                .credentials(accessKey, secretKey)
                .build();

        try {
            initializeBucket(minioClient);
        } catch (Exception e) {
            log.error(
                    "Ошибка при инициализации MinIO бакета: {}. Будет использоваться клиент без инициализации бакета.",
                    e.getMessage());
        }

        return minioClient;
    }

    /**
     * Инициализирует бакет с поддержкой повторных попыток при ошибках соединения
     */
    @Retryable(value = { ConnectException.class }, maxAttempts = 3, backoff = @Backoff(delay = 1000, multiplier = 2))
    private void initializeBucket(MinioClient minioClient) throws Exception {
        boolean exists = minioClient.bucketExists(BucketExistsArgs.builder().bucket(bucket).build());
        if (!exists) {
            minioClient.makeBucket(MakeBucketArgs.builder().bucket(bucket).build());
            log.info("Бакет '{}' успешно создан", bucket);
        } else {
            log.info("Бакет '{}' уже существует", bucket);
        }
    }

    /**
     * Вызывается, когда все повторные попытки исчерпаны
     */
    @Recover
    private void recoverFromConnectionFailure(ConnectException e, MinioClient minioClient) {
        log.warn("Не удалось подключиться к MinIO после нескольких попыток. " +
                "Убедитесь, что MinIO сервис доступен по адресу: {}", endpoint);
    }
}