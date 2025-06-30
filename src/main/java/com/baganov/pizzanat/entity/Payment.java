/**
 * @file: Payment.java
 * @description: Entity класс для платежей через ЮKassa
 * @dependencies: Order, PaymentStatus, PaymentMethod, JPA
 * @created: 2025-01-26
 */
package com.baganov.pizzanat.entity;

import jakarta.persistence.*;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * Entity для хранения информации о платежах через ЮKassa
 */
@Entity
@Table(name = "payments")
public class Payment {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /**
     * ID платежа в системе ЮKassa (уникальный идентификатор)
     */
    @Column(name = "yookassa_payment_id", unique = true)
    private String yookassaPaymentId;

    /**
     * Ссылка на заказ
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "order_id", nullable = false)
    @NotNull
    private Order order;

    /**
     * Статус платежа
     */
    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    @NotNull
    private PaymentStatus status = PaymentStatus.PENDING;

    /**
     * Метод оплаты
     */
    @Enumerated(EnumType.STRING)
    @Column(name = "method", nullable = false)
    @NotNull
    private PaymentMethod method;

    /**
     * Сумма платежа
     */
    @Column(name = "amount", nullable = false, precision = 10, scale = 2)
    @NotNull
    @Positive
    private BigDecimal amount;

    /**
     * Валюта платежа (по умолчанию RUB)
     */
    @Column(name = "currency", nullable = false, length = 3)
    @NotNull
    private String currency = "RUB";

    /**
     * ID банка для СБП платежей (sberbank, tinkoff, vtb и др.)
     */
    @Column(name = "bank_id", length = 100)
    private String bankId;

    /**
     * URL для подтверждения платежа
     */
    @Column(name = "confirmation_url", columnDefinition = "TEXT")
    private String confirmationUrl;

    /**
     * Дата создания
     */
    @CreationTimestamp
    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    /**
     * Дата последнего обновления
     */
    @UpdateTimestamp
    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    /**
     * Дата успешной оплаты
     */
    @Column(name = "paid_at")
    private LocalDateTime paidAt;

    /**
     * Сообщение об ошибке (если есть)
     */
    @Column(name = "error_message", columnDefinition = "TEXT")
    private String errorMessage;

    /**
     * Ключ идемпотентности для ЮKassa
     */
    @Column(name = "idempotence_key")
    private String idempotenceKey;

    /**
     * Дополнительные данные в формате JSON
     */
    @Column(name = "metadata", columnDefinition = "TEXT")
    private String metadata;

    /**
     * URL чека об оплате
     */
    @Column(name = "receipt_url", length = 500)
    private String receiptUrl;

    /**
     * ID возврата в случае отмены платежа
     */
    @Column(name = "refund_id")
    private String refundId;

    // Конструкторы
    public Payment() {
    }

    public Payment(Order order, PaymentMethod method, BigDecimal amount) {
        this.order = order;
        this.method = method;
        this.amount = amount;
        this.status = PaymentStatus.PENDING;
        this.currency = "RUB";
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

    public Order getOrder() {
        return order;
    }

    public void setOrder(Order order) {
        this.order = order;
    }

    public PaymentStatus getStatus() {
        return status;
    }

    public void setStatus(PaymentStatus status) {
        this.status = status;
        // Автоматически устанавливаем дату оплаты при успешном статусе
        if (status == PaymentStatus.SUCCEEDED && this.paidAt == null) {
            this.paidAt = LocalDateTime.now();
        }
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

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }

    public LocalDateTime getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(LocalDateTime updatedAt) {
        this.updatedAt = updatedAt;
    }

    public LocalDateTime getPaidAt() {
        return paidAt;
    }

    public void setPaidAt(LocalDateTime paidAt) {
        this.paidAt = paidAt;
    }

    public String getErrorMessage() {
        return errorMessage;
    }

    public void setErrorMessage(String errorMessage) {
        this.errorMessage = errorMessage;
    }

    public String getIdempotenceKey() {
        return idempotenceKey;
    }

    public void setIdempotenceKey(String idempotenceKey) {
        this.idempotenceKey = idempotenceKey;
    }

    public String getMetadata() {
        return metadata;
    }

    public void setMetadata(String metadata) {
        this.metadata = metadata;
    }

    public String getReceiptUrl() {
        return receiptUrl;
    }

    public void setReceiptUrl(String receiptUrl) {
        this.receiptUrl = receiptUrl;
    }

    public String getRefundId() {
        return refundId;
    }

    public void setRefundId(String refundId) {
        this.refundId = refundId;
    }

    // Utility методы
    public boolean isCompleted() {
        return status != null && status.isCompleted();
    }

    public boolean isSuccessful() {
        return status != null && status.isSuccessful();
    }

    public boolean isCancellable() {
        return status != null && status.isCancellable();
    }

    @Override
    public String toString() {
        return "Payment{" +
                "id=" + id +
                ", yookassaPaymentId='" + yookassaPaymentId + '\'' +
                ", orderId=" + (order != null ? order.getId() : null) +
                ", status=" + status +
                ", method=" + method +
                ", amount=" + amount +
                ", currency='" + currency + '\'' +
                ", bankId='" + bankId + '\'' +
                ", createdAt=" + createdAt +
                '}';
    }
}