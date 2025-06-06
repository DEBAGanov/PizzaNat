package com.baganov.pizzanat.service;

import com.baganov.pizzanat.repository.SmsCodeRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

/**
 * Сервис для очистки истекших SMS кодов.
 * Следует принципу Single Responsibility из SOLID.
 */
@Service
@Transactional
public class SmsCleanupService {

    private static final Logger logger = LoggerFactory.getLogger(SmsCleanupService.class);

    private final SmsCodeRepository smsCodeRepository;

    public SmsCleanupService(SmsCodeRepository smsCodeRepository) {
        this.smsCodeRepository = smsCodeRepository;
    }

    /**
     * Очистка истекших SMS кодов каждые 10 минут
     */
    @Scheduled(fixedRate = 600000) // 10 минут в миллисекундах
    public void cleanupExpiredSmsCodes() {
        try {
            LocalDateTime cutoff = LocalDateTime.now();

            logger.debug("Начинаем очистку истекших SMS кодов до: {}", cutoff);

            smsCodeRepository.deleteByExpiresAtBefore(cutoff);

            logger.debug("Очистка истекших SMS кодов завершена");

        } catch (Exception e) {
            logger.error("Ошибка при очистке истекших SMS кодов: {}", e.getMessage(), e);
        }
    }

    /**
     * Очистка старых SMS кодов (старше 24 часов) каждый день в 2:00
     */
    @Scheduled(cron = "0 0 2 * * ?")
    public void cleanupOldSmsCodes() {
        try {
            LocalDateTime cutoff = LocalDateTime.now().minusHours(24);

            logger.info("Начинаем очистку старых SMS кодов до: {}", cutoff);

            smsCodeRepository.deleteByExpiresAtBefore(cutoff);

            logger.info("Очистка старых SMS кодов завершена");

        } catch (Exception e) {
            logger.error("Ошибка при очистке старых SMS кодов: {}", e.getMessage(), e);
        }
    }

    /**
     * Ручная очистка для администраторов
     */
    public void forceCleanup() {
        try {
            LocalDateTime cutoff = LocalDateTime.now();

            logger.info("Выполняется принудительная очистка SMS кодов до: {}", cutoff);

            smsCodeRepository.deleteByExpiresAtBefore(cutoff);

            logger.info("Принудительная очистка SMS кодов завершена");

        } catch (Exception e) {
            logger.error("Ошибка при принудительной очистке SMS кодов: {}", e.getMessage(), e);
            throw e;
        }
    }
}