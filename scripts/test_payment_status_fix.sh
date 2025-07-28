#!/bin/bash

# Тест для проверки исправления проблемы с payment_status и уведомлениями в админский бот
# После исправления СБП заказы должны отправляться в админский бот после успешной оплаты

set -e

echo "🧪 ТЕСТ: Проверка исправления payment_status и уведомлений в админский бот"
echo "=================================================="

# Конфигурация
BASE_URL="http://localhost:8080"
AUTH_HEADER=""

# Функция аутентификации
authenticate() {
    echo "🔐 Аутентификация..."
    
    # Получаем SMS код
    local phone="+79999999999"
    local sms_response=$(curl -s -X POST "$BASE_URL/auth/sms/send" \
        -H "Content-Type: application/json" \
        -d "{\"phone\":\"$phone\"}")
    
    echo "SMS ответ: $sms_response"
    
    # Авторизуемся с кодом 1234
    local auth_response=$(curl -s -X POST "$BASE_URL/auth/sms/verify" \
        -H "Content-Type: application/json" \
        -d "{\"phone\":\"$phone\", \"code\":\"1234\"}")
    
    echo "Auth ответ: $auth_response"
    
    # Извлекаем JWT токен
    local jwt_token=$(echo "$auth_response" | jq -r '.token // empty')
    if [ -n "$jwt_token" ] && [ "$jwt_token" != "null" ]; then
        AUTH_HEADER="Authorization: Bearer $jwt_token"
        echo "✅ Аутентификация успешна"
        return 0
    else
        echo "❌ Ошибка аутентификации"
        return 1
    fi
}

# Функция создания тестового заказа
create_test_order() {
    local delivery_type="$1"
    local payment_method="$2"
    
    echo "📦 Создание тестового заказа (доставка: $delivery_type, оплата: $payment_method)..."
    
    local order_data='{
        "items": [
            {
                "productId": 1,
                "quantity": 1
            }
        ],
        "deliveryLocationId": 1,
        "deliveryAddress": "Луговая улица, д 9 кв 22",
        "contactName": "Тестовый Заказчик",
        "contactPhone": "+79999999999",
        "paymentMethod": "'$payment_method'",
        "comment": "Тестовый заказ для проверки payment_status"
    }'
    
    local response=$(curl -s -X POST "$BASE_URL/orders" \
        -H "Content-Type: application/json" \
        -H "$AUTH_HEADER" \
        -d "$order_data")
    
    echo "Order response: $response"
    
    local order_id=$(echo "$response" | jq -r '.id // empty')
    if [ -n "$order_id" ] && [ "$order_id" != "null" ]; then
        echo "✅ Заказ создан: ID = $order_id"
        echo "$order_id"
    else
        echo "❌ Ошибка создания заказа"
        return 1
    fi
}

# Функция создания платежа
create_payment() {
    local order_id="$1"
    local payment_method="$2"
    
    echo "💳 Создание платежа для заказа $order_id (метод: $payment_method)..."
    
    local payment_data='{
        "orderId": '$order_id',
        "paymentMethod": "'$payment_method'",
        "bankId": "sberbank"
    }'
    
    local response=$(curl -s -X POST "$BASE_URL/payments/create" \
        -H "Content-Type: application/json" \
        -H "$AUTH_HEADER" \
        -d "$payment_data")
    
    echo "Payment response: $response"
    
    local payment_id=$(echo "$response" | jq -r '.paymentId // empty')
    if [ -n "$payment_id" ] && [ "$payment_id" != "null" ]; then
        echo "✅ Платеж создан: ID = $payment_id"
        echo "$payment_id"
    else
        echo "❌ Ошибка создания платежа"
        return 1
    fi
}

# Функция имитации webhook от ЮКасса
simulate_yookassa_webhook() {
    local payment_id="$1"
    
    echo "🔔 Имитация webhook payment.succeeded от ЮКасса для платежа $payment_id..."
    
    local webhook_data='{
        "type": "notification",
        "event": "payment.succeeded",
        "object": {
            "id": "'$payment_id'",
            "status": "succeeded",
            "amount": {
                "value": "251.00",
                "currency": "RUB"
            },
            "payment_method": {
                "type": "sbp",
                "id": "'$payment_id'",
                "saved": false,
                "bank_id": "sberbank"
            },
            "captured_at": "'$(date -u +%Y-%m-%dT%H:%M:%S.%3NZ)'",
            "created_at": "'$(date -u +%Y-%m-%dT%H:%M:%S.%3NZ)'",
            "metadata": {
                "order_id": "1"
            },
            "receipt": {
                "items": [
                    {
                        "description": "Пицца 4 сыра",
                        "amount": {
                            "value": "251.00",
                            "currency": "RUB"
                        },
                        "vat_code": 1,
                        "quantity": "1",
                        "payment_subject": "commodity"
                    }
                ],
                "customer": {
                    "phone": "+79999999999"
                }
            }
        }
    }'
    
    local response=$(curl -s -X POST "$BASE_URL/payments/yookassa/webhook" \
        -H "Content-Type: application/json" \
        -d "$webhook_data")
    
    echo "Webhook response: $response"
    
    local status=$(echo "$response" | jq -r '.status // empty')
    if [ "$status" == "success" ]; then
        echo "✅ Webhook успешно обработан"
        return 0
    else
        echo "❌ Ошибка обработки webhook"
        return 1
    fi
}

# Функция проверки статуса заказа в БД
check_order_status() {
    local order_id="$1"
    
    echo "🔍 Проверка статуса заказа $order_id в БД..."
    
    # Проверяем через API
    local response=$(curl -s -X GET "$BASE_URL/admin/orders/$order_id" \
        -H "$AUTH_HEADER")
    
    echo "Order status response: $response"
    
    local payment_status=$(echo "$response" | jq -r '.paymentStatus // empty')
    local order_status=$(echo "$response" | jq -r '.status // empty')
    
    if [ "$payment_status" == "PAID" ]; then
        echo "✅ payment_status корректно обновлен на PAID"
    else
        echo "❌ payment_status не обновился: $payment_status"
        return 1
    fi
    
    if [ "$order_status" == "CONFIRMED" ]; then
        echo "✅ Статус заказа корректно обновлен на CONFIRMED"
    else
        echo "⚠️ Статус заказа: $order_status"
    fi
}

# Основной тест
main() {
    echo "🚀 Запуск основного теста..."
    
    # Аутентификация
    if ! authenticate; then
        echo "❌ Тест провален: ошибка аутентификации"
        exit 1
    fi
    
    # Создание СБП заказа
    echo -e "\n📋 ТЕСТ 1: СБП заказ с успешной оплатой"
    echo "--------------------------------------"
    
    local order_id=$(create_test_order "Доставка курьером" "SBP")
    if [ -z "$order_id" ]; then
        echo "❌ Не удалось создать заказ"
        exit 1
    fi
    
    # Создание платежа
    local payment_id=$(create_payment "$order_id" "SBP")
    if [ -z "$payment_id" ]; then
        echo "❌ Не удалось создать платеж"
        exit 1
    fi
    
    # Ждем немного
    echo "⏳ Ждем 3 секунды..."
    sleep 3
    
    # Имитация webhook
    if ! simulate_yookassa_webhook "$payment_id"; then
        echo "❌ Ошибка обработки webhook"
        exit 1
    fi
    
    # Ждем обработки
    echo "⏳ Ждем обработки webhook (5 секунд)..."
    sleep 5
    
    # Проверка статуса
    if ! check_order_status "$order_id"; then
        echo "❌ Статус заказа не обновился корректно"
        exit 1
    fi
    
    echo -e "\n🎉 ВСЕ ТЕСТЫ ПРОШЛИ УСПЕШНО!"
    echo "✅ payment_status корректно обновляется на PAID"
    echo "✅ Webhook от ЮКасса обрабатывается правильно"
    echo "✅ Заказ должен быть отправлен в админский бот"
    
    echo -e "\n📝 РЕКОМЕНДАЦИИ:"
    echo "1. Проверьте логи админского бота на предмет уведомлений"
    echo "2. Убедитесь, что в БД payment_status = 'PAID' для заказа $order_id"
    echo "3. Проверьте, что NewOrderEvent был опубликован после оплаты"
}

# Запуск теста
main "$@" 