/**
 * @file: PaymentRepository.java
 * @description: Repository для работы с платежами
 * @dependencies: Payment, PaymentStatus, JPA
 * @created: 2025-01-26
 */
package com.baganov.pizzanat.repository;

import com.baganov.pizzanat.entity.Payment;
import com.baganov.pizzanat.entity.PaymentStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

/**
 * Repository для работы с платежами
 */
@Repository
public interface PaymentRepository extends JpaRepository<Payment, Long> {

    /**
     * Найти платеж по ID платежа в ЮKassa
     */
    Optional<Payment> findByYookassaPaymentId(String yookassaPaymentId);

    /**
     * Найти все платежи по ID заказа
     */
    @Query("SELECT p FROM Payment p WHERE p.order.id = :orderId ORDER BY p.createdAt DESC")
    List<Payment> findByOrderIdOrderByCreatedAtDesc(@Param("orderId") Long orderId);

    /**
     * Найти последний платеж для заказа
     */
    @Query("SELECT p FROM Payment p WHERE p.order.id = :orderId ORDER BY p.createdAt DESC LIMIT 1")
    Optional<Payment> findLatestByOrderId(@Param("orderId") Long orderId);

    /**
     * Найти все платежи по статусу
     */
    List<Payment> findByStatusOrderByCreatedAtDesc(PaymentStatus status);

    /**
     * Найти платежи, ожидающие обработки (PENDING, WAITING_FOR_CAPTURE)
     */
    @Query("SELECT p FROM Payment p WHERE p.status IN ('PENDING', 'WAITING_FOR_CAPTURE') ORDER BY p.createdAt ASC")
    List<Payment> findPendingPayments();

    /**
     * Найти платежи, созданные в определенный период
     */
    List<Payment> findByCreatedAtBetweenOrderByCreatedAtDesc(LocalDateTime start, LocalDateTime end);

    /**
     * Найти успешные платежи за период
     */
    @Query("SELECT p FROM Payment p WHERE p.status = 'SUCCEEDED' AND p.paidAt BETWEEN :start AND :end ORDER BY p.paidAt DESC")
    List<Payment> findSuccessfulPaymentsBetween(@Param("start") LocalDateTime start, @Param("end") LocalDateTime end);

    /**
     * Найти платежи по методу оплаты
     */
    @Query("SELECT p FROM Payment p WHERE p.method = :method ORDER BY p.createdAt DESC")
    List<Payment> findByPaymentMethod(@Param("method") String method);

    /**
     * Найти платежи по банку (для СБП)
     */
    List<Payment> findByBankIdOrderByCreatedAtDesc(String bankId);

    /**
     * Проверить существование успешного платежа для заказа
     */
    @Query("SELECT COUNT(p) > 0 FROM Payment p WHERE p.order.id = :orderId AND p.status = 'SUCCEEDED'")
    boolean existsSuccessfulPaymentForOrder(@Param("orderId") Long orderId);

    /**
     * Найти платежи с ошибками
     */
    @Query("SELECT p FROM Payment p WHERE p.status = 'FAILED' AND p.errorMessage IS NOT NULL ORDER BY p.createdAt DESC")
    List<Payment> findFailedPaymentsWithErrors();

    /**
     * Найти платежи, которые нужно проверить (старше определенного времени и в
     * статусе PENDING)
     */
    @Query("SELECT p FROM Payment p WHERE p.status = 'PENDING' AND p.createdAt < :threshold ORDER BY p.createdAt ASC")
    List<Payment> findPaymentsToCheck(@Param("threshold") LocalDateTime threshold);

    /**
     * Найти активные платежи для опроса ЮКассы (младше 10 минут в статусах PENDING/WAITING_FOR_CAPTURE)
     */
    @Query("SELECT p FROM Payment p WHERE p.status IN ('PENDING', 'WAITING_FOR_CAPTURE') " +
           "AND p.createdAt > :sinceTime AND p.yookassaPaymentId IS NOT NULL " +
           "ORDER BY p.createdAt ASC")
    List<Payment> findActivePaymentsForPolling(@Param("sinceTime") LocalDateTime sinceTime);

    /**
     * Подсчитать количество платежей по статусу
     */
    long countByStatus(PaymentStatus status);

    /**
     * Подсчитать количество успешных платежей за период
     */
    @Query("SELECT COUNT(p) FROM Payment p WHERE p.status = 'SUCCEEDED' AND p.paidAt BETWEEN :start AND :end")
    long countSuccessfulPaymentsBetween(@Param("start") LocalDateTime start, @Param("end") LocalDateTime end);

    /**
     * Найти платежи пользователя (через заказы)
     */
    @Query("SELECT p FROM Payment p WHERE p.order.user.id = :userId ORDER BY p.createdAt DESC")
    List<Payment> findByUserId(@Param("userId") Long userId);

    /**
     * Найти последние N платежей
     */
    @Query("SELECT p FROM Payment p ORDER BY p.createdAt DESC LIMIT :limit")
    List<Payment> findLatestPayments(@Param("limit") int limit);

    /**
     * Удалить старые неуспешные платежи (для очистки)
     */
    @Query("DELETE FROM Payment p WHERE p.status IN ('FAILED', 'CANCELLED') AND p.createdAt < :threshold")
    void deleteOldFailedPayments(@Param("threshold") LocalDateTime threshold);
}