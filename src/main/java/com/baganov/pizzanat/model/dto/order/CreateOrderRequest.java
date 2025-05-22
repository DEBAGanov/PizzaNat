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

    @NotNull(message = "ID пункта выдачи не может быть пустым")
    private Integer deliveryLocationId;

    @Size(max = 500, message = "Комментарий не должен превышать 500 символов")
    private String comment;

    @NotBlank(message = "Имя получателя не может быть пустым")
    @Size(max = 100, message = "Имя получателя не должно превышать 100 символов")
    private String contactName;

    @NotBlank(message = "Телефон получателя не может быть пустым")
    @Pattern(regexp = "^\\+?[0-9]{10,15}$", message = "Некорректный формат телефона")
    private String contactPhone;
}