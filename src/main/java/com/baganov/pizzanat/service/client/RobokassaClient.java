/**
 * @file: RobokassaClient.java
 * @description: Клиент для взаимодействия с платежной системой Robokassa
 * @dependencies: resilience4j, BaseResilientClient
 * @created: 2023-11-01
 */
package com.baganov.pizzanat.service.client;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import io.github.resilience4j.retry.RetryRegistry;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HashMap;
import java.util.Map;

/**
 * Клиент для взаимодействия с платежной системой Robokassa
 * с поддержкой отказоустойчивости
 */
@Slf4j
@Component
public class RobokassaClient extends BaseResilientClient {

    private static final String CIRCUIT_BREAKER_NAME = "robokassa";
    private static final String RETRY_NAME = "robokassa";

    private final RestTemplate restTemplate;
    private final ObjectMapper objectMapper;

    @Value("${payment.robokassa.merchant-login}")
    private String merchantLogin;

    @Value("${payment.robokassa.password1}")
    private String password1;

    @Value("${payment.robokassa.password2}")
    private String password2;

    @Value("${payment.robokassa.test-mode:true}")
    private boolean testMode;

    @Value("${payment.robokassa.base-url:https://auth.robokassa.ru/Merchant/Index.aspx}")
    private String baseUrl;

    public RobokassaClient(
            CircuitBreakerRegistry circuitBreakerRegistry,
            RetryRegistry retryRegistry,
            RestTemplate restTemplate,
            ObjectMapper objectMapper) {
        super(circuitBreakerRegistry, retryRegistry);
        this.restTemplate = restTemplate;
        this.objectMapper = objectMapper;
    }

    /**
     * Создание URL для оплаты через Robokassa
     *
     * @param orderId     ID заказа
     * @param amount      сумма заказа
     * @param description описание заказа
     * @return URL для перенаправления пользователя на страницу оплаты
     */
    public String createPaymentUrl(String orderId, double amount, String description) {
        return executeWithResiliencePatterns(
                () -> generatePaymentUrl(orderId, amount, description),
                CIRCUIT_BREAKER_NAME,
                RETRY_NAME,
                null // В случае ошибки вернем null
        );
    }

    /**
     * Проверка подписи уведомления об оплате от Robokassa
     * 
     * @param notification параметры уведомления
     * @return true если подпись верна, иначе false
     */
    public boolean verifyNotification(Map<String, String> notification) {
        return executeWithResiliencePatterns(
                () -> doVerifyNotification(notification),
                CIRCUIT_BREAKER_NAME,
                RETRY_NAME,
                false // В случае ошибки считаем подпись неверной
        );
    }

    // Внутренние методы для реализации работы с Robokassa

    private String generatePaymentUrl(String orderId, double amount, String description) {
        // Формируем строку для подписи
        String signature = generateSignature(merchantLogin, amount, orderId, password1);

        // Формируем URL для перенаправления пользователя
        StringBuilder urlBuilder = new StringBuilder(baseUrl);
        urlBuilder.append("?MerchantLogin=").append(merchantLogin);
        urlBuilder.append("&OutSum=").append(amount);
        urlBuilder.append("&InvId=").append(orderId);
        urlBuilder.append("&Description=").append(description);
        urlBuilder.append("&SignatureValue=").append(signature);

        if (testMode) {
            urlBuilder.append("&IsTest=1");
        }

        return urlBuilder.toString();
    }

    private boolean doVerifyNotification(Map<String, String> notification) {
        String outSum = notification.get("OutSum");
        String invId = notification.get("InvId");
        String signatureValue = notification.get("SignatureValue");

        if (outSum == null || invId == null || signatureValue == null) {
            log.warn("Неполные данные в уведомлении Robokassa");
            return false;
        }

        // Формируем строку для проверки подписи (outSum:invId:password2)
        String signature = generateSignature(outSum, invId, password2);

        // Проверяем совпадение подписей
        return signature.equalsIgnoreCase(signatureValue);
    }

    // Вспомогательные методы

    private String generateSignature(Object... params) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < params.length; i++) {
            sb.append(params[i]);
            if (i < params.length - 1) {
                sb.append(":");
            }
        }

        return md5(sb.toString());
    }

    private String md5(String input) {
        try {
            MessageDigest md = MessageDigest.getInstance("MD5");
            byte[] messageDigest = md.digest(input.getBytes());

            StringBuilder hexString = new StringBuilder();
            for (byte b : messageDigest) {
                hexString.append(String.format("%02x", b));
            }

            return hexString.toString();
        } catch (NoSuchAlgorithmException e) {
            log.error("Ошибка при создании MD5 хеша", e);
            throw new RuntimeException("Ошибка при создании MD5 хеша", e);
        }
    }
}