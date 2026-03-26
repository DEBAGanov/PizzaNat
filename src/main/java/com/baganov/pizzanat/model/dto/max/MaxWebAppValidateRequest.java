package com.baganov.pizzanat.model.dto.max;

import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Request DTO для валидации MAX initData
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MaxWebAppValidateRequest {

    /**
     * Сырая строка initData от MAX WebApp
     */
    @NotBlank(message = "initDataRaw is required")
    private String initDataRaw;
}
