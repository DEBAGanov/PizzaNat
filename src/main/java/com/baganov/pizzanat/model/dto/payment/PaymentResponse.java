/**
 * @file: PaymentResponse.java
 * @description: DTO для ответа с информацией о платеже
 * @dependencies: PaymentStatus, PaymentMethod
 * @created: 2025-01-26
 */
package com.baganov.pizzanat.model.dto.payment;

import com.baganov.pizzanat.entity.PaymentMethod;
import com.baganov.pizzanat.entity.PaymentStatus;
import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.datatype.jsr310.ser.LocalDateTimeSerializer;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * DTO для возврата информации о платеже клиенту
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public class PaymentResponse {

    private Long id;
    private String yookassaPaymentId;
    private Long orderId;
    private PaymentStatus status;
    private PaymentMethod method;
    private BigDecimal amount;
    private String currency;
    private String bankId;
    private String confirmationUrl;
    private String errorMessage;
    private String receiptUrl;

    @JsonFormat(pattern = "yyyy-MM-dd'T'HH:mm:ss")
    @JsonSerialize(using = LocalDateTimeSerializer.class)
    private LocalDateTime createdAt;

    @JsonFormat(pattern = "yyyy-MM-dd'T'HH:mm:ss")
    @JsonSerialize(using = LocalDateTimeSerializer.class)
    private LocalDateTime paidAt;

    // Конструкторы
    public PaymentResponse() {
    }

    public PaymentResponse(Long id, String yookassaPaymentId, Long orderId,
            PaymentStatus status, PaymentMethod method,
            BigDecimal amount, String currency) {
        this.id = id;
        this.yookassaPaymentId = yookassaPaymentId;
        this.orderId = orderId;
        this.status = status;
        this.method = method;
        this.amount = amount;
        this.currency = currency;
    }

    // Геттеры и сеттеры
    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getYookassaPaymentId() {
        return yookassaPaymentId;
    }

    public void setYookassaPaymentId(String yookassaPaymentId) {
        this.yookassaPaymentId = yookassaPaymentId;
    }

    public Long getOrderId() {
        return orderId;
    }

    public void setOrderId(Long orderId) {
        this.orderId = orderId;
    }

    public PaymentStatus getStatus() {
        return status;
    }

    public void setStatus(PaymentStatus status) {
        this.status = status;
    }

    public PaymentMethod getMethod() {
        return method;
    }

    public void setMethod(PaymentMethod method) {
        this.method = method;
    }

    public BigDecimal getAmount() {
        return amount;
    }

    public void setAmount(BigDecimal amount) {
        this.amount = amount;
    }

    public String getCurrency() {
        return currency;
    }

    public void setCurrency(String currency) {
        this.currency = currency;
    }

    public String getBankId() {
        return bankId;
    }

    public void setBankId(String bankId) {
        this.bankId = bankId;
    }

    public String getConfirmationUrl() {
        return confirmationUrl;
    }

    public void setConfirmationUrl(String confirmationUrl) {
        this.confirmationUrl = confirmationUrl;
    }

    public String getErrorMessage() {
        return errorMessage;
    }

    public void setErrorMessage(String errorMessage) {
        this.errorMessage = errorMessage;
    }

    public String getReceiptUrl() {
        return receiptUrl;
    }

    public void setReceiptUrl(String receiptUrl) {
        this.receiptUrl = receiptUrl;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }

    public LocalDateTime getPaidAt() {
        return paidAt;
    }

    public void setPaidAt(LocalDateTime paidAt) {
        this.paidAt = paidAt;
    }

    // Utility методы
    public boolean isCompleted() {
        return status != null && status.isCompleted();
    }

    public boolean isSuccessful() {
        return status != null && status.isSuccessful();
    }

    public boolean isPending() {
        return status == PaymentStatus.PENDING;
    }

    @Override
    public String toString() {
        return "PaymentResponse{" +
                "id=" + id +
                ", yookassaPaymentId='" + yookassaPaymentId + '\'' +
                ", orderId=" + orderId +
                ", status=" + status +
                ", method=" + method +
                ", amount=" + amount +
                ", currency='" + currency + '\'' +
                ", bankId='" + bankId + '\'' +
                '}';
    }
}