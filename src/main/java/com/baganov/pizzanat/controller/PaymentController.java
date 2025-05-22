/**
 * @file: PaymentController.java
 * @description: Контроллер для обработки платежей
 * @dependencies: PaymentService
 * @created: 2023-11-01
 */
package com.baganov.pizzanat.controller;

import com.baganov.pizzanat.service.PaymentService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * Контроллер для обработки платежей и платежных уведомлений
 */
@Slf4j
@RestController
@RequestMapping("/api/v1/payments")
@RequiredArgsConstructor
public class PaymentController {

    private final PaymentService paymentService;

    /**
     * Обрабатывает уведомление о платеже от Robokassa
     *
     * @param params параметры уведомления
     * @return ответ для платежной системы
     */
    @PostMapping("/robokassa/notify")
    public ResponseEntity<String> handleRobokassaNotification(@RequestParam Map<String, String> params) {
        log.info("Получено уведомление от Robokassa: {}", params);

        boolean success = paymentService.processPaymentNotification(params);

        if (success) {
            // Robokassa ожидает получить InvId и "OK" в ответе при успешной обработке
            String invId = params.getOrDefault("InvId", "");
            return ResponseEntity.ok("OK" + invId);
        } else {
            return ResponseEntity.badRequest().body("Failed to process payment notification");
        }
    }

    /**
     * Обрабатывает успешное завершение платежа и перенаправление пользователя
     *
     * @param params параметры успешного платежа
     * @return перенаправление на страницу успешного заказа
     */
    @GetMapping("/robokassa/success")
    public ResponseEntity<String> handleRobokassaSuccess(@RequestParam Map<String, String> params) {
        log.info("Пользователь вернулся после успешной оплаты: {}", params);

        // В реальном приложении здесь будет перенаправление на страницу успешного
        // заказа
        return ResponseEntity.ok("Оплата успешно завершена. Заказ №" + params.getOrDefault("InvId", ""));
    }

    /**
     * Обрабатывает отмену платежа пользователем
     *
     * @param params параметры отмененного платежа
     * @return перенаправление на страницу отмены заказа
     */
    @GetMapping("/robokassa/fail")
    public ResponseEntity<String> handleRobokassaFail(@RequestParam Map<String, String> params) {
        log.info("Пользователь вернулся после отмены оплаты: {}", params);

        // В реальном приложении здесь будет перенаправление на страницу отмены заказа
        return ResponseEntity.ok("Оплата была отменена. Заказ №" + params.getOrDefault("InvId", ""));
    }
}