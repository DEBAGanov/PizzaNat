# Руководство по мониторингу ЮKassa интеграции

## Обзор

Система мониторинга ЮKassa интеграции обеспечивает полный контроль над платежной системой с помощью метрик, алертов и дашбордов.

## Компоненты системы мониторинга

### 1. Сбор метрик
- **PaymentMetricsService** - основной сервис сбора метрик
- **MetricsConfig** - конфигурация Micrometer метрик
- **Spring Boot Actuator** - встроенные метрики системы

### 2. Система алертов
- **PaymentAlertService** - мониторинг критических событий
- **Telegram уведомления** - отправка алертов администраторам
- **Пороговые значения** - настраиваемые лимиты для алертов

### 3. API мониторинга
- **PaymentMetricsController** - REST API для получения метрик
- **Prometheus endpoint** - интеграция с системами мониторинга
- **Health checks** - проверка работоспособности

## Основные метрики

### Счетчики платежей
```
yookassa_payments_total - Общее количество платежей
yookassa_payments_success - Успешные платежи
yookassa_payments_failure - Неудачные платежи
yookassa_payments_cancelled - Отмененные платежи
yookassa_payments_sbp - Платежи через СБП
yookassa_payments_card - Карточные платежи
```

### Webhook метрики
```
yookassa_webhook_received - Полученные webhook уведомления
yookassa_webhook_processed - Обработанные webhook
yookassa_webhook_failed - Ошибки обработки webhook
```

### Временные метрики
```
yookassa_payments_creation_time - Время создания платежа
yookassa_webhook_processing_time - Время обработки webhook
yookassa_payments_completion_time - Время завершения платежа
```

### Аналитические метрики
```
yookassa_payments_conversion_rate - Коэффициент конверсии (%)
yookassa_payments_average_amount - Средний размер платежа
yookassa_payments_last_hour_total - Платежи за последний час
yookassa_payments_daily_by_status - Платежи по статусам за день
```

## API Endpoints

### Основные endpoint'ы мониторинга

#### 1. Health Check
```http
GET /api/v1/payments/metrics/health
```
Проверка работоспособности системы метрик.

**Ответ:**
```json
{
  "status": "UP",
  "service": "payment-metrics",
  "timestamp": "2025-01-26T15:30:00",
  "version": "1.0.0"
}
```

#### 2. Сводка метрик
```http
GET /api/v1/payments/metrics/summary
Authorization: Bearer <admin-token>
```
Основные метрики платежной системы за 24 часа.

**Ответ:**
```json
{
  "totalPayments": 150,
  "successfulPayments": 142,
  "failedPayments": 8,
  "conversionRate": 94.67,
  "totalAmount": 285000.00
}
```

#### 3. Детальные метрики
```http
GET /api/v1/payments/metrics/details
Authorization: Bearer <admin-token>
```
Расширенная информация о состоянии системы.

#### 4. Конфигурация мониторинга
```http
GET /api/v1/payments/metrics/config
Authorization: Bearer <admin-token>
```
Текущие настройки системы мониторинга.

#### 5. Обновление метрик
```http
POST /api/v1/payments/metrics/refresh
Authorization: Bearer <admin-token>
```
Принудительное обновление агрегированных метрик.

### Prometheus метрики
```http
GET /actuator/prometheus
```
Endpoint для интеграции с Prometheus/Grafana.

## Система алертов

### Типы алертов

#### 1. Низкая конверсия
- **Порог:** < 70%
- **Условие:** Минимум 5 платежей для анализа
- **Кулдаун:** 30 минут между алертами

#### 2. Высокий уровень ошибок
- **Порог:** > 10%
- **Условие:** Анализ за последние 24 часа
- **Действие:** Уведомление в Telegram

#### 3. Крупные платежи
- **Создание:** > 10,000₽
- **Успешный:** > 15,000₽
- **Неудача:** > 5,000₽

#### 4. Зависшие платежи
- **Порог:** > 30 минут в статусе PENDING
- **Действие:** Автоматическая проверка статуса

#### 5. Системные ошибки
- **Условие:** Ошибки в PaymentMetricsService
- **Действие:** Немедленное уведомление

### Настройка алертов

В `application.properties`:
```properties
# Пороговые значения для алертов
yookassa.alerts.enabled=true
yookassa.alerts.low-conversion-threshold=70.0
yookassa.alerts.high-failure-threshold=10.0
yookassa.alerts.max-pending-minutes=30
yookassa.alerts.cooldown-minutes=30
yookassa.alerts.min-payments-for-analysis=5
```

## Настройка Grafana Dashboard

### 1. Подключение к Prometheus
```yaml
# prometheus.yml
scrape_configs:
  - job_name: 'pizzanat-yookassa'
    static_configs:
      - targets: ['localhost:8080']
    metrics_path: '/actuator/prometheus'
    scrape_interval: 15s
```

### 2. Основные панели

#### Панель "Обзор платежей"
```promql
# Общее количество платежей
sum(yookassa_payments_total)

# Успешные платежи
sum(yookassa_payments_success)

# Конверсия
(sum(yookassa_payments_success) / sum(yookassa_payments_total)) * 100
```

#### Панель "Производительность"
```promql
# Среднее время создания платежа
histogram_quantile(0.95, yookassa_payments_creation_time_bucket)

# Время обработки webhook
histogram_quantile(0.95, yookassa_webhook_processing_time_bucket)
```

#### Панель "Ошибки"
```promql
# Уровень ошибок
(sum(yookassa_payments_failure) / sum(yookassa_payments_total)) * 100

# Ошибки webhook
sum(yookassa_webhook_failed)
```

### 3. Алерты в Grafana
```yaml
# Низкая конверсия
- alert: YookassaLowConversion
  expr: (sum(yookassa_payments_success) / sum(yookassa_payments_total)) * 100 < 70
  for: 5m
  labels:
    severity: warning
  annotations:
    summary: "Низкая конверсия платежей ЮKassa"

# Высокий уровень ошибок
- alert: YookassaHighFailureRate
  expr: (sum(yookassa_payments_failure) / sum(yookassa_payments_total)) * 100 > 10
  for: 5m
  labels:
    severity: critical
  annotations:
    summary: "Высокий уровень ошибок ЮKassa"
```

## Тестирование системы мониторинга

### 1. Автоматическое тестирование
```bash
# Запуск тестов мониторинга
./test_yookassa_monitoring.sh
```

### 2. Ручная проверка метрик
```bash
# Проверка health endpoint
curl http://localhost:8080/api/v1/payments/metrics/health

# Получение Prometheus метрик
curl http://localhost:8080/actuator/prometheus | grep yookassa
```

### 3. Проверка алертов
```bash
# Симуляция неудачного платежа (для тестирования)
curl -X POST http://localhost:8080/api/v1/payments \
  -H "Content-Type: application/json" \
  -d '{"orderId": 999, "amount": 6000, "method": "SBP"}'
```

## Оптимизация производительности

### 1. Настройки базы данных
```properties
# Batch обработка
spring.jpa.properties.hibernate.jdbc.batch_size=20
spring.jpa.properties.hibernate.order_inserts=true
spring.jpa.properties.hibernate.order_updates=true

# Пул соединений
spring.datasource.hikari.maximum-pool-size=10
spring.datasource.hikari.minimum-idle=5
```

### 2. Кэширование метрик
```java
@Cacheable(value = "payment-metrics", key = "#root.methodName")
public PaymentMetricsSummary getMetricsSummary() {
    // Кэширование на 5 минут
}
```

### 3. Асинхронная обработка
```properties
# Настройки пула потоков
spring.task.execution.pool.core-size=8
spring.task.execution.pool.max-size=16
spring.task.execution.pool.queue-capacity=100
```

### 4. Оптимизация запросов
```java
// Использование batch запросов
@Query("SELECT p FROM Payment p WHERE p.createdAt BETWEEN :start AND :end")
List<Payment> findByCreatedAtBetween(LocalDateTime start, LocalDateTime end);
```

## Логирование и диагностика

### 1. Настройки логирования
```properties
# Детальное логирование метрик
logging.level.com.baganov.pizzanat.service.PaymentMetricsService=DEBUG
logging.level.com.baganov.pizzanat.service.PaymentAlertService=INFO
```

### 2. Структурированные логи
```java
log.info("📊 Метрика обновлена: тип={}, значение={}, время={}", 
         metricType, value, timestamp);
```

### 3. Диагностические команды
```bash
# Проверка логов приложения
docker logs pizzanat-app | grep "PaymentMetrics"

# Проверка метрик в реальном времени
watch -n 5 'curl -s http://localhost:8080/actuator/metrics/yookassa.payments.total'
```

## Безопасность мониторинга

### 1. Аутентификация
- Все admin endpoint'ы требуют JWT токен с ролью ADMIN
- Health endpoint доступен без аутентификации

### 2. Ограничение доступа
```properties
# Ограничение Actuator endpoints
management.endpoints.web.exposure.include=health,prometheus,metrics
management.endpoint.health.show-details=when-authorized
```

### 3. Маскировка чувствительных данных
```java
// Логирование без чувствительных данных
log.info("Платеж создан: ID={}, сумма={}₽", payment.getId(), payment.getAmount());
// НЕ логируем: номера карт, токены, персональные данные
```

## Производственное развертывание

### 1. Переменные окружения
```bash
# Включение мониторинга
export YOOKASSA_METRICS_ENABLED=true
export YOOKASSA_ALERTS_ENABLED=true

# Настройка алертов
export YOOKASSA_ALERTS_LOW_CONVERSION=75.0
export YOOKASSA_ALERTS_HIGH_FAILURE=5.0
```

### 2. Docker Compose
```yaml
services:
  pizzanat-app:
    environment:
      - YOOKASSA_METRICS_ENABLED=true
      - YOOKASSA_ALERTS_ENABLED=true
    ports:
      - "8080:8080"
```

### 3. Мониторинг инфраструктуры
```yaml
# docker-compose.monitoring.yml
version: '3.8'
services:
  prometheus:
    image: prom/prometheus
    ports:
      - "9090:9090"
    volumes:
      - ./prometheus.yml:/etc/prometheus/prometheus.yml
  
  grafana:
    image: grafana/grafana
    ports:
      - "3000:3000"
    environment:
      - GF_SECURITY_ADMIN_PASSWORD=admin
```

## Поиск и устранение неисправностей

### Частые проблемы

#### 1. Метрики не обновляются
```bash
# Проверка статуса сервиса
curl http://localhost:8080/api/v1/payments/metrics/health

# Проверка логов
docker logs pizzanat-app | grep "MetricsService"

# Ручное обновление
curl -X POST http://localhost:8080/api/v1/payments/metrics/refresh \
  -H "Authorization: Bearer <token>"
```

#### 2. Алерты не отправляются
```bash
# Проверка Telegram бота
curl http://localhost:8080/actuator/health

# Проверка настроек алертов
curl http://localhost:8080/api/v1/payments/metrics/config
```

#### 3. Высокая нагрузка на БД
```sql
-- Проверка медленных запросов
SELECT query, mean_time, calls 
FROM pg_stat_statements 
WHERE query LIKE '%Payment%' 
ORDER BY mean_time DESC LIMIT 10;
```

### Диагностические команды
```bash
# Полная диагностика системы
./test_yookassa_monitoring.sh

# Проверка производительности
curl -w "@curl-format.txt" -s -o /dev/null \
  http://localhost:8080/api/v1/payments/metrics/summary
```

## Контакты и поддержка

При возникновении проблем с мониторингом:

1. Проверьте логи приложения
2. Запустите диагностический скрипт
3. Проверьте конфигурацию в `/api/v1/payments/metrics/config`
4. Обратитесь к документации ЮKassa API

**Дата создания:** 26.01.2025  
**Версия:** 1.0.0  
**Автор:** AI Assistant 