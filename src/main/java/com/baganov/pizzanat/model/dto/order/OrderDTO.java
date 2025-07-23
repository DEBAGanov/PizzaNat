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
    private BigDecimal deliveryCost; // Стоимость доставки
    private String deliveryType; // Способ доставки
    private String comment;
    private String contactName;
    private String contactPhone;

    private String createdAt;
    private String updatedAt;

    @Builder.Default
    private List<OrderItemDTO> items = new ArrayList<>();

    /**
     * Возвращает сумму только товаров (без доставки)
     */
    public BigDecimal getItemsAmount() {
        if (deliveryCost != null && totalAmount != null) {
            return totalAmount.subtract(deliveryCost);
        }
        return totalAmount;
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