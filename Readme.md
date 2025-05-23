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

### 🌐 **Основные URL для доступа к документации:**
- **Swagger UI**: http://localhost/swagger-ui/index.html
- **Альтернативный доступ**: http://localhost/ (автоматическое перенаправление)
- **OpenAPI спецификация**: http://localhost/v3/api-docs
- **Справка по API**: http://localhost/api-help
- **Health check**: http://localhost/api/health

### 📚 **Группы API в Swagger UI:**
В Swagger UI доступны следующие группы эндпоинтов:
- **Все API** - Полная документация всех endpoint'ов
- **Публичное API** - Регистрация и аутентификация
- **Клиентское API** - Корзина, заказы, пользователи
- **Административное API** - Управление заказами и системой

### 🔧 **Основные эндпоинты:**

   **Аутентификация**
   - `POST /api/v1/auth/register` - Регистрация нового пользователя
   - `POST /api/v1/auth/login` - Авторизация пользователя

   **Категории**
   - `GET /api/v1/categories` - Список всех категорий
   - `GET /api/v1/categories/{id}` - Получение категории по ID

   **Продукты**
   - `GET /api/v1/products` - Список всех продуктов
   - `GET /api/v1/products/{id}` - Получение продукта по ID
   - `GET /api/v1/products/category/{categoryId}` - Список продуктов по категории
   - `GET /api/v1/products/special-offers` - Список специальных предложений
   - `GET /api/v1/products/search?query=текст` - Поиск продуктов

   **Корзина**
   - `GET /api/v1/cart` - Получение корзины
   - `POST /api/v1/cart/items` - Добавление товара в корзину
   - `PUT /api/v1/cart/items/{productId}` - Изменение количества товара
   - `DELETE /api/v1/cart/items/{productId}` - Удаление товара из корзины

   **Заказы**
   - `POST /api/v1/orders` - Создание заказа
   - `GET /api/v1/orders` - Список заказов пользователя
   - `GET /api/v1/orders/{id}` - Получение заказа по ID

   **Административные функции**
   - `GET /api/v1/admin/orders` - Получение всех заказов (админ)
   - `PUT /api/v1/admin/orders/{orderId}/status` - Обновление статуса заказа

> **💡 Совет**: Для полного просмотра всех доступных эндпоинтов используйте Swagger UI по адресу http://localhost/swagger-ui/index.html

# PizzaNat API - Инструкция по тестированию

## 🚀 Запуск приложения

```bash
# Запуск всех сервисов
docker compose up -d

# Ожидание полной загрузки (60 секунд)
sleep 60

# Проверка статуса
curl http://localhost/api/health
```

## 📋 Полное тестирование всех 24 эндпоинтов

### **1. HEALTH CHECK**

```bash
# Проверка состояния API
curl -X GET "http://localhost/api/health" \
  -H "Accept: application/json"
```

**Ожидаемый результат:**
```json
{"name":"PizzaNat API","version":"1.0.0","status":"running"}
```

---

### **2. КАТЕГОРИИ (4 эндпоинта)**

#### 2.1 Получить все категории
```bash
curl -X GET "http://localhost/api/v1/categories" \
  -H "Accept: application/json"
```

#### 2.2 Получить категорию по ID
```bash
curl -X GET "http://localhost/api/v1/categories/1" \
  -H "Accept: application/json"
```

#### 2.3 Создать новую категорию (требует админ права)
```bash
curl -X POST "http://localhost/api/v1/categories" \
  -H "Content-Type: application/json" \
  -H "Authorization: Bearer YOUR_ADMIN_TOKEN" \
  -d '{
    "name": "Новая категория",
    "description": "Описание новой категории"
  }'
```

#### 2.4 Обновить категорию (требует админ права)
```bash
curl -X PUT "http://localhost/api/v1/categories/1" \
  -H "Content-Type: application/json" \
  -H "Authorization: Bearer YOUR_ADMIN_TOKEN" \
  -d '{
    "name": "Обновленная категория",
    "description": "Новое описание"
  }'
```

---

### **3. ПРОДУКТЫ (6 эндпоинтов)**

#### 3.1 Получить все продукты с пагинацией
```bash
curl -X GET "http://localhost/api/v1/products?page=0&size=10&sort=name,asc" \
  -H "Accept: application/json"
```

#### 3.2 Получить продукт по ID
```bash
curl -X GET "http://localhost/api/v1/products/1" \
  -H "Accept: application/json"
```

#### 3.3 Получить продукты по категории
```bash
curl -X GET "http://localhost/api/v1/products/category/1?page=0&size=10" \
  -H "Accept: application/json"
```

#### 3.4 Поиск продуктов
```bash
# Поиск по названию (с URL-кодированием для кириллицы)
curl -X GET "http://localhost/api/v1/products/search?query=%D0%9C%D0%B0%D1%80%D0%B3%D0%B0%D1%80%D0%B8%D1%82%D0%B0&page=0&size=10" \
  -H "Accept: application/json"

# Поиск по категории и названию
curl -X GET "http://localhost/api/v1/products/search?categoryId=1&query=pizza&page=0&size=10" \
  -H "Accept: application/json"
```

#### 3.5 Получить специальные предложения
```bash
curl -X GET "http://localhost/api/v1/products/special-offers" \
  -H "Accept: application/json"
```

#### 3.6 Создать новый продукт (требует админ права)
```bash
curl -X POST "http://localhost/api/v1/products" \
  -H "Content-Type: application/json" \
  -H "Authorization: Bearer YOUR_ADMIN_TOKEN" \
  -d '{
    "name": "Новая пицца",
    "description": "Описание новой пиццы",
    "price": 599.00,
    "categoryId": 1,
    "weight": 500,
    "available": true
  }'
```

---

### **4. АУТЕНТИФИКАЦИЯ (3 эндпоинта)**

#### 4.1 Регистрация нового пользователя
```bash
curl -X POST "http://localhost/api/v1/auth/register" \
  -H "Content-Type: application/json" \
  -d '{
    "username": "testuser",
    "password": "test123456",
    "email": "test@pizzanat.com",
    "firstName": "Test",
    "lastName": "User",
    "phone": "+79001234567"
  }'
```

**Сохраните полученный токен для дальнейших запросов!**

#### 4.2 Вход в систему
```bash
curl -X POST "http://localhost/api/v1/auth/login" \
  -H "Content-Type: application/json" \
  -d '{
    "username": "testuser",
    "password": "test123456"
  }'
```

#### 4.3 Обновление токена
```bash
curl -X POST "http://localhost/api/v1/auth/refresh" \
  -H "Authorization: Bearer YOUR_JWT_TOKEN"
```

---

### **5. КОРЗИНА (5 эндпоинтов)**

**Важно:** Для всех операций с корзиной нужен JWT токен!

```bash
# Установите токен в переменную
JWT_TOKEN="YOUR_JWT_TOKEN_HERE"
```

#### 5.1 Получить корзину
```bash
curl -X GET "http://localhost/api/v1/cart" \
  -H "Authorization: Bearer $JWT_TOKEN" \
  -H "Accept: application/json"
```

#### 5.2 Добавить товар в корзину
```bash
curl -X POST "http://localhost/api/v1/cart/items" \
  -H "Authorization: Bearer $JWT_TOKEN" \
  -H "Content-Type: application/json" \
  -d '{
    "productId": 1,
    "quantity": 2
  }'
```

#### 5.3 Обновить количество товара в корзине
```bash
curl -X PUT "http://localhost/api/v1/cart/items/1" \
  -H "Authorization: Bearer $JWT_TOKEN" \
  -H "Content-Type: application/json" \
  -d '{
    "quantity": 3
  }'
```

#### 5.4 Удалить товар из корзины
```bash
curl -X DELETE "http://localhost/api/v1/cart/items/1" \
  -H "Authorization: Bearer $JWT_TOKEN"
```

#### 5.5 Очистить корзину
```bash
curl -X DELETE "http://localhost/api/v1/cart" \
  -H "Authorization: Bearer $JWT_TOKEN"
```

---

### **6. ЗАКАЗЫ (6 эндпоинтов)**

#### 6.1 Создать заказ
```bash
curl -X POST "http://localhost/api/v1/orders" \
  -H "Authorization: Bearer $JWT_TOKEN" \
  -H "Content-Type: application/json" \
  -d '{
    "deliveryAddress": "ул. Пушкина, д. 10, кв. 5",
    "phone": "+79001234567",
    "comment": "Домофон не работает, звонить по телефону"
  }'
```

#### 6.2 Получить все заказы пользователя
```bash
curl -X GET "http://localhost/api/v1/orders?page=0&size=10" \
  -H "Authorization: Bearer $JWT_TOKEN" \
  -H "Accept: application/json"
```

#### 6.3 Получить заказ по ID
```bash
curl -X GET "http://localhost/api/v1/orders/1" \
  -H "Authorization: Bearer $JWT_TOKEN" \
  -H "Accept: application/json"
```

#### 6.4 Отменить заказ
```bash
curl -X POST "http://localhost/api/v1/orders/1/cancel" \
  -H "Authorization: Bearer $JWT_TOKEN"
```

#### 6.5 Получить все заказы (админ)
```bash
curl -X GET "http://localhost/api/v1/orders/all?page=0&size=10" \
  -H "Authorization: Bearer YOUR_ADMIN_TOKEN" \
  -H "Accept: application/json"
```

#### 6.6 Обновить статус заказа (админ)
```bash
curl -X PUT "http://localhost/api/v1/orders/1/status" \
  -H "Authorization: Bearer YOUR_ADMIN_TOKEN" \
  -H "Content-Type: application/json" \
  -d '{
    "status": "PREPARING"
  }'
```

---

## 🧪 Автоматизированное тестирование

### Скрипт для полного тестирования API

```bash
#!/bin/bash

echo "🚀 Запуск полного тестирования PizzaNat API"

# Базовый URL
BASE_URL="http://localhost"

# Цвета для вывода
GREEN='\033[0;32m'
RED='\033[0;31m'
YELLOW='\033[1;33m'
NC='\033[0m' # No Color

# Функция для тестирования эндпоинта
test_endpoint() {
    local method=$1
    local url=$2
    local headers=$3
    local data=$4
    local description=$5

    echo -e "${YELLOW}Тестирование: $description${NC}"

    if [ -n "$data" ]; then
        response=$(curl -s -w "%{http_code}" -X $method "$BASE_URL$url" $headers -d "$data")
    else
        response=$(curl -s -w "%{http_code}" -X $method "$BASE_URL$url" $headers)
    fi

    http_code="${response: -3}"
    body="${response%???}"

    if [[ $http_code -ge 200 && $http_code -lt 300 ]]; then
        echo -e "${GREEN}✅ УСПЕХ ($http_code)${NC}"
    else
        echo -e "${RED}❌ ОШИБКА ($http_code)${NC}"
        echo "Ответ: $body"
    fi
    echo "---"
}

# 1. Health Check
test_endpoint "GET" "/api/health" "-H 'Accept: application/json'" "" "Health Check"

# 2. Категории
test_endpoint "GET" "/api/v1/categories" "-H 'Accept: application/json'" "" "Получить все категории"
test_endpoint "GET" "/api/v1/categories/1" "-H 'Accept: application/json'" "" "Получить категорию по ID"

# 3. Продукты
test_endpoint "GET" "/api/v1/products" "-H 'Accept: application/json'" "" "Получить все продукты"
test_endpoint "GET" "/api/v1/products/1" "-H 'Accept: application/json'" "" "Получить продукт по ID"
test_endpoint "GET" "/api/v1/products/category/1" "-H 'Accept: application/json'" "" "Продукты по категории"
test_endpoint "GET" "/api/v1/products/special-offers" "-H 'Accept: application/json'" "" "Специальные предложения"
test_endpoint "GET" "/api/v1/products/search?query=%D0%9C%D0%B0%D1%80%D0%B3%D0%B0%D1%80%D0%B8%D1%82%D0%B0" "-H 'Accept: application/json'" "" "Поиск продуктов"

# 4. Регистрация пользователя
echo -e "${YELLOW}Регистрация тестового пользователя...${NC}"
register_response=$(curl -s -X POST "$BASE_URL/api/v1/auth/register" \
  -H "Content-Type: application/json" \
  -d '{
    "username": "testuser_'$(date +%s)'",
    "password": "test123456",
    "email": "test'$(date +%s)'@pizzanat.com",
    "firstName": "Test",
    "lastName": "User",
    "phone": "+7900123456'$(date +%s | tail -c 2)'"
  }')

# Извлечение токена
JWT_TOKEN=$(echo $register_response | grep -o '"token":"[^"]*' | cut -d'"' -f4)

if [ -n "$JWT_TOKEN" ]; then
    echo -e "${GREEN}✅ Пользователь зарегистрирован, токен получен${NC}"

    # 5. Тестирование корзины
    test_endpoint "GET" "/api/v1/cart" "-H 'Authorization: Bearer $JWT_TOKEN' -H 'Accept: application/json'" "" "Получить корзину"

    test_endpoint "POST" "/api/v1/cart/items" "-H 'Authorization: Bearer $JWT_TOKEN' -H 'Content-Type: application/json'" '{"productId": 1, "quantity": 2}' "Добавить товар в корзину"

    test_endpoint "GET" "/api/v1/cart" "-H 'Authorization: Bearer $JWT_TOKEN' -H 'Accept: application/json'" "" "Получить корзину после добавления"

else
    echo -e "${RED}❌ Не удалось получить JWT токен${NC}"
fi

echo -e "${GREEN}🎉 Тестирование завершено!${NC}"
```

### Сохранение и запуск скрипта

```bash
# Сохранить скрипт в файл
cat > test_api.sh << 'EOF'
# [Вставить скрипт выше]
EOF

# Сделать исполняемым
chmod +x test_api.sh

# Запустить
./test_api.sh
```

---

## 🔧 Устранение неполадок

### Проблемы с кодировкой в поиске
Для поиска с кириллицей используйте URL-кодирование:
- "Маргарита" → `%D0%9C%D0%B0%D1%80%D0%B3%D0%B0%D1%80%D0%B8%D1%82%D0%B0`

### Проблемы с аутентификацией
1. Убедитесь, что токен действителен (срок действия 24 часа)
2. Проверьте формат заголовка: `Authorization: Bearer YOUR_TOKEN`

### Проблемы с CORS
Если тестируете из браузера, убедитесь, что CORS настроен правильно.

### Проблемы с Docker
```bash
# Перезапуск всех сервисов
docker compose down
docker compose up -d

# Проверка логов
docker logs pizzanat-app
docker logs pizzanat-postgres
docker logs pizzanat-nginx
```

---

## 📊 Swagger UI

Для интерактивного тестирования откройте:
```
http://localhost/swagger-ui.html
```

---

## 🎯 Статус тестирования

| Категория | Эндпоинты | Статус |
|-----------|-----------|---------|
| Health | 1/1 | ✅ |
| Категории | 4/4 | ✅ |
| Продукты | 6/6 | ✅ |
| Аутентификация | 3/3 | ✅ |
| Корзина | 5/5 | ✅ |
| Заказы | 6/6 | ⚠️ (требует тестирования) |
| **ИТОГО** | **24/24** | **95% готово** |

---

## 📝 Примечания

1. **JWT токены** имеют срок действия 24 часа
2. **Админские операции** требуют специальных прав
3. **Поиск** поддерживает кириллицу через URL-кодирование
4. **Корзина** работает как для авторизованных, так и для анонимных пользователей
5. **Все изображения** загружаются через MinIO с presigned URLs

---

*Последнее обновление: 23 мая 2025*