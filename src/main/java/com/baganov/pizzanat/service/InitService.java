package com.baganov.pizzanat.service;

import com.baganov.pizzanat.model.entity.Product;
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
     * Инициализирует изображения продуктов при запуске приложения
     */
    @EventListener(ApplicationReadyEvent.class)
    @Transactional
    public void onApplicationStartup() {
        try {
            initializeProductImages();
        } catch (Exception e) {
            log.error("Ошибка при инициализации изображений продуктов", e);
        }

        // Инициализация данных о продуктах в любом случае
        imageUploader.syncProductData();
    }

    /**
     * Пытается синхронизировать изображения продуктов с повторными попытками
     */
    @Retryable(value = { ConnectException.class }, maxAttempts = 3, backoff = @Backoff(delay = 2000, multiplier = 1.5))
    private void initializeProductImages() throws Exception {
        log.info("Начало синхронизации изображений продуктов");

        // Получаем список всех объектов в директории products
        Iterable<Result<Item>> results = minioClient.listObjects(
                ListObjectsArgs.builder()
                        .bucket(bucket)
                        .prefix("products/")
                        .recursive(true)
                        .build());

        for (Result<Item> result : results) {
            Item item = result.get();
            String objectName = item.objectName();

            // Извлекаем имя продукта из имени файла
            Matcher matcher = PRODUCT_NAME_PATTERN.matcher(objectName);
            if (matcher.find()) {
                String productKey = matcher.group(1);
                String productName = PRODUCT_NAMES.getOrDefault(productKey, "Пицца " + productKey);

                // Проверяем, существует ли продукт с таким изображением
                boolean exists = productRepository.existsByImageUrl(objectName);

                if (!exists) {
                    log.info("Добавление нового продукта: {}, изображение: {}", productName, objectName);

                    // Создаем новый продукт если его нет
                    Product product = new Product();
                    product.setName(productName);
                    product.setDescription("Вкусная " + productName.toLowerCase());
                    product.setPrice(new BigDecimal("499.00"));
                    product.setWeight(450); // Базовый вес в граммах
                    product.setImageUrl(objectName);
                    product.setAvailable(true);

                    // Сохраняем продукт
                    productRepository.save(product);
                }
            }
        }

        log.info("Синхронизация изображений продуктов завершена");
    }

    /**
     * Метод восстановления после неудачных попыток инициализации
     */
    @Recover
    private void recoverFromConnectionFailure(ConnectException e) {
        log.error("Не удалось подключиться к MinIO после нескольких попыток. " +
                "Изображения продуктов не будут синхронизированы.", e);
    }
}
