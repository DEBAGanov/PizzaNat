# ЮKassa СБП Интеграция - Руководство по внедрению

## Обзор интеграции

**Цель**: Интеграция оплаты через СБП (Система Быстрых Платежей) с использованием ЮKassa API для мобильного приложения и будущего веб-сайта PizzaNat.

**Особенности**:
- ✅ Оплата через СБП с выбором банка (как на скриншоте)
- ✅ Возможность отключения через docker-compose.yml
- ✅ Тестовая и продакшн среды
- ✅ Полная интеграция с существующей системой заказов
- ❌ Без использования Робокассы

## 1. Архитектура интеграции

### 1.1 Компоненты системы
```
┌─────────────────┐    ┌──────────────────┐    ┌─────────────────┐
│  Mobile App     │    │   PizzaNat API   │    │   ЮKassa API    │
│                 │    │                  │    │                 │
│ ┌─────────────┐ │    │ ┌──────────────┐ │    │ ┌─────────────┐ │
│ │ Корзина     │ │◄──►│ │ PaymentService│ │◄──►│ │ СБП Gateway │ │
│ └─────────────┘ │    │ └──────────────┘ │    │ └─────────────┘ │
│ ┌─────────────┐ │    │ ┌──────────────┐ │    │ ┌─────────────┐ │
│ │ СБП выбор   │ │    │ │ OrderService │ │    │ │ Webhook     │ │
│ │ банка       │ │    │ │              │ │    │ │ Handler     │ │
│ └─────────────┘ │    │ └──────────────┘ │    │ └─────────────┘ │
└─────────────────┘    └──────────────────┘    └─────────────────┘
```

### 1.2 Поток оплаты СБП
1. **Создание заказа** - пользователь оформляет заказ в корзине
2. **Выбор СБП** - пользователь выбирает "СБП" как способ оплаты
3. **Выбор банка** - отображается список банков (Сбербанк, Т-Банк, ВТБ и др.)
4. **Создание платежа** - API создает платеж в ЮKassa с методом `sbp`
5. **Перенаправление** - пользователь перенаправляется в приложение банка
6. **Подтверждение** - webhook получает уведомление об оплате
7. **Обновление заказа** - статус заказа обновляется на PAID

## 2. Настройка ЮKassa

### 2.1 Получение учетных данных
1. Зарегистрируйтесь на https://yookassa.ru/
2. Получите:
   - **Shop ID** (идентификатор магазина)
   - **Secret Key** (секретный ключ)
3. Настройте webhook URL: `https://debaganov-pizzanat-0177.twc1.net/api/v1/payments/yookassa/webhook`

### 2.2 Тестовые данные
```bash
# Тестовые учетные данные ЮKassa
YOOKASSA_SHOP_ID: "test_shop_id"
YOOKASSA_SECRET_KEY: "test_secret_key"
YOOKASSA_API_URL: "https://api.yookassa.ru/v3"
```

## 3. Реализация Backend

### 3.1 Зависимости (build.gradle)
```gradle
dependencies {
    // HTTP клиент для API запросов
    implementation 'org.springframework.boot:spring-boot-starter-webflux'

    // JSON обработка
    implementation 'com.fasterxml.jackson.core:jackson-databind'

    // Валидация
    implementation 'org.springframework.boot:spring-boot-starter-validation'

    // Метрики
    implementation 'org.springframework.boot:spring-boot-starter-actuator'
    implementation 'io.micrometer:micrometer-registry-prometheus'
}
```

### 3.2 Конфигурация Docker Compose

Добавим в `docker-compose.yml` в секцию `environment`:

```yaml
# ЮKassa настройки
YOOKASSA_ENABLED: ${YOOKASSA_ENABLED:-false}
YOOKASSA_SHOP_ID: ${YOOKASSA_SHOP_ID:-}
YOOKASSA_SECRET_KEY: ${YOOKASSA_SECRET_KEY:-}
YOOKASSA_API_URL: ${YOOKASSA_API_URL:-https://api.yookassa.ru/v3}
YOOKASSA_WEBHOOK_URL: ${YOOKASSA_WEBHOOK_URL:-https://debaganov-pizzanat-0177.twc1.net/api/v1/payments/yookassa/webhook}
YOOKASSA_TIMEOUT_SECONDS: ${YOOKASSA_TIMEOUT_SECONDS:-30}
YOOKASSA_MAX_RETRY_ATTEMPTS: ${YOOKASSA_MAX_RETRY_ATTEMPTS:-3}

# СБП настройки
SBP_ENABLED: ${SBP_ENABLED:-true}
SBP_DEFAULT_RETURN_URL: ${SBP_DEFAULT_RETURN_URL:-pizzanat://payment/result}
```

## 4. Этапы реализации

### Этап 1: Базовая конфигурация
1. ✅ Создание инструкции
2. ⏳ Обновление docker-compose.yml
3. ⏳ Создание конфигурационного класса
4. ⏳ Добавление зависимостей в build.gradle

### Этап 2: Entity и DTO
1. ⏳ Создание таблицы payments
2. ⏳ Entity классы (Payment)
3. ⏳ DTO для запросов и ответов
4. ⏳ Enum для статусов платежей

### Этап 3: Service и Controller
1. ⏳ YooKassaService для работы с API
2. ⏳ PaymentController для REST API
3. ⏳ Webhook обработчик
4. ⏳ Список банков для СБП

### Этап 4: Тестирование
1. ⏳ Создание тестового скрипта
2. ⏳ Unit тесты
3. ⏳ Integration тесты
4. ⏳ Добавление в comprehensive скрипт

### Этап 5: Мониторинг и безопасность
1. ⏳ Метрики платежей
2. ⏳ Логирование операций
3. ⏳ Rate limiting
4. ⏳ Валидация webhook

## 5. Банки для СБП интеграции

Список банков, которые будут отображаться в мобильном приложении (как на скриншоте):

1. **Сбербанк** - `sberbank`
2. **Т-Банк** - `tinkoff`
3. **Банк ВТБ** - `vtb`
4. **АЛЬФА-БАНК** - `alfabank`
5. **Райффайзенбанк** - `raiffeisen`
6. **Газпромбанк** - `gazprombank`
7. **Банк ПСБ** - `psb`

## 6. API Endpoints

### 6.1 Получение списка банков
```http
GET /api/v1/payments/sbp/banks
```

### 6.2 Создание СБП платежа
```http
POST /api/v1/payments/sbp/create
Content-Type: application/json

{
    "orderId": 123,
    "method": "SBP",
    "bankId": "sberbank",
    "returnUrl": "pizzanat://payment/result"
}
```

### 6.3 Webhook для уведомлений
```http
POST /api/v1/payments/yookassa/webhook
```

### 6.4 Получение статуса платежа
```http
GET /api/v1/payments/order/{orderId}
```

## 7. Мобильная интеграция

Экран выбора банка будет выглядеть как на предоставленном скриншоте:
- Заголовок "Выберите банк для оплаты"
- Список банков с иконками
- Поиск по названию банка
- Популярные банки вверху списка

## 8. Следующие шаги

Готов приступить к реализации. Начнем с:

1. **Обновления docker-compose.yml** - добавим переменные окружения
2. **Создания миграции БД** - таблица payments
3. **Реализации базовых классов** - Config, Entity, DTO
4. **Тестирования** - создание тестового скрипта

Подтвердите готовность к началу реализации!