package com.baganov.pizzanat.service;

import com.baganov.pizzanat.config.S3Config;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;

import java.io.IOException;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class S3Service {

    private final S3Config s3Config;

    public String uploadImage(MultipartFile file, String folder) {
        String fileName = generateFileName(file);
        String key = folder + "/" + fileName;

        try {
            PutObjectRequest putObjectRequest = PutObjectRequest.builder()
                    .bucket(s3Config.getBucketName())
                    .key(key)
                    .contentType(file.getContentType())
                    .build();

            s3Config.s3Client().putObject(putObjectRequest,
                    RequestBody.fromInputStream(file.getInputStream(), file.getSize()));

            return generatePublicUrl(key);
        } catch (IOException e) {
            throw new RuntimeException("Ошибка при загрузке файла в S3", e);
        }
    }

    private String generateFileName(MultipartFile file) {
        return UUID.randomUUID() + "_" + file.getOriginalFilename();
    }

    private String generatePublicUrl(String key) {
        return s3Config.getPublicUrl() + "/" + key;
    }
}