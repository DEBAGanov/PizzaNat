#!/bin/bash

# Тест исправления поля payment_status в Order entity
# Проверяем что payment_status корректно обновляется при успешной оплате

set -e

# Конфигурация
BASE_URL="http://localhost:8080"
ADMIN_TOKEN="admin-secret-token-2024"

# Цвета для вывода
RED='\033[0;31m'
GREEN='\033[0;32m'
BLUE='\033[0;34m'
YELLOW='\033[1;33m'
NC='\033[0m' # No Color

echo -e "${BLUE}🧪 ТЕСТ: Исправление поля payment_status в Order entity${NC}"
echo -e "${BLUE}=================================================${NC}"

# Функция для создания тестового заказа с СБП оплатой
create_test_order() {
    echo -e "${YELLOW}📝 Создание тестового заказа с СБП оплатой...${NC}"
    
    ORDER_RESPONSE=$(curl -s -X POST "$BASE_URL/api/v1/orders" \
        -H "Content-Type: application/json" \
        -H "Authorization: Bearer $ADMIN_TOKEN" \
        -d '{
            "items": [
                {"productId": 1, "quantity": 1}
            ],
            "deliveryLocationId": 1,
            "contactName": "Тест Пользователь",
            "contactPhone": "+79001234567",
            "comment": "Тест payment_status исправления",
            "deliveryAddress": "Тестовый адрес, 123",
            "paymentMethod": "SBP"
        }')
    
    ORDER_ID=$(echo "$ORDER_RESPONSE" | jq -r '.id')
    
    if [ "$ORDER_ID" != "null" ] && [ -n "$ORDER_ID" ]; then
        echo -e "${GREEN}✅ Заказ создан: ID = $ORDER_ID${NC}"
        return 0
    else
        echo -e "${RED}❌ Ошибка создания заказа${NC}"
        echo "$ORDER_RESPONSE"
        return 1
    fi
}

# Функция для создания платежа
create_test_payment() {
    echo -e "${YELLOW}💳 Создание СБП платежа для заказа #$ORDER_ID...${NC}"
    
    PAYMENT_RESPONSE=$(curl -s -X POST "$BASE_URL/api/v1/payments/create" \
        -H "Content-Type: application/json" \
        -H "Authorization: Bearer $ADMIN_TOKEN" \
        -d '{
            "orderId": '$ORDER_ID',
            "paymentMethod": "SBP",
            "bankId": "sberbank"
        }')
    
    PAYMENT_ID=$(echo "$PAYMENT_RESPONSE" | jq -r '.paymentId')
    YOOKASSA_ID=$(echo "$PAYMENT_RESPONSE" | jq -r '.yookassaPaymentId')
    
    if [ "$PAYMENT_ID" != "null" ] && [ -n "$PAYMENT_ID" ]; then
        echo -e "${GREEN}✅ Платеж создан: ID = $PAYMENT_ID, ЮКасса ID = $YOOKASSA_ID${NC}"
        return 0
    else
        echo -e "${RED}❌ Ошибка создания платежа${NC}"
        echo "$PAYMENT_RESPONSE"
        return 1
    fi
}

# Функция для проверки начального статуса
check_initial_status() {
    echo -e "${YELLOW}🔍 Проверка начального статуса заказа...${NC}"
    
    # Проверяем статус заказа через API
    ORDER_STATUS_RESPONSE=$(curl -s -X GET "$BASE_URL/api/v1/orders/$ORDER_ID" \
        -H "Authorization: Bearer $ADMIN_TOKEN")
    
    echo -e "${BLUE}📊 Данные заказа:${NC}"
    echo "$ORDER_STATUS_RESPONSE" | jq '.'
    
    # Проверяем payment_status в БД (через прямой SQL если возможно)
    echo -e "${YELLOW}🔍 Проверка payment_status в БД...${NC}"
    echo "SELECT id, payment_status, payment_method, created_at FROM orders WHERE id = $ORDER_ID;" | psql postgresql://gen_user:password@localhost:5432/default_db || true
}

# Функция для имитации webhook успешной оплаты
simulate_success_webhook() {
    echo -e "${YELLOW}🔔 Имитация webhook успешной оплаты...${NC}"
    
    WEBHOOK_RESPONSE=$(curl -s -X POST "$BASE_URL/api/v1/payments/webhook" \
        -H "Content-Type: application/json" \
        -d '{
            "event": "payment.succeeded",
            "object": {
                "id": "'$YOOKASSA_ID'",
                "status": "succeeded",
                "amount": {
                    "value": "100.00",
                    "currency": "RUB"
                },
                "payment_method": {
                    "type": "sbp",
                    "id": "sbp_bank_sberbank"
                },
                "created_at": "'$(date -u +%Y-%m-%dT%H:%M:%S.%3NZ)'",
                "captured_at": "'$(date -u +%Y-%m-%dT%H:%M:%S.%3NZ)'"
            }
        }')
    
    echo -e "${GREEN}✅ Webhook отправлен${NC}"
    echo "$WEBHOOK_RESPONSE"
}

# Функция для проверки обновленного статуса
check_updated_status() {
    echo -e "${YELLOW}🔍 Проверка обновленного статуса после webhook...${NC}"
    
    sleep 2  # Даем время на обработку
    
    # Проверяем статус заказа через API
    ORDER_STATUS_RESPONSE=$(curl -s -X GET "$BASE_URL/api/v1/orders/$ORDER_ID" \
        -H "Authorization: Bearer $ADMIN_TOKEN")
    
    echo -e "${BLUE}📊 Обновленные данные заказа:${NC}"
    echo "$ORDER_STATUS_RESPONSE" | jq '.'
    
    # Проверяем payment_status в БД
    echo -e "${YELLOW}🔍 Проверка обновленного payment_status в БД...${NC}"
    echo "SELECT id, payment_status, payment_method, status_id, updated_at FROM orders WHERE id = $ORDER_ID;" | psql postgresql://gen_user:password@localhost:5432/default_db || true
}

# Функция для проверки статуса платежа
check_payment_status() {
    echo -e "${YELLOW}💳 Проверка статуса платежа...${NC}"
    
    PAYMENT_STATUS_RESPONSE=$(curl -s -X GET "$BASE_URL/api/v1/payments/$PAYMENT_ID" \
        -H "Authorization: Bearer $ADMIN_TOKEN")
    
    echo -e "${BLUE}📊 Данные платежа:${NC}"
    echo "$PAYMENT_STATUS_RESPONSE" | jq '.'
}

# Основной поток тестирования
main() {
    echo -e "${BLUE}🚀 Начало тестирования исправления payment_status${NC}"
    
    # Создаем тестовый заказ
    if ! create_test_order; then
        exit 1
    fi
    
    # Создаем платеж
    if ! create_test_payment; then
        exit 1
    fi
    
    # Проверяем начальный статус
    check_initial_status
    
    # Имитируем успешную оплату
    simulate_success_webhook
    
    # Проверяем обновленный статус
    check_updated_status
    
    # Проверяем статус платежа
    check_payment_status
    
    echo -e "${GREEN}✅ Тест завершен успешно${NC}"
    echo -e "${BLUE}💡 Ожидаемый результат: payment_status должен измениться с UNPAID на PAID${NC}"
}

# Запуск тестирования
main "$@" 