#!/bin/bash

echo "🚀 Простое тестирование PizzaNat API"

BASE_URL="http://localhost"
GREEN='\033[0;32m'
RED='\033[0;31m'
YELLOW='\033[1;33m'
NC='\033[0m'

test_simple() {
    local url=$1
    local description=$2
    
    echo -e "${YELLOW}Тестирование: $description${NC}"
    
    response=$(curl -s -w "\n%{http_code}" "$BASE_URL$url")
    http_code=$(echo "$response" | tail -n1)
    body=$(echo "$response" | sed '$d')
    
    if [[ $http_code -eq 200 ]]; then
        echo -e "${GREEN}✅ УСПЕХ ($http_code)${NC}"
    else
        echo -e "${RED}❌ ОШИБКА ($http_code)${NC}"
        echo "Ответ: $body"
    fi
    echo "---"
}

# Тестирование основных эндпоинтов
test_simple "/api/health" "Health Check"
test_simple "/api/v1/categories" "Категории"
test_simple "/api/v1/products/1" "Продукт по ID"
test_simple "/api/v1/products/special-offers" "Специальные предложения"

# Регистрация пользователя
echo -e "${YELLOW}Регистрация пользователя...${NC}"
TIMESTAMP=$(date +%s)
register_response=$(curl -s -X POST "$BASE_URL/api/v1/auth/register" \
  -H "Content-Type: application/json" \
  -d '{
    "username": "testuser_'$TIMESTAMP'",
    "password": "test123456",
    "email": "test'$TIMESTAMP'@pizzanat.com",
    "firstName": "Test",
    "lastName": "User",
    "phone": "+7900123456'$(echo $TIMESTAMP | tail -c 3)'"
  }')

JWT_TOKEN=$(echo $register_response | grep -o '"token":"[^"]*' | cut -d'"' -f4)

if [ -n "$JWT_TOKEN" ]; then
    echo -e "${GREEN}✅ Токен получен${NC}"
    
    # Тест корзины
    echo -e "${YELLOW}Тестирование корзины...${NC}"
    cart_response=$(curl -s -X GET "$BASE_URL/api/v1/cart" \
      -H "Authorization: Bearer $JWT_TOKEN")
    echo "Корзина: $cart_response"
    
    # Добавление в корзину
    add_response=$(curl -s -X POST "$BASE_URL/api/v1/cart/items" \
      -H "Authorization: Bearer $JWT_TOKEN" \
      -H "Content-Type: application/json" \
      -d '{"productId": 1, "quantity": 2}')
    echo "Добавление: $add_response"
    
else
    echo -e "${RED}❌ Не удалось получить токен${NC}"
fi

echo -e "${GREEN}🎉 Тестирование завершено!${NC}" 