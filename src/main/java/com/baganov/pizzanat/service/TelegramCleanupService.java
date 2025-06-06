package com.baganov.pizzanat.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

/**
 * Сервис для очистки истекших Telegram токенов по расписанию.
 * Следует принципу Single Responsibility из SOLID.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class TelegramCleanupService {

    private final TelegramAuthService telegramAuthService;

    /**
     * Очистка истекших Telegram токенов каждые 10 минут
     */
    @Scheduled(fixedRate = 600000) // 10 минут = 600,000 мс
    public void cleanupExpiredTokens() {
        log.debug("Запуск очистки истекших Telegram токенов");

        try {
            telegramAuthService.cleanupExpiredTokens();
            log.debug("Очистка истекших Telegram токенов завершена");
        } catch (Exception e) {
            log.error("Ошибка при очистке истекших Telegram токенов: {}", e.getMessage(), e);
        }
    }

    /**
     * Глубокая очистка старых токенов каждый день в 3:00
     */
    @Scheduled(cron = "0 0 3 * * *")
    public void deepCleanup() {
        log.info("Запуск глубокой очистки старых Telegram токенов");

        try {
            telegramAuthService.cleanupExpiredTokens();
            log.info("Глубокая очистка старых Telegram токенов завершена");
        } catch (Exception e) {
            log.error("Ошибка при глубокой очистке Telegram токенов: {}", e.getMessage(), e);
        }
    }
}