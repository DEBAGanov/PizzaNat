package com.baganov.pizzanat.model.dto.cart;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CartDTO {
    private Integer id;
    private String sessionId;
    private BigDecimal totalAmount;
    @Builder.Default
    private List<CartItemDTO> items = new ArrayList<>();
}