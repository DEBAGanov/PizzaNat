package com.baganov.pizzanat.repository;

import com.baganov.pizzanat.entity.TelegramAuthToken;
import com.baganov.pizzanat.entity.TelegramAuthToken.TokenStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

/**
 * Repository для работы с Telegram токенами аутентификации.
 * Следует принципу Interface Segregation из SOLID.
 */
@Repository
public interface TelegramAuthTokenRepository extends JpaRepository<TelegramAuthToken, Long> {

        /**
         * Поиск действующего токена по auth_token и статусу
         *
         * @param authToken токен аутентификации
         * @param status    статус токена
         * @param now       текущее время для проверки истечения
         * @return действующий токен если найден
         */
        Optional<TelegramAuthToken> findByAuthTokenAndStatusAndExpiresAtAfter(
                        String authToken,
                        TokenStatus status,
                        LocalDateTime now);

        /**
         * Удаление истекших токенов
         *
         * @param cutoff время, до которого удалять истекшие токены
         */
        @Modifying
        @Transactional
        @Query("DELETE FROM TelegramAuthToken t WHERE t.expiresAt < :cutoff")
        void deleteByExpiresAtBefore(@Param("cutoff") LocalDateTime cutoff);

        /**
         * Подсчет количества созданных токенов за определенный период по статусу
         * Используется для rate limiting
         *
         * @param since  время с которого считать
         * @param status статус токенов для подсчета
         * @return количество токенов
         */
        @Query("SELECT COUNT(t) FROM TelegramAuthToken t WHERE t.createdAt > :since AND t.status = :status")
        int countByCreatedAtAfterAndStatus(
                        @Param("since") LocalDateTime since,
                        @Param("status") TokenStatus status);

        /**
         * Поиск токенов по статусу, которые истекли
         * Используется для очистки истекших токенов
         *
         * @param status статус токенов
         * @param cutoff время, до которого считать истекшими
         * @return список истекших токенов
         */
        List<TelegramAuthToken> findByStatusAndExpiresAtBefore(
                        TokenStatus status,
                        LocalDateTime cutoff);

        /**
         * Поиск действующего токена только по auth_token
         *
         * @param authToken токен аутентификации
         * @param now       текущее время
         * @return действующий токен если найден
         */
        @Query("SELECT t FROM TelegramAuthToken t WHERE t.authToken = :authToken AND t.expiresAt > :now")
        Optional<TelegramAuthToken> findByAuthTokenAndExpiresAtAfter(
                        @Param("authToken") String authToken,
                        @Param("now") LocalDateTime now);

        /**
         * Поиск токена по auth_token (для диагностики)
         *
         * @param authToken токен аутентификации
         * @return токен если найден
         */
        Optional<TelegramAuthToken> findByAuthToken(String authToken);

        /**
         * Поиск токенов по Telegram ID пользователя
         *
         * @param telegramId ID пользователя в Telegram
         * @return список токенов пользователя
         */
        List<TelegramAuthToken> findByTelegramIdOrderByCreatedAtDesc(Long telegramId);

        /**
         * Подсчет токенов по статусу
         *
         * @param status статус для подсчета
         * @return количество токенов
         */
        @Query("SELECT COUNT(t) FROM TelegramAuthToken t WHERE t.status = :status")
        long countByStatus(@Param("status") TokenStatus status);

        /**
         * Поиск последнего токена для устройства
         *
         * @param deviceId ID устройства
         * @return последний токен устройства
         */
        Optional<TelegramAuthToken> findTopByDeviceIdOrderByCreatedAtDesc(String deviceId);

        /**
         * Автоматическое истечение токенов по времени
         * Помечает истекшие PENDING токены как EXPIRED
         *
         * @param now текущее время
         * @return количество обновленных токенов
         */
        @Modifying
        @Transactional
        @Query("UPDATE TelegramAuthToken t SET t.status = 'EXPIRED' WHERE t.status = 'PENDING' AND t.expiresAt < :now")
        int markExpiredTokens(@Param("now") LocalDateTime now);
}