/**
 * @file: ScheduledNotification.java
 * @description: Entity для хранения отложенных уведомлений (реферальные сообщения, напоминания)
 * @dependencies: JPA, Lombok, Order, User
 * @created: 2025-06-13
 */
package com.baganov.pizzanat.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "scheduled_notifications")
public class ScheduledNotification {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "order_id", nullable = false)
    private Order order;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id")
    private User user;

    @Column(name = "telegram_id")
    private Long telegramId;

    @Enumerated(EnumType.STRING)
    @Column(name = "notification_type", nullable = false, length = 50)
    private NotificationType notificationType;

    @Column(name = "message", nullable = false, columnDefinition = "TEXT")
    private String message;

    @Column(name = "scheduled_at", nullable = false)
    private LocalDateTime scheduledAt;

    @Column(name = "sent_at")
    private LocalDateTime sentAt;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", length = 20)
    @Builder.Default
    private NotificationStatus status = NotificationStatus.PENDING;

    @Column(name = "created_at")
    @Builder.Default
    private LocalDateTime createdAt = LocalDateTime.now();

    @Column(name = "updated_at")
    @Builder.Default
    private LocalDateTime updatedAt = LocalDateTime.now();

    @Column(name = "retry_count")
    @Builder.Default
    private Integer retryCount = 0;

    @Column(name = "max_retries")
    @Builder.Default
    private Integer maxRetries = 3;

    @Column(name = "error_message", columnDefinition = "TEXT")
    private String errorMessage;

    /**
     * Типы уведомлений
     */
    public enum NotificationType {
        REFERRAL_REMINDER("Напоминание о реферальной программе"),
        ORDER_FEEDBACK("Запрос обратной связи по заказу"),
        LOYALTY_REMINDER("Напоминание о программе лояльности"),
        SPECIAL_OFFER("Специальное предложение");

        private final String description;

        NotificationType(String description) {
            this.description = description;
        }

        public String getDescription() {
            return description;
        }
    }

    /**
     * Статусы уведомлений
     */
    public enum NotificationStatus {
        PENDING("Ожидает отправки"),
        SENT("Отправлено"),
        FAILED("Ошибка отправки"),
        CANCELLED("Отменено");

        private final String description;

        NotificationStatus(String description) {
            this.description = description;
        }

        public String getDescription() {
            return description;
        }
    }

    /**
     * Проверка, можно ли повторить отправку
     */
    public boolean canRetry() {
        return status == NotificationStatus.FAILED && retryCount < maxRetries;
    }

    /**
     * Увеличение счетчика попыток
     */
    public void incrementRetryCount() {
        this.retryCount++;
        this.updatedAt = LocalDateTime.now();
    }

    /**
     * Пометка как отправленное
     */
    public void markAsSent() {
        this.status = NotificationStatus.SENT;
        this.sentAt = LocalDateTime.now();
        this.updatedAt = LocalDateTime.now();
        this.errorMessage = null;
    }

    /**
     * Пометка как неудачное
     */
    public void markAsFailed(String errorMessage) {
        this.status = NotificationStatus.FAILED;
        this.errorMessage = errorMessage;
        this.updatedAt = LocalDateTime.now();
        incrementRetryCount();
    }

    /**
     * Пометка как отмененное
     */
    public void markAsCancelled() {
        this.status = NotificationStatus.CANCELLED;
        this.updatedAt = LocalDateTime.now();
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }
}