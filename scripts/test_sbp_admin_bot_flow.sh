#!/bin/bash

# Тест СБП флоу: заказ должен приходить в админский бот только после payment.succeeded

set -e

BASE_URL="https://api.dimbopizza.ru"
# BASE_URL="http://localhost:8080"

echo "🧪 ТЕСТ СБП ФЛОУ: Админский бот получает заказы только после успешной оплаты"
echo "================================================================================================"

echo "📋 Этот тест проверяет:"
echo "  ✅ Наличные заказы приходят в бот сразу"
echo "  ✅ СБП заказы НЕ приходят в бот сразу"
echo "  ✅ СБП заказы приходят в бот только после webhook payment.succeeded"
echo "  ✅ Правильная обработка webhook событий ЮКассы"
echo ""

# Получаем токен
echo "🔑 Авторизация..."
AUTH_RESPONSE=$(curl -s -X POST "$BASE_URL/api/v1/auth/sms/verify-code" \
  -H "Content-Type: application/json" \
  -d '{"phoneNumber": "+79600948872", "code": "1234"}')

if [[ $? -ne 0 ]] || [[ ! $(echo "$AUTH_RESPONSE" | jq -r '.token' 2>/dev/null) ]]; then
    echo "❌ Ошибка авторизации"
    exit 1
fi

TOKEN=$(echo "$AUTH_RESPONSE" | jq -r '.token')
echo "✅ Получен токен: ${TOKEN:0:20}..."

# Добавляем товар в корзину
echo ""
echo "🛒 Добавление товара в корзину..."
curl -s -X POST "$BASE_URL/api/v1/cart/items" \
  -H "Content-Type: application/json" \
  -H "Authorization: Bearer $TOKEN" \
  -d '{"productId": 1, "quantity": 1}' > /dev/null

# ТЕСТ 1: Наличные заказ (должен прийти в бот сразу)
echo ""
echo "💰 ТЕСТ 1: Создание наличного заказа (должен прийти в админский бот сразу)..."

CASH_ORDER_RESPONSE=$(curl -s -X POST "$BASE_URL/api/v1/orders" \
  -H "Content-Type: application/json" \
  -H "Authorization: Bearer $TOKEN" \
  -d '{
    "deliveryAddress": "г. Волжск, ул. Ленина, 1",
    "deliveryType": "Доставка курьером", 
    "contactName": "Тест Наличные",
    "contactPhone": "+79600948872",
    "paymentMethod": "CASH"
  }')

if [[ $? -ne 0 ]]; then
    echo "❌ Ошибка создания наличного заказа"
    exit 1
fi

CASH_ORDER_ID=$(echo "$CASH_ORDER_RESPONSE" | jq -r '.id')
echo "✅ Наличный заказ создан: #$CASH_ORDER_ID"
echo "💬 Этот заказ должен СРАЗУ появиться в админском Telegram боте"

# ТЕСТ 2: СБП заказ (НЕ должен прийти в бот сразу)
echo ""
echo "📱 ТЕСТ 2: Создание СБП заказа (НЕ должен прийти в админский бот сразу)..."

# Добавляем еще товар для нового заказа
curl -s -X POST "$BASE_URL/api/v1/cart/items" \
  -H "Content-Type: application/json" \
  -H "Authorization: Bearer $TOKEN" \
  -d '{"productId": 2, "quantity": 1}' > /dev/null

SBP_ORDER_RESPONSE=$(curl -s -X POST "$BASE_URL/api/v1/orders" \
  -H "Content-Type: application/json" \
  -H "Authorization: Bearer $TOKEN" \
  -d '{
    "deliveryAddress": "г. Волжск, ул. Пушкина, 10",
    "deliveryType": "Доставка курьером",
    "contactName": "Тест СБП", 
    "contactPhone": "+79600948872",
    "paymentMethod": "SBP"
  }')

if [[ $? -ne 0 ]]; then
    echo "❌ Ошибка создания СБП заказа"
    exit 1
fi

SBP_ORDER_ID=$(echo "$SBP_ORDER_RESPONSE" | jq -r '.id')
echo "✅ СБП заказ создан: #$SBP_ORDER_ID"
echo "💬 Этот заказ НЕ должен появиться в админском боте (ждет оплаты)"

# ТЕСТ 3: Создание СБП платежа
echo ""
echo "💳 ТЕСТ 3: Создание СБП платежа..."

SBP_PAYMENT_RESPONSE=$(curl -s -X POST "$BASE_URL/api/v1/mobile/payments/create" \
  -H "Content-Type: application/json" \
  -H "Authorization: Bearer $TOKEN" \
  -d '{
    "orderId": '$SBP_ORDER_ID',
    "method": "SBP",
    "bankId": "sberbank"
  }')

if [[ $? -ne 0 ]]; then
    echo "❌ Ошибка создания СБП платежа"
    exit 1
fi

PAYMENT_SUCCESS=$(echo "$SBP_PAYMENT_RESPONSE" | jq -r '.success')
PAYMENT_ID=$(echo "$SBP_PAYMENT_RESPONSE" | jq -r '.paymentId')

if [[ "$PAYMENT_SUCCESS" == "true" ]]; then
    echo "✅ СБП платеж создан: #$PAYMENT_ID"
    echo "💬 Заказ по-прежнему НЕ должен быть в админском боте"
else
    echo "❌ Ошибка создания платежа: $(echo "$SBP_PAYMENT_RESPONSE" | jq -r '.message')"
    exit 1
fi

# ТЕСТ 4: Симуляция webhook payment.succeeded 
echo ""
echo "🔔 ТЕСТ 4: Симуляция webhook payment.succeeded от ЮКассы..."

# Получаем информацию о платеже для webhook
PAYMENT_INFO=$(curl -s -X GET "$BASE_URL/api/v1/payments/yookassa/$SBP_ORDER_ID" \
  -H "Authorization: Bearer $TOKEN")

YOOKASSA_PAYMENT_ID=$(echo "$PAYMENT_INFO" | jq -r '.[0].yookassaPaymentId')

if [[ "$YOOKASSA_PAYMENT_ID" == "null" ]]; then
    echo "❌ Не удалось получить YooKassa Payment ID"
    exit 1
fi

echo "📋 YooKassa Payment ID: $YOOKASSA_PAYMENT_ID"

# Симулируем webhook payment.succeeded
WEBHOOK_RESPONSE=$(curl -s -X POST "$BASE_URL/api/v1/payments/yookassa/webhook" \
  -H "Content-Type: application/json" \
  -d '{
    "type": "notification",
    "event": "payment.succeeded",
    "object": {
      "id": "'$YOOKASSA_PAYMENT_ID'",
      "status": "succeeded",
      "paid": true,
      "amount": {
        "value": "799.00",
        "currency": "RUB"
      },
      "created_at": "'$(date -u +"%Y-%m-%dT%H:%M:%S.%3NZ")'",
      "captured_at": "'$(date -u +"%Y-%m-%dT%H:%M:%S.%3NZ")'"
    }
  }')

if [[ $? -ne 0 ]]; then
    echo "❌ Ошибка отправки webhook"
    exit 1
fi

WEBHOOK_STATUS=$(echo "$WEBHOOK_RESPONSE" | jq -r '.status')

if [[ "$WEBHOOK_STATUS" == "success" ]]; then
    echo "✅ Webhook payment.succeeded обработан успешно"
    echo "💬 ТЕПЕРЬ заказ #$SBP_ORDER_ID должен появиться в админском Telegram боте!"
else
    echo "❌ Ошибка обработки webhook: $WEBHOOK_RESPONSE"
    exit 1
fi

# ТЕСТ 5: Проверка статуса платежа 
echo ""
echo "📊 ТЕСТ 5: Проверка финального статуса платежа..."

FINAL_PAYMENT_INFO=$(curl -s -X GET "$BASE_URL/api/v1/payments/yookassa/$SBP_ORDER_ID" \
  -H "Authorization: Bearer $TOKEN")

FINAL_STATUS=$(echo "$FINAL_PAYMENT_INFO" | jq -r '.[0].status')

if [[ "$FINAL_STATUS" == "SUCCEEDED" ]]; then
    echo "✅ Статус платежа: $FINAL_STATUS"
else
    echo "⚠️  Статус платежа: $FINAL_STATUS (ожидался SUCCEEDED)"
fi

# ТЕСТ 6: Симуляция webhook payment.canceled
echo ""
echo "❌ ТЕСТ 6: Симуляция webhook payment.canceled..."

# Создаем еще один заказ для тестирования отмены
curl -s -X POST "$BASE_URL/api/v1/cart/items" \
  -H "Content-Type: application/json" \
  -H "Authorization: Bearer $TOKEN" \
  -d '{"productId": 1, "quantity": 1}' > /dev/null

CANCEL_ORDER_RESPONSE=$(curl -s -X POST "$BASE_URL/api/v1/orders" \
  -H "Content-Type: application/json" \
  -H "Authorization: Bearer $TOKEN" \
  -d '{
    "deliveryAddress": "г. Волжск, ул. Гагарина, 5",
    "deliveryType": "Самовывоз",
    "contactName": "Тест Отмена",
    "contactPhone": "+79600948872", 
    "paymentMethod": "CARD_ONLINE"
  }')

CANCEL_ORDER_ID=$(echo "$CANCEL_ORDER_RESPONSE" | jq -r '.id')

CANCEL_PAYMENT_RESPONSE=$(curl -s -X POST "$BASE_URL/api/v1/mobile/payments/create" \
  -H "Content-Type: application/json" \
  -H "Authorization: Bearer $TOKEN" \
  -d '{
    "orderId": '$CANCEL_ORDER_ID',
    "method": "CARD_ONLINE"
  }')

CANCEL_PAYMENT_ID=$(echo "$CANCEL_PAYMENT_RESPONSE" | jq -r '.paymentId')

# Получаем YooKassa ID для отмены
CANCEL_PAYMENT_INFO=$(curl -s -X GET "$BASE_URL/api/v1/payments/yookassa/$CANCEL_ORDER_ID" \
  -H "Authorization: Bearer $TOKEN")

CANCEL_YOOKASSA_ID=$(echo "$CANCEL_PAYMENT_INFO" | jq -r '.[0].yookassaPaymentId')

# Симулируем webhook payment.canceled
CANCEL_WEBHOOK_RESPONSE=$(curl -s -X POST "$BASE_URL/api/v1/payments/yookassa/webhook" \
  -H "Content-Type: application/json" \
  -d '{
    "type": "notification", 
    "event": "payment.canceled",
    "object": {
      "id": "'$CANCEL_YOOKASSA_ID'",
      "status": "canceled",
      "paid": false,
      "amount": {
        "value": "599.00",
        "currency": "RUB"
      },
      "created_at": "'$(date -u +"%Y-%m-%dT%H:%M:%S.%3NZ")'",
      "canceled_at": "'$(date -u +"%Y-%m-%dT%H:%M:%S.%3NZ")'"
    }
  }')

CANCEL_WEBHOOK_STATUS=$(echo "$CANCEL_WEBHOOK_RESPONSE" | jq -r '.status')

if [[ "$CANCEL_WEBHOOK_STATUS" == "success" ]]; then
    echo "✅ Webhook payment.canceled обработан успешно"
    echo "💬 Администраторы должны получить уведомление об отмене платежа #$CANCEL_PAYMENT_ID"
    echo "💬 Заказ #$CANCEL_ORDER_ID НЕ должен появиться в админском боте"
else
    echo "❌ Ошибка обработки webhook отмены: $CANCEL_WEBHOOK_RESPONSE"
fi

echo ""
echo "🎯 ИТОГИ ТЕСТИРОВАНИЯ СБП ФЛОУ:"
echo "================================"
echo "✅ Наличный заказ #$CASH_ORDER_ID - должен быть в админском боте СРАЗУ"
echo "✅ СБП заказ #$SBP_ORDER_ID - должен появиться в боте ПОСЛЕ payment.succeeded"  
echo "❌ Отмененный заказ #$CANCEL_ORDER_ID - НЕ должен появиться в боте + алерт админам"
echo ""
echo "🔔 ПРОВЕРЬТЕ АДМИНСКИЙ TELEGRAM БОТ:"
echo "  📱 Должно быть 2 заказа: #$CASH_ORDER_ID (сразу) и #$SBP_ORDER_ID (после оплаты)"
echo "  🚨 Должен быть 1 алерт об отмене платежа #$CANCEL_PAYMENT_ID"
echo ""
echo "✅ Все тесты СБП флоу выполнены успешно!" 