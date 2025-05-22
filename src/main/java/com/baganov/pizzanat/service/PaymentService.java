/**
 * @file: PaymentService.java
 * @description: Сервис для обработки платежей
 * @dependencies: RobokassaClient
 * @created: 2023-11-01
 */
package com.baganov.pizzanat.service;

import com.baganov.pizzanat.service.client.RobokassaClient;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.Map;

/**
 * Сервис для обработки платежей
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PaymentService {

    private final RobokassaClient robokassaClient;

    /**
     * Создает URL для оплаты заказа через Robokassa
     * 
     * @param orderId     идентификатор заказа
     * @param amount      сумма заказа
     * @param description описание заказа
     * @return URL для перенаправления на страницу оплаты
     */
    public String createPaymentUrl(Integer orderId, BigDecimal amount, String description) {
        try {
            log.info("Создание URL для оплаты заказа #{} на сумму {}", orderId, amount);
            String url = robokassaClient.createPaymentUrl(
                    orderId.toString(),
                    amount.doubleValue(),
                    description);

            if (url == null) {
                log.error("Не удалось создать URL для оплаты заказа #{}", orderId);
                throw new RuntimeException("Ошибка при создании URL для оплаты");
            }

            return url;
        } catch (Exception e) {
            log.error("Ошибка при создании URL для оплаты заказа #{}: {}", orderId, e.getMessage(), e);
            throw new RuntimeException("Ошибка при создании URL для оплаты", e);
        }
    }

    /**
     * Обрабатывает уведомление о платеже от Robokassa
     * 
     * @param notification параметры уведомления
     * @return true если уведомление успешно обработано
     */
    public boolean processPaymentNotification(Map<String, String> notification) {
        try {
            String invId = notification.get("InvId");
            String outSum = notification.get("OutSum");

            log.info("Получено уведомление об оплате заказа #{} на сумму {}", invId, outSum);

            // Проверяем подпись уведомления
            boolean isValid = robokassaClient.verifyNotification(notification);

            if (!isValid) {
                log.warn("Недействительная подпись в уведомлении об оплате заказа #{}", invId);
                return false;
            }

            // Здесь будет логика обновления статуса заказа
            // TODO: Обновить статус заказа на "Оплачен"

            log.info("Успешно обработано уведомление об оплате заказа #{}", invId);
            return true;
        } catch (Exception e) {
            log.error("Ошибка при обработке уведомления об оплате: {}", e.getMessage(), e);
            return false;
        }
    }
}