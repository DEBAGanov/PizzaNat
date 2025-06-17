# SMS Авторизация через Exolve API - Руководство по настройке

## Обзор

PizzaNat поддерживает SMS авторизацию пользователей через [Exolve API](https://docs.exolve.ru/docs/ru/instructions/sending-sms/). Пользователи могут получать 4-значные коды подтверждения на свой номер телефона для входа в приложение.

## Архитектура

### Компоненты системы
- **ExolveService** - интеграция с Exolve SMS API
- **SmsAuthService** - бизнес-логика SMS авторизации 
- **SmsAuthController** - REST API эндпоинты
- **PhoneNumberValidator** - валидация российских номеров
- **SmsCodeGenerator** - генерация 4-значных кодов
- **База данных** - таблица `sms_codes` для хранения кодов

### API эндпоинты
- `POST /api/v1/auth/sms/send-code` - отправка SMS кода
- `POST /api/v1/auth/sms/verify-code` - проверка SMS кода
- `GET /api/v1/auth/sms/test` - проверка доступности сервиса

## Настройка Exolve API

### 1. Получение доступов
1. Зарегистрируйтесь на [Exolve](https://exolve.ru/)
2. Получите API токен и номер отправителя
3. Изучите [документацию API](https://docs.exolve.ru/docs/ru/instructions/sending-sms/)

### 2. Конфигурация переменных окружения

В `docker-compose.yml` добавьте/обновите следующие переменные:

```yaml
environment:
  # Exolve SMS API настройки
  EXOLVE_API_KEY: ${EXOLVE_API_KEY:-your_exolve_token_here}
  EXOLVE_SENDER_NAME: ${EXOLVE_SENDER_NAME:-your_sender_number}
  EXOLVE_API_URL: ${EXOLVE_API_URL:-https://api.exolve.ru/messaging/v1}
  EXOLVE_TIMEOUT_SECONDS: ${EXOLVE_TIMEOUT_SECONDS:-10}
  EXOLVE_RETRY_MAX_ATTEMPTS: ${EXOLVE_RETRY_MAX_ATTEMPTS:-3}

  # SMS Settings
  SMS_CODE_LENGTH: ${SMS_CODE_LENGTH:-4}
  SMS_CODE_TTL_MINUTES: ${SMS_CODE_TTL_MINUTES:-10}
  SMS_RATE_LIMIT_PER_HOUR: ${SMS_RATE_LIMIT_PER_HOUR:-3}
  SMS_MAX_ATTEMPTS: ${SMS_MAX_ATTEMPTS:-3}
```

### 3. Конфигурация application.properties

Убедитесь, что в `src/main/resources/application.properties` есть:

```properties
# Exolve SMS API
exolve.api.url=${EXOLVE_API_URL:https://api.exolve.ru/messaging/v1}/SendSMS
exolve.api.key=${EXOLVE_API_KEY:}
exolve.sender.name=${EXOLVE_SENDER_NAME:PizzaNat}
exolve.timeout.seconds=${EXOLVE_TIMEOUT_SECONDS:10}
exolve.retry.max-attempts=${EXOLVE_RETRY_MAX_ATTEMPTS:3}
exolve.circuit-breaker.failure-threshold=5

# SMS Settings
sms.code.length=${SMS_CODE_LENGTH:4}
sms.code.ttl.minutes=${SMS_CODE_TTL_MINUTES:10}
sms.rate.limit.per.hour=${SMS_RATE_LIMIT_PER_HOUR:3}
sms.max.attempts=${SMS_MAX_ATTEMPTS:3}
```

## Тестирование

### Автоматический тест

Используйте тестовый скрипт для проверки функциональности:

```bash
chmod +x scripts/test_exolve_sms_auth.sh
./scripts/test_exolve_sms_auth.sh
```

Скрипт проверит:
1. ✅ Доступность сервера
2. ✅ Доступность SMS API эндпоинта
3. ✅ Отправку SMS кода
4. ✅ Интерактивную верификацию кода
5. ✅ Авторизованный запрос с JWT токеном
6. ✅ Прямое тестирование Exolve API

### Ручное тестирование

#### 1. Отправка SMS кода
```bash
curl -X POST "http://localhost:8080/api/v1/auth/sms/send-code" \
  -H "Content-Type: application/json" \
  -d '{"phoneNumber": "+79061382868"}'
```

**Успешный ответ:**
```json
{
  "success": true,
  "message": "SMS код отправлен",
  "expiresAt": "2025-06-17T14:29:27.955704043",
  "codeLength": 4,
  "maskedPhoneNumber": "+7 (906) ***-**-68"
}
```

#### 2. Верификация кода
```bash
curl -X POST "http://localhost:8080/api/v1/auth/sms/verify-code" \
  -H "Content-Type: application/json" \
  -d '{"phoneNumber": "+79061382868", "code": "1234"}'
```

**Успешный ответ:**
```json
{
  "token": "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9...",
  "userId": 123,
  "username": "user79061382868",
  "phoneNumber": "+79061382868"
}
```

## Особенности Exolve API

### Формат номеров телефонов
- **Входной формат**: `+79061382868` или `79061382868`
- **Формат для Exolve API**: `79061382868` (без символа `+`)
- **Поддерживаются только российские номера**

### Ответы API
- **При успешной отправке**: `{"message_id": "571497026622089119"}`
- **При ошибке**: `{"error": {"message": "...", "details": "..."}}`

### Ограничения
- **Rate limiting**: 3 SMS в час на номер (настраивается)
- **Время жизни кода**: 10 минут (настраивается)
- **Максимум попыток ввода**: 3 (настраивается)

## Безопасность

### Rate Limiting
Система автоматически ограничивает:
- Количество отправок SMS на номер
- Количество попыток ввода кода
- Блокировка при превышении лимитов

### Валидация
- Проверка формата российских номеров телефонов
- Валидация 4-значных кодов
- Проверка времени истечения кодов

### Логирование
- Все номера маскируются в логах: `+7 (906) ***-**-68`
- Коды не сохраняются в открытом виде в логах
- Детальное логирование для диагностики

## Мониторинг

### Логи приложения
```bash
# Просмотр SMS-related логов
docker compose logs app | grep -i "SMS\|Exolve"

# Мониторинг ошибок
docker compose logs app | grep -i "ERROR\|Exception"
```

### Проверка здоровья
```bash
# Проверка SMS сервиса
curl http://localhost:8080/api/v1/auth/sms/test

# Проверка здоровья приложения
curl http://localhost:8080/actuator/health
```

## Troubleshooting

### Частые проблемы

#### 1. SMS не отправляется
```bash
# Проверка конфигурации
curl -s http://localhost:8080/api/v1/auth/sms/test

# Проверка логов
docker compose logs app --tail=50 | grep -i exolve
```

#### 2. Неправильный формат номера
**Ошибка**: `"invalid SendRequest.Destination: value does not match regex pattern"`

**Решение**: Убедитесь, что номер в формате `79XXXXXXXXX`

#### 3. Проблемы JSON сериализации
**Ошибка**: `LocalDateTime not supported`

**Решение**: Проверьте JacksonConfig и используйте String вместо LocalDateTime

### Диагностические команды

```bash
# Прямой тест Exolve API
curl -X POST "https://api.exolve.ru/messaging/v1/SendSMS" \
  -H "Authorization: Bearer YOUR_TOKEN" \
  -H "Content-Type: application/json" \
  -d '{"number": "79304410750", "destination": "79061382868", "text": "Test SMS"}'

# Проверка переменных окружения
docker compose exec app env | grep EXOLVE

# Проверка базы данных SMS кодов
docker compose exec app curl http://localhost:8080/actuator/health
```

## Конфигурация для Production

### Environment файл (.env)
```bash
# .env file for production
EXOLVE_API_KEY=your_production_token_here
EXOLVE_SENDER_NAME=79304410750
EXOLVE_API_URL=https://api.exolve.ru/messaging/v1

# Rate limiting for production
SMS_RATE_LIMIT_PER_HOUR=5
SMS_CODE_TTL_MINUTES=10
SMS_MAX_ATTEMPTS=3
```

### Мониторинг в Production
- Настройте алерты на ошибки Exolve API
- Мониторинг rate limiting превышений
- Отслеживание успешности доставки SMS

## API Reference

### Exolve API Documentation
- [Официальная документация](https://docs.exolve.ru/docs/ru/instructions/sending-sms/)
- [API Reference](https://api.exolve.ru/messaging/v1)

### PizzaNat SMS API
Полную документацию API можно найти в `docs/postman_testing_guide.md` в разделе SMS Authentication. 