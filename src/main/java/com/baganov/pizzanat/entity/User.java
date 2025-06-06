package com.baganov.pizzanat.entity;

import jakarta.persistence.*;
import lombok.*;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.HashSet;
import java.util.Set;
import java.util.stream.Collectors;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "users")
public class User implements UserDetails {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;

    @Column(nullable = false, unique = true)
    private String username;

    /**
     * Email - теперь может быть null для пользователей, регистрирующихся только
     * через SMS/Telegram
     */
    @Column(unique = true)
    private String email;

    @Column(nullable = false)
    private String password;

    @Column(name = "first_name")
    private String firstName;

    @Column(name = "last_name")
    private String lastName;

    private String phone;

    @Column(name = "is_active")
    private boolean isActive;

    @Column(name = "created_at")
    private LocalDateTime createdAt;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    // === Поля для SMS аутентификации ===

    /**
     * Номер телефона для SMS аутентификации в формате +7XXXXXXXXXX
     */
    @Column(name = "phone_number", unique = true, length = 20)
    private String phoneNumber;

    /**
     * Флаг подтверждения номера телефона через SMS
     */
    @Column(name = "is_phone_verified")
    @Builder.Default
    private Boolean isPhoneVerified = false;

    // === Поля для Telegram аутентификации ===

    /**
     * ID пользователя в Telegram для аутентификации
     */
    @Column(name = "telegram_id", unique = true)
    private Long telegramId;

    /**
     * Username пользователя в Telegram
     */
    @Column(name = "telegram_username", length = 100)
    private String telegramUsername;

    /**
     * Флаг подтверждения Telegram аутентификации
     */
    @Column(name = "is_telegram_verified")
    @Builder.Default
    private Boolean isTelegramVerified = false;

    @ToString.Exclude
    @EqualsAndHashCode.Exclude
    @ManyToMany(fetch = FetchType.EAGER)
    @JoinTable(name = "user_roles", joinColumns = @JoinColumn(name = "user_id"), inverseJoinColumns = @JoinColumn(name = "role_id"))
    @Builder.Default
    private Set<Role> roles = new HashSet<>();

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
        updatedAt = LocalDateTime.now();
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }

    // === Дополнительные методы для аутентификации ===

    /**
     * Проверяет, есть ли у пользователя подтвержденный способ аутентификации
     *
     * @return true если есть хотя бы один подтвержденный способ
     */
    public boolean hasVerifiedAuthentication() {
        return (email != null) ||
                (phoneNumber != null && Boolean.TRUE.equals(isPhoneVerified)) ||
                (telegramId != null && Boolean.TRUE.equals(isTelegramVerified));
    }

    /**
     * Возвращает основной идентификатор пользователя для отображения
     *
     * @return email, номер телефона или Telegram username
     */
    public String getPrimaryIdentifier() {
        if (email != null) {
            return email;
        } else if (phoneNumber != null) {
            return phoneNumber;
        } else if (telegramUsername != null) {
            return "@" + telegramUsername;
        } else {
            return username;
        }
    }

    /**
     * Возвращает отображаемое имя пользователя
     *
     * @return полное имя или основной идентификатор
     */
    public String getDisplayName() {
        if (firstName != null && lastName != null) {
            return firstName + " " + lastName;
        } else if (firstName != null) {
            return firstName;
        } else {
            return getPrimaryIdentifier();
        }
    }

    @Override
    public Collection<? extends GrantedAuthority> getAuthorities() {
        return roles.stream()
                .map(role -> new SimpleGrantedAuthority(role.getName()))
                .collect(Collectors.toSet());
    }

    @Override
    public boolean isAccountNonExpired() {
        return true;
    }

    @Override
    public boolean isAccountNonLocked() {
        return true;
    }

    @Override
    public boolean isCredentialsNonExpired() {
        return true;
    }

    @Override
    public boolean isEnabled() {
        return isActive;
    }
}