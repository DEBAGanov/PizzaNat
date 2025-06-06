package com.baganov.pizzanat.service.client;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import io.github.resilience4j.retry.RetryRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.client.RestTemplate;

import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RobokassaClientTest {

    @Mock
    private CircuitBreakerRegistry circuitBreakerRegistry;

    @Mock
    private RetryRegistry retryRegistry;

    @Mock
    private RestTemplate restTemplate;

    @Mock
    private ObjectMapper objectMapper;

    private RobokassaClient robokassaClient;

    @BeforeEach
    void setUp() {
        robokassaClient = new RobokassaClient(
                circuitBreakerRegistry,
                retryRegistry,
                restTemplate,
                objectMapper);

        // Устанавливаем тестовые значения для конфигурации
        ReflectionTestUtils.setField(robokassaClient, "merchantLogin", "test_merchant");
        ReflectionTestUtils.setField(robokassaClient, "password1", "test_password1");
        ReflectionTestUtils.setField(robokassaClient, "password2", "test_password2");
        ReflectionTestUtils.setField(robokassaClient, "testMode", true);
        ReflectionTestUtils.setField(robokassaClient, "baseUrl", "https://auth.robokassa.ru/Merchant/Index.aspx");
    }

    @Test
    void testClientCreation() {
        // Проверяем, что клиент создается без ошибок
        assertNotNull(robokassaClient);
    }

    @Test
    void testCreatePaymentUrl() {
        // Тестируем создание URL для оплаты
        String orderId = "12345";
        double amount = 1000.00;
        String description = "Test payment";

        String paymentUrl = robokassaClient.createPaymentUrl(orderId, amount, description);

        // Проверяем, что URL содержит необходимые параметры
        assertNotNull(paymentUrl);
        assertTrue(paymentUrl.contains("MerchantLogin=test_merchant"));
        assertTrue(paymentUrl.contains("OutSum=1000.0"));
        assertTrue(paymentUrl.contains("InvId=12345"));
        assertTrue(paymentUrl.contains("IsTest=1"));
    }

    @Test
    void testVerifyNotification() {
        // Тестируем проверку уведомления
        Map<String, String> notification = new HashMap<>();
        notification.put("OutSum", "1000.00");
        notification.put("InvId", "12345");
        notification.put("SignatureValue", "invalid_signature");

        boolean result = robokassaClient.verifyNotification(notification);

        // Результат должен быть false для неверной подписи
        assertFalse(result);
    }

    @Test
    void testVerifyNotificationWithMissingData() {
        // Тестируем проверку уведомления с неполными данными
        Map<String, String> notification = new HashMap<>();
        notification.put("OutSum", "1000.00");
        // Отсутствует InvId и SignatureValue

        boolean result = robokassaClient.verifyNotification(notification);

        // Результат должен быть false для неполных данных
        assertFalse(result);
    }
}