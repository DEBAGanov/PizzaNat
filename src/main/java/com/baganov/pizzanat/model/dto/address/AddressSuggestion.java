package com.baganov.pizzanat.model.dto.address;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * DTO для автоподсказок адресов
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AddressSuggestion {

    /**
     * Полный адрес
     */
    private String address;

    /**
     * Короткий адрес (без региона и страны)
     */
    private String shortAddress;

    /**
     * Широта
     */
    private Double latitude;

    /**
     * Долгота
     */
    private Double longitude;

    /**
     * Источник данных (yandex, local, etc.)
     */
    private String source;

    /**
     * Дополнительная информация (тип объекта, почтовый индекс и т.д.)
     */
    private String metadata;
}