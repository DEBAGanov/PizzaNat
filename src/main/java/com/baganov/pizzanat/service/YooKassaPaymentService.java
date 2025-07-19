/**
 * @file: YooKassaPaymentService.java
 * @description: Сервис для работы с платежами через ЮKassa API
 * @dependencies: YooKassaConfig, PaymentRepository, OrderRepository, WebClient
 * @created: 2025-01-26
 */
package com.baganov.pizzanat.service;

import com.baganov.pizzanat.config.YooKassaConfig;
import com.baganov.pizzanat.entity.*;
import com.baganov.pizzanat.event.NewOrderEvent;
import com.baganov.pizzanat.model.dto.payment.CreatePaymentRequest;
import com.baganov.pizzanat.model.dto.payment.PaymentResponse;
import com.baganov.pizzanat.model.dto.payment.SbpBankInfo;
import com.baganov.pizzanat.repository.OrderRepository;
import com.baganov.pizzanat.repository.PaymentRepository;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.micrometer.core.instrument.Timer;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientResponseException;
import reactor.core.publisher.Mono;
import reactor.util.retry.Retry;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.*;

/**
 * Сервис для обработки платежей через ЮKassa
 */
@Slf4j
@Service
@RequiredArgsConstructor
@ConditionalOnProperty(name = "yookassa.enabled", havingValue = "true")
public class YooKassaPaymentService {

    private final YooKassaConfig yooKassaConfig;
    private final WebClient yooKassaWebClient;
    private final PaymentRepository paymentRepository;
    private final OrderRepository orderRepository;
    private final ObjectMapper objectMapper;
    private final PaymentMetricsService paymentMetricsService;
    private final PaymentAlertService paymentAlertService;
    private final ApplicationEventPublisher eventPublisher;

    /**
     * Создание платежа через ЮKassa API
     */
    @Transactional
    public PaymentResponse createPayment(CreatePaymentRequest request) {
        log.info("🔄 Создание платежа ЮKassa для заказа {}", request.getOrderId());

        // Начинаем измерение времени создания платежа
        Timer.Sample timerSample = paymentMetricsService.startPaymentCreationTimer();

        // Получаем заказ
        Order order = orderRepository.findById(request.getOrderId().intValue())
                .orElseThrow(() -> new IllegalArgumentException("Заказ не найден: " + request.getOrderId()));

        // Проверяем, что заказ можно оплачивать
        validateOrderForPayment(order);

        // Определяем сумму платежа
        BigDecimal amount = request.hasCustomAmount() ? request.getAmount() : order.getTotalAmount();

        // Создаем запись платежа в БД
        Payment payment = new Payment(order, request.getMethod(), amount);
        payment.setBankId(request.getBankId());
        payment.setIdempotenceKey(generateIdempotenceKey());

        // Сохраняем платеж
        payment = paymentRepository.save(payment);

        try {
            // Записываем метрику создания платежа
            paymentMetricsService.recordPaymentCreated(payment);

            // Отправляем алерт о крупном платеже
            paymentAlertService.onPaymentCreated(payment);

            // Формируем запрос к ЮKassa API
            Map<String, Object> yooKassaRequest = buildYooKassaPaymentRequest(payment, request);

            // Отправляем запрос к ЮKassa
            JsonNode response = sendPaymentRequest(yooKassaRequest, payment.getIdempotenceKey());

            // Обрабатываем ответ
            updatePaymentFromYooKassaResponse(payment, response);

            // Сохраняем обновленный платеж
            payment = paymentRepository.save(payment);

            log.info("✅ Платеж ЮKassa создан успешно: ID={}, YooKassa ID={}",
                    payment.getId(), payment.getYookassaPaymentId());

            // Завершаем измерение времени
            PaymentResponse result = mapToPaymentResponse(payment);
            return paymentMetricsService.recordPaymentCreationTime(timerSample, result);

        } catch (Exception e) {
            log.error("❌ Ошибка создания платежа ЮKassa для заказа {}: {}", request.getOrderId(), e.getMessage());

            // Обновляем статус платежа на ошибку
            PaymentStatus oldStatus = payment.getStatus();
            payment.setStatus(PaymentStatus.FAILED);
            payment.setErrorMessage(e.getMessage());
            payment = paymentRepository.save(payment);

            // Записываем метрику изменения статуса
            paymentMetricsService.recordPaymentStatusChange(payment, oldStatus);
            paymentAlertService.onPaymentStatusChanged(payment, oldStatus);

            // Завершаем измерение времени (даже при ошибке)
            paymentMetricsService.recordPaymentCreationTime(timerSample, null);

            throw new RuntimeException("Ошибка создания платежа: " + e.getMessage(), e);
        }
    }

    /**
     * Получение информации о платеже
     */
    @Transactional(readOnly = true)
    public PaymentResponse getPayment(Long paymentId) {
        Payment payment = paymentRepository.findById(paymentId)
                .orElseThrow(() -> new IllegalArgumentException("Платеж не найден: " + paymentId));

        return mapToPaymentResponse(payment);
    }

    /**
     * Получение платежа по ЮKassa ID
     */
    @Transactional(readOnly = true)
    public PaymentResponse getPaymentByYooKassaId(String yookassaPaymentId) {
        Payment payment = paymentRepository.findByYookassaPaymentId(yookassaPaymentId)
                .orElseThrow(() -> new IllegalArgumentException("Платеж не найден: " + yookassaPaymentId));

        return mapToPaymentResponse(payment);
    }

    /**
     * Получение всех платежей для заказа
     */
    @Transactional(readOnly = true)
    public List<PaymentResponse> getPaymentsForOrder(Long orderId) {
        List<Payment> payments = paymentRepository.findByOrderIdOrderByCreatedAtDesc(orderId);
        return payments.stream()
                .map(this::mapToPaymentResponse)
                .toList();
    }

    /**
     * Обработка webhook уведомления от ЮKassa
     */
    @Transactional
    public boolean processWebhookNotification(JsonNode notification) {
        try {
            log.info("🔔 Получено webhook уведомление от ЮKassa: {}", notification);

            String eventType = notification.path("event").asText();
            JsonNode paymentObject = notification.path("object");
            String yookassaPaymentId = paymentObject.path("id").asText();

            // Находим платеж в нашей БД
            Optional<Payment> paymentOpt = paymentRepository.findByYookassaPaymentId(yookassaPaymentId);
            if (paymentOpt.isEmpty()) {
                log.warn("⚠️ Платеж не найден в БД: {}", yookassaPaymentId);
                return false;
            }

            Payment payment = paymentOpt.get();
            PaymentStatus oldStatus = payment.getStatus();

            // Обновляем платеж на основе уведомления
            updatePaymentFromYooKassaResponse(payment, paymentObject);
            payment = paymentRepository.save(payment);

            log.info("📊 Статус платежа {} изменен: {} → {}",
                    payment.getId(), oldStatus, payment.getStatus());

            // Записываем метрики изменения статуса
            if (oldStatus != payment.getStatus()) {
                paymentMetricsService.recordPaymentStatusChange(payment, oldStatus);
                paymentAlertService.onPaymentStatusChanged(payment, oldStatus);
            }

            // Обновляем статус заказа при успешной оплате
            if (payment.getStatus() == PaymentStatus.SUCCEEDED && oldStatus != PaymentStatus.SUCCEEDED) {
                updateOrderStatusAfterPayment(payment.getOrder());
            }

            return true;

        } catch (Exception e) {
            log.error("❌ Ошибка обработки webhook ЮKassa: {}", e.getMessage(), e);
            return false;
        }
    }

    /**
     * Отмена платежа
     */
    @Transactional
    public PaymentResponse cancelPayment(Long paymentId) {
        Payment payment = paymentRepository.findById(paymentId)
                .orElseThrow(() -> new IllegalArgumentException("Платеж не найден: " + paymentId));

        if (!payment.isCancellable()) {
            throw new IllegalStateException("Платеж нельзя отменить в текущем статусе: " + payment.getStatus());
        }

        try {
            // Отправляем запрос на отмену в ЮKassa
            String cancelUrl = "/payments/" + payment.getYookassaPaymentId() + "/cancel";
            Map<String, Object> cancelRequest = Map.of("reason", "user_cancelled");

            JsonNode response = yooKassaWebClient
                    .post()
                    .uri(cancelUrl)
                    .header("Idempotence-Key", generateIdempotenceKey())
                    .bodyValue(cancelRequest)
                    .retrieve()
                    .bodyToMono(JsonNode.class)
                    .timeout(yooKassaConfig.getTimeout())
                    .retryWhen(Retry.backoff(yooKassaConfig.getMaxRetryAttempts(), Duration.ofSeconds(1)))
                    .block();

            // Обновляем статус платежа
            updatePaymentFromYooKassaResponse(payment, response);
            payment = paymentRepository.save(payment);

            log.info("🚫 Платеж {} отменен", payment.getId());

            return mapToPaymentResponse(payment);

        } catch (Exception e) {
            log.error("❌ Ошибка отмены платежа {}: {}", paymentId, e.getMessage());
            throw new RuntimeException("Ошибка отмены платежа: " + e.getMessage(), e);
        }
    }

    /**
     * Получение списка банков, поддерживающих СБП
     */
    public List<SbpBankInfo> getSbpBanks() {
        return Arrays.asList(
                new SbpBankInfo("sberbank", "Сбербанк", "https://static.yoomoney.ru/files-front/banks-logos/sber.svg"),
                new SbpBankInfo("tinkoff", "Тинькофф Банк",
                        "https://static.yoomoney.ru/files-front/banks-logos/tcs.svg"),
                new SbpBankInfo("vtb", "ВТБ", "https://static.yoomoney.ru/files-front/banks-logos/vtb.svg"),
                new SbpBankInfo("alfabank", "Альфа-Банк",
                        "https://static.yoomoney.ru/files-front/banks-logos/alfabank.svg"),
                new SbpBankInfo("raiffeisen", "Райффайзенбанк",
                        "https://static.yoomoney.ru/files-front/banks-logos/raiffeisen.svg"),
                new SbpBankInfo("gazprombank", "Газпромбанк",
                        "https://static.yoomoney.ru/files-front/banks-logos/gazprom.svg"),
                new SbpBankInfo("rosbank", "Росбанк", "https://static.yoomoney.ru/files-front/banks-logos/rosbank.svg"),
                new SbpBankInfo("mkb", "МКБ", "https://static.yoomoney.ru/files-front/banks-logos/mkb.svg"));
    }

    /**
     * Проверка статуса платежа в ЮKassa
     */
    @Transactional
    public PaymentResponse checkPaymentStatus(Long paymentId) {
        Payment payment = paymentRepository.findById(paymentId)
                .orElseThrow(() -> new IllegalArgumentException("Платеж не найден: " + paymentId));

        if (payment.getYookassaPaymentId() == null) {
            throw new IllegalStateException("Платеж не создан в ЮKassa");
        }

        try {
            // Запрашиваем актуальный статус из ЮKassa
            JsonNode response = yooKassaWebClient
                    .get()
                    .uri("/payments/" + payment.getYookassaPaymentId())
                    .retrieve()
                    .bodyToMono(JsonNode.class)
                    .timeout(yooKassaConfig.getTimeout())
                    .block();

            // Обновляем платеж
            PaymentStatus oldStatus = payment.getStatus();
            updatePaymentFromYooKassaResponse(payment, response);
            payment = paymentRepository.save(payment);

            if (oldStatus != payment.getStatus()) {
                log.info("📊 Статус платежа {} обновлен: {} → {}",
                        payment.getId(), oldStatus, payment.getStatus());

                // Обновляем статус заказа при успешной оплате
                if (payment.getStatus() == PaymentStatus.SUCCEEDED && oldStatus != PaymentStatus.SUCCEEDED) {
                    updateOrderStatusAfterPayment(payment.getOrder());
                }
            }

            return mapToPaymentResponse(payment);

        } catch (Exception e) {
            log.error("❌ Ошибка проверки статуса платежа {}: {}", paymentId, e.getMessage());
            throw new RuntimeException("Ошибка проверки статуса платежа: " + e.getMessage(), e);
        }
    }

    // Приватные методы

    private void validateOrderForPayment(Order order) {
        if (order.getTotalAmount() == null || order.getTotalAmount().compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException("Сумма заказа должна быть положительной");
        }

        // Проверяем, что для заказа еще нет успешного платежа
        boolean hasSuccessfulPayment = paymentRepository.existsSuccessfulPaymentForOrder(order.getId().longValue());
        if (hasSuccessfulPayment) {
            throw new IllegalStateException("Заказ уже оплачен");
        }
    }

    private Map<String, Object> buildYooKassaPaymentRequest(Payment payment, CreatePaymentRequest request) {
        Map<String, Object> paymentRequest = new HashMap<>();

        // Основные параметры
        Map<String, Object> amount = Map.of(
                "value", payment.getAmount().toString(),
                "currency", payment.getCurrency());
        paymentRequest.put("amount", amount);

        // Описание
        String description = request.getDescription() != null
                ? request.getDescription()
                : "Оплата заказа №" + payment.getOrder().getId() + " в PizzaNat";
        paymentRequest.put("description", description);

        // Метод оплаты
        Map<String, Object> paymentMethod = new HashMap<>();
        paymentMethod.put("type", payment.getMethod().getYookassaMethod());

        // Для СБП добавляем банк, если указан
        if (payment.getMethod() == PaymentMethod.SBP && payment.getBankId() != null) {
            paymentMethod.put("bank_id", payment.getBankId());
        }
        paymentRequest.put("payment_method", paymentMethod);

        // Подтверждение - с принудительным использованием только СБП
        Map<String, Object> confirmation = new HashMap<>();
        confirmation.put("type", "redirect");
        confirmation.put("return_url", request.getReturnUrl() != null ? request.getReturnUrl()
                : "https://dimbopizza.ru/orders/" + payment.getOrder().getId());

        // Ограничиваем способы оплаты только СБП
        if (payment.getMethod() == PaymentMethod.SBP) {
            confirmation.put("enforce_payment_method", true);
        }
        paymentRequest.put("confirmation", confirmation);

        // Метаданные
        Map<String, Object> metadata = Map.of(
                "order_id", payment.getOrder().getId().toString(),
                "payment_id", payment.getId().toString());
        paymentRequest.put("metadata", metadata);

        // Захват платежа
        paymentRequest.put("capture", true);

        return paymentRequest;
    }

    private JsonNode sendPaymentRequest(Map<String, Object> request, String idempotenceKey) {
        return yooKassaWebClient
                .post()
                .uri("/payments")
                .header("Idempotence-Key", idempotenceKey)
                .bodyValue(request)
                .retrieve()
                .onStatus(HttpStatusCode::isError, response -> {
                    return response.bodyToMono(String.class)
                            .flatMap(errorBody -> {
                                log.error("❌ Ошибка ЮKassa API: {}", errorBody);
                                return Mono.error(new RuntimeException("Ошибка ЮKassa API: " + errorBody));
                            });
                })
                .bodyToMono(JsonNode.class)
                .timeout(yooKassaConfig.getTimeout())
                .retryWhen(Retry.backoff(yooKassaConfig.getMaxRetryAttempts(), Duration.ofSeconds(1)))
                .block();
    }

    private void updatePaymentFromYooKassaResponse(Payment payment, JsonNode response) {
        try {
            // ID платежа в ЮKassa
            if (response.has("id")) {
                payment.setYookassaPaymentId(response.get("id").asText());
            }

            // Статус платежа
            if (response.has("status")) {
                String yookassaStatus = response.get("status").asText();
                PaymentStatus status = PaymentStatus.fromYookassaStatus(yookassaStatus);
                payment.setStatus(status);
            }

            // URL для подтверждения
            if (response.has("confirmation") && response.get("confirmation").has("confirmation_url")) {
                payment.setConfirmationUrl(response.get("confirmation").get("confirmation_url").asText());
            }

            // Метаданные
            if (response.has("metadata")) {
                try {
                    // Сериализуем JsonNode в строку для хранения в JSONB поле
                    payment.setMetadata(objectMapper.writeValueAsString(response.get("metadata")));
                } catch (Exception e) {
                    log.warn("Ошибка сериализации метаданных: {}", e.getMessage());
                    payment.setMetadata(response.get("metadata").toString());
                }
            }

            // URL чека
            if (response.has("receipt_registration") && response.get("receipt_registration").has("status")) {
                String receiptStatus = response.get("receipt_registration").get("status").asText();
                if ("succeeded".equals(receiptStatus) && response.get("receipt_registration").has("receipt_url")) {
                    payment.setReceiptUrl(response.get("receipt_registration").get("receipt_url").asText());
                }
            }

            // Ошибка
            if (response.has("error")) {
                JsonNode error = response.get("error");
                String errorMessage = error.has("description") ? error.get("description").asText()
                        : "Неизвестная ошибка";
                payment.setErrorMessage(errorMessage);
            }

        } catch (Exception e) {
            log.error("❌ Ошибка обработки ответа ЮKassa: {}", e.getMessage(), e);
            throw new RuntimeException("Ошибка обработки ответа ЮKassa", e);
        }
    }

    private void updateOrderStatusAfterPayment(Order order) {
        try {
            log.info("💰 Заказ {} успешно оплачен через ЮКассу", order.getId());

            // Публикуем событие о новом заказе для админского бота
            // Поскольку платеж завершен, AdminBotService.hasActivePendingPayments() вернет false
            // и уведомление будет отправлено в бот
            try {
                eventPublisher.publishEvent(new NewOrderEvent(this, order));
                log.info("✅ Событие о успешно оплаченном заказе #{} опубликовано", order.getId());
            } catch (Exception e) {
                log.error("❌ Ошибка публикации события для заказа #{}: {}", order.getId(), e.getMessage(), e);
                // Не прерываем выполнение, так как основная функция платежа выполнена
            }

            // Здесь можно добавить дополнительную логику обновления статуса заказа
            // Например, изменить статус на "PAID" или "IN_PROGRESS" если потребуется

        } catch (Exception e) {
            log.error("❌ Ошибка обработки успешного платежа для заказа {}: {}", order.getId(), e.getMessage());
        }
    }

    private PaymentResponse mapToPaymentResponse(Payment payment) {
        PaymentResponse response = new PaymentResponse();
        response.setId(payment.getId());
        response.setYookassaPaymentId(payment.getYookassaPaymentId());
        response.setOrderId(payment.getOrder().getId().longValue());
        response.setStatus(payment.getStatus());
        response.setMethod(payment.getMethod());
        response.setAmount(payment.getAmount());
        response.setCurrency(payment.getCurrency());
        response.setBankId(payment.getBankId());
        response.setConfirmationUrl(payment.getConfirmationUrl());
        response.setErrorMessage(payment.getErrorMessage());
        response.setReceiptUrl(payment.getReceiptUrl());
        response.setCreatedAt(payment.getCreatedAt());
        response.setPaidAt(payment.getPaidAt());
        return response;
    }

    private String generateIdempotenceKey() {
        return "pizzanat_" + UUID.randomUUID().toString().replace("-", "");
    }
}