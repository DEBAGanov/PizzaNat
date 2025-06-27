/**
 * @file: DeliveryZone.java
 * @description: Entity для управления зонами доставки с разными тарифами
 * @dependencies: JPA, Lombok
 * @created: 2025-01-23
 */
package com.baganov.pizzanat.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "delivery_zones")
public class DeliveryZone {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;

    @Column(nullable = false, length = 100)
    private String name;

    @Column(columnDefinition = "TEXT")
    private String description;

    @Column(name = "base_cost", nullable = false, precision = 10, scale = 2)
    private BigDecimal baseCost;

    @Column(name = "free_delivery_threshold", precision = 10, scale = 2)
    private BigDecimal freeDeliveryThreshold;

    @Column(name = "delivery_time_min")
    @Builder.Default
    private Integer deliveryTimeMin = 30;

    @Column(name = "delivery_time_max")
    @Builder.Default
    private Integer deliveryTimeMax = 45;

    @Column(name = "is_active")
    @Builder.Default
    private Boolean isActive = true;

    @Column(name = "priority")
    @Builder.Default
    private Integer priority = 0;

    @Column(name = "color_hex", length = 7)
    @Builder.Default
    private String colorHex = "#3498db";

    @Column(name = "created_at")
    private LocalDateTime createdAt;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    @OneToMany(mappedBy = "zone", cascade = CascadeType.ALL, orphanRemoval = true)
    @Builder.Default
    private List<DeliveryZoneStreet> streets = new ArrayList<>();

    @OneToMany(mappedBy = "zone", cascade = CascadeType.ALL, orphanRemoval = true)
    @Builder.Default
    private List<DeliveryZoneKeyword> keywords = new ArrayList<>();

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
        updatedAt = LocalDateTime.now();
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }

    /**
     * Возвращает время доставки в читаемом формате
     */
    public String getFormattedDeliveryTime() {
        return deliveryTimeMin + "-" + deliveryTimeMax + " минут";
    }

    /**
     * Проверяет, нужна ли оплата доставки для указанной суммы заказа
     */
    public boolean isDeliveryFree(BigDecimal orderAmount) {
        return freeDeliveryThreshold != null &&
                orderAmount != null &&
                orderAmount.compareTo(freeDeliveryThreshold) >= 0;
    }

    /**
     * Возвращает итоговую стоимость доставки с учетом суммы заказа
     */
    public BigDecimal getFinalDeliveryCost(BigDecimal orderAmount) {
        return isDeliveryFree(orderAmount) ? BigDecimal.ZERO : baseCost;
    }
}