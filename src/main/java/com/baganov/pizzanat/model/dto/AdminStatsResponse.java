/**
 * @file: AdminStatsResponse.java
 * @description: DTO для ответа API статистики админ панели
 * @dependencies: Lombok
 * @created: 2025-01-27
 */
package com.baganov.pizzanat.model.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AdminStatsResponse {

    /**
     * Общее количество заказов
     */
    private Long totalOrders;

    /**
     * Общая выручка
     */
    private BigDecimal totalRevenue;

    /**
     * Общее количество продуктов
     */
    private Long totalProducts;

    /**
     * Общее количество категорий
     */
    private Long totalCategories;

    /**
     * Количество заказов за сегодня
     */
    private Long ordersToday;

    /**
     * Выручка за сегодня
     */
    private BigDecimal revenueToday;

    /**
     * ТОП-5 популярных товаров
     */
    private List<PopularProduct> popularProducts;

    /**
     * Статистика по статусам заказов
     */
    private Map<String, Long> orderStatusStats;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class PopularProduct {
        /**
         * ID товара
         */
        private Integer productId;

        /**
         * Название товара
         */
        private String productName;

        /**
         * Количество продаж
         */
        private Long totalSold;

        /**
         * Общая выручка от товара
         */
        private BigDecimal totalRevenue;
    }
}