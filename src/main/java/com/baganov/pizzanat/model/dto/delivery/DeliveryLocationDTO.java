package com.baganov.pizzanat.model.dto.delivery;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DeliveryLocationDTO {
    private Integer id;
    private String name;
    private String address;
    private BigDecimal latitude;
    private BigDecimal longitude;
    private String workingHours;
    private String phone;
    private boolean isActive;
}