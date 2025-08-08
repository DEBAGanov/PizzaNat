/**
 * @file: GoogleSheetsService.java
 * @description: Сервис для работы с Google Sheets API
 * @dependencies: Google Sheets API, Spring Retry, Order, Payment
 * @created: 2025-01-28
 */
package com.baganov.pizzanat.service;

import com.baganov.pizzanat.config.GoogleSheetsConfiguration;
import com.baganov.pizzanat.entity.Order;
import com.baganov.pizzanat.entity.Payment;
import com.baganov.pizzanat.entity.PaymentMethod;
import com.baganov.pizzanat.repository.PaymentRepository;
import com.google.api.services.sheets.v4.Sheets;
import com.google.api.services.sheets.v4.model.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.retry.annotation.Backoff;
import org.springframework.retry.annotation.Retryable;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.math.BigDecimal;
import java.time.format.DateTimeFormatter;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
@ConditionalOnProperty(name = "google.sheets.enabled", havingValue = "true")
public class GoogleSheetsService {

    private final Sheets sheetsClient;
    private final GoogleSheetsConfiguration config;
    private final PaymentRepository paymentRepository;
    
    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm");
    private static final String HEADER_RANGE = "A1:P1";
    private static final String INSERT_RANGE = "A2:P2";

    /**
     * Инициализация таблицы с заголовками
     */
    @Async
    public void initializeSheet() {
        try {
            log.info("🔧 Инициализация Google Sheets таблицы");
            
            // Создание заголовков
            List<Object> headers = Arrays.asList(
                "ID заказа", "Дата создания", "Имя клиента", "Телефон", "Email",
                "Состав заказа", "Адрес доставки", "Тип доставки", 
                "Стоимость товаров", "Стоимость доставки", "Общая сумма",
                "Способ оплаты", "Статус платежа", "Статус заказа", 
                "Комментарий", "Ссылка на платеж"
            );
            
            ValueRange headerRange = new ValueRange()
                    .setValues(Arrays.asList(headers));
            
            UpdateValuesResponse response = sheetsClient.spreadsheets().values()
                    .update(config.getSpreadsheetId(), HEADER_RANGE, headerRange)
                    .setValueInputOption("RAW")
                    .execute();
                    
            log.info("✅ Заголовки таблицы успешно созданы: {} ячеек обновлено", 
                    response.getUpdatedCells());
                    
        } catch (Exception e) {
            log.error("❌ Ошибка инициализации Google Sheets таблицы: {}", e.getMessage(), e);
        }
    }

    /**
     * Добавление нового заказа в таблицу (в начало)
     */
    @Async
    @Retryable(value = {IOException.class}, maxAttempts = 3, backoff = @Backoff(delay = 1000))
    public void addOrderToSheet(Order order) {
        try {
            log.info("📊 Добавление заказа #{} в Google Sheets", order.getId());
            
            // Формирование данных заказа
            List<Object> orderData = formatOrderData(order);
            
            // Вставка строки в начало таблицы (после заголовков)
            insertRowAtTop(orderData);
            
            log.info("✅ Заказ #{} успешно добавлен в Google Sheets", order.getId());
            
        } catch (Exception e) {
            log.error("❌ Ошибка добавления заказа #{} в Google Sheets: {}", 
                    order.getId(), e.getMessage(), e);
            throw new RuntimeException("Failed to add order to Google Sheets", e);
        }
    }

    /**
     * Обновление статуса заказа в таблице
     */
    @Async
    @Retryable(value = {IOException.class}, maxAttempts = 3, backoff = @Backoff(delay = 1000))
    public void updateOrderStatus(Integer orderId, String newStatus) {
        try {
            log.info("🔄 Обновление статуса заказа #{} в Google Sheets: {}", orderId, newStatus);
            
            // Поиск строки с заказом
            int rowIndex = findOrderRow(orderId);
            if (rowIndex == -1) {
                log.warn("⚠️ Заказ #{} не найден в Google Sheets", orderId);
                return;
            }
            
            // Обновление статуса (колонка N)
            String range = String.format("%s!N%d", config.getSheetName(), rowIndex);
            ValueRange valueRange = new ValueRange()
                    .setValues(Arrays.asList(Arrays.asList(newStatus)));
            
            sheetsClient.spreadsheets().values()
                    .update(config.getSpreadsheetId(), range, valueRange)
                    .setValueInputOption("RAW")
                    .execute();
                    
            log.info("✅ Статус заказа #{} обновлен в Google Sheets", orderId);
            
        } catch (Exception e) {
            log.error("❌ Ошибка обновления статуса заказа #{}: {}", orderId, e.getMessage(), e);
        }
    }

    /**
     * Обновление статуса платежа в таблице
     */
    @Async
    @Retryable(value = {IOException.class}, maxAttempts = 3, backoff = @Backoff(delay = 1000))
    public void updatePaymentStatus(Integer orderId, String paymentStatus) {
        try {
            log.info("💳 Обновление статуса платежа для заказа #{} в Google Sheets: {}", 
                    orderId, paymentStatus);
            
            int rowIndex = findOrderRow(orderId);
            if (rowIndex == -1) {
                log.warn("⚠️ Заказ #{} не найден в Google Sheets", orderId);
                return;
            }
            
            // Обновление статуса платежа (колонка M)
            String range = String.format("%s!M%d", config.getSheetName(), rowIndex);
            ValueRange valueRange = new ValueRange()
                    .setValues(Arrays.asList(Arrays.asList(paymentStatus)));
            
            sheetsClient.spreadsheets().values()
                    .update(config.getSpreadsheetId(), range, valueRange)
                    .setValueInputOption("RAW")
                    .execute();
                    
            log.info("✅ Статус платежа для заказа #{} обновлен в Google Sheets", orderId);
            
        } catch (Exception e) {
            log.error("❌ Ошибка обновления статуса платежа для заказа #{}: {}", 
                    orderId, e.getMessage(), e);
        }
    }

    /**
     * Форматирование данных заказа для Google Sheets
     */
    private List<Object> formatOrderData(Order order) {
        // Получение платежей для заказа
        List<Payment> payments = paymentRepository.findByOrderIdOrderByCreatedAtDesc(order.getId().longValue());
        Payment lastPayment = payments.isEmpty() ? null : payments.get(0);
        
        // Форматирование состава заказа - каждый товар на новой строке
        String orderItems = order.getItems().stream()
                .map(item -> String.format("%s x%d (%.0f₽)", 
                        item.getProduct().getName(),
                        item.getQuantity(),
                        item.getPrice()))
                .collect(Collectors.joining(";\n")); // Используем ;\n для новой строки в ячейке
        
        // Определение статуса платежа
        String paymentStatus = "Не оплачен";
        String paymentUrl = "";
        if (order.getPaymentMethod() != null) {
            switch (order.getPaymentMethod()) {
                case CASH:
                    paymentStatus = "Наличными";
                    break;
                case SBP:
                case BANK_CARD:
                    if (lastPayment != null) {
                        paymentStatus = lastPayment.getStatus().getDescription();
                        if (lastPayment.getConfirmationUrl() != null) {
                            paymentUrl = lastPayment.getConfirmationUrl();
                        }
                    }
                    break;
            }
        }
        
        return Arrays.asList(
            order.getId(),                                                    // A: ID заказа
            order.getCreatedAt().format(DATE_FORMATTER),                     // B: Дата создания
            order.getContactName(),                                          // C: Имя клиента
            order.getContactPhone(),                                         // D: Телефон
            order.getUser() != null ? order.getUser().getEmail() : "",       // E: Email
            orderItems,                                                      // F: Состав заказа
            order.getDeliveryAddress() != null ? 
                order.getDeliveryAddress() : 
                order.getDeliveryLocation().getAddress(),                    // G: Адрес доставки
            order.getDeliveryType() != null ? order.getDeliveryType() : "Самовывоз", // H: Тип доставки
            formatAmount(order.getItemsAmount()),                            // I: Стоимость товаров
            formatAmount(order.getDeliveryCost()),                           // J: Стоимость доставки
            formatAmount(order.getTotalAmount()),                            // K: Общая сумма
            order.getPaymentMethod() != null ? 
                order.getPaymentMethod().getDisplayName() : "Наличными",     // L: Способ оплаты
            paymentStatus,                                                   // M: Статус платежа
            order.getStatus().getName(),                                     // N: Статус заказа
            order.getComment() != null ? order.getComment() : "",            // O: Комментарий
            paymentUrl                                                       // P: Ссылка на платеж
        );
    }

    /**
     * Вставка строки в начало таблицы (после заголовков)
     */
    private void insertRowAtTop(List<Object> rowData) throws IOException {
        // Сначала вставляем пустую строку
        InsertDimensionRequest insertRequest = new InsertDimensionRequest()
                .setRange(new DimensionRange()
                        .setSheetId(getSheetId())
                        .setDimension("ROWS")
                        .setStartIndex(1)
                        .setEndIndex(2));

        BatchUpdateSpreadsheetRequest batchRequest = new BatchUpdateSpreadsheetRequest()
                .setRequests(Arrays.asList(new Request().setInsertDimension(insertRequest)));

        sheetsClient.spreadsheets()
                .batchUpdate(config.getSpreadsheetId(), batchRequest)
                .execute();

        // Затем заполняем данными
        ValueRange valueRange = new ValueRange()
                .setValues(Arrays.asList(rowData));

        sheetsClient.spreadsheets().values()
                .update(config.getSpreadsheetId(), INSERT_RANGE, valueRange)
                .setValueInputOption("RAW")
                .execute();
    }

    /**
     * Поиск строки с заказом по ID
     */
    private int findOrderRow(Integer orderId) throws IOException {
        String range = String.format("%s!A:A", config.getSheetName());
                    ValueRange response = sheetsClient.spreadsheets().values()
                .get(config.getSpreadsheetId(), range)
                .execute();

        List<List<Object>> values = response.getValues();
        if (values != null) {
            for (int i = 1; i < values.size(); i++) { // Пропускаем заголовок
                List<Object> row = values.get(i);
                if (!row.isEmpty() && row.get(0).toString().equals(orderId.toString())) {
                    return i + 1; // Возвращаем 1-indexed номер строки
                }
            }
        }
        return -1;
    }

    /**
     * Получение ID листа
     */
    private Integer getSheetId() throws IOException {
                    Spreadsheet spreadsheet = sheetsClient.spreadsheets()
                .get(config.getSpreadsheetId())
                .execute();
        
        return spreadsheet.getSheets().get(0).getProperties().getSheetId();
    }

    /**
     * Форматирование суммы для отображения
     */
    private String formatAmount(BigDecimal amount) {
        return amount != null ? String.format("%.0f₽", amount) : "0₽";
    }
}