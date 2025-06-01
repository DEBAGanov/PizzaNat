/**
 * @file: AdminProductService.java
 * @description: Сервис для административного управления продуктами
 * @dependencies: Spring Data JPA, Spring Transactions
 * @created: 2025-05-31
 */
package com.baganov.pizzanat.service;

import com.baganov.pizzanat.entity.Category;
import com.baganov.pizzanat.entity.Product;
import com.baganov.pizzanat.model.dto.product.CreateProductRequest;
import com.baganov.pizzanat.model.dto.product.ProductDTO;
import com.baganov.pizzanat.model.dto.product.UpdateProductRequest;
import com.baganov.pizzanat.repository.CategoryRepository;
import com.baganov.pizzanat.repository.ProductRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class AdminProductService {

    private final ProductRepository productRepository;
    private final CategoryRepository categoryRepository;
    private final StorageService storageService;

    @Transactional
    public ProductDTO createProduct(CreateProductRequest request) {
        log.info("Создание продукта: {}", request.getName());

        // Проверяем существование категории
        Category category = categoryRepository.findById(request.getCategoryId())
                .orElseThrow(
                        () -> new IllegalArgumentException("Категория не найдена с ID: " + request.getCategoryId()));

        // Проверяем уникальность имени
        if (productRepository.existsByName(request.getName())) {
            throw new IllegalArgumentException("Продукт с таким именем уже существует");
        }

        Product product = Product.builder()
                .name(request.getName())
                .description(request.getDescription())
                .price(request.getPrice())
                .discountedPrice(request.getDiscountedPrice())
                .category(category)
                .imageUrl(request.getImageUrl())
                .weight(request.getWeight())
                .isAvailable(request.getIsAvailable() != null ? request.getIsAvailable() : true)
                .isSpecialOffer(request.getIsSpecialOffer() != null ? request.getIsSpecialOffer() : false)
                .discountPercent(request.getDiscountPercent())
                .build();

        Product savedProduct = productRepository.save(product);
        log.info("Продукт создан с ID: {}", savedProduct.getId());

        return mapToDTO(savedProduct);
    }

    @Transactional
    public ProductDTO updateProduct(Integer productId, UpdateProductRequest request) {
        log.info("Обновление продукта с ID: {}", productId);

        Product product = productRepository.findById(productId)
                .orElseThrow(() -> new IllegalArgumentException("Продукт не найден с ID: " + productId));

        // Проверяем существование категории
        Category category = categoryRepository.findById(request.getCategoryId())
                .orElseThrow(
                        () -> new IllegalArgumentException("Категория не найдена с ID: " + request.getCategoryId()));

        // Проверяем уникальность имени (исключая текущий продукт)
        if (!product.getName().equals(request.getName()) && productRepository.existsByName(request.getName())) {
            throw new IllegalArgumentException("Продукт с таким именем уже существует");
        }

        product.setName(request.getName());
        product.setDescription(request.getDescription());
        product.setPrice(request.getPrice());
        product.setDiscountedPrice(request.getDiscountedPrice());
        product.setCategory(category);

        if (request.getImageUrl() != null) {
            product.setImageUrl(request.getImageUrl());
        }

        if (request.getWeight() != null) {
            product.setWeight(request.getWeight());
        }

        if (request.getIsAvailable() != null) {
            product.setAvailable(request.getIsAvailable());
        }

        if (request.getIsSpecialOffer() != null) {
            product.setSpecialOffer(request.getIsSpecialOffer());
        }

        if (request.getDiscountPercent() != null) {
            product.setDiscountPercent(request.getDiscountPercent());
        }

        Product savedProduct = productRepository.save(product);
        log.info("Продукт обновлен с ID: {}", savedProduct.getId());

        return mapToDTO(savedProduct);
    }

    @Transactional
    public void deleteProduct(Integer productId) {
        log.info("Удаление продукта с ID: {}", productId);

        Product product = productRepository.findById(productId)
                .orElseThrow(() -> new IllegalArgumentException("Продукт не найден с ID: " + productId));

        productRepository.delete(product);
        log.info("Продукт удален с ID: {}", productId);
    }

    public ProductDTO getProductById(Integer productId) {
        log.info("Получение продукта с ID: {}", productId);

        Product product = productRepository.findByIdWithCategory(productId)
                .orElseThrow(() -> new IllegalArgumentException("Продукт не найден с ID: " + productId));

        return mapToDTO(product);
    }

    /**
     * Маппинг Entity в DTO
     */
    private ProductDTO mapToDTO(Product product) {
        String imageUrl = null;
        if (product.getImageUrl() != null) {
            imageUrl = storageService.getPublicUrl(product.getImageUrl());
        }

        return ProductDTO.builder()
                .id(product.getId())
                .name(product.getName())
                .description(product.getDescription())
                .price(product.getPrice())
                .discountedPrice(product.getDiscountedPrice())
                .categoryId(product.getCategory().getId())
                .categoryName(product.getCategory().getName())
                .imageUrl(imageUrl)
                .weight(product.getWeight())
                .isAvailable(product.isAvailable())
                .isSpecialOffer(product.isSpecialOffer())
                .discountPercent(product.getDiscountPercent())
                .build();
    }
}