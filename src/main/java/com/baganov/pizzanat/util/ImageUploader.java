package com.baganov.pizzanat.util;

import com.baganov.pizzanat.entity.Category;
import com.baganov.pizzanat.entity.Product;
import com.baganov.pizzanat.repository.CategoryRepository;
import com.baganov.pizzanat.repository.ProductRepository;
import com.baganov.pizzanat.service.StorageService;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.annotation.Profile;
import org.springframework.context.event.EventListener;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StreamUtils;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.math.BigDecimal;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

@Slf4j
@Component
@Profile("!test")
@RequiredArgsConstructor
public class ImageUploader {

    private final ProductRepository productRepository;
    private final CategoryRepository categoryRepository;
    private final StorageService storageService;

    private static final String RESOURCE_BASE_PATH = "static/images/";
    private static final String PRODUCTS_FOLDER = "products";
    private static final Map<String, String> PRODUCT_NAMES = new HashMap<>();
    private static final long MAX_IMAGE_SIZE = 10 * 1024 * 1024; // 10MB

    static {
        PRODUCT_NAMES.put("margarita", "Пицца Маргарита");
        PRODUCT_NAMES.put("peperoni", "Пицца Пепперони");
        PRODUCT_NAMES.put("gavaiyaskay", "Гавайская пицца");
        PRODUCT_NAMES.put("4_chees", "Сырная пицца");
        PRODUCT_NAMES.put("5_chees", "Пицца 5 сыров");
        PRODUCT_NAMES.put("mzysnay", "Мясная пицца");
        PRODUCT_NAMES.put("mario", "Пицца Марио");
        PRODUCT_NAMES.put("karbonara", "Пицца Карбонара");
        PRODUCT_NAMES.put("gribnaya", "Грибная пицца");
        PRODUCT_NAMES.put("tom_yam", "Пицца Том Ям");
    }

    @PostConstruct
    public void init() {
        try {
            // Проверяем и создаем директории для ресурсов
            createResourceDirectories();
        } catch (Exception e) {
            log.error("Ошибка при инициализации директорий ресурсов: {}", e.getMessage(), e);
        }
    }

    private void createResourceDirectories() {
        try {
            // Получаем путь к ресурсам в classpath
            ClassPathResource baseResource = new ClassPathResource(RESOURCE_BASE_PATH);
            if (!baseResource.exists()) {
                log.warn("Директория ресурсов не найдена: {}", RESOURCE_BASE_PATH);
                // Создаем директории
                String fullPath = "src/main/resources/" + RESOURCE_BASE_PATH;
                java.nio.file.Files.createDirectories(java.nio.file.Paths.get(fullPath));
                java.nio.file.Files.createDirectories(java.nio.file.Paths.get(fullPath + "categories"));
                java.nio.file.Files.createDirectories(java.nio.file.Paths.get(fullPath + PRODUCTS_FOLDER));
                log.info("Созданы директории для ресурсов: {}", fullPath);
            }
        } catch (Exception e) {
            log.error("Ошибка при создании директорий ресурсов: {}", e.getMessage(), e);
            throw new RuntimeException("Не удалось создать директории для ресурсов", e);
        }
    }

    private ClassPathResource getResource(String path) {
        String resourcePath = RESOURCE_BASE_PATH + path;
        ClassPathResource resource = new ClassPathResource(resourcePath);

        if (!resource.exists()) {
            log.error("Ресурс не найден: {}", resourcePath);
            throw new RuntimeException("Ресурс не найден: " + resourcePath);
        }

        return resource;
    }

    @EventListener(ApplicationReadyEvent.class)
    @Transactional
    public void initializeProductData() {
        try {
            log.info("Начало инициализации данных продуктов");
            syncProductData();
            log.info("Инициализация данных продуктов завершена успешно");
        } catch (Exception e) {
            log.error("Ошибка при инициализации данных о продуктах: {}", e.getMessage(), e);
            if (e.getCause() != null) {
                log.error("Причина ошибки: {}", e.getCause().getMessage());
            }
        }
    }

    private boolean validateImageSize(byte[] imageData, String imageName) {
        if (imageData.length > MAX_IMAGE_SIZE) {
            log.error("Изображение {} слишком большое: {} байт (максимум: {} байт)",
                    imageName, imageData.length, MAX_IMAGE_SIZE);
            return false;
        }
        return true;
    }

    @Transactional
    public void syncProductData() {
        log.info("Начало синхронизации данных о продуктах и категориях");

        // Сначала синхронизируем категории
        Map<String, String> categoryImageMap = new HashMap<>();
        categoryImageMap.put("Пиццы", "categories/pizza.png");
        categoryImageMap.put("Бургеры", "categories/burghers.png");
        categoryImageMap.put("Напитки", "categories/drinks.png");
        categoryImageMap.put("Десерты", "categories/desserts.png");
        categoryImageMap.put("Закуски", "categories/snacks.png");

        // Загружаем изображения категорий
        for (Map.Entry<String, String> entry : categoryImageMap.entrySet()) {
            String categoryName = entry.getKey();
            String imageUrl = entry.getValue();

            try {
                // Загружаем изображение в S3
                ClassPathResource resource = new ClassPathResource("static/images/" + imageUrl);
                try (InputStream inputStream = resource.getInputStream()) {
                    byte[] imageData = StreamUtils.copyToByteArray(inputStream);

                    if (validateImageSize(imageData, imageUrl)) {
                        // Загружаем в S3
                        storageService.uploadFile(
                                new ByteArrayInputStream(imageData),
                                imageUrl,
                                "image/png",
                                imageData.length);

                        // Обновляем URL в базе данных
                        Optional<Category> categoryOpt = categoryRepository.findByName(categoryName);
                        if (categoryOpt.isPresent()) {
                            Category category = categoryOpt.get();
                            String fullImageUrl = storageService.getFullPublicUrl(imageUrl);
                            category.setImageUrl(fullImageUrl);
                            categoryRepository.save(category);
                            log.info("Обновлен URL изображения для категории {}: {}", categoryName, fullImageUrl);
                        } else {
                            log.warn("Категория не найдена в базе данных: {}", categoryName);
                        }
                    }
                }
            } catch (Exception e) {
                log.error("Ошибка при загрузке изображения для категории {}: {}", categoryName, e.getMessage(), e);
            }
        }

        // Затем синхронизируем продукты
        Map<String, String> productImageMap = new HashMap<>();
        productImageMap.put("Пицца Маргарита", "products/pizza_margarita.png");
        productImageMap.put("Пицца 4 сыра", "products/pizza_4_chees.png");
        productImageMap.put("Пицца 5 сыров", "products/pizza_5_chees.png");
        productImageMap.put("Пицца Пепперони", "products/pizza_peperoni.png");
        productImageMap.put("Гавайская пицца", "products/pizza_gavaiyaskay.png");
        productImageMap.put("Сырная пицца", "products/pizza_4_chees.png");
        productImageMap.put("Мясная пицца", "products/pizza_mzysnay.png");
        productImageMap.put("Пицца Марио", "products/pizza_mario.png");
        productImageMap.put("Пицца Карбонара", "products/pizza_karbonara.png");
        productImageMap.put("Грибная пицца", "products/pizza_gribnaya.png");
        productImageMap.put("Пицца Том Ям", "products/pizza_tom_yam.png");

        // Загружаем изображения продуктов
        for (Map.Entry<String, String> entry : productImageMap.entrySet()) {
            String productName = entry.getKey();
            String imageUrl = entry.getValue();

            try {
                // Загружаем изображение в S3
                ClassPathResource resource = new ClassPathResource("static/images/" + imageUrl);
                try (InputStream inputStream = resource.getInputStream()) {
                    byte[] imageData = StreamUtils.copyToByteArray(inputStream);

                    if (validateImageSize(imageData, imageUrl)) {
                        // Загружаем в S3
                        storageService.uploadFile(
                                new ByteArrayInputStream(imageData),
                                imageUrl,
                                "image/png",
                                imageData.length);

                        // Обновляем URL в базе данных
                        Optional<Product> productOpt = productRepository.findByName(productName);
                        if (productOpt.isPresent()) {
                            Product product = productOpt.get();
                            String fullImageUrl = storageService.getFullPublicUrl(imageUrl);
                            product.setImageUrl(fullImageUrl);
                            productRepository.save(product);
                            log.info("Обновлен URL изображения для продукта {}: {}", productName, fullImageUrl);
                        } else {
                            log.warn("Продукт не найден в базе данных: {}", productName);
                        }
                    }
                }
            } catch (Exception e) {
                log.error("Ошибка при загрузке изображения для продукта {}: {}", productName, e.getMessage(), e);
            }
        }

        log.info("Завершена синхронизация данных о продуктах и категориях");
    }
}
