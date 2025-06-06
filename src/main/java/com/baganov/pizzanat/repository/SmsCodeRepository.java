package com.baganov.pizzanat.repository;

import com.baganov.pizzanat.entity.SmsCode;
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
 * Repository для работы с SMS кодами аутентификации.
 * Следует принципу Interface Segregation из SOLID.
 */
@Repository
public interface SmsCodeRepository extends JpaRepository<SmsCode, Long> {

        /**
         * Поиск действующего SMS кода для номера телефона
         * 
         * @param phoneNumber номер телефона в формате +7XXXXXXXXXX
         * @param now         текущее время для проверки истечения
         * @return действующий неиспользованный код
         */
        Optional<SmsCode> findByPhoneNumberAndUsedFalseAndExpiresAtAfter(
                        String phoneNumber,
                        LocalDateTime now);

        /**
         * Удаление истекших SMS кодов
         * 
         * @param cutoff время, до которого удалять истекшие коды
         */
        @Modifying
        @Transactional
        @Query("DELETE FROM SmsCode s WHERE s.expiresAt < :cutoff")
        void deleteByExpiresAtBefore(@Param("cutoff") LocalDateTime cutoff);

        /**
         * Подсчет количества отправленных SMS за определенный период
         * Используется для rate limiting
         * 
         * @param phoneNumber номер телефона
         * @param since       время с которого считать
         * @return количество отправленных SMS
         */
        @Query("SELECT COUNT(s) FROM SmsCode s WHERE s.phoneNumber = :phoneNumber AND s.createdAt > :since")
        int countByPhoneNumberAndCreatedAtAfter(
                        @Param("phoneNumber") String phoneNumber,
                        @Param("since") LocalDateTime since);

        /**
         * Поиск всех неиспользованных кодов для номера телефона
         * Отсортированы по времени создания (новые сначала)
         * 
         * @param phoneNumber номер телефона
         * @return список неиспользованных кодов
         */
        List<SmsCode> findByPhoneNumberAndUsedFalseOrderByCreatedAtDesc(String phoneNumber);

        /**
         * Поиск последнего отправленного кода для номера телефона
         * 
         * @param phoneNumber номер телефона
         * @return последний отправленный код
         */
        @Query("SELECT s FROM SmsCode s WHERE s.phoneNumber = :phoneNumber ORDER BY s.createdAt DESC")
        List<SmsCode> findByPhoneNumberOrderByCreatedAtDesc(@Param("phoneNumber") String phoneNumber);

        /**
         * Поиск действующего кода по номеру телефона и коду
         * 
         * @param phoneNumber номер телефона
         * @param code        SMS код
         * @param now         текущее время
         * @return действующий код если найден
         */
        @Query("SELECT s FROM SmsCode s WHERE s.phoneNumber = :phoneNumber AND s.code = :code AND s.used = false AND s.expiresAt > :now")
        Optional<SmsCode> findByPhoneNumberAndCodeAndUsedFalseAndExpiresAtAfter(
                        @Param("phoneNumber") String phoneNumber,
                        @Param("code") String code,
                        @Param("now") LocalDateTime now);

        /**
         * Подсчет попыток ввода кода за последние 24 часа
         * 
         * @param phoneNumber номер телефона
         * @param since       время с которого считать попытки
         * @return количество попыток
         */
        @Query("SELECT COALESCE(SUM(s.attempts), 0) FROM SmsCode s WHERE s.phoneNumber = :phoneNumber AND s.createdAt > :since")
        int countAttemptsByPhoneNumberAndCreatedAtAfter(
                        @Param("phoneNumber") String phoneNumber,
                        @Param("since") LocalDateTime since);
}