package com.baganov.pizzanat.model.dto.cart;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CartItemDTO {
    private Integer id;
    private Integer productId;
    private String productName;
    private String productImageUrl;
    private BigDecimal price;
    private BigDecimal discountedPrice;
    private Integer quantity;
    private BigDecimal subtotal;
}