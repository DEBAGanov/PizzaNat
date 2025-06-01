package com.baganov.pizzanat.model.dto.order;

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
public class OrderDTO {
    private Integer id;
    private String status;
    private String statusDescription;
    private Integer deliveryLocationId;
    private String deliveryLocationName;
    private String deliveryLocationAddress;
    private String deliveryAddress;
    private BigDecimal totalAmount;
    private String comment;
    private String contactName;
    private String contactPhone;

    private String createdAt;
    private String updatedAt;

    @Builder.Default
    private List<OrderItemDTO> items = new ArrayList<>();
}