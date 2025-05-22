package com.baganov.pizzanat.model.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "products")
public class Product {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;

    @Column(nullable = false)
    private String name;

    @Column(columnDefinition = "TEXT")
    private String description;

    @Column(nullable = false, precision = 10, scale = 2)
    private BigDecimal price;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "category_id")
    private Category category;

    @Column(name = "image_url")
    private String imageUrl;

    private Integer weight;

    @Column(name = "is_available")
    private boolean isAvailable;

    @Column(name = "is_special_offer")
    private boolean isSpecialOffer;

    @Column(name = "discount_percent")
    private Integer discountPercent;

    @Column(name = "created_at")
    private LocalDateTime createdAt;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    @Column(name = "discounted_price", nullable = false)
    private BigDecimal discountedPrice;

    @Column(nullable = false)
    private boolean available;

    public BigDecimal getDiscountedPrice() {
        if (discountPercent != null && discountPercent > 0) {
            return price.subtract(
                    price.multiply(BigDecimal.valueOf(discountPercent))
                            .divide(BigDecimal.valueOf(100)));
        }
        return price;
    }

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
        updatedAt = LocalDateTime.now();
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }
}