package com.baganov.pizzanat.service;

import com.baganov.pizzanat.entity.Product;
import com.baganov.pizzanat.repository.ProductRepository;
import com.baganov.pizzanat.util.ImageUploader;
import io.minio.ListObjectsArgs;
import io.minio.MinioClient;
import io.minio.Result;
import io.minio.messages.Item;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.retry.annotation.Backoff;
import org.springframework.retry.annotation.Recover;
import org.springframework.retry.annotation.Retryable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.net.ConnectException;
import java.util.HashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Slf4j
@Service
@RequiredArgsConstructor
public class InitService {

    private final MinioClient minioClient;
    private final ProductRepository productRepository;
    private final ImageUploader imageUploader;

    @Value("${s3.bucket}")
    private String bucket;

    private static final Pattern PRODUCT_NAME_PATTERN = Pattern.compile("pizza_(.*?)\\.png");

    // Соответствие имен файлов реальным названиям продуктов
    private static final Map<String, String> PRODUCT_NAMES = new HashMap<>();

    static {
        PRODUCT_NAMES.put("margarita", "Пицца Маргарита");
        PRODUCT_NAMES.put("peperoni", "Пицца Пепперони");
        PRODUCT_NAMES.put("gavaiyaskay", "Гавайская пицца");
        PRODUCT_NAMES.put("chees", "Сырная пицца");
        PRODUCT_NAMES.put("5_chees", "Пицца 5 сыров");
        PRODUCT_NAMES.put("mzysnay", "Мясная пицца");
        PRODUCT_NAMES.put("mario", "Пицца Марио");
        PRODUCT_NAMES.put("karbonara", "Пицца Карбонара");
        PRODUCT_NAMES.put("gribnaya", "Грибная пицца");
        PRODUCT_NAMES.put("tom_yam", "Пицца Том Ям");
    }

    /**
     * Инициализирует данные при запуске приложения
     */
    @EventListener(ApplicationReadyEvent.class)
    @Transactional
    public void onApplicationStartup() {
        // Сначала синхронизируем данные о продуктах
        imageUploader.syncProductData();

        // Затем проверяем состояние изображений в S3
        try {
            initializeProductImages();
        } catch (Exception e) {
            log.error("Ошибка при проверке изображений продуктов в S3: {}", e.getMessage(), e);
            if (e.getCause() != null) {
                log.error("Причина ошибки: {}", e.getCause().getMessage());
            }
        }
    }

    /**
     * Проверяет состояние изображений продуктов в S3
     */
    @Retryable(value = { ConnectException.class }, maxAttempts = 3, backoff = @Backoff(delay = 2000, multiplier = 1.5))
    private void initializeProductImages() throws Exception {
        log.info("Проверка состояния изображений продуктов в S3");

        try {
            // Получаем список всех объектов в директории products
            Iterable<Result<Item>> results = minioClient.listObjects(
                    ListObjectsArgs.builder()
                            .bucket(bucket)
                            .prefix("products/")
                            .recursive(true)
                            .build());

            int count = 0;
            for (Result<Item> result : results) {
                Item item = result.get();
                String objectName = item.objectName();
                log.info("Найден объект в S3: {}", objectName);
                count++;
            }

            log.info("Проверка завершена. Найдено {} объектов в директории products/", count);
        } catch (Exception e) {
            log.error("Ошибка при листинге объектов в S3: {}", e.getMessage());
            throw e;
        }
    }

    /**
     * Метод восстановления после неудачных попыток
     */
    @Recover
    private void recoverFromConnectionFailure(ConnectException e) {
        log.error("Не удалось подключиться к S3 после нескольких попыток: {}", e.getMessage());
    }
}
