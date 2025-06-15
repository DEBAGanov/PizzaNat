/**
 * @file: TelegramAdminUser.java
 * @description: Entity для администраторов Telegram бота
 * @dependencies: JPA, Lombok
 * @created: 2025-06-13
 */
package com.baganov.pizzanat.model.entity;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "telegram_admin_users")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TelegramAdminUser {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /**
     * Telegram Chat ID администратора
     */
    @Column(name = "telegram_chat_id", nullable = false, unique = true)
    private Long telegramChatId;

    /**
     * Telegram username администратора
     */
    @Column(name = "username")
    private String username;

    /**
     * Имя администратора
     */
    @Column(name = "first_name")
    private String firstName;

    /**
     * Фамилия администратора
     */
    @Column(name = "last_name")
    private String lastName;

    /**
     * Активен ли администратор
     */
    @Column(name = "is_active", nullable = false)
    @Builder.Default
    private Boolean isActive = true;

    /**
     * Дата регистрации
     */
    @Column(name = "registered_at", nullable = false)
    private LocalDateTime registeredAt;

    /**
     * Дата последней активности
     */
    @Column(name = "last_activity_at")
    private LocalDateTime lastActivityAt;

    /**
     * Дата создания записи
     */
    @Column(name = "created_at", nullable = false)
    @Builder.Default
    private LocalDateTime createdAt = LocalDateTime.now();

    /**
     * Дата обновления записи
     */
    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    @PrePersist
    protected void onCreate() {
        if (createdAt == null) {
            createdAt = LocalDateTime.now();
        }
        if (registeredAt == null) {
            registeredAt = LocalDateTime.now();
        }
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }

    /**
     * Обновление времени последней активности
     */
    public void updateLastActivity() {
        this.lastActivityAt = LocalDateTime.now();
        this.updatedAt = LocalDateTime.now();
    }

    /**
     * Деактивация администратора
     */
    public void deactivate() {
        this.isActive = false;
        this.updatedAt = LocalDateTime.now();
    }

    /**
     * Активация администратора
     */
    public void activate() {
        this.isActive = true;
        this.updatedAt = LocalDateTime.now();
    }
}