package com.baganov.pizzanat.model.dto.product;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ProductDTO {

    private Integer id;
    private String name;
    private String description;
    private BigDecimal price;
    private BigDecimal discountedPrice;
    private Integer categoryId;
    private String categoryName;
    private String imageUrl;
    private Integer weight;
    private boolean isAvailable;
    private boolean isSpecialOffer;
    private Integer discountPercent;
}