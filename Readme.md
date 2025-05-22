# PizzaNat - Сервис для заказа пиццы

Микросервис для заказа пиццы на Java 17 с использованием Spring Boot.

## Требования

- Java 17+
- PostgreSQL
- Redis
- MinIO (для хранения изображений)
- Docker и Docker Compose

## Запуск приложения

### 1. Запуск инфраструктуры

Запустите необходимые сервисы с помощью Docker Compose:

```bash
docker compose up -d
```

Это запустит:
- PostgreSQL (порт 5432)
- Redis (порт 6379)
- MinIO (порты 9000, 9001)
- MinIO Initializer (создаст необходимый бакет)

### 2. Запуск приложения

#### Вариант 1: Запуск через Gradle

```bash
./gradlew bootRun
```

#### Вариант 2: Запуск с профилем разработки

```bash
./gradlew bootRun --args='--spring.profiles.active=dev'
```

#### Вариант 3: Запуск JAR-файла

```bash
./gradlew build
java -jar build/libs/PizzaNat-0.0.1-SNAPSHOT.jar
```

## Тестирование API

### Проверка доступности

```bash
curl http://localhost:8080/
```

### Отладочные эндпоинты

```bash
curl http://localhost:8080/debug/status
```

### Регистрация нового пользователя

```bash
curl -X POST -H "Content-Type: application/json" -d '{
  "username": "test",
  "password": "test123",
  "email": "test@example.com",
  "firstName": "Test",
  "lastName": "User",
  "phone": "+79001234567"
}' http://localhost:8080/api/v1/auth/register
```

### Аутентификация

```bash
curl -X POST -H "Content-Type: application/json" -d '{
  "username": "test",
  "password": "test123"
}' http://localhost:8080/api/v1/auth/login
```

Ответ будет содержать JWT-токен, который нужно использовать для авторизованных запросов.

### Доступ к защищенным ресурсам

```bash
curl -H "Authorization: Bearer YOUR_JWT_TOKEN" http://localhost:8080/api/v1/products
```

## Архитектура

Приложение использует:
- Spring Boot как основной фреймворк
- Spring Security для аутентификации и авторизации
- Spring Data JPA для работы с базой данных
- Flyway для миграций
- Redis для кэширования
- MinIO для хранения изображений

## Решение проблем

### Проблема с подключением к базе данных

Если приложение не может подключиться к базе данных, проверьте:
1. Запущен ли контейнер PostgreSQL
2. Правильный ли URL подключения в application.properties
3. Соответствуют ли учетные данные

### Проблема с авторизацией

Если получаете ошибку 403 при попытке авторизации:
1. Убедитесь, что используете правильные учетные данные
2. Проверьте, что JWT-токен корректный и не просрочен
3. Включите отладочное логирование для Spring Security

# PizzaNat API - Документация

## Общая информация
- Базовый URL: `http://localhost:8080`
- Формат ответов: JSON
- Аутентификация: JWT токен в заголовке Authorization (Bearer)

## Авторизация
- Все запросы к защищенным эндпоинтам требуют JWT токена
- Токен передается в заголовке запроса: `Authorization: Bearer <token>`
- Для получения токена используйте   эндпоинт `/api/v1/auth/login`

## Пример интеграции с мобильным приложением

### 1. Авторизация пользователя
POST /api/v1/auth/login
Content-Type: application/json
'''
{
"username": "user@example.com",
"password": "password"
}
'''

Ответ:
{
"token": "eyJhbGciOiJIUzI1NiJ9...",
"type": "Bearer",
"expiresIn": 86400000
}
### 2. Получение категорий
GET /api/v1/categories
Authorization: Bearer <token>

### 3. Получение продуктов по категории
GET /api/v1/products/category/1?page=0&size=10
Authorization: Bearer <token>

### 4. Добавление товара в корзину
POST /api/v1/cart/items
Authorization: Bearer <token>
Content-Type: application/json
{
"productId": 1,
"quantity": 2
}
### 5. Оформление заказа
POST /api/v1/orders
Authorization: Bearer <token>
Content-Type: application/json
{
"deliveryLocationId": 1,
"paymentMethod": "CASH"
}

## Полная документация API
Полная интерактивная документация доступна через Swagger UI:
http://localhost:8080/swagger-ui.html
http://localhost:8080/swagger-ui/swagger-ui/index.html






   # Аутентификация
   POST /api/v1/auth/register - Регистрация нового пользователя
   POST /api/v1/auth/login - Авторизация пользователя

   # Категории
   GET /api/v1/categories - Список всех категорий
   GET /api/v1/categories/{id} - Получение категории по ID

   # Продукты
   GET /api/v1/products - Список всех продуктов
   GET /api/v1/products/{id} - Получение продукта по ID
   GET /api/v1/products/category/{categoryId} - Список продуктов по категории
   GET /api/v1/products/special-offers - Список специальных предложений
   GET /api/v1/products/search?query=текст - Поиск продуктов

   # Корзина
   GET /api/v1/cart - Получение корзины
   POST /api/v1/cart/items - Добавление товара в корзину
   PUT /api/v1/cart/items/{productId} - Изменение количества товара
   DELETE /api/v1/cart/items/{productId} - Удаление товара из корзины

   # Заказы
   POST /api/v1/orders - Создание заказа
   GET /api/v1/orders - Список заказов пользователя
   GET /api/v1/orders/{id} - Получение заказа по ID

   # Пункты выдачи
   GET /api/v1/delivery-locations - Список пунктов выдачи