package com.baganov.pizzanat.util;

import com.baganov.pizzanat.model.entity.Category;
import com.baganov.pizzanat.model.entity.Product;
import com.baganov.pizzanat.repository.CategoryRepository;
import com.baganov.pizzanat.repository.ProductRepository;
import com.baganov.pizzanat.service.StorageService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.annotation.Profile;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

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

    @EventListener(ApplicationReadyEvent.class)
    @Transactional
    public void initializeProductData() {
        try {
            syncProductData();
        } catch (Exception e) {
            log.error("Ошибка при инициализации данных о продуктах", e);
        }
    }

    /**
     * Синхронизирует данные о продуктах с базой данных
     */
    @Transactional
    public void syncProductData() {
        log.info("Начало синхронизации данных о продуктах");

        // Метод findByName может вернуть Optional.empty, обрабатываем этот случай
        Optional<Category> pizzaCategoryOpt = categoryRepository.findByName("Пиццы");

        Category pizzaCategory;
        if (pizzaCategoryOpt.isPresent()) {
            pizzaCategory = pizzaCategoryOpt.get();
        } else {
            // Создаем категорию, если она не существует
            pizzaCategory = new Category();
            pizzaCategory.setName("Пиццы");
            pizzaCategory.setDescription("Вкусные и ароматные пиццы");
            pizzaCategory.setImageUrl("categories/pizza.png");
            pizzaCategory.setDisplayOrder(1);
            pizzaCategory.setActive(true);
            pizzaCategory = categoryRepository.save(pizzaCategory);
        }

        // Перебираем все известные продукты
        for (Map.Entry<String, String> entry : PRODUCT_NAMES.entrySet()) {
            String productKey = entry.getKey();
            String productName = entry.getValue();
            String imageUrl = "products/pizza_" + productKey + ".png";

            // Проверяем, существует ли продукт с таким именем
            if (!productRepository.existsByName(productName)) {
                log.info("Добавление нового продукта: {}, изображение: {}", productName, imageUrl);

                Product product = new Product();
                product.setName(productName);
                product.setDescription("Вкусная " + productName.toLowerCase());
                product.setPrice(new BigDecimal("499.00")); // Используем BigDecimal
                product.setWeight(450); // Базовый вес в граммах
                product.setImageUrl(imageUrl);
                product.setAvailable(true);
                product.setCategory(pizzaCategory);

                // Добавляем скидку для некоторых продуктов
                if ("Пицца Пепперони".equals(productName) || "Пицца Марио".equals(productName)) {
                    product.setSpecialOffer(true);
                    product.setDiscountPercent(15);
                    product.setDiscountedPrice(new BigDecimal("424.15")); // 499.00 * 0.85
                }

                productRepository.save(product);
            }
        }

        log.info("Синхронизация данных о продуктах завершена");
    }
}
