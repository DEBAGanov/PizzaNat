#!/bin/bash

echo "🚀 Тестирование передачи способа оплаты из Android приложения"

BASE_URL="http://localhost:8080"

GREEN='\033[0;32m'
RED='\033[0;31m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
CYAN='\033[0;36m'
NC='\033[0m'

# Получаем токен авторизации
echo -e "${YELLOW}Получение токена авторизации...${NC}"
timestamp=$(date +%s)

register_data='{
    "username": "test_android_'$timestamp'",
    "email": "test_android'$timestamp'@example.com",
    "password": "TestPassword123!",
    "firstName": "Android",
    "lastName": "Test User",
    "phone": "+79001234567"
}'

register_response=$(curl -s -w "%{http_code}" -X POST \
    -H "Content-Type: application/json" \
    -d "$register_data" \
    "$BASE_URL/api/v1/auth/register")

username="test_android_$timestamp"
login_data='{
    "username": "'$username'",
    "password": "TestPassword123!"
}'

login_response=$(curl -s -w "%{http_code}" -X POST \
    -H "Content-Type: application/json" \
    -d "$login_data" \
    "$BASE_URL/api/v1/auth/login")

login_http_code=${login_response: -3}
login_body=${login_response%???}

if [ "$login_http_code" = "200" ]; then
    JWT_TOKEN=$(echo "$login_body" | grep -o '"token":"[^"]*' | cut -d'"' -f4)
    echo -e "${GREEN}✅ Токен авторизации получен${NC}"
else
    echo -e "${RED}❌ Ошибка получения токена (HTTP $login_http_code)${NC}"
    exit 1
fi

echo
echo -e "${BLUE}📱 ТЕСТИРОВАНИЕ ANDROID СПОСОБОВ ОПЛАТЫ${NC}"
echo "=================================================================="

# Тест 1: Заказ с СБП (как в Android приложении)
echo -e "${YELLOW}Тест 1: Создание заказа с СБП (Android)${NC}"

# Добавляем товар в корзину
cart_data='{
    "productId": 1,
    "quantity": 1
}'

cart_response=$(curl -s -w "%{http_code}" -X POST "$BASE_URL/api/v1/cart/items" \
    -H "Content-Type: application/json" \
    -H "Authorization: Bearer $JWT_TOKEN" \
    -d "$cart_data")

cart_http_code=${cart_response: -3}

if [ "$cart_http_code" = "200" ]; then
    echo -e "${GREEN}✅ Товар добавлен в корзину${NC}"
else
    echo -e "${YELLOW}⚠️ Не удалось добавить товар в корзину (HTTP $cart_http_code)${NC}"
fi

# Создаем заказ с СБП (имитируем Android запрос)
android_sbp_order='{
    "deliveryLocationId": 1,
    "contactName": "Android СБП Тест",
    "contactPhone": "+79001234567",
    "comment": "Заказ из Android приложения с СБП",
    "paymentMethod": "SBP"
}'

echo -e "${CYAN}📦 Создание Android заказа с СБП...${NC}"
sbp_order_response=$(curl -s -w "%{http_code}" -X POST "$BASE_URL/api/v1/orders" \
    -H "Content-Type: application/json" \
    -H "Authorization: Bearer $JWT_TOKEN" \
    -d "$android_sbp_order")

sbp_order_http_code=${sbp_order_response: -3}
sbp_order_body=${sbp_order_response%???}

if [ "$sbp_order_http_code" = "200" ] || [ "$sbp_order_http_code" = "201" ]; then
    SBP_ORDER_ID=$(echo "$sbp_order_body" | grep -o '"id":[0-9]*' | cut -d':' -f2)
    echo -e "${GREEN}✅ Android СБП заказ #$SBP_ORDER_ID создан${NC}"
    echo -e "${BLUE}🔄 Должен НЕ попасть в админский бот до оплаты${NC}"
else
    echo -e "${RED}❌ Ошибка создания Android СБП заказа (HTTP $sbp_order_http_code)${NC}"
    echo "Ответ: $sbp_order_body"
fi

echo

# Тест 2: Заказ с наличными (как в Android приложении)
echo -e "${YELLOW}Тест 2: Создание заказа с наличными (Android)${NC}"

# Добавляем товар в корзину для второго заказа
cart_response2=$(curl -s -w "%{http_code}" -X POST "$BASE_URL/api/v1/cart/items" \
    -H "Content-Type: application/json" \
    -H "Authorization: Bearer $JWT_TOKEN" \
    -d "$cart_data")

cart_http_code2=${cart_response2: -3}

if [ "$cart_http_code2" = "200" ]; then
    echo -e "${GREEN}✅ Товар добавлен в корзину для наличного заказа${NC}"
else
    echo -e "${YELLOW}⚠️ Не удалось добавить товар в корзину (HTTP $cart_http_code2)${NC}"
fi

# Создаем заказ с наличными (имитируем Android запрос)
android_cash_order='{
    "deliveryLocationId": 1,
    "contactName": "Android Наличные Тест",
    "contactPhone": "+79001234568",
    "comment": "Заказ из Android приложения с наличными",
    "paymentMethod": "CASH"
}'

echo -e "${CYAN}📦 Создание Android заказа с наличными...${NC}"
cash_order_response=$(curl -s -w "%{http_code}" -X POST "$BASE_URL/api/v1/orders" \
    -H "Content-Type: application/json" \
    -H "Authorization: Bearer $JWT_TOKEN" \
    -d "$android_cash_order")

cash_order_http_code=${cash_order_response: -3}
cash_order_body=${cash_order_response%???}

if [ "$cash_order_http_code" = "200" ] || [ "$cash_order_http_code" = "201" ]; then
    CASH_ORDER_ID=$(echo "$cash_order_body" | grep -o '"id":[0-9]*' | cut -d':' -f2)
    echo -e "${GREEN}✅ Android наличный заказ #$CASH_ORDER_ID создан${NC}"
    echo -e "${BLUE}📤 Должен попасть в админский бот сразу${NC}"
else
    echo -e "${RED}❌ Ошибка создания Android наличного заказа (HTTP $cash_order_http_code)${NC}"
    echo "Ответ: $cash_order_body"
fi

echo

# Тест 3: Заказ без указания способа оплаты (по умолчанию)
echo -e "${YELLOW}Тест 3: Создание заказа без paymentMethod (по умолчанию)${NC}"

# Добавляем товар в корзину для третьего заказа
cart_response3=$(curl -s -w "%{http_code}" -X POST "$BASE_URL/api/v1/cart/items" \
    -H "Content-Type: application/json" \
    -H "Authorization: Bearer $JWT_TOKEN" \
    -d "$cart_data")

# Создаем заказ БЕЗ поля paymentMethod
default_order='{
    "deliveryLocationId": 1,
    "contactName": "Дефолтный Тест",
    "contactPhone": "+79001234569",
    "comment": "Заказ без указания способа оплаты"
}'

echo -e "${CYAN}📦 Создание заказа без paymentMethod...${NC}"
default_order_response=$(curl -s -w "%{http_code}" -X POST "$BASE_URL/api/v1/orders" \
    -H "Content-Type: application/json" \
    -H "Authorization: Bearer $JWT_TOKEN" \
    -d "$default_order")

default_order_http_code=${default_order_response: -3}
default_order_body=${default_order_response%???}

if [ "$default_order_http_code" = "200" ] || [ "$default_order_http_code" = "201" ]; then
    DEFAULT_ORDER_ID=$(echo "$default_order_body" | grep -o '"id":[0-9]*' | cut -d':' -f2)
    echo -e "${GREEN}✅ Дефолтный заказ #$DEFAULT_ORDER_ID создан${NC}"
    echo -e "${BLUE}📤 Должен попасть в админский бот сразу (CASH по умолчанию)${NC}"
else
    echo -e "${RED}❌ Ошибка создания дефолтного заказа (HTTP $default_order_http_code)${NC}"
fi

echo
echo "=================================================================="
echo -e "${CYAN}📋 РЕЗЮМЕ ANDROID ТЕСТИРОВАНИЯ:${NC}"
echo "=================================================================="

if [ -n "$SBP_ORDER_ID" ]; then
    echo -e "${BLUE}📱 Android СБП заказ #$SBP_ORDER_ID${NC} - НЕ должен быть в админском боте"
fi

if [ -n "$CASH_ORDER_ID" ]; then
    echo -e "${BLUE}💵 Android наличный заказ #$CASH_ORDER_ID${NC} - должен быть в админском боте"
fi

if [ -n "$DEFAULT_ORDER_ID" ]; then
    echo -e "${BLUE}⚙️ Дефолтный заказ #$DEFAULT_ORDER_ID${NC} - должен быть в админском боте"
fi

echo
echo -e "${YELLOW}📝 ИНСТРУКЦИЯ ДЛЯ ANDROID РАЗРАБОТЧИКА:${NC}"
echo "1. Добавьте поле 'paymentMethod' в CreateOrderRequest"
echo "2. Передавайте 'SBP' когда выбран СБП"
echo "3. Передавайте 'CASH' когда выбраны наличные"
echo "4. Если не передать paymentMethod, будет использован CASH по умолчанию"

echo
echo -e "${GREEN}✅ Тестирование Android способов оплаты завершено!${NC}" 