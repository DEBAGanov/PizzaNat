/**
 * @file: ProductControllerTest.java
 * @description: Тесты для ProductController
 * @dependencies: Spring Boot Test
 * @created: 2025-05-22
 */
package com.baganov.pizzanat.integration;

import com.baganov.pizzanat.model.entity.Category;
import com.baganov.pizzanat.model.entity.Product;
import com.baganov.pizzanat.repository.CategoryRepository;
import com.baganov.pizzanat.repository.ProductRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultHandlers.print;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@Transactional
public class ProductControllerTest extends BaseIntegrationTest {

        @Autowired
        private CategoryRepository categoryRepository;

        @Autowired
        private ProductRepository productRepository;

        private Integer categoryId;
        private Integer productId;
        private Integer specialOfferId;

        @BeforeEach
        void setupTestData() {
                // Создаем категорию
                Category category = categoryRepository.save(Category.builder()
                                .name("Тестовая категория")
                                .isActive(true)
                                .build());

                categoryId = category.getId();

                // Создаем обычный продукт
                Product product = productRepository.save(Product.builder()
                                .name("Тестовая пицца")
                                .description("Очень вкусная тестовая пицца")
                                .price(BigDecimal.valueOf(500))
                                .isAvailable(true)
                                .category(category)
                                .build());

                productId = product.getId();

                // Создаем продукт со скидкой (специальное предложение)
                Product specialOffer = productRepository.save(Product.builder()
                                .name("Специальное предложение")
                                .description("Пицца со скидкой")
                                .price(BigDecimal.valueOf(600))
                                .discountedPrice(BigDecimal.valueOf(500))
                                .isAvailable(true)
                                .category(category)
                                .build());

                specialOfferId = specialOffer.getId();
        }

        @Test
        @DisplayName("Получение списка всех продуктов")
        public void testGetAllProducts() throws Exception {
                mockMvc.perform(get("/api/v1/products")
                                .contentType(MediaType.APPLICATION_JSON))
                                .andDo(print())
                                .andExpect(status().isOk())
                                .andExpect(jsonPath("$.content").isArray())
                                .andExpect(jsonPath("$.pageable").exists());
        }

        @Test
        @DisplayName("Получение продукта по ID")
        public void testGetProductById() throws Exception {
                mockMvc.perform(get("/api/v1/products/" + productId)
                                .contentType(MediaType.APPLICATION_JSON))
                                .andDo(print())
                                .andExpect(status().isOk())
                                .andExpect(jsonPath("$.id").value(productId))
                                .andExpect(jsonPath("$.name").value("Тестовая пицца"));
        }

        @Test
        @DisplayName("Получение продуктов по категории")
        public void testGetProductsByCategory() throws Exception {
                mockMvc.perform(get("/api/v1/products/category/" + categoryId)
                                .contentType(MediaType.APPLICATION_JSON))
                                .andDo(print())
                                .andExpect(status().isOk())
                                .andExpect(jsonPath("$.content").isArray())
                                .andExpect(jsonPath("$.content[0].name").exists());
        }

        @Test
        @DisplayName("Получение специальных предложений")
        public void testGetSpecialOffers() throws Exception {
                mockMvc.perform(get("/api/v1/products/special-offers")
                                .contentType(MediaType.APPLICATION_JSON))
                                .andDo(print())
                                .andExpect(status().isOk())
                                .andExpect(jsonPath("$").isArray())
                                .andExpect(jsonPath("$[0].id").value(specialOfferId));
        }

        @Test
        @DisplayName("Поиск продуктов по запросу")
        public void testSearchProducts() throws Exception {
                mockMvc.perform(get("/api/v1/products/search")
                                .param("query", "пицца")
                                .contentType(MediaType.APPLICATION_JSON))
                                .andDo(print())
                                .andExpect(status().isOk())
                                .andExpect(jsonPath("$.content").isArray());
        }

        @Test
        @DisplayName("Поиск продуктов по запросу и категории")
        public void testSearchProductsWithCategory() throws Exception {
                mockMvc.perform(get("/api/v1/products/search")
                                .param("query", "пицца")
                                .param("categoryId", categoryId.toString())
                                .contentType(MediaType.APPLICATION_JSON))
                                .andDo(print())
                                .andExpect(status().isOk())
                                .andExpect(jsonPath("$.content").isArray());
        }
}