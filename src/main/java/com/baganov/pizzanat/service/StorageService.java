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

import java.io.IOException;
import java.io.InputStream;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

@Slf4j
@Service
@RequiredArgsConstructor
public class StorageService {

    private final MinioClient minioClient;
    private final UrlTransformer urlTransformer;

    @Value("${s3.bucket}")
    private String bucket;

    /**
     * Загрузка файла в хранилище S3
     *
     * @param file   файл для загрузки
     * @param prefix префикс пути (папка)
     * @return имя файла в хранилище
     */
    @Retryable(value = { Exception.class }, maxAttempts = 10, backoff = @Backoff(delay = 3000))
    public String uploadFile(MultipartFile file, String prefix) {
        try {
            String originalFilename = file.getOriginalFilename();
            String extension = originalFilename != null ? originalFilename.substring(originalFilename.lastIndexOf("."))
                    : "";
            String objectName = prefix + "/" + UUID.randomUUID() + extension;

            minioClient.putObject(
                    PutObjectArgs.builder()
                            .bucket(bucket)
                            .object(objectName)
                            .contentType(file.getContentType())
                            .stream(file.getInputStream(), file.getSize(), -1)
                            .build());

            log.info("File uploaded successfully: {}", objectName);
            return objectName;
        } catch (Exception e) {
            log.error("Error uploading file: {}", e.getMessage(), e);
            throw new RuntimeException("Error uploading file", e);
        }
    }

    /**
     * Получение временной ссылки на файл
     *
     * @param objectName имя файла в хранилище
     * @param expiryTime время жизни ссылки в секундах
     * @return временная ссылка на файл
     */
    public String getPresignedUrl(String objectName, int expiryTime) {
        try {
            String presignedUrl = minioClient.getPresignedObjectUrl(
                    GetPresignedObjectUrlArgs.builder()
                            .bucket(bucket)
                            .object(objectName)
                            .method(Method.GET)
                            .expiry(expiryTime, TimeUnit.SECONDS)
                            .build());

            // Применяем трансформацию URL, если нужно
            return urlTransformer.transform(presignedUrl);
        } catch (Exception e) {
            log.error("Error generating presigned URL: {}", e.getMessage(), e);
            throw new RuntimeException("Error generating presigned URL", e);
        }
    }

    /**
     * Удаление файла
     *
     * @param objectName имя файла в хранилище
     */
    public void deleteFile(String objectName) {
        try {
            minioClient.removeObject(
                    RemoveObjectArgs.builder()
                            .bucket(bucket)
                            .object(objectName)
                            .build());
            log.info("File deleted successfully: {}", objectName);
        } catch (Exception e) {
            log.error("Error deleting file: {}", e.getMessage(), e);
            throw new RuntimeException("Error deleting file", e);
        }
    }

    @Recover
    public void recover(Exception e) {
        // обработка ситуации, если все попытки не удались
        // например, логирование или graceful degradation
    }
}