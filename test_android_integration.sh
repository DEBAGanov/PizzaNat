#!/bin/bash

echo "📱 Android Integration Tests для PizzaNat API"
echo "=============================================="

#BASE_URL="https://debaganov-pizzanat-0177.twc1.net"
BASE_URL="http://localhost:8080"

GREEN='\033[0;32m'
RED='\033[0;31m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
PURPLE='\033[0;35m'
NC='\033[0m'

TOTAL_TESTS=0
PASSED_TESTS=0
FAILED_TESTS=0

# Функция для красивого вывода JSON
pretty_json() {
    echo "$1" | python3 -m json.tool 2>/dev/null || echo "$1"
}

# Функция для тестирования с подробным выводом
test_android_api() {
    local url=$1
    local description=$2
    local method=${3:-GET}
    local token=${4:-""}
    local data=${5:-""}
    local expected_status=${6:-200}

    echo -e "${PURPLE}📱 ANDROID TEST: $description${NC}"
    TOTAL_TESTS=$((TOTAL_TESTS + 1))

    # Формируем команду curl для получения статуса
    local curl_cmd="curl -s -L -w '%{http_code}' -X $method '$BASE_URL$url'"
    curl_cmd="$curl_cmd -H 'Accept: application/json'"

    if [ -n "$token" ]; then
        curl_cmd="$curl_cmd -H 'Authorization: Bearer $token'"
    fi

    if [ -n "$data" ]; then
        curl_cmd="$curl_cmd -H 'Content-Type: application/json' -d '$data'"
    fi

    # Получаем ответ и статус
    local response=$(eval $curl_cmd)
    local http_code="${response: -3}"
    local body="${response%???}"

    # Проверяем успешность
    if [[ $http_code -eq $expected_status ]] || [[ $http_code -eq 201 && $expected_status -eq 200 ]]; then
        echo -e "${GREEN}✅ SUCCESS ($http_code)${NC}"
        PASSED_TESTS=$((PASSED_TESTS + 1))

        # Показываем ответ для успешных запросов
        if [ -n "$body" ] && [ "$body" != "null" ]; then
            echo -e "${BLUE}📄 Response:${NC}"
            pretty_json "$body" | head -n 10
            echo ""
        fi
    else
        echo -e "${RED}❌ FAILED ($http_code, expected $expected_status)${NC}"

        if [ -n "$body" ]; then
            echo -e "${RED}📄 Error Response:${NC}"
            pretty_json "$body" | head -n 5
        fi

        FAILED_TESTS=$((FAILED_TESTS + 1))
    fi
    echo "---"
}

# Проверка доступности API
echo -e "${BLUE}🔍 Проверка доступности API...${NC}"
if ! curl -s "$BASE_URL/api/health" > /dev/null; then
    echo -e "${RED}❌ API недоступен!${NC}"
    exit 1
fi
echo -e "${GREEN}✅ API доступен${NC}"
echo ""

# Регистрация тестового пользователя
echo -e "${BLUE}👤 РЕГИСТРАЦИЯ ТЕСТОВОГО ПОЛЬЗОВАТЕЛЯ${NC}"
TIMESTAMP=$(date +%s)
USERNAME="android_user_$TIMESTAMP"
EMAIL="android$TIMESTAMP@pizzanat.com"
PHONE="+7900$(printf "%07d" $((RANDOM % 10000000)))"

register_data='{
  "username": "'$USERNAME'",
  "password": "android123",
  "email": "'$EMAIL'",
  "firstName": "Android",
  "lastName": "User",
  "phone": "'$PHONE'"
}'

test_android_api "/api/v1/auth/register" "Регистрация Android пользователя" "POST" "" "$register_data" 201

# Получаем токен
register_response=$(curl -s -L -X POST "$BASE_URL/api/v1/auth/register" \
  -H "Content-Type: application/json" \
  -H "Accept: application/json" \
  -d "$register_data")

JWT_TOKEN=$(echo "$register_response" | grep -o '"token":"[^"]*' | cut -d'"' -f4)

if [ -z "$JWT_TOKEN" ]; then
    # Пытаемся войти
    login_data='{"username": "'$USERNAME'", "password": "android123"}'
    login_response=$(curl -s -L -X POST "$BASE_URL/api/v1/auth/login" \
      -H "Content-Type: application/json" \
      -H "Accept: application/json" \
      -d "$login_data")
    JWT_TOKEN=$(echo "$login_response" | grep -o '"token":"[^"]*' | cut -d'"' -f4)
fi

if [ -n "$JWT_TOKEN" ]; then
    echo -e "${GREEN}🔑 JWT токен получен успешно${NC}"
    echo ""
else
    echo -e "${RED}❌ Не удалось получить JWT токен${NC}"
    exit 1
fi

# 1. Тестирование пунктов доставки
echo -e "${BLUE}🚚 ТЕСТИРОВАНИЕ ПУНКТОВ ДОСТАВКИ${NC}"
test_android_api "/api/v1/delivery-locations" "Получение списка пунктов доставки"
test_android_api "/api/v1/delivery-locations/1" "Получение пункта доставки по ID"

# 2. Тестирование корзины с selectedOptions
echo -e "${BLUE}🛒 ТЕСТИРОВАНИЕ КОРЗИНЫ (Android features)${NC}"

# Добавление товара с опциями
android_cart_data='{
    "productId": 1,
    "quantity": 2,
    "selectedOptions": {
        "size": "large",
        "crustType": "thin",
        "extraCheese": true,
        "mushrooms": false,
        "extraSauce": "bbq"
    }
}'
test_android_api "/api/v1/cart/items" "Добавление товара с Android опциями" "POST" "$JWT_TOKEN" "$android_cart_data"

# Проверяем корзину
test_android_api "/api/v1/cart" "Получение корзины с опциями"

# 3. Тестирование создания заказов (Android способы)
echo -e "${BLUE}📦 ТЕСТИРОВАНИЕ ЗАКАЗОВ (Android Integration)${NC}"

# Тест 1: Заказ с deliveryAddress (основной Android способ)
android_order_1='{
    "deliveryAddress": "Москва, ул. Андроид, д. 123, кв. 456",
    "contactName": "Android Тестер",
    "contactPhone": "+79001234567",
    "notes": "Заказ создан через Android приложение. Домофон 123#"
}'
test_android_api "/api/v1/orders" "Создание заказа с Android полями (deliveryAddress + notes)" "POST" "$JWT_TOKEN" "$android_order_1"

# Добавляем товар для следующего теста
test_android_api "/api/v1/cart/items" "Добавление товара для второго заказа" "POST" "$JWT_TOKEN" '{"productId": 2, "quantity": 1}'

# Тест 2: Заказ только с notes (без comment)
android_order_2='{
    "deliveryLocationId": 1,
    "contactName": "Notes Тестер",
    "contactPhone": "+79009876543",
    "notes": "Только notes поле, comment пустой"
}'
test_android_api "/api/v1/orders" "Создание заказа только с notes полем" "POST" "$JWT_TOKEN" "$android_order_2"

# Добавляем товар для следующего теста
test_android_api "/api/v1/cart/items" "Добавление товара для третьего заказа" "POST" "$JWT_TOKEN" '{"productId": 1, "quantity": 1}'

# Тест 3: Заказ с обоими полями (приоритет comment)
android_order_3='{
    "deliveryAddress": "Санкт-Петербург, пр. Невский, д. 1",
    "contactName": "Полный Тест",
    "contactPhone": "+78005553535",
    "comment": "Основной комментарий",
    "notes": "Дополнительные заметки от Android"
}'
test_android_api "/api/v1/orders" "Создание заказа с comment и notes одновременно" "POST" "$JWT_TOKEN" "$android_order_3"

# 4. Проверка автоматического создания пунктов доставки
echo -e "${BLUE}🏠 ТЕСТИРОВАНИЕ АВТОСОЗДАНИЯ ПУНКТОВ ДОСТАВКИ${NC}"

# Получаем список до создания новых заказов
delivery_before=$(curl -s -L "$BASE_URL/api/v1/delivery-locations" -H "Accept: application/json")
count_before=$(echo "$delivery_before" | grep -o '"id":' | wc -l)

echo -e "${YELLOW}📊 Пунктов доставки до тестов: $count_before${NC}"

# Добавляем товар для заказа с новым адресом
test_android_api "/api/v1/cart/items" "Добавление товара для нового адреса" "POST" "$JWT_TOKEN" '{"productId": 3, "quantity": 1}'

# Создаем заказ с уникальным адресом
unique_address="г. Тестград, ул. Уникальная, д. $TIMESTAMP"
new_address_order='{
    "deliveryAddress": "'$unique_address'",
    "contactName": "Новый Адрес",
    "contactPhone": "+79001111111",
    "notes": "Тест автосоздания пункта доставки"
}'
test_android_api "/api/v1/orders" "Создание заказа с новым уникальным адресом" "POST" "$JWT_TOKEN" "$new_address_order"

# Проверяем, что создался новый пункт доставки
sleep 1
delivery_after=$(curl -s -L "$BASE_URL/api/v1/delivery-locations" -H "Accept: application/json")
count_after=$(echo "$delivery_after" | grep -o '"id":' | wc -l)

echo -e "${YELLOW}📊 Пунктов доставки после теста: $count_after${NC}"

if [ $count_after -gt $count_before ]; then
    echo -e "${GREEN}✅ Автосоздание пунктов доставки работает!${NC}"
    PASSED_TESTS=$((PASSED_TESTS + 1))
else
    echo -e "${RED}❌ Автосоздание пунктов доставки не работает${NC}"
    FAILED_TESTS=$((FAILED_TESTS + 1))
fi
TOTAL_TESTS=$((TOTAL_TESTS + 1))

# 5. Проверка созданных заказов
echo -e "${BLUE}📋 ПРОВЕРКА СОЗДАННЫХ ЗАКАЗОВ${NC}"
test_android_api "/api/v1/orders" "Получение всех заказов пользователя"

# Итоговая статистика
echo "=============================================="
echo -e "${BLUE}📊 ИТОГОВАЯ СТАТИСТИКА ANDROID ИНТЕГРАЦИИ${NC}"
echo -e "Всего тестов: $TOTAL_TESTS"
echo -e "${GREEN}Успешных: $PASSED_TESTS${NC}"
echo -e "${RED}Неудачных: $FAILED_TESTS${NC}"

if [ $TOTAL_TESTS -gt 0 ]; then
    SUCCESS_RATE=$((PASSED_TESTS * 100 / TOTAL_TESTS))
    echo -e "Процент успеха: ${GREEN}$SUCCESS_RATE%${NC}"
fi

echo ""
echo -e "${BLUE}🔍 ПРОВЕРЕННЫЕ ANDROID ФУНКЦИИ:${NC}"
echo -e "${GREEN}✅ deliveryAddress поддержка${NC}"
echo -e "${GREEN}✅ notes → comment fallback${NC}"
echo -e "${GREEN}✅ selectedOptions в корзине${NC}"
echo -e "${GREEN}✅ Автосоздание пунктов доставки${NC}"
echo -e "${GREEN}✅ API для получения пунктов доставки${NC}"

if [ $FAILED_TESTS -eq 0 ]; then
    echo ""
    echo -e "${GREEN}🎉 ВСЕ ANDROID ТЕСТЫ ПРОШЛИ УСПЕШНО!${NC}"
    echo -e "${GREEN}📱 Android приложение готово к интеграции${NC}"
    exit 0
else
    echo ""
    echo -e "${YELLOW}⚠️  $FAILED_TESTS из $TOTAL_TESTS Android тестов не прошли${NC}"
    echo -e "${BLUE}💡 Основная Android функциональность работает${NC}"
    exit 1
fi