/**
 * @file: CartControllerTest.java
 * @description: Тесты для CartController
 * @dependencies: Spring Boot Test, JWT
 * @created: 2025-05-22
 */
package com.baganov.pizzanat.integration;

import com.baganov.pizzanat.model.dto.cart.AddToCartRequest;
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

import jakarta.servlet.http.Cookie;

import java.math.BigDecimal;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultHandlers.print;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@Transactional
public class CartControllerTest extends BaseIntegrationTest {

        @Autowired
        private CategoryRepository categoryRepository;

        @Autowired
        private ProductRepository productRepository;

        private Integer productId;
        private final String TEST_SESSION_ID = "test-session-id";

        @BeforeEach
        void setupTestData() {
                // Создаем категорию
                Category category = categoryRepository.save(Category.builder()
                                .name("Тестовая категория")
                                .isActive(true)
                                .build());

                // Создаем продукт
                Product product = productRepository.save(Product.builder()
                                .name("Тестовая пицца")
                                .description("Вкусная тестовая пицца")
                                .price(BigDecimal.valueOf(500))
                                .discountedPrice(BigDecimal.valueOf(450))
                                .isAvailable(true)
                                .category(category)
                                .build());

                productId = product.getId();
        }

        @Test
        @DisplayName("Получение пустой корзины неаутентифицированным пользователем")
        public void testGetEmptyCartAsAnonymous() throws Exception {
                mockMvc.perform(get("/api/v1/cart")
                                .contentType(MediaType.APPLICATION_JSON)
                                .cookie(new Cookie(CART_SESSION_ID_COOKIE, TEST_SESSION_ID)))
                                .andDo(print())
                                .andExpect(status().isOk())
                                .andExpect(jsonPath("$.items").isArray())
                                .andExpect(jsonPath("$.items").isEmpty())
                                .andExpect(jsonPath("$.totalAmount").value(0));
        }

        @Test
        @DisplayName("Получение корзины аутентифицированным пользователем")
        public void testGetCartAsAuthenticatedUser() throws Exception {
                mockMvc.perform(get("/api/v1/cart")
                                .contentType(MediaType.APPLICATION_JSON)
                                .header("Authorization", getBearerToken(userToken)))
                                .andDo(print())
                                .andExpect(status().isOk())
                                .andExpect(jsonPath("$.items").isArray());
        }

        @Test
        @DisplayName("Добавление товара в корзину")
        public void testAddToCart() throws Exception {
                AddToCartRequest request = new AddToCartRequest();
                request.setProductId(productId);
                request.setQuantity(2);

                mockMvc.perform(post("/api/v1/cart/items")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(request))
                                .cookie(new Cookie(CART_SESSION_ID_COOKIE, TEST_SESSION_ID)))
                                .andDo(print())
                                .andExpect(status().isOk())
                                .andExpect(jsonPath("$.items").isArray())
                                .andExpect(jsonPath("$.items[0].productId").value(productId))
                                .andExpect(jsonPath("$.items[0].quantity").value(2));
        }

        @Test
        @DisplayName("Обновление количества товара в корзине")
        public void testUpdateCartItem() throws Exception {
                // Сначала добавляем товар
                AddToCartRequest request = new AddToCartRequest();
                request.setProductId(productId);
                request.setQuantity(2);

                mockMvc.perform(post("/api/v1/cart/items")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(request))
                                .cookie(new Cookie(CART_SESSION_ID_COOKIE, TEST_SESSION_ID)))
                                .andExpect(status().isOk());

                // Затем обновляем количество
                mockMvc.perform(put("/api/v1/cart/items/" + productId)
                                .contentType(MediaType.APPLICATION_JSON)
                                .param("quantity", "3")
                                .cookie(new Cookie(CART_SESSION_ID_COOKIE, TEST_SESSION_ID)))
                                .andDo(print())
                                .andExpect(status().isOk())
                                .andExpect(jsonPath("$.items[0].quantity").value(3));
        }

        @Test
        @DisplayName("Удаление товара из корзины")
        public void testRemoveFromCart() throws Exception {
                // Сначала добавляем товар
                AddToCartRequest request = new AddToCartRequest();
                request.setProductId(productId);
                request.setQuantity(2);

                mockMvc.perform(post("/api/v1/cart/items")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(request))
                                .cookie(new Cookie(CART_SESSION_ID_COOKIE, TEST_SESSION_ID)))
                                .andExpect(status().isOk());

                // Затем удаляем его
                mockMvc.perform(delete("/api/v1/cart/items/" + productId)
                                .contentType(MediaType.APPLICATION_JSON)
                                .cookie(new Cookie(CART_SESSION_ID_COOKIE, TEST_SESSION_ID)))
                                .andDo(print())
                                .andExpect(status().isOk())
                                .andExpect(jsonPath("$.items").isEmpty());
        }

        @Test
        @DisplayName("Очистка корзины")
        public void testClearCart() throws Exception {
                // Сначала добавляем товар
                AddToCartRequest request = new AddToCartRequest();
                request.setProductId(productId);
                request.setQuantity(2);

                mockMvc.perform(post("/api/v1/cart/items")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(request))
                                .cookie(new Cookie(CART_SESSION_ID_COOKIE, TEST_SESSION_ID)))
                                .andExpect(status().isOk());

                // Затем очищаем корзину
                mockMvc.perform(delete("/api/v1/cart")
                                .contentType(MediaType.APPLICATION_JSON)
                                .cookie(new Cookie(CART_SESSION_ID_COOKIE, TEST_SESSION_ID)))
                                .andDo(print())
                                .andExpect(status().isNoContent());

                // Проверяем, что корзина пуста
                mockMvc.perform(get("/api/v1/cart")
                                .contentType(MediaType.APPLICATION_JSON)
                                .cookie(new Cookie(CART_SESSION_ID_COOKIE, TEST_SESSION_ID)))
                                .andExpect(status().isOk())
                                .andExpect(jsonPath("$.items").isEmpty());
        }

        // Константа для имени куки с идентификатором сессии
        private static final String CART_SESSION_ID_COOKIE = "CART_SESSION_ID";
}