package com.baganov.pizzanat.config;

import software.amazon.awssdk.services.s3.S3Client;

public interface S3Config {
    S3Client s3Client();

    String getBucketName();

    String getPublicUrl();
}