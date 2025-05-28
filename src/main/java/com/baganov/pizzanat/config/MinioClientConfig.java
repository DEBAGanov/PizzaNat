/**
 * @file: MinioClientConfig.java
 * @description: Конфигурация клиента MinIO с проксированием URL для внешнего доступа
 * @dependencies: MinIO
 * @created: 2025-05-23
 */
package com.baganov.pizzanat.config;

import io.minio.*;
import io.minio.messages.Bucket;
import io.minio.messages.Item;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.context.annotation.Profile;
import org.springframework.core.env.Environment;
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
public class MinioClientConfig {

    private final Environment environment;

    @Value("${s3.endpoint:#{null}}")
    private String devEndpoint;

    @Value("${timeweb.s3.endpoint:#{null}}")
    private String prodEndpoint;

    @Value("${s3.access-key:#{null}}")
    private String devAccessKey;

    @Value("${timeweb.s3.access-key:#{null}}")
    private String prodAccessKey;

    @Value("${s3.secret-key:#{null}}")
    private String devSecretKey;

    @Value("${timeweb.s3.secret-key:#{null}}")
    private String prodSecretKey;

    @Value("${s3.bucket:#{null}}")
    private String devBucket;

    @Value("${timeweb.s3.bucket:#{null}}")
    private String prodBucket;

    @Value("${s3.public-url:#{null}}")
    private String devPublicUrl;

    @Value("${timeweb.s3.public-url:#{null}}")
    private String prodPublicUrl;

    private boolean isProd() {
        for (String profile : environment.getActiveProfiles()) {
            if (profile.equals("prod")) {
                return true;
            }
        }
        return false;
    }

    private String getEndpoint() {
        String endpoint = isProd() ? prodEndpoint : devEndpoint;
        log.debug("Using endpoint: {} (isProd: {})", endpoint, isProd());
        return endpoint;
    }

    private String getAccessKey() {
        String accessKey = isProd() ? prodAccessKey : devAccessKey;
        log.debug("Using access key: {} (isProd: {})", accessKey, isProd());
        return accessKey;
    }

    private String getSecretKey() {
        String secretKey = isProd() ? prodSecretKey : devSecretKey;
        log.debug("Using secret key: {} (isProd: {})", secretKey, isProd());
        return secretKey;
    }

    private String getBucket() {
        String bucket = isProd() ? prodBucket : devBucket;
        log.debug("Using bucket: {} (isProd: {})", bucket, isProd());
        return bucket;
    }

    private String getPublicUrl() {
        return isProd() ? prodPublicUrl : devPublicUrl;
    }

    @Bean
    @Primary
    public MinioClient minioClient() {
        String endpoint = getEndpoint();
        String accessKey = getAccessKey();
        String secretKey = getSecretKey();

        log.info("Initializing MinIO client with endpoint: {}, accessKey: {}", endpoint, accessKey);

        try {
            MinioClient minioClient = MinioClient.builder()
                    .endpoint(endpoint)
                    .credentials(accessKey, secretKey)
                    .build();

            // Проверяем подключение
            minioClient.listBuckets();
            log.info("Successfully connected to MinIO/S3");

            String bucket = getBucket();
            log.info("Checking if bucket exists: {}", bucket);

            boolean bucketExists = minioClient.bucketExists(BucketExistsArgs.builder().bucket(bucket).build());
            log.info("Bucket {} exists: {}", bucket, bucketExists);

            if (!bucketExists) {
                minioClient.makeBucket(MakeBucketArgs.builder().bucket(bucket).build());
                log.info("Created new bucket: {}", bucket);
            }

            // Устанавливаем политику доступа к бакету
            String publicPolicy = String.format("""
                    {
                        "Version": "2012-10-17",
                        "Statement": [
                            {
                                "Sid": "PublicReadGetObject",
                                "Effect": "Allow",
                                "Principal": "*",
                                "Action": [
                                    "s3:GetObject",
                                    "s3:PutObject",
                                    "s3:DeleteObject",
                                    "s3:ListBucket"
                                ],
                                "Resource": [
                                    "arn:aws:s3:::%s/*",
                                    "arn:aws:s3:::%s"
                                ]
                            }
                        ]
                    }
                    """, bucket, bucket);

            minioClient.setBucketPolicy(
                    SetBucketPolicyArgs.builder()
                            .bucket(bucket)
                            .config(publicPolicy)
                            .build());
            log.info("Bucket policy set successfully for: {}", bucket);

            // Проверяем текущую политику
            String currentPolicy = minioClient.getBucketPolicy(
                    GetBucketPolicyArgs.builder()
                            .bucket(bucket)
                            .build());
            log.info("Current bucket policy: {}", currentPolicy);

            // Проверяем, что можем листать объекты
            minioClient.listObjects(
                    ListObjectsArgs.builder()
                            .bucket(bucket)
                            .recursive(true)
                            .build());
            log.info("Successfully listed objects in bucket: {}", bucket);

            // Настраиваем проксирование URL
            String publicUrl = getPublicUrl();
            log.info("Настройка проксирования MinIO URL: {} -> {}", endpoint, publicUrl);

            return minioClient;
        } catch (Exception e) {
            log.error("Failed to initialize MinIO client: {}", e.getMessage(), e);
            throw new RuntimeException("Failed to initialize MinIO client", e);
        }
    }

    /**
     * Перезаписывает URL для presignedUrl в StorageService, заменяя внутренний URL
     * на публичный
     *
     * @return функция трансформации URL
     */
    @Bean
    public UrlTransformer minioUrlTransformer() {
        String endpoint = getEndpoint();
        String publicUrl = getPublicUrl();

        if (publicUrl != null && !publicUrl.isEmpty()) {
            // Убираем завершающий слэш из public URL, чтобы избежать двойных слэшей
            String normalizedPublicUrl = publicUrl.endsWith("/") ? publicUrl.substring(0, publicUrl.length() - 1)
                    : publicUrl;
            log.info("Настройка проксирования MinIO URL: {} -> {}", endpoint, normalizedPublicUrl);
            return url -> {
                // Более точная замена URL: заменяем endpoint на normalized public URL
                String result = url.replace(endpoint, normalizedPublicUrl);

                // Дополнительная проверка и исправление двойных слэшей
                result = result.replaceAll("([^:])/+", "$1/");

                log.debug("Трансформирован URL: {} -> {}", url, result);
                return result;
            };
        }

        log.info("Проксирование MinIO URL отключено, URL используются без изменений");
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