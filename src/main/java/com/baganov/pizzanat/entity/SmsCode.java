package com.baganov.pizzanat.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * Entity для хранения SMS кодов аутентификации.
 * Соответствует принципам SOLID - Single Responsibility.
 */
@Entity
@Table(name = "sms_codes", indexes = {
        @Index(name = "idx_sms_codes_phone_number", columnList = "phoneNumber"),
        @Index(name = "idx_sms_codes_expires_at", columnList = "expiresAt"),
        @Index(name = "idx_sms_codes_phone_used_expires", columnList = "phoneNumber, used, expiresAt")
})
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class SmsCode {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /**
     * Номер телефона в формате +7XXXXXXXXXX
     */
    @Column(name = "phone_number", nullable = false, length = 20)
    private String phoneNumber;

    /**
     * 4-значный SMS код для подтверждения
     */
    @Column(name = "code", nullable = false, length = 4)
    private String code;

    /**
     * Время создания записи
     */
    @Column(name = "created_at", nullable = false)
    @Builder.Default
    private LocalDateTime createdAt = LocalDateTime.now();

    /**
     * Время истечения кода (TTL 10 минут)
     */
    @Column(name = "expires_at", nullable = false)
    private LocalDateTime expiresAt;

    /**
     * Флаг использования кода
     */
    @Column(name = "used", nullable = false)
    @Builder.Default
    private Boolean used = false;

    /**
     * Количество попыток ввода кода
     */
    @Column(name = "attempts", nullable = false)
    @Builder.Default
    private Integer attempts = 0;

    /**
     * Проверяет, действителен ли код
     * 
     * @return true если код не использован и не истек
     */
    public boolean isValid() {
        return !used && LocalDateTime.now().isBefore(expiresAt);
    }

    /**
     * Проверяет, истек ли код
     * 
     * @return true если код истек
     */
    public boolean isExpired() {
        return LocalDateTime.now().isAfter(expiresAt);
    }

    /**
     * Увеличивает счетчик попыток ввода кода
     */
    public void incrementAttempts() {
        this.attempts++;
    }

    /**
     * Помечает код как использованный
     */
    public void markAsUsed() {
        this.used = true;
    }
}