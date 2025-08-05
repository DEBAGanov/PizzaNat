# Google Sheets API Интеграция - Техническое руководство

## 📋 Обзор

Данное руководство описывает полную реализацию интеграции PizzaNat с Google Sheets API для автоматического отслеживания заказов в реальном времени.

### 🎯 Цели интеграции
- **Централизованный мониторинг**: Все заказы автоматически попадают в Google таблицу
- **Реальное время**: Мгновенное обновление при создании заказов и изменении статусов
- **Аналитика**: Возможность создания отчетов и графиков прямо в Google Sheets
- **Интеграция**: Совместимость с внешними системами через Google Sheets API

---

## 🚀 План реализации

### Этап 1: Настройка Google Cloud Platform

#### 1.1 Создание проекта в Google Cloud Console
```bash
# 1. Перейти в Google Cloud Console: https://console.cloud.google.com
# 2. Создать новый проект или выбрать существующий
# 3. Включить Google Sheets API
# 4. Создать Service Account
# 5. Скачать credentials.json файл
```

#### 1.2 Настройка Service Account
```json
{
  "type": "service_account",
  "project_id": "your-project-id",
  "private_key_id": "key-id",
  "private_key": "-----BEGIN PRIVATE KEY-----\n...\n-----END PRIVATE KEY-----\n",
  "client_email": "service-account@your-project.iam.gserviceaccount.com",
  "client_id": "client-id",
  "auth_uri": "https://accounts.google.com/o/oauth2/auth",
  "token_uri": "https://oauth2.googleapis.com/token"
}
```

### Этап 2: Добавление зависимостей

#### 2.1 Обновление build.gradle
```gradle
dependencies {
    // Google Sheets API
    implementation 'com.google.apis:google-api-services-sheets:v4-rev612-1.25.0'
    implementation 'com.google.auth:google-auth-library-oauth2-http:1.19.0'
    implementation 'com.google.auth:google-auth-library-credentials:1.19.0'
    
    // Google HTTP Client
    implementation 'com.google.http-client:google-http-client-jackson2:1.43.3'
    
    // Spring Retry для обработки сбоев
    implementation 'org.springframework.retry:spring-retry'
    implementation 'org.springframework:spring-aspects'
}
```

### Этап 3: Конфигурация

#### 3.1 GoogleSheetsConfiguration.java
```java
/**
 * @file: GoogleSheetsConfiguration.java
 * @description: Конфигурация Google Sheets API интеграции
 * @dependencies: Spring Configuration, Google Sheets API
 * @created: 2025-01-28
 */
package com.baganov.pizzanat.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Data
@Configuration
@ConfigurationProperties(prefix = "google.sheets")
public class GoogleSheetsConfiguration {
    
    /**
     * Включена ли интеграция с Google Sheets
     */
    private boolean enabled = false;
    
    /**
     * ID Google таблицы (из URL)
     */
    private String spreadsheetId;
    
    /**
     * Название листа в таблице
     */
    private String sheetName = "Заказы";
    
    /**
     * Путь к файлу с credentials
     */
    private String credentialsPath = "/app/config/google-credentials.json";
    
    /**
     * Название приложения для Google API
     */
    private String applicationName = "PizzaNat Order Tracker";
    
    /**
     * Timeout для HTTP запросов (мс)
     */
    private int connectTimeout = 10000;
    
    /**
     * Timeout для чтения ответа (мс)
     */
    private int readTimeout = 30000;
    
    /**
     * Максимальное количество попыток при ошибках
     */
    private int maxRetryAttempts = 3;
    
    /**
     * Задержка между попытками (мс)
     */
    private int retryDelay = 1000;
}
```

#### 3.2 GoogleSheetsApiConfiguration.java
```java
/**
 * @file: GoogleSheetsApiConfiguration.java
 * @description: Bean конфигурация для Google Sheets API
 * @dependencies: Google Auth, Google Sheets API
 * @created: 2025-01-28
 */
package com.baganov.pizzanat.config;

import com.google.api.client.googleapis.javanet.GoogleNetHttpTransport;
import com.google.api.client.http.HttpTransport;
import com.google.api.client.json.JsonFactory;
import com.google.api.client.json.jackson2.JacksonFactory;
import com.google.api.services.sheets.v4.Sheets;
import com.google.api.services.sheets.v4.SheetsScopes;
import com.google.auth.http.HttpCredentialsAdapter;
import com.google.auth.oauth2.GoogleCredentials;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.io.FileInputStream;
import java.io.IOException;
import java.security.GeneralSecurityException;
import java.util.Collections;

@Slf4j
@Configuration
@RequiredArgsConstructor
@ConditionalOnProperty(name = "google.sheets.enabled", havingValue = "true")
public class GoogleSheetsApiConfiguration {

    private final GoogleSheetsConfiguration config;
    private static final JsonFactory JSON_FACTORY = JacksonFactory.getDefaultInstance();

    @Bean
    public Sheets googleSheetsService() throws IOException, GeneralSecurityException {
        log.info("🔧 Инициализация Google Sheets API сервиса");
        
        // HTTP Transport
        HttpTransport httpTransport = GoogleNetHttpTransport.newTrustedTransport();
        
        // Credentials
        GoogleCredentials credentials = GoogleCredentials
                .fromStream(new FileInputStream(config.getCredentialsPath()))
                .createScoped(Collections.singleton(SheetsScopes.SPREADSHEETS));
        
        // Создание Sheets сервиса
        Sheets service = new Sheets.Builder(
                httpTransport, 
                JSON_FACTORY, 
                new HttpCredentialsAdapter(credentials))
                .setApplicationName(config.getApplicationName())
                .build();
                
        log.info("✅ Google Sheets API сервис успешно инициализирован");
        return service;
    }
}
```

### Этап 4: Основной сервис

#### 4.1 GoogleSheetsService.java
```java
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

    private final Sheets sheetsService;
    private final GoogleSheetsConfiguration config;
    private final PaymentService paymentService;
    
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
            
            UpdateValuesResponse response = sheetsService.spreadsheets().values()
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
            
            sheetsService.spreadsheets().values()
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
            
            sheetsService.spreadsheets().values()
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
        List<Payment> payments = paymentService.getPaymentsForOrder(order.getId().longValue());
        Payment lastPayment = payments.isEmpty() ? null : payments.get(0);
        
        // Форматирование состава заказа
        String orderItems = order.getItems().stream()
                .map(item -> String.format("%s x%d (%.0f₽)", 
                        item.getProduct().getName(),
                        item.getQuantity(),
                        item.getPrice()))
                .collect(Collectors.joining("; "));
        
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
                        paymentStatus = lastPayment.getStatus().getDisplayName();
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

        sheetsService.spreadsheets()
                .batchUpdate(config.getSpreadsheetId(), batchRequest)
                .execute();

        // Затем заполняем данными
        ValueRange valueRange = new ValueRange()
                .setValues(Arrays.asList(rowData));

        sheetsService.spreadsheets().values()
                .update(config.getSpreadsheetId(), INSERT_RANGE, valueRange)
                .setValueInputOption("RAW")
                .execute();
    }

    /**
     * Поиск строки с заказом по ID
     */
    private int findOrderRow(Integer orderId) throws IOException {
        String range = String.format("%s!A:A", config.getSheetName());
        ValueRange response = sheetsService.spreadsheets().values()
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
        Spreadsheet spreadsheet = sheetsService.spreadsheets()
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
```

### Этап 5: Event Listener

#### 5.1 GoogleSheetsEventListener.java
```java
/**
 * @file: GoogleSheetsEventListener.java
 * @description: Обработчик событий для автоматического обновления Google Sheets
 * @dependencies: Spring Events, GoogleSheetsService, Order Events
 * @created: 2025-01-28
 */
package com.baganov.pizzanat.service.listener;

import com.baganov.pizzanat.event.NewOrderEvent;
import com.baganov.pizzanat.event.OrderStatusChangedEvent;
import com.baganov.pizzanat.event.PaymentStatusChangedEvent;
import com.baganov.pizzanat.service.GoogleSheetsService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(name = "google.sheets.enabled", havingValue = "true")
public class GoogleSheetsEventListener {

    private final GoogleSheetsService googleSheetsService;

    /**
     * Обработка события создания нового заказа
     */
    @EventListener
    public void handleNewOrderEvent(NewOrderEvent event) {
        try {
            log.info("📊 Получено событие нового заказа #{} для Google Sheets", 
                    event.getOrder().getId());
            
            googleSheetsService.addOrderToSheet(event.getOrder());
            
        } catch (Exception e) {
            log.error("❌ Ошибка обработки события нового заказа #{} для Google Sheets: {}", 
                    event.getOrder().getId(), e.getMessage(), e);
        }
    }

    /**
     * Обработка события изменения статуса заказа
     */
    @EventListener
    public void handleOrderStatusChangedEvent(OrderStatusChangedEvent event) {
        try {
            log.info("🔄 Получено событие изменения статуса заказа #{} для Google Sheets: {} → {}", 
                    event.getOrderId(), event.getOldStatus(), event.getNewStatus());
            
            googleSheetsService.updateOrderStatus(event.getOrderId(), event.getNewStatus());
            
        } catch (Exception e) {
            log.error("❌ Ошибка обработки события изменения статуса заказа #{}: {}", 
                    event.getOrderId(), e.getMessage(), e);
        }
    }

    /**
     * Обработка события изменения статуса платежа
     */
    @EventListener
    public void handlePaymentStatusChangedEvent(PaymentStatusChangedEvent event) {
        try {
            log.info("💳 Получено событие изменения статуса платежа для заказа #{}: {} → {}", 
                    event.getOrderId(), event.getOldStatus(), event.getNewStatus());
            
            googleSheetsService.updatePaymentStatus(
                    event.getOrderId(), 
                    event.getNewStatus().getDisplayName()
            );
            
        } catch (Exception e) {
            log.error("❌ Ошибка обработки события изменения статуса платежа для заказа #{}: {}", 
                    event.getOrderId(), e.getMessage(), e);
        }
    }
}
```

### Этап 6: События (если не существуют)

#### 6.1 OrderStatusChangedEvent.java
```java
/**
 * @file: OrderStatusChangedEvent.java
 * @description: Событие изменения статуса заказа
 * @dependencies: Spring Events
 * @created: 2025-01-28
 */
package com.baganov.pizzanat.event;

import lombok.Getter;
import org.springframework.context.ApplicationEvent;

@Getter
public class OrderStatusChangedEvent extends ApplicationEvent {
    
    private final Integer orderId;
    private final String oldStatus;
    private final String newStatus;

    public OrderStatusChangedEvent(Object source, Integer orderId, String oldStatus, String newStatus) {
        super(source);
        this.orderId = orderId;
        this.oldStatus = oldStatus;
        this.newStatus = newStatus;
    }
}
```

#### 6.2 PaymentStatusChangedEvent.java
```java
/**
 * @file: PaymentStatusChangedEvent.java
 * @description: Событие изменения статуса платежа
 * @dependencies: Spring Events, PaymentStatus
 * @created: 2025-01-28
 */
package com.baganov.pizzanat.event;

import com.baganov.pizzanat.entity.PaymentStatus;
import lombok.Getter;
import org.springframework.context.ApplicationEvent;

@Getter
public class PaymentStatusChangedEvent extends ApplicationEvent {
    
    private final Integer orderId;
    private final PaymentStatus oldStatus;
    private final PaymentStatus newStatus;

    public PaymentStatusChangedEvent(Object source, Integer orderId, 
                                   PaymentStatus oldStatus, PaymentStatus newStatus) {
        super(source);
        this.orderId = orderId;
        this.oldStatus = oldStatus;
        this.newStatus = newStatus;
    }
}
```

### Этап 7: Обновление Docker Compose

#### 7.1 Добавление переменных в docker-compose.yml
```yaml
environment:
  # Google Sheets API настройки
  GOOGLE_SHEETS_ENABLED: ${GOOGLE_SHEETS_ENABLED:-false}
  GOOGLE_SHEETS_SPREADSHEET_ID: ${GOOGLE_SHEETS_SPREADSHEET_ID:-}
  GOOGLE_SHEETS_SHEET_NAME: ${GOOGLE_SHEETS_SHEET_NAME:-Заказы}
  GOOGLE_SHEETS_CREDENTIALS_PATH: ${GOOGLE_SHEETS_CREDENTIALS_PATH:-/app/config/google-credentials.json}
  GOOGLE_SHEETS_APPLICATION_NAME: ${GOOGLE_SHEETS_APPLICATION_NAME:-PizzaNat Order Tracker}
  GOOGLE_SHEETS_CONNECT_TIMEOUT: ${GOOGLE_SHEETS_CONNECT_TIMEOUT:-10000}
  GOOGLE_SHEETS_READ_TIMEOUT: ${GOOGLE_SHEETS_READ_TIMEOUT:-30000}
  GOOGLE_SHEETS_MAX_RETRY_ATTEMPTS: ${GOOGLE_SHEETS_MAX_RETRY_ATTEMPTS:-3}
  GOOGLE_SHEETS_RETRY_DELAY: ${GOOGLE_SHEETS_RETRY_DELAY:-1000}

volumes:
  # Монтирование credentials файла
  - ${GOOGLE_CREDENTIALS_FILE_PATH:-./config/google-credentials.json}:/app/config/google-credentials.json:ro
```

#### 7.2 Создание .env файла для локальной разработки
```bash
# Google Sheets API Configuration
GOOGLE_SHEETS_ENABLED=true
GOOGLE_SHEETS_SPREADSHEET_ID=1ABC123def456GHI789jkl_your_spreadsheet_id
GOOGLE_SHEETS_SHEET_NAME=Заказы
GOOGLE_CREDENTIALS_FILE_PATH=./config/google-credentials.json
```

### Этап 8: Интеграция с существующими событиями

#### 8.1 Обновление OrderService.java
```java
// Добавить в метод updateOrderStatus()
@Transactional
public OrderDTO updateOrderStatus(Integer orderId, Integer statusId, Integer adminUserId) {
    // ... существующий код ...
    
    String oldStatusName = order.getStatus().getName();
    
    // ... обновление статуса ...
    
    String newStatusName = newStatus.getName();
    
    // Публикация события изменения статуса
    eventPublisher.publishEvent(new OrderStatusChangedEvent(this, orderId, oldStatusName, newStatusName));
    
    // ... остальной код ...
}
```

#### 8.2 Обновление YooKassaPaymentService.java
```java
// Добавить в метод updatePaymentFromYooKassaResponse()
private void updatePaymentFromYooKassaResponse(Payment payment, JsonNode response) {
    PaymentStatus oldStatus = payment.getStatus();
    
    // ... существующий код обновления ...
    
    PaymentStatus newStatus = payment.getStatus();
    
    // Публикация события изменения статуса платежа
    if (oldStatus != newStatus) {
        eventPublisher.publishEvent(new PaymentStatusChangedEvent(
            this, 
            payment.getOrder().getId(), 
            oldStatus, 
            newStatus
        ));
    }
}
```

### Этап 9: Тестирование

#### 9.1 Создание тестового скрипта
```bash
#!/bin/bash
# test_google_sheets_integration.sh

echo "🧪 Тестирование интеграции Google Sheets API"

# 1. Проверка конфигурации
echo "📋 Проверка конфигурации..."
curl -X GET "http://localhost:8080/actuator/health" -H "Accept: application/json"

# 2. Создание тестового заказа
echo "🛍️ Создание тестового заказа..."
ORDER_RESPONSE=$(curl -s -X POST "http://localhost:8080/api/v1/orders" \
  -H "Content-Type: application/json" \
  -H "Authorization: Bearer $USER_TOKEN" \
  -d '{
    "contactName": "Google Sheets Тест",
    "contactPhone": "+79999999999",
    "deliveryLocationId": 1,
    "comment": "Тестовый заказ для Google Sheets",
    "paymentMethod": "CASH"
  }')

ORDER_ID=$(echo $ORDER_RESPONSE | jq -r '.id')
echo "✅ Создан заказ #$ORDER_ID"

# 3. Проверка добавления в Google Sheets
echo "📊 Ожидание обновления Google Sheets..."
sleep 5

# 4. Изменение статуса заказа
echo "🔄 Изменение статуса заказа..."
curl -X PUT "http://localhost:8080/api/v1/admin/orders/$ORDER_ID/status" \
  -H "Content-Type: application/json" \
  -H "Authorization: Bearer $ADMIN_TOKEN" \
  -d '{"statusId": 2}'

echo "✅ Тестирование завершено. Проверьте Google таблицу: https://docs.google.com/spreadsheets/d/$GOOGLE_SHEETS_SPREADSHEET_ID"
```

### Этап 10: Мониторинг и логирование

#### 10.1 Добавление метрик (опционально)
```java
@Component
@ConditionalOnProperty(name = "google.sheets.enabled", havingValue = "true")
public class GoogleSheetsMetrics {
    
    private final Counter ordersAddedCounter;
    private final Counter updateFailuresCounter;
    private final Timer apiCallTimer;
    
    public GoogleSheetsMetrics(MeterRegistry meterRegistry) {
        this.ordersAddedCounter = Counter.builder("google_sheets_orders_added_total")
                .description("Total number of orders added to Google Sheets")
                .register(meterRegistry);
                
        this.updateFailuresCounter = Counter.builder("google_sheets_update_failures_total")
                .description("Total number of Google Sheets update failures")
                .register(meterRegistry);
                
        this.apiCallTimer = Timer.builder("google_sheets_api_call_duration")
                .description("Duration of Google Sheets API calls")
                .register(meterRegistry);
    }
}
```

---

## 🔧 Инструкции по настройке

### 1. Получение ID Google таблицы
```
URL таблицы: https://docs.google.com/spreadsheets/d/1ABC123def456GHI789jkl_your_id/edit
Spreadsheet ID: 1ABC123def456GHI789jkl_your_id
```

### 2. Настройка прав доступа
1. Откройте Google таблицу
2. Нажмите "Настройки доступа"
3. Добавьте email Service Account с правами "Редактор"
4. Убедитесь, что таблица доступна для редактирования

### 3. Проверка работы
1. Запустите приложение
2. Создайте тестовый заказ
3. Проверьте появление записи в Google таблице
4. Измените статус заказа и проверьте обновление

---

## 📊 Результат

После реализации интеграции:

✅ **Автоматическое добавление**: Все новые заказы мгновенно попадают в Google таблицу  
✅ **Реальное время**: Обновления статусов заказов и платежей отражаются в таблице  
✅ **Структурированные данные**: Все важные поля заказа отображаются в удобном формате  
✅ **Надежность**: Retry механизм обеспечивает доставку данных даже при сбоях  
✅ **Масштабируемость**: Асинхронная обработка не влияет на производительность  
✅ **Аналитика**: Возможность создания отчетов и графиков в Google Sheets