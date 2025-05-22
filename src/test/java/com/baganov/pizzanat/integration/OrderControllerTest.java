/**
 * @file: OrderControllerTest.java
 * @description: Тесты для OrderController
 * @dependencies: Spring Boot Test, JWT
 * @created: 2025-05-22
 */
package com.baganov.pizzanat.integration;

import com.baganov.pizzanat.model.dto.cart.AddToCartRequest;
import com.baganov.pizzanat.model.dto.order.CreateOrderRequest;
import com.baganov.pizzanat.model.entity.Category;
import com.baganov.pizzanat.model.entity.DeliveryLocation;
import com.baganov.pizzanat.model.entity.OrderStatus;
import com.baganov.pizzanat.model.entity.Product;
import com.baganov.pizzanat.repository.CategoryRepository;
import com.baganov.pizzanat.repository.DeliveryLocationRepository;
import com.baganov.pizzanat.repository.OrderStatusRepository;
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
public class OrderControllerTest extends BaseIntegrationTest {

        @Autowired
        private CategoryRepository categoryRepository;

        @Autowired
        private ProductRepository productRepository;

        @Autowired
        private DeliveryLocationRepository deliveryLocationRepository;

        @Autowired
        private OrderStatusRepository orderStatusRepository;

        private Integer productId;
        private Integer deliveryLocationId;

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
                                .available(true)
                                .category(category)
                                .build());

                productId = product.getId();

                // Создаем точку доставки
                DeliveryLocation location = deliveryLocationRepository.save(DeliveryLocation.builder()
                                .name("Тестовая точка")
                                .address("Тестовая улица, 123")
                                .latitude(BigDecimal.valueOf(55.7558))
                                .longitude(BigDecimal.valueOf(37.6173))
                                .isActive(true)
                                .build());

                deliveryLocationId = location.getId();

                // Создаем статусы заказов, если их нет
                if (orderStatusRepository.findByName("CREATED").isEmpty()) {
                        orderStatusRepository.save(OrderStatus.builder()
                                        .name("CREATED")
                                        .description("Заказ создан")
                                        .active(true)
                                        .build());
                }
        }

        @Test
        @DisplayName("Создание заказа аутентифицированным пользователем")
        public void testCreateOrder() throws Exception {
                // Сначала добавляем товар в корзину
                AddToCartRequest addToCartRequest = new AddToCartRequest();
                addToCartRequest.setProductId(productId);
                addToCartRequest.setQuantity(2);

                mockMvc.perform(post("/api/v1/cart/items")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(addToCartRequest))
                                .header("Authorization", getBearerToken(userToken)))
                                .andExpect(status().isOk());

                // Создаем заказ
                CreateOrderRequest createOrderRequest = CreateOrderRequest.builder()
                                .deliveryLocationId(deliveryLocationId)
                                .contactName("Тест Тестов")
                                .contactPhone("+79001234567")
                                .comment("Тестовый заказ")
                                .build();

                mockMvc.perform(post("/api/v1/orders")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(createOrderRequest))
                                .header("Authorization", getBearerToken(userToken)))
                                .andDo(print())
                                .andExpect(status().isOk())
                                .andExpect(jsonPath("$.id").exists())
                                .andExpect(jsonPath("$.status").value("CREATED"))
                                .andExpect(jsonPath("$.items").isArray())
                                .andExpect(jsonPath("$.contactName").value("Тест Тестов"));
        }

        @Test
        @DisplayName("Создание заказа анонимным пользователем")
        public void testCreateOrderAnonymous() throws Exception {
                // Сначала добавляем товар в корзину
                AddToCartRequest addToCartRequest = new AddToCartRequest();
                addToCartRequest.setProductId(productId);
                addToCartRequest.setQuantity(2);

                mockMvc.perform(post("/api/v1/cart/items")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(addToCartRequest))
                                .cookie(new Cookie("CART_SESSION_ID", "test-session-id")))
                                .andExpect(status().isOk());

                // Создаем заказ
                CreateOrderRequest createOrderRequest = CreateOrderRequest.builder()
                                .deliveryLocationId(deliveryLocationId)
                                .contactName("Аноним Анонимов")
                                .contactPhone("+79001234567")
                                .comment("Тестовый заказ анонимного пользователя")
                                .build();

                mockMvc.perform(post("/api/v1/orders")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(createOrderRequest))
                                .cookie(new Cookie("CART_SESSION_ID", "test-session-id")))
                                .andDo(print())
                                .andExpect(status().isOk())
                                .andExpect(jsonPath("$.id").exists())
                                .andExpect(jsonPath("$.status").value("CREATED"))
                                .andExpect(jsonPath("$.items").isArray())
                                .andExpect(jsonPath("$.contactName").value("Аноним Анонимов"));
        }

        @Test
        @DisplayName("Получение заказа по ID")
        public void testGetOrderById() throws Exception {
                // Сначала создаем заказ
                // Добавляем товар в корзину
                AddToCartRequest addToCartRequest = new AddToCartRequest();
                addToCartRequest.setProductId(productId);
                addToCartRequest.setQuantity(2);

                mockMvc.perform(post("/api/v1/cart/items")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(addToCartRequest))
                                .header("Authorization", getBearerToken(userToken)))
                                .andExpect(status().isOk());

                // Создаем заказ
                CreateOrderRequest createOrderRequest = CreateOrderRequest.builder()
                                .deliveryLocationId(deliveryLocationId)
                                .contactName("Тест Тестов")
                                .contactPhone("+79001234567")
                                .comment("Тестовый заказ")
                                .build();

                String orderResponse = mockMvc.perform(post("/api/v1/orders")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(createOrderRequest))
                                .header("Authorization", getBearerToken(userToken)))
                                .andExpect(status().isOk())
                                .andReturn().getResponse().getContentAsString();

                // Извлекаем ID заказа
                Integer id = objectMapper.readTree(orderResponse).get("id").asInt();

                // Получаем заказ по ID
                mockMvc.perform(get("/api/v1/orders/" + id)
                                .contentType(MediaType.APPLICATION_JSON)
                                .header("Authorization", getBearerToken(userToken)))
                                .andDo(print())
                                .andExpect(status().isOk())
                                .andExpect(jsonPath("$.id").value(id))
                                .andExpect(jsonPath("$.status").value("CREATED"));
        }

        @Test
        @DisplayName("Получение списка заказов пользователя")
        public void testGetUserOrders() throws Exception {
                mockMvc.perform(get("/api/v1/orders")
                                .contentType(MediaType.APPLICATION_JSON)
                                .header("Authorization", getBearerToken(userToken)))
                                .andDo(print())
                                .andExpect(status().isOk())
                                .andExpect(jsonPath("$.content").isArray());
        }

        @Test
        @DisplayName("Получение URL для оплаты заказа")
        public void testGetPaymentUrl() throws Exception {
                // Сначала создаем заказ
                // Добавляем товар в корзину
                AddToCartRequest addToCartRequest = new AddToCartRequest();
                addToCartRequest.setProductId(productId);
                addToCartRequest.setQuantity(2);

                mockMvc.perform(post("/api/v1/cart/items")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(addToCartRequest))
                                .header("Authorization", getBearerToken(userToken)))
                                .andExpect(status().isOk());

                // Создаем заказ
                CreateOrderRequest createOrderRequest = CreateOrderRequest.builder()
                                .deliveryLocationId(deliveryLocationId)
                                .contactName("Тест Тестов")
                                .contactPhone("+79001234567")
                                .comment("Тестовый заказ")
                                .build();

                String orderResponse = mockMvc.perform(post("/api/v1/orders")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(createOrderRequest))
                                .header("Authorization", getBearerToken(userToken)))
                                .andExpect(status().isOk())
                                .andReturn().getResponse().getContentAsString();

                // Извлекаем ID заказа
                Integer id = objectMapper.readTree(orderResponse).get("id").asInt();

                // Получаем URL для оплаты
                mockMvc.perform(get("/api/v1/orders/" + id + "/payment-url")
                                .contentType(MediaType.APPLICATION_JSON)
                                .header("Authorization", getBearerToken(userToken)))
                                .andDo(print())
                                .andExpect(status().isOk())
                                .andExpect(jsonPath("$.paymentUrl").exists());
        }
}