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
import com.baganov.pizzanat.event.PaymentAlertEvent;
import com.baganov.pizzanat.model.dto.payment.CreatePaymentRequest;
import com.baganov.pizzanat.model.dto.payment.PaymentResponse;
import com.baganov.pizzanat.model.dto.payment.SbpBankInfo;
import com.baganov.pizzanat.model.dto.payment.YooKassaReceiptDto;
import com.baganov.pizzanat.model.dto.payment.CustomerDto;
import com.baganov.pizzanat.model.dto.payment.ReceiptItemDto;
import com.baganov.pizzanat.model.dto.payment.AmountDto;
import com.baganov.pizzanat.repository.OrderRepository;
import com.baganov.pizzanat.repository.PaymentRepository;
import com.baganov.pizzanat.repository.OrderStatusRepository;
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

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import org.springframework.web.reactive.function.client.WebClientResponseException;
import reactor.core.publisher.Mono;
import reactor.util.retry.Retry;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.*;
import java.util.Optional;

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
    private final OrderStatusRepository orderStatusRepository;
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

            // Проверяем тип события согласно документации ЮKassa API
            if (!isValidPaymentEvent(eventType)) {
                log.warn("⚠️ Неизвестный тип события ЮKassa: {}", eventType);
                return false;
            }

            log.info("📋 Обработка события ЮKassa: {} для платежа {}", eventType, yookassaPaymentId);

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

            log.info("📊 Статус платежа {} изменен: {} → {} (событие: {})",
                    payment.getId(), oldStatus, payment.getStatus(), eventType);

            // Записываем метрики изменения статуса
            if (oldStatus != payment.getStatus()) {
                paymentMetricsService.recordPaymentStatusChange(payment, oldStatus);
                paymentAlertService.onPaymentStatusChanged(payment, oldStatus);
                
            }

            // Обрабатываем специфичные события ЮKassa
            switch (eventType) {
                case "payment.succeeded":
                    handlePaymentSucceededEvent(payment, oldStatus);
                    break;
                case "payment.canceled":
                    handlePaymentCanceledEvent(payment, oldStatus);
                    break;
                default:
                    log.debug("🔄 Событие {} не требует дополнительной обработки", eventType);
            }

            return true;

        } catch (Exception e) {
            log.error("❌ Ошибка обработки webhook ЮKassa: {}", e.getMessage(), e);
            return false;
        }
    }

    /**
     * Проверка валидности типа события согласно API ЮKassa
     * https://yookassa.ru/developers/using-api/webhooks
     */
    private boolean isValidPaymentEvent(String eventType) {
        return "payment.succeeded".equals(eventType) || 
               "payment.canceled".equals(eventType);
    }

    /**
     * Обработка события payment.succeeded - платеж успешно завершен
     */
    private void handlePaymentSucceededEvent(Payment payment, PaymentStatus oldStatus) {
        if (payment.getStatus() == PaymentStatus.SUCCEEDED && oldStatus != PaymentStatus.SUCCEEDED) {
            log.info("💰 Платеж {} успешно завершен - обновляем статус заказа", payment.getId());
            updateOrderStatusAfterPayment(payment.getOrder());
        } else {
            log.debug("🔄 Платеж {} уже был в статусе SUCCEEDED", payment.getId());
        }
    }

    /**
     * Обработка события payment.canceled - платеж отменен
     */
    private void handlePaymentCanceledEvent(Payment payment, PaymentStatus oldStatus) {
        if (payment.getStatus() == PaymentStatus.CANCELLED && oldStatus != PaymentStatus.CANCELLED) {
            log.info("❌ Платеж {} отменен - уведомляем администраторов", payment.getId());
            
            // Уведомляем администраторов об отмене платежа через AlertService
            try {
                String alertMessage = String.format("❌ *ПЛАТЕЖ ОТМЕНЕН*\n\n" +
                        "💳 Платеж #%d\n" +
                        "🆔 Заказ #%d\n" +
                        "💰 Сумма: %.2f ₽\n" +
                        "🕐 Время: %s\n\n" +
                        "Заказ НЕ будет отправлен в работу.",
                        payment.getId(),
                        payment.getOrder().getId(),
                        payment.getAmount().doubleValue(),
                        LocalDateTime.now().format(DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm")));
                
                // Отправляем алерт об отмене платежа через PaymentAlertEvent
                PaymentAlertEvent alertEvent = new PaymentAlertEvent(this, alertMessage, PaymentAlertEvent.AlertType.CRITICAL_PAYMENT_FAILURE);
                eventPublisher.publishEvent(alertEvent);
                log.info("✅ Уведомление об отмене платежа {} отправлено администраторам", payment.getId());
            } catch (Exception e) {
                log.error("❌ Ошибка отправки уведомления об отмене платежа {}: {}", payment.getId(), e.getMessage());
            }
        } else {
            log.debug("🔄 Платеж {} уже был в статусе CANCELLED", payment.getId());
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

        // Формируем чек для онлайн-кассы (54-ФЗ)
        YooKassaReceiptDto receipt = buildReceipt(payment.getOrder());
        if (receipt != null) {
            try {
                Map<String, Object> receiptMap = objectMapper.convertValue(receipt, Map.class);
                paymentRequest.put("receipt", receiptMap);
                log.debug("📄 Чек добавлен в платеж: {} позиций", receipt.getItems().size());
            } catch (Exception e) {
                log.warn("⚠️ Ошибка сериализации чека: {}", e.getMessage());
                // Продолжаем без чека, это не критично для платежа
            }
        }

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
            log.debug("🔍 Обновление платежа {} данными от ЮKassa: {}", payment.getId(), response);

            // ID платежа в ЮKassa
            if (response.has("id")) {
                payment.setYookassaPaymentId(response.get("id").asText());
            }

            // Статус платежа
            if (response.has("status")) {
                String yookassaStatus = response.get("status").asText();
                PaymentStatus status = PaymentStatus.fromYookassaStatus(yookassaStatus);
                payment.setStatus(status);
                log.debug("📊 Статус платежа обновлен на: {}", status);
            }

            // Сумма платежа (для подтверждения)
            if (response.has("amount")) {
                JsonNode amountNode = response.get("amount");
                if (amountNode.has("value")) {
                    BigDecimal webhookAmount = new BigDecimal(amountNode.get("value").asText());
                    if (!payment.getAmount().equals(webhookAmount)) {
                        log.warn("⚠️ Сумма в webhook ({}) не совпадает с суммой платежа ({}) для платежа #{}",
                                webhookAmount, payment.getAmount(), payment.getId());
                    }
                }
            }

            // Время захвата платежа (captured_at)
            if (response.has("captured_at") && !response.get("captured_at").isNull()) {
                try {
                    String capturedAtStr = response.get("captured_at").asText();
                    // ЮKassa использует ISO 8601 формат: 2023-07-10T15:45:30.123Z
                    LocalDateTime capturedAt = LocalDateTime.parse(capturedAtStr.substring(0, 19));
                    payment.setPaidAt(capturedAt);
                    log.debug("💰 Время захвата платежа: {}", capturedAt);
                } catch (Exception e) {
                    log.warn("⚠️ Ошибка парсинга captured_at: {}", e.getMessage());
                }
            }

            // Способ оплаты (payment_method)
            if (response.has("payment_method")) {
                JsonNode paymentMethodNode = response.get("payment_method");
                if (paymentMethodNode.has("type")) {
                    String paymentMethodType = paymentMethodNode.get("type").asText();
                    
                    // Логируем дополнительную информацию в зависимости от типа
                    switch (paymentMethodType) {
                        case "bank_card":
                            if (paymentMethodNode.has("card")) {
                                JsonNode cardNode = paymentMethodNode.get("card");
                                String cardMask = String.format("%s****%s",
                                        cardNode.path("first6").asText(""),
                                        cardNode.path("last4").asText(""));
                                log.info("💳 Платеж картой: {} ({})", cardMask, 
                                        cardNode.path("card_type").asText("unknown"));
                            }
                            break;
                        case "sbp":
                            log.info("📱 Платеж через СБП");
                            break;
                        default:
                            log.info("💰 Платеж через: {}", paymentMethodType);
                    }
                }
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
                    log.debug("📋 Метаданные обновлены: {}", payment.getMetadata());
                } catch (Exception e) {
                    log.warn("⚠️ Ошибка сериализации метаданных: {}", e.getMessage());
                    payment.setMetadata(response.get("metadata").toString());
                }
            }

            // Данные чека (receipt)
            if (response.has("receipt")) {
                JsonNode receiptNode = response.get("receipt");
                StringBuilder receiptInfo = new StringBuilder();
                
                if (receiptNode.has("registered") && receiptNode.get("registered").asBoolean()) {
                    if (receiptNode.has("fiscal_document_number")) {
                        receiptInfo.append("ФД: ").append(receiptNode.get("fiscal_document_number").asText());
                    }
                    if (receiptNode.has("fiscal_storage_number")) {
                        receiptInfo.append(", ФН: ").append(receiptNode.get("fiscal_storage_number").asText());
                    }
                    
                    log.info("🧾 Фискальный чек зарегистрирован: {}", receiptInfo.toString());
                }
            }

            // URL чека (legacy поддержка)
            if (response.has("receipt_registration") && response.get("receipt_registration").has("status")) {
                String receiptStatus = response.get("receipt_registration").get("status").asText();
                if ("succeeded".equals(receiptStatus) && response.get("receipt_registration").has("receipt_url")) {
                    payment.setReceiptUrl(response.get("receipt_registration").get("receipt_url").asText());
                    log.info("🧾 URL чека: {}", payment.getReceiptUrl());
                }
            }

            // Информация о возврате (refund)
            if (response.has("refunded_amount")) {
                JsonNode refundedAmountNode = response.get("refunded_amount");
                if (refundedAmountNode.has("value")) {
                    BigDecimal refundedAmount = new BigDecimal(refundedAmountNode.get("value").asText());
                    if (refundedAmount.compareTo(BigDecimal.ZERO) > 0) {
                        log.warn("🔄 Платеж имеет возврат на сумму: {} ₽", refundedAmount);
                    }
                }
            }

            // Ошибка
            if (response.has("error")) {
                JsonNode error = response.get("error");
                String errorCode = error.path("code").asText("unknown");
                String errorMessage = error.has("description") ? error.get("description").asText()
                        : "Неизвестная ошибка";
                payment.setErrorMessage(String.format("%s: %s", errorCode, errorMessage));
                log.error("❌ Ошибка платежа #{}: {} - {}", payment.getId(), errorCode, errorMessage);
            }

            log.info("✅ Платеж #{} обновлен данными от ЮKassa (статус: {})", 
                    payment.getId(), payment.getStatus());

        } catch (Exception e) {
            log.error("❌ Ошибка обработки ответа ЮKassa для платежа #{}: {}", payment.getId(), e.getMessage(), e);
            throw new RuntimeException("Ошибка обработки ответа ЮKassa", e);
        }
    }

    private void updateOrderStatusAfterPayment(Order order) {
        try {
            log.info("💰 Заказ {} успешно оплачен через ЮКассу", order.getId());

            // Находим статус "CONFIRMED" для оплаченного заказа
            Optional<OrderStatus> paidStatusOpt = orderStatusRepository.findByName("CONFIRMED");
            if (paidStatusOpt.isPresent()) {
                order.setStatus(paidStatusOpt.get());
                log.info("📋 Статус заказа {} изменен на CONFIRMED", order.getId());
            } else {
                log.warn("⚠️ Статус CONFIRMED не найден в БД");
            }
            
            // Устанавливаем способ оплаты на основе платежа
            List<Payment> payments = paymentRepository.findByOrderIdOrderByCreatedAtDesc(order.getId().longValue());
            if (!payments.isEmpty()) {
                Payment successfulPayment = payments.stream()
                    .filter(p -> p.getStatus() == PaymentStatus.SUCCEEDED)
                    .findFirst()
                    .orElse(null);
                
                if (successfulPayment != null) {
                    // Устанавливаем способ оплаты из платежа (уже PaymentMethod enum)
                    order.setPaymentMethod(successfulPayment.getMethod());
                    // Устанавливаем статус оплаты как PAID
                    order.setPaymentStatus(OrderPaymentStatus.PAID);
                    log.info("💳 Заказ {} - установлен способ оплаты: {}, статус оплаты: PAID", order.getId(), successfulPayment.getMethod());
                }
            }
            
            // Обновляем время изменения
            order.setUpdatedAt(LocalDateTime.now());
            
            // Сохраняем изменения заказа
            Order updatedOrder = orderRepository.save(order);
            log.info("✅ Статус заказа {} обновлен на CONFIRMED, способ оплаты: {}", 
                    order.getId(), updatedOrder.getPaymentMethod());

            // Публикуем событие о новом заказе для админского бота
            // Поскольку платеж завершен, AdminBotService.hasActivePendingPayments() вернет false
            // и уведомление будет отправлено в бот
            try {
                eventPublisher.publishEvent(new NewOrderEvent(this, updatedOrder));
                log.info("✅ Событие о успешно оплаченном заказе #{} опубликовано", updatedOrder.getId());
            } catch (Exception e) {
                log.error("❌ Ошибка публикации события для заказа #{}: {}", updatedOrder.getId(), e.getMessage(), e);
                // Не прерываем выполнение, так как основная функция платежа выполнена
            }

        } catch (Exception e) {
            log.error("❌ Ошибка обновления статуса заказа #{}: {}", order.getId(), e.getMessage(), e);
            // Не пробрасываем исключение, чтобы не нарушить обработку webhook
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

    /**
     * Формирует чек для онлайн-кассы согласно 54-ФЗ
     */
    private YooKassaReceiptDto buildReceipt(Order order) {
        try {
            // Проверяем обязательные данные
            if (order.getContactPhone() == null || order.getContactPhone().trim().isEmpty()) {
                log.warn("⚠️ Не указан телефон покупателя для чека заказа #{}", order.getId());
                return null;
            }

            if (order.getContactName() == null || order.getContactName().trim().isEmpty()) {
                log.warn("⚠️ Не указано имя покупателя для чека заказа #{}", order.getId());
                return null;
            }

            if (order.getItems() == null || order.getItems().isEmpty()) {
                log.warn("⚠️ Нет товаров в заказе #{} для формирования чека", order.getId());
                return null;
            }

            // Нормализуем номер телефона для ЮКассы (формат +7XXXXXXXXXX)
            String normalizedPhone = normalizePhoneNumber(order.getContactPhone());
            if (normalizedPhone == null) {
                log.warn("⚠️ Некорректный формат телефона {} для чека заказа #{}", 
                        order.getContactPhone(), order.getId());
                return null;
            }

            // Формируем данные покупателя
            CustomerDto customer = CustomerDto.builder()
                    .fullName(order.getContactName())
                    .phone(normalizedPhone)
                    .build();

            // Формируем товарные позиции
            List<ReceiptItemDto> items = new ArrayList<>();
            for (OrderItem orderItem : order.getItems()) {
                ReceiptItemDto receiptItem = buildReceiptItem(orderItem);
                if (receiptItem != null) {
                    items.add(receiptItem);
                }
            }

            // Добавляем доставку как отдельную позицию чека (если платная)
            if (order.getDeliveryCost() != null && order.getDeliveryCost().compareTo(BigDecimal.ZERO) > 0) {
                ReceiptItemDto deliveryItem = buildDeliveryReceiptItem(order.getDeliveryCost());
                if (deliveryItem != null) {
                    items.add(deliveryItem);
                    log.debug("📦 Добавлена позиция доставки в чек: {} ₽", order.getDeliveryCost());
                }
            }

            if (items.isEmpty()) {
                log.warn("⚠️ Не удалось сформировать ни одной позиции чека для заказа #{}", order.getId());
                return null;
            }

            YooKassaReceiptDto receipt = YooKassaReceiptDto.builder()
                    .customer(customer)
                    .items(items)
                    .build();

            log.debug("✅ Сформирован чек для заказа #{}: {} позиций, покупатель: {} ({})", 
                    order.getId(), items.size(), order.getContactName(), normalizedPhone);

            return receipt;

        } catch (Exception e) {
            log.error("❌ Ошибка формирования чека для заказа #{}: {}", order.getId(), e.getMessage(), e);
            return null;
        }
    }

    /**
     * Формирует позицию доставки для чека
     */
    private ReceiptItemDto buildDeliveryReceiptItem(BigDecimal deliveryCost) {
        try {
            // Формируем позицию доставки согласно требованиям ЮКассы
            AmountDto amount = AmountDto.builder()
                    .value(deliveryCost.toString())  // Стоимость доставки за единицу
                    .currency("RUB")
                    .build();

            return ReceiptItemDto.builder()
                    .description("Доставка")  // Название услуги
                    .quantity("1.00")         // Количество = 1
                    .amount(amount)           // Стоимость доставки
                    .vatCode(1)               // НДС 0% для услуги доставки
                    .paymentSubject("service") // Услуга (не товар)
                    .paymentMode("full_payment") // Полный расчет
                    .build();

        } catch (Exception e) {
            log.warn("⚠️ Ошибка формирования позиции доставки в чеке: {}", e.getMessage());
            return null;
        }
    }

    /**
     * Формирует товарную позицию для чека
     */
    private ReceiptItemDto buildReceiptItem(OrderItem orderItem) {
        try {
            Product product = orderItem.getProduct();
            if (product == null) {
                log.warn("⚠️ Товар не найден для позиции заказа ID: {}", orderItem.getId());
                return null;
            }

            // Обрезаем название товара до 128 символов согласно требованиям ЮКассы
            String description = product.getName();
            if (description != null && description.length() > 128) {
                description = description.substring(0, 125) + "...";
            }

            // ИСПРАВЛЕНИЕ: Формируем сумму товара как цену за единицу, а не общую сумму
            // YooKassa ожидает amount = цена за единицу, а общую сумму рассчитывает сам (amount * quantity)
            AmountDto amount = AmountDto.builder()
                    .value(orderItem.getPrice().toString())  // Цена за единицу, а не getSubtotal()
                    .currency("RUB")
                    .build();

            return ReceiptItemDto.builder()
                    .description(description != null ? description : "Товар")
                    .quantity(orderItem.getQuantity().toString() + ".00")
                    .amount(amount)
                    .vatCode(1) // НДС 0% для доставки еды
                    .paymentSubject("commodity") // товар
                    .paymentMode("full_payment") // полный расчет
                    .build();

        } catch (Exception e) {
            log.warn("⚠️ Ошибка формирования позиции чека для товара ID {}: {}", 
                    orderItem.getId(), e.getMessage());
            return null;
        }
    }

    /**
     * Нормализует номер телефона для ЮКассы
     * Принимает: +7XXXXXXXXXX, 8XXXXXXXXXX, 7XXXXXXXXXX
     * Возвращает: +7XXXXXXXXXX или null если формат некорректный
     */
    private String normalizePhoneNumber(String phone) {
        if (phone == null) {
            return null;
        }

        // Убираем все символы кроме цифр и +
        String cleanPhone = phone.replaceAll("[^\\d+]", "");

        // Проверяем различные форматы
        if (cleanPhone.matches("\\+7\\d{10}")) {
            // Уже в нужном формате: +7XXXXXXXXXX
            return cleanPhone;
        } else if (cleanPhone.matches("8\\d{10}")) {
            // Формат: 8XXXXXXXXXX -> +7XXXXXXXXXX
            return "+7" + cleanPhone.substring(1);
        } else if (cleanPhone.matches("7\\d{10}")) {
            // Формат: 7XXXXXXXXXX -> +7XXXXXXXXXX
            return "+" + cleanPhone;
        } else if (cleanPhone.matches("\\d{10}")) {
            // Формат: XXXXXXXXXX -> +7XXXXXXXXXX
            return "+7" + cleanPhone;
        }

        // Некорректный формат
        return null;
    }
}