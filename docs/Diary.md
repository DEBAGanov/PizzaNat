# PizzaNat - Дневник наблюдений

## 2023-06-10 - Запуск проекта

### Наблюдения
- Стандартные Spring Security настройки требуют дополнительной конфигурации для JWT
- PostgreSQL требует правильной настройки схемы для эффективной работы
- Docker Compose упрощает локальную разработку, но требует тщательной настройки томов

### Проблемы и решения
- Проблема: Высокая связность сервисов
  - Решение: Внедрение паттерна Repository и Service для разделения ответственности
- Проблема: Сложность управления транзакциями
  - Решение: Использование @Transactional на уровне сервисов

## 2023-07-01 - Расширение функциональности

### Наблюдения
- REST API становится сложнее с ростом функциональности
- Оптимизация БД критична для высокой нагрузки
- Swagger UI удобен для тестирования API

### Проблемы и решения
- Проблема: N+1 запросы при загрузке связанных сущностей
  - Решение: Использование JPA EntityGraph и FetchType.LAZY
- Проблема: Долгое время ответа при большом количестве продуктов
  - Решение: Внедрение кэширования с помощью Redis

## 2023-08-05 - Оптимизация производительности

### Наблюдения
- Redis значительно ускоряет работу с часто запрашиваемыми данными
- Batch-операции более эффективны для массовых изменений

### Проблемы и решения
- Проблема: Устаревание данных в кэше
  - Решение: Реализация стратегии инвалидации кэша при изменении данных
- Проблема: Медленная загрузка изображений из S3
  - Решение: Использование пресетов и миниатюр для уменьшения объема данных

## 2023-09-20 - Повышение надежности

### Наблюдения
- Внешние сервисы могут быть нестабильны
- Необходим мониторинг для выявления узких мест

### Проблемы и решения
- Проблема: Отказы внешних сервисов вызывают каскадные ошибки
  - Решение: Использование таймаутов и повторных попыток
- Проблема: Сложно определить причину производительности
  - Решение: Внедрение метрик и логирования ключевых операций

## 2023-10-15 - Расширение административных возможностей

### Наблюдения
- Аналитика по заказам помогает оптимизировать бизнес-процессы
- Необходимы гибкие инструменты для администраторов

### Проблемы и решения
- Проблема: Сложность управления специальными предложениями
  - Решение: Создание отдельного модуля для промо-акций
- Проблема: Формирование сложных отчетов нагружает основную БД
  - Решение: Внедрение отдельной БД для аналитики и отчетов

## 2023-11-01 - Отказоустойчивость и интеграция с платежной системой

### Наблюдения
- Внешние платежные системы часто имеют непредсказуемое время ответа
- Circuit Breaker эффективно предотвращает каскадные сбои
- Настройка Resilience4j требует баланса между надежностью и производительностью

### Проблемы и решения
- Проблема: Таймауты при обращении к платежной системе в часы пик
  - Решение: Настройка динамических таймаутов и Retry с экспоненциальной задержкой
- Проблема: Недостаточное логирование при сбоях внешних сервисов
  - Решение: Внедрение подробного логирования всех взаимодействий с внешними API

## 2025-05-22 - Улучшение документации API

### Наблюдения
- Качественная документация API существенно упрощает интеграцию и тестирование
- Swagger UI с аутентификацией значительно улучшает процесс разработки
- Настройка OpenAPI требует дополнительной конфигурации для корректного отображения всех параметров

### Проблемы и решения
- Проблема: Неудобная авторизация в Swagger UI
  - Решение: Добавление специальной конфигурации для JWT аутентификации
- Проблема: Отсутствие примеров запросов и ответов
  - Решение: Создание конфигурационного класса OpenApiConfig с подробными примерами

## [2025-05-31] - Интеграция с Android приложением PizzaNatApp

### Наблюдения
- Проанализирован код Android приложения **PizzaNatApp** (https://github.com/DEBAGanov/PizzaNatApp)
- Изучены тесты интеграции в `test_android_integration.sh` для понимания требований
- Обнаружено, что поле `delivery_address` уже присутствует в entity Order, но не используется
- Android приложение отправляет дополнительные поля: `deliveryAddress`, `notes`
- Требуется автоматическое создание пунктов доставки из произвольных адресов

### Решения
1. **Расширение CreateOrderRequest**:
   - Добавлены поля `deliveryAddress` и `notes` для Android интеграции
   - Реализована гибкая валидация через метод `hasValidDeliveryInfo()`
   - Добавлен метод `getFinalComment()` для приоритета полей

2. **Автоматическое создание пунктов доставки**:
   - Создан метод `createDeliveryLocationFromAddress()` в OrderService
   - Автоматическое создание DeliveryLocation при указании deliveryAddress
   - Координаты по умолчанию: Москва (55.7558, 37.6173)

3. **Обновление DTO и маппинга**:
   - Добавлено поле `deliveryAddress` в OrderDTO
   - Обновлен метод `mapToDTO()` для включения Android поля

4. **Обратная совместимость**:
   - Сохранена полная совместимость с существующими клиентами
   - Приоритет существующих полей: `deliveryLocationId` > `deliveryAddress`

### Проблемы
1. **Ошибка запуска приложения**:
   - Проблема: `Failed to connect to minio/127.0.0.1:9000`
   - Причина: Отсутствие MinIO в конфигурации docker-compose.base.yml
   - Статус: Идентифицирована, требует исправления инфраструктуры

2. **Координаты по умолчанию**:
   - Текущее решение: Москва как значение по умолчанию
   - Потенциальное улучшение: Геокодирование адресов через внешние API

### Технические детали
- **Приоритет comment vs notes**: `comment` имеет высший приоритет
- **Автосоздание локаций**: Каждый уникальный deliveryAddress создает новый DeliveryLocation
- **Логирование**: Добавлено детальное логирование создания заказов и локаций
- **Валидация**: Требуется либо deliveryLocationId, либо deliveryAddress (не оба пустые)

## [2025-05-23] - Достижение 85% функциональности API - Исправление LazyInitializationException и улучшение корзины