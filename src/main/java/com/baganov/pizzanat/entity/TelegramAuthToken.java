package com.baganov.pizzanat.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * Entity для хранения Telegram токенов аутентификации.
 * Соответствует принципам SOLID - Single Responsibility.
 */
@Entity
@Table(name = "telegram_auth_tokens", indexes = {
        @Index(name = "idx_telegram_auth_tokens_auth_token", columnList = "authToken"),
        @Index(name = "idx_telegram_auth_tokens_expires_at", columnList = "expiresAt"),
        @Index(name = "idx_telegram_auth_tokens_status", columnList = "status"),
        @Index(name = "idx_telegram_auth_tokens_telegram_id", columnList = "telegramId")
})
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class TelegramAuthToken {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /**
     * Уникальный токен аутентификации с префиксом tg_auth_
     */
    @Column(name = "auth_token", nullable = false, unique = true, length = 50)
    private String authToken;

    /**
     * ID пользователя в Telegram
     */
    @Column(name = "telegram_id")
    private Long telegramId;

    /**
     * Username пользователя в Telegram (без @)
     */
    @Column(name = "telegram_username", length = 100)
    private String telegramUsername;

    /**
     * Имя пользователя в Telegram
     */
    @Column(name = "telegram_first_name", length = 100)
    private String telegramFirstName;

    /**
     * Фамилия пользователя в Telegram
     */
    @Column(name = "telegram_last_name", length = 100)
    private String telegramLastName;

    /**
     * Статус токена
     */
    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    @Builder.Default
    private TokenStatus status = TokenStatus.PENDING;

    /**
     * Время создания записи
     */
    @Column(name = "created_at", nullable = false)
    @Builder.Default
    private LocalDateTime createdAt = LocalDateTime.now();

    /**
     * Время истечения токена (TTL 10 минут)
     */
    @Column(name = "expires_at", nullable = false)
    private LocalDateTime expiresAt;

    /**
     * Время подтверждения аутентификации
     */
    @Column(name = "confirmed_at")
    private LocalDateTime confirmedAt;

    /**
     * ID устройства для отслеживания (опционально)
     */
    @Column(name = "device_id")
    private String deviceId;

    /**
     * Enum для статусов токена
     */
    public enum TokenStatus {
        PENDING, // Ожидание подтверждения
        CONFIRMED, // Подтверждено
        EXPIRED // Истекло
    }

    /**
     * Проверяет, действителен ли токен
     * 
     * @return true если токен не истек и находится в состоянии PENDING
     */
    public boolean isValid() {
        return status == TokenStatus.PENDING && LocalDateTime.now().isBefore(expiresAt);
    }

    /**
     * Проверяет, истек ли токен
     * 
     * @return true если токен истек
     */
    public boolean isExpired() {
        return LocalDateTime.now().isAfter(expiresAt);
    }

    /**
     * Подтверждает токен и устанавливает время подтверждения
     */
    public void confirm() {
        this.status = TokenStatus.CONFIRMED;
        this.confirmedAt = LocalDateTime.now();
    }

    /**
     * Помечает токен как истекший
     */
    public void markAsExpired() {
        this.status = TokenStatus.EXPIRED;
    }

    /**
     * Возвращает полное имя пользователя
     * 
     * @return полное имя или username если имя не указано
     */
    public String getFullName() {
        if (telegramFirstName != null && telegramLastName != null) {
            return telegramFirstName + " " + telegramLastName;
        } else if (telegramFirstName != null) {
            return telegramFirstName;
        } else if (telegramUsername != null) {
            return "@" + telegramUsername;
        } else {
            return "Telegram User";
        }
    }
}