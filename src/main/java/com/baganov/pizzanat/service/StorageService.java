package com.baganov.pizzanat.service;

import com.baganov.pizzanat.config.MinioClientConfig.UrlTransformer;
import io.minio.*;
import io.minio.errors.*;
import io.minio.http.Method;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.retry.annotation.Backoff;
import org.springframework.retry.annotation.Retryable;
import org.springframework.retry.annotation.Recover;
import org.springframework.context.annotation.Lazy;
import org.springframework.core.env.Environment;

import java.io.IOException;
import java.io.InputStream;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.io.ByteArrayInputStream;

@Slf4j
@Service
@RequiredArgsConstructor
public class StorageService {

    private final MinioClient minioClient;
    private final UrlTransformer urlTransformer;
    private final Environment environment;

    @Value("${s3.bucket:#{null}}")
    private String devBucket;

    @Value("${timeweb.s3.bucket:#{null}}")
    private String prodBucket;

    @Value("${s3.public-url:#{null}}")
    private String devPublicUrl;

    @Value("${timeweb.s3.public-url:#{null}}")
    private String prodPublicUrl;

    @Value("${minio.bucket}")
    private String bucket;

    @Value("${minio.public-url}")
    private String publicUrl;

    private String getBucket() {
        String bucket = isProd() ? prodBucket : devBucket;
        log.debug("Using bucket: {} (isProd: {})", bucket, isProd());
        return bucket;
    }

    public String getPublicUrl() {
        return publicUrl;
    }

    public String getFullPublicUrl(String objectName) {
        return publicUrl + "/" + bucket + "/" + objectName;
    }

    private boolean isProd() {
        for (String profile : environment.getActiveProfiles()) {
            if (profile.equals("prod")) {
                return true;
            }
        }
        return false;
    }

    /**
     * Загрузка файла в хранилище S3
     */
    @Retryable(value = { Exception.class }, maxAttempts = 3, backoff = @Backoff(delay = 2000))
    public String uploadFile(MultipartFile file, String prefix) {
        try {
            String originalFilename = file.getOriginalFilename();
            String extension = originalFilename != null ? originalFilename.substring(originalFilename.lastIndexOf("."))
                    : "";
            String objectName = prefix + "/" + UUID.randomUUID() + extension;

            log.info("Uploading file to S3. Bucket: {}, Object: {}, Size: {} bytes",
                    getBucket(), objectName, file.getSize());

            minioClient.putObject(
                    PutObjectArgs.builder()
                            .bucket(getBucket())
                            .object(objectName)
                            .contentType(file.getContentType())
                            .stream(file.getInputStream(), file.getSize(), -1)
                            .build());

            log.info("File uploaded successfully: {}", objectName);
            return objectName;
        } catch (Exception e) {
            log.error("Error uploading file: {}", e.getMessage(), e);
            if (e.getCause() != null) {
                log.error("Cause: {}", e.getCause().getMessage());
            }
            throw new RuntimeException("Error uploading file", e);
        }
    }

    /**
     * Загрузка файла из InputStream в хранилище S3
     */
    @Retryable(value = { Exception.class }, maxAttempts = 3, backoff = @Backoff(delay = 2000))
    public void uploadFile(InputStream inputStream, String objectName, String contentType, long size) {
        try {
            log.info("Uploading file to S3. Bucket: {}, Object: {}, Size: {} bytes, Content-Type: {}",
                    bucket, objectName, size, contentType);

            // Создаем новый InputStream для каждой попытки
            byte[] data = inputStream.readAllBytes();

            PutObjectArgs args = PutObjectArgs.builder()
                    .bucket(bucket)
                    .object(objectName)
                    .contentType(contentType)
                    .stream(new ByteArrayInputStream(data), size, -1)
                    .build();

            minioClient.putObject(args);
            log.info("File uploaded successfully: {}/{}", bucket, objectName);

            // Проверяем, что файл действительно загружен
            StatObjectResponse stat = minioClient.statObject(
                    StatObjectArgs.builder()
                            .bucket(bucket)
                            .object(objectName)
                            .build());
            log.info("File status check successful. Size: {}, LastModified: {}",
                    stat.size(), stat.lastModified());

        } catch (Exception e) {
            log.error("Error uploading file to S3: {}", e.getMessage(), e);
            throw new RuntimeException("Failed to upload file to S3", e);
        }
    }

    /**
     * Получение временной ссылки на файл
     */
    public String getPresignedUrl(String objectName, int expiryTime) {
        try {
            // Для изображений продуктов и категорий используем публичные URL
            if (objectName.startsWith("products/") || objectName.startsWith("categories/")) {
                return getPublicUrl(objectName);
            }

            String presignedUrl = minioClient.getPresignedObjectUrl(
                    GetPresignedObjectUrlArgs.builder()
                            .bucket(getBucket())
                            .object(objectName)
                            .method(Method.GET)
                            .expiry(expiryTime, TimeUnit.SECONDS)
                            .build());

            // Применяем трансформацию URL, если нужно
            String transformedUrl = urlTransformer.transform(presignedUrl);
            log.debug("Generated presigned URL: {} -> {}", presignedUrl, transformedUrl);
            return transformedUrl;
        } catch (Exception e) {
            log.error("Error generating presigned URL for {}: {}", objectName, e.getMessage(), e);
            if (e.getCause() != null) {
                log.error("Cause: {}", e.getCause().getMessage());
            }
            throw new RuntimeException("Error generating presigned URL", e);
        }
    }

    /**
     * Получение публичного URL для файла
     */
    public String getPublicUrl(String objectName) {
        String publicUrl = getPublicUrl();
        String url = publicUrl + "/" + objectName;
        log.debug("Generated public URL: {}", url);
        return url;
    }

    /**
     * Удаление файла
     */
    @Retryable(value = { Exception.class }, maxAttempts = 3, backoff = @Backoff(delay = 2000))
    public void deleteFile(String objectName) {
        try {
            log.info("Deleting file from S3. Bucket: {}, Object: {}", getBucket(), objectName);

            minioClient.removeObject(
                    RemoveObjectArgs.builder()
                            .bucket(getBucket())
                            .object(objectName)
                            .build());

            log.info("File deleted successfully: {}", objectName);
        } catch (Exception e) {
            log.error("Error deleting file {}: {}", objectName, e.getMessage(), e);
            if (e.getCause() != null) {
                log.error("Cause: {}", e.getCause().getMessage());
            }
            throw new RuntimeException("Error deleting file", e);
        }
    }

    @Recover
    public void recover(Exception e) {
        log.error("All retry attempts failed: {}", e.getMessage(), e);
        if (e.getCause() != null) {
            log.error("Root cause: {}", e.getCause().getMessage());
        }
    }
}