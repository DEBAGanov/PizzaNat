/**
 * @file: CreatePaymentRequest.java
 * @description: DTO для запроса создания платежа
 * @dependencies: PaymentMethod, Jakarta Validation
 * @created: 2025-01-26
 */
package com.baganov.pizzanat.model.dto.payment;

import com.baganov.pizzanat.entity.PaymentMethod;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

import java.math.BigDecimal;

/**
 * DTO для запроса создания платежа
 */
public class CreatePaymentRequest {

    /**
     * ID заказа для оплаты
     */
    @NotNull(message = "ID заказа обязателен")
    @Positive(message = "ID заказа должен быть положительным числом")
    private Long orderId;

    /**
     * Метод оплаты (по умолчанию СБП)
     */
    @NotNull(message = "Метод оплаты обязателен")
    private PaymentMethod method = PaymentMethod.SBP;

    /**
     * ID банка для СБП платежей (опционально)
     * Примеры: sberbank, tinkoff, vtb, alfabank, raiffeisen
     */
    private String bankId;

    /**
     * Сумма платежа (если не указана, берется из заказа)
     */
    @Positive(message = "Сумма платежа должна быть положительной")
    private BigDecimal amount;

    /**
     * URL для возврата после оплаты (опционально)
     */
    private String returnUrl;

    /**
     * Описание платежа (опционально)
     */
    private String description;

    // Конструкторы
    public CreatePaymentRequest() {
    }

    public CreatePaymentRequest(Long orderId, PaymentMethod method) {
        this.orderId = orderId;
        this.method = method;
    }

    public CreatePaymentRequest(Long orderId, PaymentMethod method, String bankId) {
        this.orderId = orderId;
        this.method = method;
        this.bankId = bankId;
    }

    // Геттеры и сеттеры
    public Long getOrderId() {
        return orderId;
    }

    public void setOrderId(Long orderId) {
        this.orderId = orderId;
    }

    public PaymentMethod getMethod() {
        return method;
    }

    public void setMethod(PaymentMethod method) {
        this.method = method;
    }

    public String getBankId() {
        return bankId;
    }

    public void setBankId(String bankId) {
        this.bankId = bankId;
    }

    public BigDecimal getAmount() {
        return amount;
    }

    public void setAmount(BigDecimal amount) {
        this.amount = amount;
    }

    public String getReturnUrl() {
        return returnUrl;
    }

    public void setReturnUrl(String returnUrl) {
        this.returnUrl = returnUrl;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    // Utility методы
    public boolean isSbpPayment() {
        return method == PaymentMethod.SBP;
    }

    public boolean hasBankId() {
        return bankId != null && !bankId.trim().isEmpty();
    }

    public boolean hasCustomAmount() {
        return amount != null;
    }

    @Override
    public String toString() {
        return "CreatePaymentRequest{" +
                "orderId=" + orderId +
                ", method=" + method +
                ", bankId='" + bankId + '\'' +
                ", amount=" + amount +
                ", returnUrl='" + returnUrl + '\'' +
                ", description='" + description + '\'' +
                '}';
    }
}