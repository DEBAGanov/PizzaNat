/**
 * @file: PaymentControllerTest.java
 * @description: Тесты для PaymentController
 * @dependencies: Spring Boot Test
 * @created: 2025-05-22
 */
package com.baganov.pizzanat.integration;

import com.baganov.pizzanat.entity.Order;
import com.baganov.pizzanat.entity.OrderStatus;
import com.baganov.pizzanat.repository.OrderRepository;
import com.baganov.pizzanat.repository.OrderStatusRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultHandlers.print;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@Transactional
public class PaymentControllerTest extends BaseIntegrationTest {

    @Autowired
    private OrderRepository orderRepository;

    @Autowired
    private OrderStatusRepository orderStatusRepository;

    private Integer orderId;

    @BeforeEach
    void setupTestData() {
        // Создаем статус заказа "ОЖИДАЕТ ОПЛАТЫ"
        OrderStatus awaitingPaymentStatus = orderStatusRepository.findByName("AWAITING_PAYMENT")
                .orElseGet(() -> orderStatusRepository.save(OrderStatus.builder()
                        .name("AWAITING_PAYMENT")
                        .description("Ожидает оплаты")
                        .isActive(true)
                        .build()));

        // Создаем тестовый заказ с минимально необходимыми полями
        Order order = Order.builder()
                .totalAmount(BigDecimal.valueOf(1000.00))
                .status(awaitingPaymentStatus)
                .contactName("Тестовый пользователь")
                .contactPhone("+79001234567")
                .createdAt(LocalDateTime.now())
                .build();

        // Сохраняем заказ
        Order savedOrder = orderRepository.save(order);
        orderId = savedOrder.getId();
    }

    @Test
    @DisplayName("Обработка успешного уведомления о платеже")
    public void testHandleRobokassaNotification() throws Exception {
        mockMvc.perform(post("/api/v1/payments/robokassa/notify")
                .contentType(MediaType.APPLICATION_FORM_URLENCODED_VALUE)
                .param("InvId", orderId.toString())
                .param("OutSum", "1000.00")
                .param("SignatureValue", "test_signature"))
                .andDo(print())
                .andExpect(status().isOk())
                .andExpect(content().string("OK" + orderId));
    }

    @Test
    @DisplayName("Обработка успешного завершения платежа")
    public void testHandleRobokassaSuccess() throws Exception {
        mockMvc.perform(get("/api/v1/payments/robokassa/success")
                .param("InvId", orderId.toString())
                .param("OutSum", "1000.00")
                .param("SignatureValue", "test_signature"))
                .andDo(print())
                .andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers.containsString("Оплата успешно завершена")));
    }

    @Test
    @DisplayName("Обработка отмены платежа")
    public void testHandleRobokassaFail() throws Exception {
        mockMvc.perform(get("/api/v1/payments/robokassa/fail")
                .param("InvId", orderId.toString())
                .param("OutSum", "1000.00")
                .param("SignatureValue", "test_signature"))
                .andDo(print())
                .andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers.containsString("Оплата была отменена")));
    }
}