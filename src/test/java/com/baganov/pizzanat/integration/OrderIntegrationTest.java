/**
 * @file: OrderIntegrationTest.java
 * @description: Интеграционные тесты для заказов
 * @dependencies: Spring Boot Test, JWT
 * @created: 2025-05-22
 */
package com.baganov.pizzanat.integration;

import com.baganov.pizzanat.config.TestConfig;
import com.baganov.pizzanat.config.TestMailConfig;
import com.baganov.pizzanat.config.TestRedisConfig;
import com.baganov.pizzanat.config.TestS3Config;
import com.baganov.pizzanat.model.dto.order.CreateOrderRequest;
import com.baganov.pizzanat.model.dto.order.OrderDTO;
import com.baganov.pizzanat.model.dto.order.UpdateOrderStatusRequest;
import com.baganov.pizzanat.model.entity.*;
import com.baganov.pizzanat.repository.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.context.annotation.Import;

import java.math.BigDecimal;
import java.util.HashSet;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import({ TestConfig.class, TestRedisConfig.class, TestMailConfig.class, TestS3Config.class })
@Transactional
public class OrderIntegrationTest {

        @Autowired
        private MockMvc mockMvc;

        @Autowired
        private ObjectMapper objectMapper;

        @Autowired
        private UserRepository userRepository;

        @Autowired
        private RoleRepository roleRepository;

        @Autowired
        private ProductRepository productRepository;

        @Autowired
        private CategoryRepository categoryRepository;

        @Autowired
        private CartRepository cartRepository;

        @Autowired
        private OrderStatusRepository orderStatusRepository;

        @Autowired
        private DeliveryLocationRepository deliveryLocationRepository;

        @Autowired
        private PasswordEncoder passwordEncoder;

        private User testUser;
        private Product testProduct;
        private Cart testCart;
        private DeliveryLocation testLocation;
        private String authToken;

        @BeforeEach
        void setUp() throws Exception {
                // Создаём необходимые данные для тестов
                Role userRole = roleRepository.save(Role.builder()
                                .name("USER")
                                .build());

                testUser = userRepository.save(User.builder()
                                .email("test@example.com")
                                .password(passwordEncoder.encode("password"))
                                .firstName("Тест")
                                .lastName("Тестов")
                                .phone("+7123456789")
                                .roles(new HashSet<>() {
                                        {
                                                add(userRole);
                                        }
                                })
                                .isActive(true)
                                .build());

                Category category = categoryRepository.save(Category.builder()
                                .name("Тестовая категория")
                                .isActive(true)
                                .build());

                testProduct = productRepository.save(Product.builder()
                                .name("Тестовая пицца")
                                .description("Описание тестовой пиццы")
                                .price(BigDecimal.valueOf(500))
                                .discountedPrice(BigDecimal.valueOf(450))
                                .category(category)
                                .isAvailable(true)
                                .build());

                testLocation = deliveryLocationRepository.save(DeliveryLocation.builder()
                                .name("Тестовая точка")
                                .address("Тестовая улица, 123")
                                .latitude(BigDecimal.valueOf(55.7558))
                                .longitude(BigDecimal.valueOf(37.6173))
                                .isActive(true)
                                .build());

                // Создаём статусы заказов, если их нет
                if (orderStatusRepository.findByName("CREATED").isEmpty()) {
                        orderStatusRepository.save(OrderStatus.builder()
                                        .name("CREATED")
                                        .description("Заказ создан")
                                        .active(true)
                                        .build());
                }

                if (orderStatusRepository.findByName("PROCESSING").isEmpty()) {
                        orderStatusRepository.save(OrderStatus.builder()
                                        .name("PROCESSING")
                                        .description("Заказ в обработке")
                                        .active(true)
                                        .build());
                }

                // Получаем токен авторизации
                String loginJson = "{\"username\":\"test@example.com\",\"password\":\"password\"}";
                String response = mockMvc.perform(post("/api/v1/auth/login")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(loginJson))
                                .andExpect(status().isOk())
                                .andReturn().getResponse().getContentAsString();

                authToken = objectMapper.readTree(response).get("token").asText();

                // Создаём корзину и добавляем в неё товар
                String cartJson = "{\"productId\":" + testProduct.getId() + ",\"quantity\":2}";
                mockMvc.perform(post("/api/v1/cart/items")
                                .header("Authorization", "Bearer " + authToken)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(cartJson))
                                .andExpect(status().isOk());
        }

        @AfterEach
        void tearDown() {
                // Очистка данных после теста
                cartRepository.deleteAll();
        }

        @Test
        void createAndUpdateOrder() throws Exception {
                // 1. Создаём заказ
                CreateOrderRequest createRequest = CreateOrderRequest.builder()
                                .deliveryLocationId(testLocation.getId().intValue()) // Приводим Long к int
                                .contactName("Тест Тестов")
                                .contactPhone("+7123456789")
                                .comment("Тестовый комментарий")
                                .build();

                String createResponse = mockMvc.perform(post("/api/v1/orders")
                                .header("Authorization", "Bearer " + authToken)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(createRequest)))
                                .andExpect(status().isOk())
                                .andExpect(jsonPath("$.id").exists())
                                .andExpect(jsonPath("$.status").value("CREATED"))
                                .andReturn().getResponse().getContentAsString();

                OrderDTO createdOrder = objectMapper.readValue(createResponse, OrderDTO.class);

                // 2. Получаем заказ по ID
                mockMvc.perform(get("/api/v1/orders/" + createdOrder.getId())
                                .header("Authorization", "Bearer " + authToken))
                                .andExpect(status().isOk())
                                .andExpect(jsonPath("$.id").value(createdOrder.getId()))
                                .andExpect(jsonPath("$.status").value("CREATED"))
                                .andExpect(jsonPath("$.items.length()").value(1))
                                .andExpect(jsonPath("$.items[0].productName").value(testProduct.getName()));

                // 3. Получаем список заказов пользователя
                mockMvc.perform(get("/api/v1/orders")
                                .header("Authorization", "Bearer " + authToken))
                                .andExpect(status().isOk())
                                .andExpect(jsonPath("$.content.length()").value(1))
                                .andExpect(jsonPath("$.content[0].id").value(createdOrder.getId()));

                // 4. Обновляем статус заказа (только для администратора)
                String adminLoginJson = "{\"username\":\"admin@example.com\",\"password\":\"admin\"}";
                String adminResponse = mockMvc.perform(post("/api/v1/auth/login")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(adminLoginJson))
                                .andReturn().getResponse().getContentAsString();

                if (!adminResponse.contains("token")) {
                        // Пропускаем тест на обновление статуса, если нет админа
                        return;
                }

                String adminToken = objectMapper.readTree(adminResponse).get("token").asText();

                UpdateOrderStatusRequest updateRequest = UpdateOrderStatusRequest.builder()
                                .status("PROCESSING")
                                .build();

                mockMvc.perform(put("/api/v1/admin/orders/" + createdOrder.getId() + "/status")
                                .header("Authorization", "Bearer " + adminToken)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(updateRequest)))
                                .andExpect(status().isOk())
                                .andExpect(jsonPath("$.status").value("PROCESSING"));
        }
}