/**
 * @file: AdminStatsService.java
 * @description: Сервис для получения статистики админ панели
 * @dependencies: Spring Data JPA, Repositories
 * @created: 2025-01-27
 */
package com.baganov.pizzanat.service;

import com.baganov.pizzanat.model.dto.AdminStatsResponse;
import com.baganov.pizzanat.repository.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import jakarta.persistence.EntityManager;
import jakarta.persistence.Query;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class AdminStatsService {

    private final OrderRepository orderRepository;
    private final ProductRepository productRepository;
    private final CategoryRepository categoryRepository;
    private final OrderStatusRepository orderStatusRepository;
    private final EntityManager entityManager;

    /**
     * Получает полную статистику для админ панели
     */
    @Transactional(readOnly = true)
    public AdminStatsResponse getAdminStats() {
        log.info("Получение статистики админ панели");

        try {
            // Получаем все метрики параллельно
            Long totalOrders = getTotalOrders();
            BigDecimal totalRevenue = getTotalRevenue();
            Long totalProducts = getTotalProducts();
            Long totalCategories = getTotalCategories();
            Long ordersToday = getOrdersToday();
            BigDecimal revenueToday = getRevenueToday();
            List<AdminStatsResponse.PopularProduct> popularProducts = getPopularProducts();
            Map<String, Long> orderStatusStats = getOrderStatusStats();

            AdminStatsResponse response = AdminStatsResponse.builder()
                    .totalOrders(totalOrders)
                    .totalRevenue(totalRevenue)
                    .totalProducts(totalProducts)
                    .totalCategories(totalCategories)
                    .ordersToday(ordersToday)
                    .revenueToday(revenueToday)
                    .popularProducts(popularProducts)
                    .orderStatusStats(orderStatusStats)
                    .build();

            log.info("Статистика успешно получена: {} заказов, {} выручка", totalOrders, totalRevenue);
            return response;

        } catch (Exception e) {
            log.error("Ошибка при получении статистики админ панели", e);
            throw new RuntimeException("Не удалось получить статистику", e);
        }
    }

    /**
     * Получает общее количество заказов
     */
    private Long getTotalOrders() {
        String sql = "SELECT COUNT(*) FROM orders";
        Query query = entityManager.createNativeQuery(sql);
        return ((Number) query.getSingleResult()).longValue();
    }

    /**
     * Получает общую выручку
     */
    private BigDecimal getTotalRevenue() {
        String sql = "SELECT COALESCE(SUM(total_amount), 0) FROM orders";
        Query query = entityManager.createNativeQuery(sql);
        Object result = query.getSingleResult();
        return result != null ? (BigDecimal) result : BigDecimal.ZERO;
    }

    /**
     * Получает общее количество продуктов
     */
    private Long getTotalProducts() {
        return productRepository.count();
    }

    /**
     * Получает общее количество категорий
     */
    private Long getTotalCategories() {
        return categoryRepository.count();
    }

    /**
     * Получает количество заказов за сегодня
     */
    private Long getOrdersToday() {
        LocalDateTime startOfDay = LocalDate.now().atStartOfDay();
        LocalDateTime endOfDay = startOfDay.plusDays(1);

        String sql = "SELECT COUNT(*) FROM orders WHERE created_at >= ? AND created_at < ?";
        Query query = entityManager.createNativeQuery(sql);
        query.setParameter(1, startOfDay);
        query.setParameter(2, endOfDay);
        return ((Number) query.getSingleResult()).longValue();
    }

    /**
     * Получает выручку за сегодня
     */
    private BigDecimal getRevenueToday() {
        LocalDateTime startOfDay = LocalDate.now().atStartOfDay();
        LocalDateTime endOfDay = startOfDay.plusDays(1);

        String sql = "SELECT COALESCE(SUM(total_amount), 0) FROM orders WHERE created_at >= ? AND created_at < ?";
        Query query = entityManager.createNativeQuery(sql);
        query.setParameter(1, startOfDay);
        query.setParameter(2, endOfDay);
        Object result = query.getSingleResult();
        return result != null ? (BigDecimal) result : BigDecimal.ZERO;
    }

    /**
     * Получает ТОП-5 популярных товаров
     */
    @SuppressWarnings("unchecked")
    private List<AdminStatsResponse.PopularProduct> getPopularProducts() {
        String sql = """
                SELECT
                    p.id as product_id,
                    p.name as product_name,
                    SUM(oi.quantity) as total_sold,
                    SUM(oi.quantity * oi.price) as total_revenue
                FROM order_items oi
                JOIN products p ON oi.product_id = p.id
                GROUP BY p.id, p.name
                ORDER BY total_sold DESC
                LIMIT 5
                """;

        Query query = entityManager.createNativeQuery(sql);
        List<Object[]> results = query.getResultList();

        return results.stream()
                .map(row -> AdminStatsResponse.PopularProduct.builder()
                        .productId(((Number) row[0]).intValue())
                        .productName((String) row[1])
                        .totalSold(((Number) row[2]).longValue())
                        .totalRevenue((BigDecimal) row[3])
                        .build())
                .collect(Collectors.toList());
    }

    /**
     * Получает статистику по статусам заказов
     */
    @SuppressWarnings("unchecked")
    private Map<String, Long> getOrderStatusStats() {
        String sql = """
                SELECT
                    os.name as status_name,
                    COUNT(o.id) as order_count
                FROM order_statuses os
                LEFT JOIN orders o ON o.status_id = os.id
                WHERE os.is_active = true
                GROUP BY os.name
                ORDER BY order_count DESC
                """;

        Query query = entityManager.createNativeQuery(sql);
        List<Object[]> results = query.getResultList();

        Map<String, Long> statusStats = new LinkedHashMap<>();
        for (Object[] row : results) {
            String statusName = (String) row[0];
            Long count = ((Number) row[1]).longValue();
            statusStats.put(statusName, count);
        }

        return statusStats;
    }
}