# Полный список всех API эндпоинтов PizzaNat

## Системные эндпоинты (HomeController)
- `GET /` - Перенаправление на Swagger UI
- `GET /api/health` - Проверка состояния сервиса

## Аутентификация (AuthController)
**Базовый путь**: `/api/v1/auth`
- `GET /test` - Тест доступности API аутентификации
- `POST /register` - Регистрация нового пользователя
- `POST /login` - Аутентификация пользователя

## SMS Аутентификация (SmsAuthController)
**Базовый путь**: `/api/v1/auth/sms`
- `POST /send-code` - Отправка SMS кода
- `POST /verify-code` - Подтверждение SMS кода
- `GET /test` - Тест SMS сервиса

## Telegram Аутентификация (TelegramAuthController)
**Базовый путь**: `/api/v1/auth/telegram`
- `POST /init` - Инициализация Telegram аутентификации
- `GET /status/{authToken}` - Проверка статуса Telegram аутентификации
- `GET /test` - Тест Telegram сервиса

## Telegram Gateway (TelegramGatewayController)
**Базовый путь**: `/api/v1/auth/telegram-gateway`
- `POST /send-code` - Отправка кода через Telegram
- `POST /verify-code` - Подтверждение кода через Telegram
- `GET /status/{requestId}` - Проверка статуса запроса
- `DELETE /revoke/{requestId}` - Отмена запроса
- `GET /test` - Тест Telegram Gateway

## Telegram Webhook (TelegramWebhookController)
**Базовый путь**: `/api/v1/telegram`
- `POST /webhook` - Обработка Telegram webhook
- `GET /webhook/info` - Информация о webhook
- `POST /webhook/register` - Регистрация webhook
- `DELETE /webhook` - Удаление webhook

## Категории (CategoryController)
**Базовый путь**: `/api/v1/categories`
- `GET /` - Получение списка активных категорий
- `GET /{id}` - Получение категории по ID

## Продукты (ProductController)
**Базовый путь**: `/api/v1/products`
- `POST /` - Создание нового продукта (multipart/form-data)
- `GET /` - Получение всех продуктов (с пагинацией)
- `GET /{id}` - Получение продукта по ID
- `GET /category/{categoryId}` - Получение продуктов по категории
- `GET /special-offers` - Получение специальных предложений
- `GET /search` - Поиск продуктов

## Корзина (CartController)
**Базовый путь**: `/api/v1/cart`
- `GET /` - Получение корзины
- `POST /items` - Добавление товара в корзину
- `PUT /items/{productId}` - Обновление количества товара в корзине
- `DELETE /items/{productId}` - Удаление товара из корзины
- `DELETE /` - Очистка корзины
- `POST /merge` - Объединение анонимной корзины с корзиной пользователя 🔒

## Заказы (OrderController)
**Базовый путь**: `/api/v1/orders`
- `POST /` - Создание нового заказа 🔒
- `GET /{orderId}` - Получение заказа по ID 🔒
- `GET /{orderId}/payment-url` - Получение URL для оплаты заказа 🔒
- `GET /` - Получение заказов пользователя 🔒

## Локации доставки (DeliveryLocationController)
**Базовый путь**: `/api/v1/delivery-locations`
- `GET /` - Получение списка локаций доставки
- `GET /{id}` - Получение локации по ID

## Платежи (PaymentController)
**Базовый путь**: `/api/v1/payments`
- `POST /robokassa/notify` - Уведомления от Robokassa
- `GET /robokassa/success` - Успешная оплата Robokassa
- `GET /robokassa/fail` - Неуспешная оплата Robokassa

## Администрирование (AdminController)
**Базовый путь**: `/api/v1/admin` 🔒 **ADMIN**
- `GET /stats` - Получение статистики для админ панели
- `POST /upload` - Загрузка изображения

## Админ - Продукты (AdminProductController)
**Базовый путь**: `/api/v1/admin/products` 🔒 **ADMIN**
- `POST /` - Создание продукта
- `PUT /{productId}` - Обновление продукта
- `DELETE /{productId}` - Удаление продукта
- `GET /{productId}` - Получение продукта для редактирования

## Админ - Заказы (AdminOrderController)
**Базовый путь**: `/api/v1/admin/orders` 🔒 **ADMIN**
- `GET /` - Получение всех заказов (с пагинацией)
- `GET /{orderId}` - Получение заказа по ID
- `PUT /{orderId}/status` - Обновление статуса заказа

## Отладка (DebugController)
**Базовый путь**: `/debug`
- `GET /status` - Статус отладки
- `GET /auth` - Информация об аутентификации

---

## Условные обозначения:
- 🔒 - Требует аутентификации (JWT токен)
- **ADMIN** - Требует роль администратора
- Все эндпоинты возвращают JSON, кроме redirect'ов

## Итого эндпоинтов: 43

### Публичные эндпоинты: 18
### Требующие аутентификации: 15
### Администраторские: 10

**Swagger UI**: http://localhost:8080/swagger-ui.html
**OpenAPI Schema**: http://localhost:8080/v3/api-docs