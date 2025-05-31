package com.baganov.pizzanat.model.dto.order;

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

    /**
     * Валидация: должен быть указан либо deliveryLocationId, либо deliveryAddress
     */
    public boolean hasValidDeliveryInfo() {
        return (deliveryLocationId != null) ||
                (deliveryAddress != null && !deliveryAddress.trim().isEmpty());
    }

    /**
     * Получает итоговый комментарий (приоритет: comment > notes)
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
}