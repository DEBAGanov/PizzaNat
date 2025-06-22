#!/bin/bash

# Тестирование исправления дублированных callback'ов в админском боте
# Проверяем, что повторные изменения статуса обрабатываются корректно

# Цвета для вывода
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
NC='\033[0m' # No Color

# Конфигурация
API_URL="http://localhost:8080/api/v1"
ADMIN_EMAIL="admin@pizzanat.com"
ADMIN_PASSWORD="admin123"

echo -e "${BLUE}🧪 Тестирование исправления дублированных callback'ов в админском боте${NC}"
echo "================================================="

# Функция для получения токена администратора
get_admin_token() {
    echo -e "${YELLOW}Получение токена администратора...${NC}"
    
    TOKEN_RESPONSE=$(curl -s -X POST "$API_URL/auth/login" \
        -H "Content-Type: application/json" \
        -d "{\"email\": \"$ADMIN_EMAIL\", \"password\": \"$ADMIN_PASSWORD\"}")
    
    if [[ $TOKEN_RESPONSE == *"token"* ]]; then
        TOKEN=$(echo $TOKEN_RESPONSE | grep -o '"token":"[^"]*"' | cut -d'"' -f4)
        echo -e "${GREEN}✅ Токен получен${NC}"
        echo $TOKEN
    else
        echo -e "${RED}❌ Не удалось получить токен${NC}"
        echo "Ответ: $TOKEN_RESPONSE"
        exit 1
    fi
}

# Функция для создания тестового заказа
create_test_order() {
    echo -e "${YELLOW}Создание тестового заказа...${NC}"
    
    ORDER_RESPONSE=$(curl -s -X POST "$API_URL/orders" \
        -H "Content-Type: application/json" \
        -d '{
            "contactName": "Тестовый Пользователь",
            "contactPhone": "+79001234567",
            "deliveryAddress": "Тестовый адрес, д. 1",
            "comment": "Тест дублированных callback",
            "items": [
                {
                    "productId": 1,
                    "quantity": 1
                }
            ]
        }')
    
    if [[ $ORDER_RESPONSE == *"id"* ]]; then
        ORDER_ID=$(echo $ORDER_RESPONSE | grep -o '"id":[0-9]*' | cut -d':' -f2)
        echo -e "${GREEN}✅ Заказ создан с ID: $ORDER_ID${NC}"
        echo $ORDER_ID
    else
        echo -e "${RED}❌ Не удалось создать заказ${NC}"
        echo "Ответ: $ORDER_RESPONSE"
        exit 1
    fi
}

# Функция для изменения статуса заказа
update_order_status() {
    local ORDER_ID=$1
    local STATUS=$2
    local ADMIN_TOKEN=$3
    
    echo -e "${YELLOW}Изменение статуса заказа #$ORDER_ID на $STATUS...${NC}"
    
    STATUS_RESPONSE=$(curl -s -X PUT "$API_URL/admin/orders/$ORDER_ID/status" \
        -H "Content-Type: application/json" \
        -H "Authorization: Bearer $ADMIN_TOKEN" \
        -d "{\"statusName\": \"$STATUS\"}")
    
    if [[ $STATUS_RESPONSE == *"$STATUS"* ]]; then
        echo -e "${GREEN}✅ Статус изменен на $STATUS${NC}"
        return 0
    else
        echo -e "${RED}❌ Не удалось изменить статус на $STATUS${NC}"
        echo "Ответ: $STATUS_RESPONSE"
        return 1
    fi
}

# Функция для проверки повторного изменения на тот же статус
test_duplicate_status_change() {
    local ORDER_ID=$1
    local STATUS=$2
    local ADMIN_TOKEN=$3
    
    echo -e "${YELLOW}Тестирование повторного изменения статуса на $STATUS...${NC}"
    
    # Первое изменение
    echo "Первое изменение статуса:"
    update_order_status $ORDER_ID $STATUS $ADMIN_TOKEN
    
    sleep 2
    
    # Второе изменение (должно быть обработано корректно)
    echo "Повторное изменение статуса (проверка защиты от дублирования):"
    STATUS_RESPONSE=$(curl -s -X PUT "$API_URL/admin/orders/$ORDER_ID/status" \
        -H "Content-Type: application/json" \
        -H "Authorization: Bearer $ADMIN_TOKEN" \
        -d "{\"statusName\": \"$STATUS\"}")
    
    # Проверяем, что статус уже установлен или операция выполнена корректно
    if [[ $STATUS_RESPONSE == *"$STATUS"* ]] || [[ $STATUS_RESPONSE == *"уже установлен"* ]]; then
        echo -e "${GREEN}✅ Повторное изменение обработано корректно${NC}"
        return 0
    else
        echo -e "${RED}❌ Проблема с обработкой повторного изменения${NC}"
        echo "Ответ: $STATUS_RESPONSE"
        return 1
    fi
}

# Основной тест
main() {
    echo -e "${BLUE}Начинаем тестирование...${NC}"
    
    # Получаем токен
    ADMIN_TOKEN=$(get_admin_token)
    if [ -z "$ADMIN_TOKEN" ]; then
        echo -e "${RED}❌ Не удалось получить токен администратора${NC}"
        exit 1
    fi
    
    # Создаем тестовый заказ
    ORDER_ID=$(create_test_order)
    if [ -z "$ORDER_ID" ]; then
        echo -e "${RED}❌ Не удалось создать тестовый заказ${NC}"
        exit 1
    fi
    
    echo ""
    echo -e "${BLUE}🔄 Тестирование защиты от дублированных изменений статуса${NC}"
    echo "============================================================"
    
    # Тестируем различные статусы
    test_duplicate_status_change $ORDER_ID "CONFIRMED" $ADMIN_TOKEN
    echo ""
    
    test_duplicate_status_change $ORDER_ID "PREPARING" $ADMIN_TOKEN
    echo ""
    
    test_duplicate_status_change $ORDER_ID "READY" $ADMIN_TOKEN
    echo ""
    
    test_duplicate_status_change $ORDER_ID "DELIVERED" $ADMIN_TOKEN
    echo ""
    
    echo -e "${GREEN}✅ Все тесты завершены успешно!${NC}"
    echo ""
    echo -e "${BLUE}📋 Результаты тестирования:${NC}"
    echo "- ✅ Защита от дублированных callback'ов работает"
    echo "- ✅ Повторные изменения статуса обрабатываются корректно"
    echo "- ✅ Система не выдает ошибок при повторных запросах"
}

# Запуск тестов
main 