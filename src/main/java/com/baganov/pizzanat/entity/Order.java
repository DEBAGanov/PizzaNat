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
import com.baganov.pizzanat.util.TimeZoneUtils;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "orders")
public class Order {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id")
    private User user;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "status_id", nullable = false)
    private OrderStatus status;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "delivery_location_id", nullable = false)
    private DeliveryLocation deliveryLocation;

    @Column(name = "total_amount", nullable = false, precision = 10, scale = 2)
    private BigDecimal totalAmount;

    // Стоимость доставки (отдельно от общей суммы)
    @Column(name = "delivery_cost", precision = 10, scale = 2)
    @Builder.Default
    private BigDecimal deliveryCost = BigDecimal.ZERO;

    @Column(columnDefinition = "TEXT")
    private String comment;

    @Column(name = "delivery_address", columnDefinition = "TEXT")
    private String deliveryAddress;

    // Способ доставки ("Самовывоз" или "Доставка курьером")
    @Column(name = "delivery_type", length = 100)
    private String deliveryType;

    @Column(name = "contact_name", nullable = false)
    private String contactName;

    @Column(name = "contact_phone", nullable = false)
    private String contactPhone;

    @Enumerated(EnumType.STRING)
    @Column(name = "payment_method")
    @Builder.Default
    private PaymentMethod paymentMethod = PaymentMethod.CASH;

    @Column(name = "created_at")
    private LocalDateTime createdAt;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    @OneToMany(mappedBy = "order", cascade = CascadeType.ALL, orphanRemoval = true)
    @Builder.Default
    private List<OrderItem> items = new ArrayList<>();

    public void addItem(OrderItem item) {
        items.add(item);
        item.setOrder(this);
    }

    public void removeItem(OrderItem item) {
        items.remove(item);
        item.setOrder(null);
    }

    @PrePersist
    protected void onCreate() {
        createdAt = TimeZoneUtils.nowInMoscow();
        updatedAt = TimeZoneUtils.nowInMoscow();
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = TimeZoneUtils.nowInMoscow();
    }

    /**
     * Возвращает сумму только товаров (без доставки)
     */
    public BigDecimal getItemsAmount() {
        return items.stream()
                .map(item -> item.getPrice().multiply(BigDecimal.valueOf(item.getQuantity())))
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    /**
     * Проверяет, является ли заказ самовывозом
     */
    public boolean isPickup() {
        return deliveryType != null && 
               (deliveryType.toLowerCase().contains("самовывоз") || 
                deliveryType.toLowerCase().contains("самов"));
    }

    /**
     * Проверяет, является ли заказ доставкой курьером
     */
    public boolean isDeliveryByCourier() {
        return deliveryType != null && 
               (deliveryType.toLowerCase().contains("курьер") || 
                deliveryType.toLowerCase().contains("доставка"));
    }
}