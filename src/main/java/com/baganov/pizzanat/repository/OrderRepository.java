package com.baganov.pizzanat.repository;

import com.baganov.pizzanat.entity.Order;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public interface OrderRepository extends JpaRepository<Order, Integer> {

    Page<Order> findByUserIdOrderByCreatedAtDesc(Integer userId, Pageable pageable);

    List<Order> findTop5ByUserIdOrderByCreatedAtDesc(Integer userId);

    Optional<Order> findByIdAndUserId(Integer id, Integer userId);

    /**
     * Поиск заказов по диапазону дат
     */
    List<Order> findByCreatedAtBetween(LocalDateTime startDate, LocalDateTime endDate);

    /**
     * Поиск активных заказов (не завершенных и не отмененных)
     */
    @Query("SELECT o FROM Order o WHERE o.status.name IN ('CONFIRMED', 'PREPARING', 'READY', 'DELIVERING') ORDER BY o.createdAt DESC")
    List<Order> findActiveOrders();

    /**
     * Поиск активных заказов включая новые (для админского бота)
     */
    @Query("SELECT o FROM Order o WHERE o.status.name IN ('CREATED', 'PENDING', 'CONFIRMED', 'PREPARING', 'COOKING', 'READY', 'DELIVERING') ORDER BY o.createdAt DESC")
    List<Order> findActiveOrdersIncludingNew();

    /**
     * Подсчет заказов за сегодня
     */
    @Query("SELECT COUNT(o) FROM Order o WHERE o.createdAt >= :startOfDay AND o.createdAt < :endOfDay")
    Long countOrdersToday(@Param("startOfDay") LocalDateTime startOfDay, @Param("endOfDay") LocalDateTime endOfDay);

    /**
     * Подсчет заказов по статусу
     */
    @Query("SELECT COUNT(o) FROM Order o WHERE o.status.name = :statusName")
    Long countByStatusName(@Param("statusName") String statusName);
}