/**
 * @file: TelegramAdminUserRepository.java
 * @description: Repository для работы с администраторами Telegram бота
 * @dependencies: Spring Data JPA, TelegramAdminUser
 * @created: 2025-06-13
 */
package com.baganov.pizzanat.repository;

import com.baganov.pizzanat.model.entity.TelegramAdminUser;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public interface TelegramAdminUserRepository extends JpaRepository<TelegramAdminUser, Long> {

    /**
     * Поиск администратора по Telegram Chat ID
     */
    Optional<TelegramAdminUser> findByTelegramChatId(Long telegramChatId);

    /**
     * Поиск активного администратора по Telegram Chat ID
     */
    Optional<TelegramAdminUser> findByTelegramChatIdAndIsActiveTrue(Long telegramChatId);

    /**
     * Поиск всех активных администраторов
     */
    List<TelegramAdminUser> findByIsActiveTrue();

    /**
     * Поиск всех администраторов (активных и неактивных)
     */
    List<TelegramAdminUser> findAllByOrderByRegisteredAtDesc();

    /**
     * Поиск администраторов по username
     */
    List<TelegramAdminUser> findByUsernameContainingIgnoreCase(String username);

    /**
     * Проверка существования администратора по Telegram Chat ID
     */
    boolean existsByTelegramChatId(Long telegramChatId);

    /**
     * Подсчет активных администраторов
     */
    long countByIsActiveTrue();

    /**
     * Обновление времени последней активности
     */
    @Modifying
    @Query("UPDATE TelegramAdminUser t SET t.lastActivityAt = :activityTime, t.updatedAt = :activityTime WHERE t.telegramChatId = :chatId")
    int updateLastActivity(@Param("chatId") Long telegramChatId, @Param("activityTime") LocalDateTime activityTime);

    /**
     * Деактивация администратора
     */
    @Modifying
    @Query("UPDATE TelegramAdminUser t SET t.isActive = false, t.updatedAt = :updateTime WHERE t.telegramChatId = :chatId")
    int deactivateAdmin(@Param("chatId") Long telegramChatId, @Param("updateTime") LocalDateTime updateTime);

    /**
     * Активация администратора
     */
    @Modifying
    @Query("UPDATE TelegramAdminUser t SET t.isActive = true, t.updatedAt = :updateTime WHERE t.telegramChatId = :chatId")
    int activateAdmin(@Param("chatId") Long telegramChatId, @Param("updateTime") LocalDateTime updateTime);

    /**
     * Поиск неактивных администраторов старше определенной даты
     */
    @Query("SELECT t FROM TelegramAdminUser t WHERE t.isActive = false AND t.updatedAt < :cutoffDate")
    List<TelegramAdminUser> findInactiveAdminsOlderThan(@Param("cutoffDate") LocalDateTime cutoffDate);

    /**
     * Удаление старых неактивных администраторов
     */
    @Modifying
    @Query("DELETE FROM TelegramAdminUser t WHERE t.isActive = false AND t.updatedAt < :cutoffDate")
    int deleteInactiveAdminsOlderThan(@Param("cutoffDate") LocalDateTime cutoffDate);
}