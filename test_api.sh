#!/bin/bash

echo "🚀 Запуск полного тестирования PizzaNat API"

# Базовый URL
BASE_URL="http://localhost"

# Цвета для вывода
GREEN='\033[0;32m'
RED='\033[0;31m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
NC='\033[0m' # No Color

# Счетчики
TOTAL_TESTS=0
PASSED_TESTS=0
FAILED_TESTS=0

# Функция для тестирования эндпоинта
test_endpoint() {
    local method=$1
    local url=$2
    local headers=$3
    local data=$4
    local description=$5
    
    echo -e "${YELLOW}Тестирование: $description${NC}"
    TOTAL_TESTS=$((TOTAL_TESTS + 1))
    
    if [ -n "$data" ]; then
        response=$(curl -s -w "\n%{http_code}" -X $method "$BASE_URL$url" $headers -d "$data")
    else
        response=$(curl -s -w "\n%{http_code}" -X $method "$BASE_URL$url" $headers)
    fi
    
    # Извлекаем HTTP код из последней строки
    http_code=$(echo "$response" | tail -n1 | tr -d '\n\r')
    # Извлекаем тело ответа (все строки кроме последней)
    body=$(echo "$response" | sed '$d')
    
    # Проверяем, что http_code является числом
    if [[ "$http_code" =~ ^[0-9]+$ ]] && [[ $http_code -ge 200 && $http_code -lt 300 ]]; then
        echo -e "${GREEN}✅ УСПЕХ ($http_code)${NC}"
        PASSED_TESTS=$((PASSED_TESTS + 1))
    else
        echo -e "${RED}❌ ОШИБКА ($http_code)${NC}"
        if [ -n "$body" ]; then
            echo "Ответ: $body"
        fi
        FAILED_TESTS=$((FAILED_TESTS + 1))
    fi
    echo "---"
}

echo -e "${BLUE}Проверка доступности API...${NC}"

# Проверка доступности API
if ! curl -s "$BASE_URL/api/health" > /dev/null; then
    echo -e "${RED}❌ API недоступен! Убедитесь, что сервисы запущены:${NC}"
    echo "docker compose up -d"
    exit 1
fi

echo -e "${GREEN}✅ API доступен${NC}"
echo "=================================="

# 1. Health Check
echo -e "${BLUE}1. HEALTH CHECK${NC}"
test_endpoint "GET" "/api/health" "-H 'Accept: application/json'" "" "Health Check"

# 2. Категории
echo -e "${BLUE}2. КАТЕГОРИИ${NC}"
test_endpoint "GET" "/api/v1/categories" "-H 'Accept: application/json'" "" "Получить все категории"
test_endpoint "GET" "/api/v1/categories/1" "-H 'Accept: application/json'" "" "Получить категорию по ID"

# 3. Продукты
echo -e "${BLUE}3. ПРОДУКТЫ${NC}"
test_endpoint "GET" "/api/v1/products" "-H 'Accept: application/json'" "" "Получить все продукты"
test_endpoint "GET" "/api/v1/products/1" "-H 'Accept: application/json'" "" "Получить продукт по ID"
test_endpoint "GET" "/api/v1/products/category/1" "-H 'Accept: application/json'" "" "Продукты по категории"
test_endpoint "GET" "/api/v1/products/special-offers" "-H 'Accept: application/json'" "" "Специальные предложения"
test_endpoint "GET" "/api/v1/products/search?query=%D0%9C%D0%B0%D1%80%D0%B3%D0%B0%D1%80%D0%B8%D1%82%D0%B0" "-H 'Accept: application/json'" "" "Поиск продуктов (кириллица)"

# 4. Аутентификация
echo -e "${BLUE}4. АУТЕНТИФИКАЦИЯ${NC}"
echo -e "${YELLOW}Регистрация тестового пользователя...${NC}"

# Генерация уникального пользователя
TIMESTAMP=$(date +%s)
USERNAME="testuser_$TIMESTAMP"
EMAIL="test$TIMESTAMP@pizzanat.com"
PHONE="+7900123456$(echo $TIMESTAMP | tail -c 3)"

register_response=$(curl -s -X POST "$BASE_URL/api/v1/auth/register" \
  -H "Content-Type: application/json" \
  -d '{
    "username": "'$USERNAME'",
    "password": "test123456",
    "email": "'$EMAIL'",
    "firstName": "Test",
    "lastName": "User",
    "phone": "'$PHONE'"
  }')

# Извлечение токена
JWT_TOKEN=$(echo $register_response | grep -o '"token":"[^"]*' | cut -d'"' -f4)

if [ -n "$JWT_TOKEN" ]; then
    echo -e "${GREEN}✅ Пользователь зарегистрирован, токен получен${NC}"
    PASSED_TESTS=$((PASSED_TESTS + 1))
    
    # Тест входа
    test_endpoint "POST" "/api/v1/auth/login" "-H 'Content-Type: application/json'" '{"username": "'$USERNAME'", "password": "test123456"}' "Вход в систему"
    
    # 5. Корзина
    echo -e "${BLUE}5. КОРЗИНА${NC}"
    test_endpoint "GET" "/api/v1/cart" "-H 'Authorization: Bearer $JWT_TOKEN' -H 'Accept: application/json'" "" "Получить пустую корзину"
    
    test_endpoint "POST" "/api/v1/cart/items" "-H 'Authorization: Bearer $JWT_TOKEN' -H 'Content-Type: application/json'" '{"productId": 1, "quantity": 2}' "Добавить товар в корзину"
    
    test_endpoint "GET" "/api/v1/cart" "-H 'Authorization: Bearer $JWT_TOKEN' -H 'Accept: application/json'" "" "Получить корзину с товарами"
    
    test_endpoint "PUT" "/api/v1/cart/items/1" "-H 'Authorization: Bearer $JWT_TOKEN' -H 'Content-Type: application/json'" '{"quantity": 3}' "Обновить количество товара"
    
    test_endpoint "DELETE" "/api/v1/cart/items/1" "-H 'Authorization: Bearer $JWT_TOKEN'" "" "Удалить товар из корзины"
    
else
    echo -e "${RED}❌ Не удалось получить JWT токен${NC}"
    FAILED_TESTS=$((FAILED_TESTS + 1))
fi

TOTAL_TESTS=$((TOTAL_TESTS + 1)) # Добавляем регистрацию к общему счету

# Итоговая статистика
echo "=================================="
echo -e "${BLUE}📊 ИТОГОВАЯ СТАТИСТИКА${NC}"
echo -e "Всего тестов: $TOTAL_TESTS"
echo -e "${GREEN}Успешных: $PASSED_TESTS${NC}"
echo -e "${RED}Неудачных: $FAILED_TESTS${NC}"

if [ $FAILED_TESTS -eq 0 ]; then
    echo -e "${GREEN}🎉 Все тесты прошли успешно!${NC}"
    exit 0
else
    echo -e "${YELLOW}⚠️  Некоторые тесты не прошли. Проверьте логи выше.${NC}"
    exit 1
fi 