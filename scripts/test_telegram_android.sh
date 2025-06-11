#!/bin/bash

# PizzaNat - Тест интеграции с Telegram ботом
# Автор: Backend Team
# Дата: 2025-05-31

echo "🤖 PizzaNat - Тестирование Telegram интеграции"
echo "=============================================="
API_URL="https://debaganov-pizzanat-0177.twc1.net"
#API_URL="http://localhost:8080"
ADMIN_TOKEN=""

# Цвета для вывода
GREEN='\033[0;32m'
RED='\033[0;31m'
YELLOW='\033[1;33m'
NC='\033[0m' # No Color

# Функция для проверки статуса
check_status() {
    if [ $1 -eq 0 ]; then
        echo -e "${GREEN}✓ УСПЕХ${NC}"
    else
        echo -e "${RED}✗ ОШИБКА${NC}"
    fi
}

echo ""
echo "📝 Предварительные требования:"
echo "1. Создайте Telegram бота через @BotFather"
echo "2. Получите токен бота"
echo "3. Добавьте бота в группу/чат и получите chat_id"
echo "4. Установите переменные окружения:"
echo "   TELEGRAM_ENABLED=true"
echo "   TELEGRAM_BOT_TOKEN=ваш_токен"
echo "   TELEGRAM_CHAT_ID=ваш_chat_id"
echo ""

# Шаг 1: Получение токена администратора
echo "🔐 Шаг 1: Авторизация администратора"
echo "Логин: admin"
echo "Пароль: admin123"

AUTH_RESPONSE=$(curl -s -X POST \
  "$API_URL/api/v1/auth/login" \
  -H "Content-Type: application/json" \
  -d '{
    "username": "admin",
    "password": "admin123"
  }')

echo "Ответ авторизации: $AUTH_RESPONSE"

ADMIN_TOKEN=$(echo $AUTH_RESPONSE | grep -o '"token":"[^"]*' | cut -d'"' -f4)

if [ -z "$ADMIN_TOKEN" ]; then
    echo -e "${RED}✗ Не удалось получить токен администратора${NC}"
    exit 1
fi

echo -e "${GREEN}✓ Токен администратора получен${NC}"
echo ""

# Шаг 1.5: Создание тестового пользователя для заказа
echo "👤 Шаг 1.5: Создание тестового пользователя"

TIMESTAMP=$(date +%s)
USERNAME="telegram_test_$TIMESTAMP"
EMAIL="telegram_test_$TIMESTAMP@example.com"

USER_RESPONSE=$(curl -s -X POST \
  "$API_URL/api/v1/auth/register" \
  -H "Content-Type: application/json" \
  -d '{
    "username": "'$USERNAME'",
    "password": "test123456",
    "email": "'$EMAIL'",
    "firstName": "Telegram",
    "lastName": "Test",
    "phone": "+79001234567"
  }')

echo "Ответ регистрации пользователя: $USER_RESPONSE"

USER_TOKEN=$(echo $USER_RESPONSE | grep -o '"token":"[^"]*' | cut -d'"' -f4)

if [ -z "$USER_TOKEN" ]; then
    echo -e "${RED}✗ Не удалось создать тестового пользователя${NC}"
    exit 1
fi

echo -e "${GREEN}✓ Тестовый пользователь создан${NC}"
echo ""

# Шаг 1.6: Добавление товара в корзину
echo "🛒 Шаг 1.6: Добавление товара в корзину"

CART_RESPONSE=$(curl -s -X POST \
  "$API_URL/api/v1/cart/items" \
  -H "Content-Type: application/json" \
  -H "Authorization: Bearer $USER_TOKEN" \
  -d '{
    "productId": 1,
    "quantity": 1
  }')

echo "Ответ добавления в корзину: $CART_RESPONSE"

if [[ $CART_RESPONSE == *"productId"* ]] || [[ $CART_RESPONSE == *"quantity"* ]]; then
    echo -e "${GREEN}✓ Товар добавлен в корзину${NC}"
else
    echo -e "${RED}✗ Не удалось добавить товар в корзину${NC}"
    exit 1
fi
echo ""

# Шаг 2: Создание тестового заказа (который должен отправить Telegram уведомление)
echo "🍕 Шаг 2: Создание тестового заказа"

ORDER_RESPONSE=$(curl -s -X POST \
  "$API_URL/api/v1/orders" \
  -H "Content-Type: application/json" \
  -H "Authorization: Bearer $USER_TOKEN" \
  -d '{
    "deliveryAddress": "ул. Тестовая, д. 123, кв. 45",
    "contactName": "Тест Telegram",
    "contactPhone": "+79001234567",
    "comment": "Тестовый заказ для проверки Telegram уведомлений"
  }')

echo "Ответ создания заказа: $ORDER_RESPONSE"

ORDER_ID=$(echo $ORDER_RESPONSE | grep -o '"id":[0-9]*' | head -1 | cut -d':' -f2)

if [ -z "$ORDER_ID" ]; then
    echo -e "${RED}✗ Не удалось создать заказ${NC}"
    exit 1
fi

echo -e "${GREEN}✓ Заказ #$ORDER_ID создан${NC}"
echo ""

# Шаг 3: Изменение статуса заказа (должно отправить второе уведомление)
echo "🔄 Шаг 3: Изменение статуса заказа на CONFIRMED"

STATUS_RESPONSE=$(curl -s -X PUT \
  "$API_URL/api/v1/admin/orders/$ORDER_ID/status" \
  -H "Content-Type: application/json" \
  -H "Authorization: Bearer $ADMIN_TOKEN" \
  -d '{
    "statusName": "CONFIRMED"
  }')

echo "Ответ изменения статуса: $STATUS_RESPONSE"

if [[ $STATUS_RESPONSE == *"CONFIRMED"* ]]; then
    echo -e "${GREEN}✓ Статус заказа изменен на CONFIRMED${NC}"
else
    echo -e "${RED}✗ Не удалось изменить статус заказа${NC}"
fi
echo ""

# Шаг 4: Еще одно изменение статуса
echo "🚚 Шаг 4: Изменение статуса заказа на DELIVERING"

STATUS_RESPONSE2=$(curl -s -X PUT \
  "$API_URL/api/v1/admin/orders/$ORDER_ID/status" \
  -H "Content-Type: application/json" \
  -H "Authorization: Bearer $ADMIN_TOKEN" \
  -d '{
    "statusName": "DELIVERING"
  }')

echo "Ответ изменения статуса: $STATUS_RESPONSE2"

if [[ $STATUS_RESPONSE2 == *"DELIVERING"* ]]; then
    echo -e "${GREEN}✓ Статус заказа изменен на DELIVERING${NC}"
else
    echo -e "${RED}✗ Не удалось изменить статус заказа${NC}"
fi
echo ""

echo "📱 Проверьте ваш Telegram чат/группу:"
echo "1. Уведомление о создании заказа #$ORDER_ID"
echo "2. Уведомление об изменении статуса на CONFIRMED"
echo "3. Уведомление об изменении статуса на DELIVERING"
echo ""

echo "🔧 Если уведомления не приходят, проверьте:"
echo "1. Переменные окружения TELEGRAM_ENABLED, TELEGRAM_BOT_TOKEN, TELEGRAM_CHAT_ID"
echo "2. Логи приложения на наличие ошибок отправки"
echo "3. Корректность токена бота и chat_id"
echo ""

echo -e "${YELLOW}Тестирование завершено!${NC}"