package com.baganov.pizzanat.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/**
 * Сервис для управления ограничениями частоты запросов (Rate Limiting).
 * Следует принципам SOLID - Single Responsibility и Open/Closed.
 */
@Service
public class RateLimitService {

    private static final Logger logger = LoggerFactory.getLogger(RateLimitService.class);

    // Хранилище для отслеживания попыток
    private final ConcurrentMap<String, RateLimitEntry> attempts = new ConcurrentHashMap<>();

    @Value("${sms.rate.limit.per.hour:3}")
    private Integer smsRateLimitPerHour;

    @Value("${telegram.auth.rate.limit.per.hour:5}")
    private Integer telegramRateLimitPerHour;

    /**
     * Типы rate limiting
     */
    public enum RateLimitType {
        SMS_SEND("sms_send"),
        SMS_VERIFY("sms_verify"),
        TELEGRAM_INIT("telegram_init"),
        TELEGRAM_VERIFY("telegram_verify");

        private final String key;

        RateLimitType(String key) {
            this.key = key;
        }

        public String getKey() {
            return key;
        }
    }

    /**
     * Проверяет, разрешен ли запрос для указанного идентификатора и типа
     * 
     * @param identifier идентификатор (номер телефона, IP-адрес, etc.)
     * @param type       тип операции
     * @return true если запрос разрешен
     */
    public boolean isAllowed(String identifier, RateLimitType type) {
        if (identifier == null || type == null) {
            return false;
        }

        String key = generateKey(identifier, type);
        RateLimitEntry entry = attempts.get(key);

        if (entry == null) {
            // Первый запрос - разрешаем
            return true;
        }

        // Очищаем устаревшие записи
        cleanupExpiredEntries();

        // Проверяем лимит
        int limit = getLimitForType(type);
        Duration window = getWindowForType(type);

        LocalDateTime windowStart = LocalDateTime.now().minus(window);
        long recentAttempts = entry.getAttemptsAfter(windowStart);

        boolean allowed = recentAttempts < limit;

        if (!allowed) {
            logger.warn("Rate limit exceeded for identifier {} and type {}. Attempts: {}/{}",
                    identifier, type, recentAttempts, limit);
        }

        return allowed;
    }

    /**
     * Записывает попытку для указанного идентификатора и типа
     * 
     * @param identifier идентификатор
     * @param type       тип операции
     */
    public void recordAttempt(String identifier, RateLimitType type) {
        if (identifier == null || type == null) {
            return;
        }

        String key = generateKey(identifier, type);
        RateLimitEntry entry = attempts.computeIfAbsent(key, k -> new RateLimitEntry());
        entry.addAttempt(LocalDateTime.now());

        logger.debug("Recorded attempt for identifier {} and type {}. Total attempts: {}",
                identifier, type, entry.getTotalAttempts());
    }

    /**
     * Возвращает время до следующей разрешенной попытки
     * 
     * @param identifier идентификатор
     * @param type       тип операции
     * @return время до retry или null если retry разрешен сразу
     */
    public Duration getRetryAfter(String identifier, RateLimitType type) {
        if (identifier == null || type == null) {
            return null;
        }

        String key = generateKey(identifier, type);
        RateLimitEntry entry = attempts.get(key);

        if (entry == null) {
            return null; // Нет предыдущих попыток
        }

        Duration window = getWindowForType(type);
        LocalDateTime oldestAllowedTime = LocalDateTime.now().minus(window);
        LocalDateTime earliestAttempt = entry.getEarliestAttemptAfter(oldestAllowedTime);

        if (earliestAttempt == null) {
            return null; // Все попытки устарели
        }

        LocalDateTime nextAllowedTime = earliestAttempt.plus(window);
        LocalDateTime now = LocalDateTime.now();

        if (nextAllowedTime.isAfter(now)) {
            return Duration.between(now, nextAllowedTime);
        }

        return null; // Retry разрешен сразу
    }

    /**
     * Очищает все записи для указанного идентификатора
     * 
     * @param identifier идентификатор для очистки
     */
    public void clearAttempts(String identifier) {
        if (identifier == null) {
            return;
        }

        attempts.entrySet().removeIf(entry -> entry.getKey().startsWith(identifier + ":"));
        logger.debug("Cleared all attempts for identifier {}", identifier);
    }

    /**
     * Возвращает статистику по попыткам
     * 
     * @param identifier идентификатор
     * @param type       тип операции
     * @return количество попыток за последний час
     */
    public int getAttemptCount(String identifier, RateLimitType type) {
        if (identifier == null || type == null) {
            return 0;
        }

        String key = generateKey(identifier, type);
        RateLimitEntry entry = attempts.get(key);

        if (entry == null) {
            return 0;
        }

        Duration window = getWindowForType(type);
        LocalDateTime windowStart = LocalDateTime.now().minus(window);
        return (int) entry.getAttemptsAfter(windowStart);
    }

    /**
     * Генерирует ключ для хранения
     */
    private String generateKey(String identifier, RateLimitType type) {
        return identifier + ":" + type.getKey();
    }

    /**
     * Возвращает лимит для типа операции
     */
    private int getLimitForType(RateLimitType type) {
        switch (type) {
            case SMS_SEND:
            case SMS_VERIFY:
                return smsRateLimitPerHour;
            case TELEGRAM_INIT:
            case TELEGRAM_VERIFY:
                return telegramRateLimitPerHour;
            default:
                return 5; // Значение по умолчанию
        }
    }

    /**
     * Возвращает временное окно для типа операции
     */
    private Duration getWindowForType(RateLimitType type) {
        // Для всех типов используем окно в 1 час
        return Duration.ofHours(1);
    }

    /**
     * Очищает устаревшие записи (старше 24 часов)
     */
    private void cleanupExpiredEntries() {
        LocalDateTime cutoff = LocalDateTime.now().minusHours(24);
        attempts.entrySet().removeIf(entry -> entry.getValue().isExpired(cutoff));
    }

    /**
     * Класс для хранения информации о попытках
     */
    private static class RateLimitEntry {
        private final ConcurrentMap<LocalDateTime, Integer> timestamps = new ConcurrentHashMap<>();

        public void addAttempt(LocalDateTime timestamp) {
            timestamps.put(timestamp, 1);
        }

        public long getAttemptsAfter(LocalDateTime after) {
            return timestamps.keySet().stream()
                    .filter(timestamp -> timestamp.isAfter(after))
                    .count();
        }

        public LocalDateTime getEarliestAttemptAfter(LocalDateTime after) {
            return timestamps.keySet().stream()
                    .filter(timestamp -> timestamp.isAfter(after))
                    .min(LocalDateTime::compareTo)
                    .orElse(null);
        }

        public int getTotalAttempts() {
            return timestamps.size();
        }

        public boolean isExpired(LocalDateTime cutoff) {
            return timestamps.keySet().stream().allMatch(timestamp -> timestamp.isBefore(cutoff));
        }
    }
}