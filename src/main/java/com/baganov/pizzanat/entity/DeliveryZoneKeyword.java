/**
 * @file: DeliveryZoneKeyword.java
 * @description: Entity для ключевых слов определения зон доставки
 * @dependencies: JPA, Lombok
 * @created: 2025-01-23
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
@Table(name = "delivery_zone_keywords")
public class DeliveryZoneKeyword {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "zone_id", nullable = false)
    private DeliveryZone zone;

    @Column(name = "keyword", nullable = false)
    private String keyword;

    @Column(name = "match_type")
    @Builder.Default
    private String matchType = "contains"; // contains, starts_with, exact

    @Column(name = "created_at")
    private LocalDateTime createdAt;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
    }

    /**
     * Проверяет, соответствует ли адрес данному ключевому слову
     */
    public boolean matchesAddress(String address) {
        if (address == null || address.trim().isEmpty() || keyword == null) {
            return false;
        }

        String normalizedAddress = address.toLowerCase().trim();
        String normalizedKeyword = keyword.toLowerCase().trim();

        switch (matchType.toLowerCase()) {
            case "exact":
                return normalizedAddress.equals(normalizedKeyword);

            case "starts_with":
                return normalizedAddress.startsWith(normalizedKeyword);

            case "contains":
            default:
                return normalizedAddress.contains(normalizedKeyword);
        }
    }
}