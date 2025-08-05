#!/bin/bash

# test_dimbopizza_sheets.sh
# Тест интеграции Google Sheets для таблицы Dimbopizza

set -e

echo "🍕 Тестирование Google Sheets интеграции для Dimbopizza"
echo "======================================================="

# Цвета для вывода
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
NC='\033[0m' # No Color

# Конфигурация Dimbopizza
export GOOGLE_SHEETS_ENABLED=true
export GOOGLE_SHEETS_SPREADSHEET_ID="1K_g-EGPQgu4aFv4bIPP6yE_raHyUrlr6GYi-MTEJtu4"
export GOOGLE_SHEETS_SHEET_NAME="Лист1"

# Базовый URL API
BASE_URL="http://localhost:8080"

echo -e "${GREEN}📊 Настройки таблицы:${NC}"
echo "   Таблица: Dimbopizza"
echo "   ID: $GOOGLE_SHEETS_SPREADSHEET_ID"
echo "   Лист: $GOOGLE_SHEETS_SHEET_NAME"
echo "   URL: https://docs.google.com/spreadsheets/d/$GOOGLE_SHEETS_SPREADSHEET_ID/edit"
echo ""

# Проверка доступности API
check_api_health() {
    echo -e "${BLUE}🏥 Проверка доступности API...${NC}"
    
    if curl -f -s "$BASE_URL/actuator/health" > /dev/null; then
        echo -e "${GREEN}✅ API доступен${NC}"
    else
        echo -e "${RED}❌ API недоступен${NC}"
        echo "Запустите приложение: docker-compose --env-file test-dimbopizza-env up"
        exit 1
    fi
}

# Получение токена аутентификации
get_auth_token() {
    echo -e "${BLUE}🔐 Получение токена аутентификации...${NC}"
    
    # Попытка входа с тестовыми данными
    local login_response=$(curl -s -X POST "$BASE_URL/api/v1/auth/login" \
        -H "Content-Type: application/json" \
        -d '{
            "email": "dimbopizza_test@example.com",
            "password": "password123"
        }' || echo '{"error": "login_failed"}')
    
    if [[ "$login_response" == *"token"* ]]; then
        USER_TOKEN=$(echo "$login_response" | grep -o '"token":"[^"]*"' | cut -d'"' -f4)
        echo -e "${GREEN}✅ Токен получен${NC}"
    else
        # Регистрация нового пользователя
        echo -e "${YELLOW}⚠️ Создание нового пользователя...${NC}"
        
        local timestamp=$(date +%s)
        local register_response=$(curl -s -X POST "$BASE_URL/api/v1/auth/register" \
            -H "Content-Type: application/json" \
            -d "{
                \"username\": \"dimbopizza_test_$timestamp\",
                \"email\": \"dimbopizza_test_$timestamp@example.com\",
                \"password\": \"password123\",
                \"firstName\": \"Dimbopizza\",
                \"lastName\": \"Test User\",
                \"phone\": \"+79991234567\"
            }")
        
        if [[ "$register_response" == *"token"* ]]; then
            USER_TOKEN=$(echo "$register_response" | grep -o '"token":"[^"]*"' | cut -d'"' -f4)
            echo -e "${GREEN}✅ Пользователь создан и токен получен${NC}"
        else
            echo -e "${RED}❌ Не удалось получить токен${NC}"
            echo "Ответ: $register_response"
            exit 1
        fi
    fi
}

# Добавление товара в корзину
add_pizza_to_cart() {
    echo -e "${BLUE}🛒 Добавление пиццы в корзину...${NC}"
    
    local cart_response=$(curl -s -X POST "$BASE_URL/api/v1/cart/items" \
        -H "Content-Type: application/json" \
        -H "Authorization: Bearer $USER_TOKEN" \
        -d '{
            "productId": 2,
            "quantity": 1
        }')
    
    if [[ "$cart_response" == *"id"* ]]; then
        echo -e "${GREEN}✅ Грибная пицца добавлена в корзину${NC}"
    else
        echo -e "${RED}❌ Не удалось добавить товар в корзину${NC}"
        echo "Ответ: $cart_response"
        exit 1
    fi
}

# Создание тестового заказа для Dimbopizza
create_dimbopizza_order() {
    echo -e "${BLUE}🍕 Создание тестового заказа Dimbopizza...${NC}"
    
    local order_response=$(curl -s -X POST "$BASE_URL/api/v1/orders" \
        -H "Content-Type: application/json" \
        -H "Authorization: Bearer $USER_TOKEN" \
        -d '{
            "contactName": "Тест Dimbopizza",
            "contactPhone": "+79991234567",
            "deliveryLocationId": 1,
            "comment": "Тестовый заказ для проверки Google Sheets Dimbopizza",
            "paymentMethod": "CASH"
        }')
    
    if [[ "$order_response" == *"id"* ]]; then
        ORDER_ID=$(echo "$order_response" | grep -o '"id":[0-9]*' | cut -d':' -f2)
        echo -e "${GREEN}✅ Заказ Dimbopizza создан #$ORDER_ID${NC}"
        echo "   Ожидание добавления в Google Sheets..."
        sleep 5
    else
        echo -e "${RED}❌ Не удалось создать заказ${NC}"
        echo "Ответ: $order_response"
        exit 1
    fi
}

# Добавление товара для СБП заказа
add_pizza_for_sbp() {
    echo -e "${BLUE}🛒 Добавление товара для СБП заказа...${NC}"
    
    local cart_response=$(curl -s -X POST "$BASE_URL/api/v1/cart/items" \
        -H "Content-Type: application/json" \
        -H "Authorization: Bearer $USER_TOKEN" \
        -d '{
            "productId": 5,
            "quantity": 2
        }')
    
    if [[ "$cart_response" == *"id"* ]]; then
        echo -e "${GREEN}✅ Пицца Пепперони (2 шт) добавлена в корзину${NC}"
    else
        echo -e "${YELLOW}⚠️ Не удалось добавить товар для СБП заказа${NC}"
    fi
}

# Создание СБП заказа
create_sbp_order() {
    echo -e "${BLUE}💳 Создание СБП заказа...${NC}"
    
    local sbp_order_response=$(curl -s -X POST "$BASE_URL/api/v1/orders" \
        -H "Content-Type: application/json" \
        -H "Authorization: Bearer $USER_TOKEN" \
        -d '{
            "contactName": "СБП Тест Dimbopizza",
            "contactPhone": "+79887776655",
            "deliveryLocationId": 1,
            "comment": "Тестовый СБП заказ Dimbopizza",
            "paymentMethod": "SBP"
        }')
    
    if [[ "$sbp_order_response" == *"id"* ]]; then
        SBP_ORDER_ID=$(echo "$sbp_order_response" | grep -o '"id":[0-9]*' | cut -d':' -f2)
        echo -e "${GREEN}✅ СБП заказ создан #$SBP_ORDER_ID${NC}"
        sleep 3
    else
        echo -e "${YELLOW}⚠️ Не удалось создать СБП заказ${NC}"
    fi
}

# Проверка Google Sheets
check_dimbopizza_sheets() {
    echo -e "${BLUE}📊 Проверка Google Sheets Dimbopizza...${NC}"
    echo ""
    echo -e "${GREEN}🔗 Ваша Google таблица Dimbopizza:${NC}"
    echo "   https://docs.google.com/spreadsheets/d/$GOOGLE_SHEETS_SPREADSHEET_ID/edit"
    echo ""
    echo -e "${YELLOW}📋 Что проверить в таблице:${NC}"
    echo "   1. Заголовки в первой строке (16 колонок A-P)"
    echo "   2. Заказ #$ORDER_ID в строке 2 (новые заказы наверху)"
    if [[ -n "$SBP_ORDER_ID" ]]; then
        echo "   3. СБП заказ #$SBP_ORDER_ID (если создался)"
    fi
    echo "   4. Все данные заказа корректно заполнены"
    echo "   5. Русские символы отображаются правильно"
    echo ""
    echo -e "${BLUE}📝 Структура колонок (A-P):${NC}"
    echo "   A: ID заказа          I: Стоимость товаров"
    echo "   B: Дата создания      J: Стоимость доставки"
    echo "   C: Имя клиента        K: Общая сумма"
    echo "   D: Телефон            L: Способ оплаты"
    echo "   E: Email              M: Статус платежа"
    echo "   F: Состав заказа      N: Статус заказа"
    echo "   G: Адрес доставки     O: Комментарий"
    echo "   H: Тип доставки       P: Ссылка на платеж"
}

# Дополнительные тесты
additional_tests() {
    echo -e "${BLUE}🧪 Дополнительные тесты...${NC}"
    
    # Добавляем еще товар для третьего заказа
    echo "   Добавление товара для третьего заказа..."
    local cart_response3=$(curl -s -X POST "$BASE_URL/api/v1/cart/items" \
        -H "Content-Type: application/json" \
        -H "Authorization: Bearer $USER_TOKEN" \
        -d '{
            "productId": 8,
            "quantity": 1
        }')
    
    # Создание еще одного заказа для проверки сортировки
    echo "   Создание второго заказа для проверки сортировки..."
    local order2_response=$(curl -s -X POST "$BASE_URL/api/v1/orders" \
        -H "Content-Type: application/json" \
        -H "Authorization: Bearer $USER_TOKEN" \
        -d '{
            "contactName": "Второй Тест",
            "contactPhone": "+79990001122",
            "deliveryLocationId": 1,
            "comment": "Второй заказ - должен быть выше первого",
            "paymentMethod": "BANK_CARD"
        }')
    
    if [[ "$order2_response" == *"id"* ]]; then
        ORDER2_ID=$(echo "$order2_response" | grep -o '"id":[0-9]*' | cut -d':' -f2)
        echo -e "${GREEN}   ✅ Второй заказ создан #$ORDER2_ID${NC}"
        echo "   Этот заказ должен появиться ВЫШЕ первого в таблице"
        sleep 3
    fi
}

# Финальный отчет
final_report() {
    echo ""
    echo "🎯 ФИНАЛЬНЫЙ ОТЧЕТ DIMBOPIZZA"
    echo "============================="
    echo -e "${GREEN}✅ Тестирование завершено${NC}"
    echo ""
    echo "📊 Созданные тестовые заказы:"
    echo "   • Наличный заказ: #$ORDER_ID"
    if [[ -n "$SBP_ORDER_ID" ]]; then
        echo "   • СБП заказ: #$SBP_ORDER_ID"
    fi
    if [[ -n "$ORDER2_ID" ]]; then
        echo "   • Карточный заказ: #$ORDER2_ID (должен быть наверху)"
    fi
    echo ""
    echo "🔗 Google таблица Dimbopizza:"
    echo "   https://docs.google.com/spreadsheets/d/$GOOGLE_SHEETS_SPREADSHEET_ID/edit"
    echo ""
    echo -e "${BLUE}📝 Что должно быть в таблице:${NC}"
    echo "   1. Заголовки в строке 1"
    if [[ -n "$ORDER2_ID" ]]; then
        echo "   2. Заказ #$ORDER2_ID в строке 2 (самый новый)"
        echo "   3. Заказ #$ORDER_ID в строке 3"
    else
        echo "   2. Заказ #$ORDER_ID в строке 2"
    fi
    echo "   4. Все 16 колонок заполнены"
    echo "   5. Русский текст отображается корректно"
    echo ""
    echo -e "${YELLOW}🔄 Следующие шаги:${NC}"
    echo "   1. Проверьте таблицу - заказы должны отображаться"
    echo "   2. Создайте еще заказы и убедитесь в правильной сортировке"
    echo "   3. Проверьте обновление статусов (если есть админские права)"
}

# Основная функция
main() {
    echo "🚀 Начало тестирования Dimbopizza Google Sheets..."
    
    check_api_health
    get_auth_token
    add_pizza_to_cart
    create_dimbopizza_order
    add_pizza_for_sbp
    create_sbp_order
    additional_tests
    check_dimbopizza_sheets
    final_report
    
    echo ""
    echo -e "${GREEN}🎉 Тестирование Dimbopizza Google Sheets завершено!${NC}"
}

# Обработка ошибок
trap 'echo -e "${RED}❌ Тест прерван с ошибкой${NC}"; exit 1' ERR

# Запуск
main "$@"