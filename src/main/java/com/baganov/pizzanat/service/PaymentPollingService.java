/**
 * @file: PaymentPollingService.java
 * @description: Сервис активного опроса статуса платежей ЮКассы для решения проблемы задержки webhook'ов
 * @dependencies: YooKassaPaymentService, AdminBotService, PaymentRepository
 * @created: 2025-01-23
 */
package com.baganov.pizzanat.service;

import com.baganov.pizzanat.entity.Payment;
import com.baganov.pizzanat.entity.PaymentStatus;
import com.baganov.pizzanat.entity.PaymentMethod;
import com.baganov.pizzanat.entity.Order;
import com.baganov.pizzanat.entity.OrderPaymentStatus;
import com.baganov.pizzanat.repository.PaymentRepository;
import com.baganov.pizzanat.repository.OrderRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.List;

/**
 * Сервис для активного опроса статуса платежей ЮКассы
 * 
 * ПРОБЛЕМА: Webhook'и от ЮКассы приходят с задержкой до 10 минут
 * РЕШЕНИЕ: Опрашиваем ЮКассу каждую минуту для платежей в статусе PENDING/WAITING_FOR_CAPTURE
 * 
 * ЛОГИКА:
 * 1. Каждую минуту ищем активные неподтвержденные платежи (возраст < 10 минут)
 * 2. Для каждого платежа запрашиваем статус из ЮКассы через API
 * 3. Если статус изменился на SUCCEEDED - отправляем заказ в админский бот с пометкой "ОПЛАЧЕН СБП"
 * 4. Если статус CANCELLED/FAILED - логируем и отправляем алерт
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PaymentPollingService {

    private final PaymentRepository paymentRepository;
    private final OrderRepository orderRepository;
    private final YooKassaPaymentService yooKassaPaymentService;
    private final AdminBotService adminBotService;

    /**
     * Периодическая проверка статуса активных платежей каждую минуту
     * Проверяем только платежи младше 10 минут в статусах PENDING/WAITING_FOR_CAPTURE
     */
    @Scheduled(fixedRate = 60000) // Каждую минуту
    @Async
    @Transactional
    public void pollPendingPayments() {
        try {
            // Время 10 минут назад - старше этого времени не опрашиваем
            LocalDateTime tenMinutesAgo = LocalDateTime.now().minus(10, ChronoUnit.MINUTES);
            
            // Ищем активные неподтвержденные платежи (созданные за последние 10 минут)
            List<Payment> pendingPayments = paymentRepository
                .findActivePaymentsForPolling(tenMinutesAgo);
            
            if (pendingPayments.isEmpty()) {
                log.debug("🔍 Нет активных платежей для опроса");
                return;
            }
            
            log.info("🔄 Опрос статуса {} активных платежей ЮКассы", pendingPayments.size());
            
            for (Payment payment : pendingPayments) {
                pollSinglePayment(payment);
            }
            
        } catch (Exception e) {
            log.error("❌ Ошибка при опросе платежей ЮКассы: {}", e.getMessage(), e);
        }
    }

    /**
     * Опрос одного конкретного платежа
     */
    @Async
    @Transactional
    public void pollSinglePayment(Payment payment) {
        try {
            PaymentStatus oldStatus = payment.getStatus();
            
            log.debug("🔍 Опрос платежа #{} (статус: {}, возраст: {}мин)", 
                payment.getId(), oldStatus, 
                ChronoUnit.MINUTES.between(payment.getCreatedAt(), LocalDateTime.now()));
            
            // Запрашиваем актуальный статус из ЮКассы
            var updatedPayment = yooKassaPaymentService.checkPaymentStatus(payment.getId());
            
            // Получаем обновленный платеж из БД
            payment = paymentRepository.findById(payment.getId())
                .orElseThrow(() -> new RuntimeException("Платеж не найден после обновления"));
            
            PaymentStatus newStatus = payment.getStatus();
            
            // Проверяем изменился ли статус
            if (oldStatus != newStatus) {
                log.info("📊 Статус платежа #{} изменен через polling: {} → {}", 
                    payment.getId(), oldStatus, newStatus);
                
                handlePaymentStatusChange(payment, oldStatus, newStatus);
            }
            
        } catch (Exception e) {
            log.error("❌ Ошибка опроса платежа #{}: {}", payment.getId(), e.getMessage());
        }
    }

    /**
     * Обработка изменения статуса платежа обнаруженного через polling
     */
    private void handlePaymentStatusChange(Payment payment, PaymentStatus oldStatus, PaymentStatus newStatus) {
        Order order = payment.getOrder();
        
        switch (newStatus) {
            case SUCCEEDED:
                // Платеж успешно подтвержден - отправляем уведомление в админский бот
                handleSuccessfulPayment(payment, order);
                break;
                
            case CANCELLED:
                log.warn("❌ Платеж #{} отменен через polling для заказа #{}", 
                    payment.getId(), order.getId());
                // Алерт администраторам
                adminBotService.sendPaymentCancelAlert(payment, "Платеж отменен (обнаружено через опрос ЮКассы)");
                break;
                
            case FAILED:
                log.error("💥 Платеж #{} завершился ошибкой через polling для заказа #{}", 
                    payment.getId(), order.getId());
                // Алерт администраторам  
                adminBotService.sendPaymentFailureAlert(payment, "Платеж завершился ошибкой (обнаружено через опрос ЮКассы)");
                break;
                
            default:
                log.debug("🔄 Платеж #{} изменил статус на {} - дополнительных действий не требуется", 
                    payment.getId(), newStatus);
        }
    }

    /**
     * Обработка успешного платежа - отправка уведомления в админский бот с пометкой о способе оплаты
     */
    private void handleSuccessfulPayment(Payment payment, Order order) {
        try {
            log.info("✅ Платеж #{} успешно подтвержден через polling для заказа #{} (способ: {})", 
                payment.getId(), order.getId(), payment.getMethod());
            
            // Обновляем статус оплаты заказа
            order.setPaymentStatus(OrderPaymentStatus.PAID);
            orderRepository.save(order);
            log.info("💰 Заказ #{} - статус оплаты обновлен на PAID через polling", order.getId());
            
            // Определяем пометку о способе оплаты
            String paymentLabel = getPaymentMethodLabel(payment.getMethod());
            
            // Отправляем уведомление в админский бот с указанием способа оплаты
            adminBotService.sendSuccessfulPaymentOrderNotification(order, paymentLabel);
            
            log.info("📢 Уведомление о заказе #{} с подтвержденной оплатой {} отправлено в админский бот", 
                order.getId(), paymentLabel);
            
        } catch (Exception e) {
            log.error("❌ Ошибка отправки уведомления о успешном платеже #{}: {}", payment.getId(), e.getMessage(), e);
        }
    }

    /**
     * Получение человеко-читаемой пометки о способе оплаты
     */
    private String getPaymentMethodLabel(PaymentMethod method) {
        switch (method) {
            case SBP:
                return "ОПЛАЧЕН СБП";
            case BANK_CARD:
                return "ОПЛАЧЕН КАРТОЙ";
            case CASH:
                return "НАЛИЧНЫМИ";
            default:
                return "ОПЛАЧЕН ОНЛАЙН";
        }
    }

    /**
     * Принудительная проверка конкретного платежа (для тестирования)
     */
    @Async
    @Transactional
    public void forcePollPayment(Long paymentId) {
        try {
            Payment payment = paymentRepository.findById(paymentId)
                .orElseThrow(() -> new IllegalArgumentException("Платеж не найден: " + paymentId));
            
            log.info("🔧 Принудительная проверка платежа #{}", paymentId);
            pollSinglePayment(payment);
            
        } catch (Exception e) {
            log.error("❌ Ошибка принудительной проверки платежа #{}: {}", paymentId, e.getMessage(), e);
        }
    }

    /**
     * Получение статистики активных платежей для мониторинга
     */
    @Transactional(readOnly = true)
    public void logPollingStatistics() {
        try {
            LocalDateTime tenMinutesAgo = LocalDateTime.now().minus(10, ChronoUnit.MINUTES);
            List<Payment> activePayments = paymentRepository.findActivePaymentsForPolling(tenMinutesAgo);
            
            if (!activePayments.isEmpty()) {
                log.info("📊 Статистика активных платежей для опроса: {} шт.", activePayments.size());
                
                activePayments.forEach(payment -> 
                    log.debug("   - Платеж #{}: статус {}, возраст {}мин", 
                        payment.getId(), payment.getStatus(), 
                        ChronoUnit.MINUTES.between(payment.getCreatedAt(), LocalDateTime.now()))
                );
            }
            
        } catch (Exception e) {
            log.error("❌ Ошибка получения статистики polling: {}", e.getMessage());
        }
    }
} 