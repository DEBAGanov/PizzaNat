/**
 * @file: OrderServiceTest.java
 * @description: Модульные тесты для OrderService
 * @dependencies: JUnit, Mockito
 * @created: 2025-05-22
 */
package com.baganov.pizzanat.service;

import com.baganov.pizzanat.model.dto.order.CreateOrderRequest;
import com.baganov.pizzanat.model.dto.order.OrderDTO;
import com.baganov.pizzanat.model.entity.*;
import com.baganov.pizzanat.repository.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class OrderServiceTest {

        @Mock
        private OrderRepository orderRepository;

        @Mock
        private OrderStatusRepository orderStatusRepository;

        @Mock
        private CartRepository cartRepository;

        @Mock
        private UserRepository userRepository;

        @Mock
        private DeliveryLocationRepository deliveryLocationRepository;

        @Mock
        private StorageService storageService;

        @Mock
        private NotificationService notificationService;

        @InjectMocks
        private OrderService orderService;

        private User testUser;
        private Cart testCart;
        private Product testProduct;
        private OrderStatus createdStatus;
        private OrderStatus processingStatus;
        private DeliveryLocation testLocation;

        @BeforeEach
        void setUp() {
                // Подготовка тестовых данных
                testUser = User.builder()
                                .id(1)
                                .firstName("Тест")
                                .lastName("Тестов")
                                .email("test@example.com")
                                .build();

                testProduct = Product.builder()
                                .id(1)
                                .name("Тестовая пицца")
                                .price(BigDecimal.valueOf(500))
                                .discountedPrice(BigDecimal.valueOf(450))
                                .isAvailable(true)
                                .build();

                CartItem cartItem = CartItem.builder()
                                .id(1)
                                .product(testProduct)
                                .quantity(2)
                                .build();

                testCart = Cart.builder()
                                .id(1)
                                .user(testUser)
                                .items(new ArrayList<>(List.of(cartItem)))
                                .build();
                testCart.setTotalAmount(BigDecimal.valueOf(900));

                createdStatus = OrderStatus.builder()
                                .id(1)
                                .name("CREATED")
                                .description("Заказ создан")
                                .active(true)
                                .build();

                processingStatus = OrderStatus.builder()
                                .id(2)
                                .name("PROCESSING")
                                .description("Заказ обрабатывается")
                                .active(true)
                                .build();

                testLocation = DeliveryLocation.builder()
                                .id(1)
                                .name("Тестовая точка")
                                .address("Тестовая улица, 123")
                                .isActive(true)
                                .build();
        }

        @Test
        void createOrder_Success() {
                // Подготовка
                CreateOrderRequest request = CreateOrderRequest.builder()
                                .deliveryLocationId(1)
                                .contactName("Тест Тестов")
                                .contactPhone("+7123456789")
                                .comment("Тестовый комментарий")
                                .build();

                when(userRepository.findById(1)).thenReturn(Optional.of(testUser));
                when(cartRepository.findByUserId(1)).thenReturn(Optional.of(testCart));
                when(deliveryLocationRepository.findById(1)).thenReturn(Optional.of(testLocation));
                when(orderStatusRepository.findByName("CREATED")).thenReturn(Optional.of(createdStatus));

                when(orderRepository.save(any(Order.class))).thenAnswer(invocation -> {
                        Order order = invocation.getArgument(0);
                        order.setId(1);
                        return order;
                });

                // Вызов
                OrderDTO result = orderService.createOrder(1, null, request);

                // Проверка
                assertNotNull(result);
                assertEquals(1, result.getId());
                assertEquals("CREATED", result.getStatus());
                assertEquals("Заказ создан", result.getStatusDescription());
                assertEquals(1, result.getDeliveryLocationId());
                assertEquals("Тестовая точка", result.getDeliveryLocationName());
                assertEquals("Тестовая улица, 123", result.getDeliveryLocationAddress());
                assertEquals(BigDecimal.valueOf(900), result.getTotalAmount());
                assertEquals("Тестовый комментарий", result.getComment());
                assertEquals("Тест Тестов", result.getContactName());
                assertEquals("+7123456789", result.getContactPhone());
                assertEquals(1, result.getItems().size());

                verify(orderRepository).save(any(Order.class));
                verify(cartRepository).save(testCart);
                assertTrue(testCart.getItems().isEmpty());
        }

        @Test
        void updateOrderStatus_Success() {
                // Подготовка
                OrderItem orderItem = OrderItem.builder()
                                .id(1)
                                .product(testProduct)
                                .quantity(2)
                                .price(BigDecimal.valueOf(450))
                                .build();

                Order order = Order.builder()
                                .id(1)
                                .user(testUser)
                                .status(createdStatus)
                                .deliveryLocation(testLocation)
                                .totalAmount(BigDecimal.valueOf(900))
                                .items(new ArrayList<>(List.of(orderItem)))
                                .build();

                when(orderRepository.findById(1)).thenReturn(Optional.of(order));
                when(orderStatusRepository.findByName("PROCESSING")).thenReturn(Optional.of(processingStatus));
                when(orderRepository.save(any(Order.class))).thenReturn(order);

                // Вызов
                OrderDTO result = orderService.updateOrderStatus(1, "PROCESSING");

                // Проверка
                assertNotNull(result);
                assertEquals("PROCESSING", result.getStatus());

                verify(notificationService).sendOrderStatusChangeNotification(order, "CREATED", "PROCESSING");
        }

        @Test
        void getUserOrders_Success() {
                // Подготовка
                OrderItem orderItem = OrderItem.builder()
                                .id(1)
                                .product(testProduct)
                                .quantity(2)
                                .price(BigDecimal.valueOf(450))
                                .build();

                Order order = Order.builder()
                                .id(1)
                                .user(testUser)
                                .status(createdStatus)
                                .deliveryLocation(testLocation)
                                .totalAmount(BigDecimal.valueOf(900))
                                .items(new ArrayList<>(List.of(orderItem)))
                                .build();

                PageRequest pageRequest = PageRequest.of(0, 10);
                when(orderRepository.findByUserIdOrderByCreatedAtDesc(eq(1), eq(pageRequest)))
                                .thenReturn(new PageImpl<>(List.of(order)));

                // Вызов
                var result = orderService.getUserOrders(1, pageRequest);

                // Проверка
                assertNotNull(result);
                assertEquals(1, result.getTotalElements());
                assertEquals(1, result.getContent().get(0).getId());
        }
}