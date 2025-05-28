package com.baganov.pizzanat.service;

import com.baganov.pizzanat.entity.Order;
import com.baganov.pizzanat.entity.User;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class NotificationService {

    private final JavaMailSender mailSender;

    @Value("${app.notification.email.from}")
    private String emailFrom;

    @Value("${app.notification.email.enabled:false}")
    private boolean emailEnabled;

    @Async
    public void sendOrderStatusChangeNotification(Order order, String oldStatus, String newStatus) {
        if (order.getUser() != null) {
            // Логируем вместо отправки (реальная отправка отключена, пока не решим
            // проблему)
            logEmailNotification(order, oldStatus, newStatus);
        }

        log.info("Уведомление о смене статуса заказа #{} с {} на {} отправлено (симуляция)",
                order.getId(), oldStatus, newStatus);
    }

    private void logEmailNotification(Order order, String oldStatus, String newStatus) {
        if (!emailEnabled) {
            log.debug("Email-уведомления отключены");
            return;
        }

        User user = order.getUser();
        if (user == null || user.getEmail() == null || user.getEmail().isEmpty()) {
            log.warn("Невозможно отправить email: пользователь или email отсутствует");
            return;
        }

        // Формируем сообщение для логов
        String messageText = String.format(
                "Здравствуйте, %s!\n\n" +
                        "Статус вашего заказа #%d изменился с '%s' на '%s'.\n\n" +
                        "С уважением,\nКоманда PizzaNat",
                user.getFirstName(),
                order.getId(),
                oldStatus,
                newStatus);

        log.info("СИМУЛЯЦИЯ EMAIL: To: {}, Subject: Обновление статуса заказа #{}, Содержимое: {}",
                user.getEmail(), order.getId(), messageText);
    }
}