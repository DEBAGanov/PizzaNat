/**
 * @file: ScheduledNotificationRepository.java
 * @description: Repository для работы с отложенными уведомлениями
 * @dependencies: JPA, ScheduledNotification
 * @created: 2025-06-13
 */
package com.baganov.pizzanat.repository;

import com.baganov.pizzanat.entity.ScheduledNotification;
import com.baganov.pizzanat.entity.ScheduledNotification.NotificationStatus;
import com.baganov.pizzanat.entity.ScheduledNotification.NotificationType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public interface ScheduledNotificationRepository extends JpaRepository<ScheduledNotification, Long> {

    /**
     * Поиск уведомлений готовых к отправке
     *
     * @param now текущее время
     * @return список уведомлений готовых к отправке
     */
    @Query("SELECT sn FROM ScheduledNotification sn " +
            "WHERE sn.status = :pendingStatus " +
            "AND sn.scheduledAt <= :now " +
            "ORDER BY sn.scheduledAt ASC")
    List<ScheduledNotification> findReadyToSend(@Param("now") LocalDateTime now,
            @Param("pendingStatus") NotificationStatus pendingStatus);

    /**
     * Поиск неудачных уведомлений для повторной отправки
     *
     * @param now текущее время
     * @return список уведомлений для повтора
     */
    @Query("SELECT sn FROM ScheduledNotification sn " +
            "WHERE sn.status = :failedStatus " +
            "AND sn.retryCount < sn.maxRetries " +
            "AND sn.scheduledAt <= :now " +
            "ORDER BY sn.updatedAt ASC")
    List<ScheduledNotification> findFailedForRetry(@Param("now") LocalDateTime now,
            @Param("failedStatus") NotificationStatus failedStatus);

    /**
     * Поиск уведомлений по заказу и типу
     *
     * @param orderId ID заказа
     * @param type    тип уведомления
     * @return уведомление если найдено
     */
    Optional<ScheduledNotification> findByOrderIdAndNotificationType(
            Integer orderId,
            NotificationType type);

    /**
     * Поиск уведомлений по пользователю и статусу
     *
     * @param userId ID пользователя
     * @param status статус уведомления
     * @return список уведомлений
     */
    List<ScheduledNotification> findByUserIdAndStatus(Integer userId, NotificationStatus status);

    /**
     * Поиск уведомлений по Telegram ID и статусу
     *
     * @param telegramId Telegram ID пользователя
     * @param status     статус уведомления
     * @return список уведомлений
     */
    List<ScheduledNotification> findByTelegramIdAndStatus(Long telegramId, NotificationStatus status);

    /**
     * Подсчет уведомлений по типу и статусу
     *
     * @param type   тип уведомления
     * @param status статус уведомления
     * @return количество уведомлений
     */
    long countByNotificationTypeAndStatus(NotificationType type, NotificationStatus status);

    /**
     * Удаление старых отправленных уведомлений
     *
     * @param cutoff время, до которого удалять старые уведомления
     */
    @Modifying
    @Transactional
    @Query("DELETE FROM ScheduledNotification sn " +
            "WHERE sn.status = :sentStatus " +
            "AND sn.sentAt < :cutoff")
    void deleteOldSentNotifications(@Param("cutoff") LocalDateTime cutoff,
            @Param("sentStatus") NotificationStatus sentStatus);

    /**
     * Удаление старых неудачных уведомлений (превысивших лимит попыток)
     *
     * @param cutoff время, до которого удалять старые уведомления
     */
    @Modifying
    @Transactional
    @Query("DELETE FROM ScheduledNotification sn " +
            "WHERE sn.status = :failedStatus " +
            "AND sn.retryCount >= sn.maxRetries " +
            "AND sn.updatedAt < :cutoff")
    void deleteOldFailedNotifications(@Param("cutoff") LocalDateTime cutoff,
            @Param("failedStatus") NotificationStatus failedStatus);

    /**
     * Отмена всех ожидающих уведомлений для заказа
     *
     * @param orderId ID заказа
     */
    @Modifying
    @Transactional
    @Query("UPDATE ScheduledNotification sn " +
            "SET sn.status = :cancelledStatus, sn.updatedAt = :now " +
            "WHERE sn.order.id = :orderId " +
            "AND sn.status = :pendingStatus")
    void cancelPendingNotificationsByOrderId(@Param("orderId") Integer orderId, @Param("now") LocalDateTime now,
            @Param("cancelledStatus") NotificationStatus cancelledStatus,
            @Param("pendingStatus") NotificationStatus pendingStatus);

    /**
     * Поиск статистики по уведомлениям за период
     *
     * @param from начало периода
     * @param to   конец периода
     * @return статистика в виде Object[] {type, status, count}
     */
    @Query("SELECT sn.notificationType, sn.status, COUNT(sn) " +
            "FROM ScheduledNotification sn " +
            "WHERE sn.createdAt BETWEEN :from AND :to " +
            "GROUP BY sn.notificationType, sn.status " +
            "ORDER BY sn.notificationType, sn.status")
    List<Object[]> getStatistics(@Param("from") LocalDateTime from, @Param("to") LocalDateTime to);
}