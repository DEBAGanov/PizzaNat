package com.baganov.pizzanat.model.dto.order;

import com.baganov.pizzanat.entity.PaymentMethod;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CreateOrderRequest {

    // Может быть null если используется deliveryAddress
    private Integer deliveryLocationId;

    // Android поле: адрес доставки (альтернатива deliveryLocationId)
    @Size(max = 500, message = "Адрес доставки не должен превышать 500 символов")
    private String deliveryAddress;

    // Способ доставки из мобильного приложения
    @Size(max = 100, message = "Способ доставки не должен превышать 100 символов")
    private String deliveryType; // "Самовывоз" или "Доставка курьером"

    @Size(max = 500, message = "Комментарий не должен превышать 500 символов")
    private String comment;

    // Android поле: заметки (приоритет ниже чем comment)
    @Size(max = 500, message = "Заметки не должны превышать 500 символов")
    private String notes;

    @NotBlank(message = "Имя получателя не может быть пустым")
    @Size(max = 100, message = "Имя получателя не должно превышать 100 символов")
    private String contactName;

    @NotBlank(message = "Телефон получателя не может быть пустым")
    @Pattern(regexp = "^\\+?[0-9]{10,15}$", message = "Некорректный формат телефона")
    private String contactPhone;

    // Способ оплаты (по умолчанию наличными)
    @Builder.Default
    private PaymentMethod paymentMethod = PaymentMethod.CASH;

    /**
     * Проверяет корректность информации о доставке
     */
    public boolean hasValidDeliveryInfo() {
        return deliveryLocationId != null || (deliveryAddress != null && !deliveryAddress.trim().isEmpty());
    }

    /**
     * Возвращает финальный комментарий с приоритетом comment > notes
     */
    public String getFinalComment() {
        if (comment != null && !comment.trim().isEmpty()) {
            return comment.trim();
        }
        if (notes != null && !notes.trim().isEmpty()) {
            return notes.trim();
        }
        return null;
    }

    /**
     * Проверяет, выбрана ли доставка курьером
     */
    public boolean isDeliveryByCourier() {
        return deliveryType != null && 
               (deliveryType.toLowerCase().contains("курьер") || 
                deliveryType.toLowerCase().contains("доставка"));
    }

    /**
     * Проверяет, выбран ли самовывоз
     */
    public boolean isPickup() {
        return deliveryType != null && 
               (deliveryType.toLowerCase().contains("самовывоз") || 
                deliveryType.toLowerCase().contains("самов"));
    }
}