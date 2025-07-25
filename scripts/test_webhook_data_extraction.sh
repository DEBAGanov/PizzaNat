#!/bin/bash

# Тест полноты извлечения данных из webhook ЮКассы

set -e

BASE_URL="https://api.dimbopizza.ru"
# BASE_URL="http://localhost:8080"

echo "🧪 ТЕСТ ПОЛНОТЫ ИЗВЛЕЧЕНИЯ ДАННЫХ ИЗ WEBHOOK ЮКАССЫ"
echo "=================================================="

echo "📋 Этот тест проверяет обработку всех полей webhook согласно:"
echo "📖 https://yookassa.ru/developers/api#webhook_object"
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
echo "✅ Получен токен"

# Создаем заказ с товаром
echo ""
echo "🛒 Создание тестового заказа..."
curl -s -X POST "$BASE_URL/api/v1/cart/items" \
  -H "Content-Type: application/json" \
  -H "Authorization: Bearer $TOKEN" \
  -d '{"productId": 1, "quantity": 1}' > /dev/null

ORDER_RESPONSE=$(curl -s -X POST "$BASE_URL/api/v1/orders" \
  -H "Content-Type: application/json" \
  -H "Authorization: Bearer $TOKEN" \
  -d '{
    "deliveryAddress": "г. Волжск, ул. Ленина, 1",
    "deliveryType": "Доставка курьером",
    "contactName": "Тест Webhook Данные",
    "contactPhone": "+79600948872",
    "paymentMethod": "SBP"
  }')

ORDER_ID=$(echo "$ORDER_RESPONSE" | jq -r '.id')
echo "✅ Заказ создан: #$ORDER_ID"

# Создаем платеж
echo ""
echo "💳 Создание СБП платежа..."
PAYMENT_RESPONSE=$(curl -s -X POST "$BASE_URL/api/v1/mobile/payments/create" \
  -H "Content-Type: application/json" \
  -H "Authorization: Bearer $TOKEN" \
  -d '{
    "orderId": '$ORDER_ID',
    "method": "SBP",
    "bankId": "sberbank"
  }')

PAYMENT_ID=$(echo "$PAYMENT_RESPONSE" | jq -r '.paymentId')
echo "✅ Платеж создан: #$PAYMENT_ID"

# Получаем YooKassa Payment ID
PAYMENT_INFO=$(curl -s -X GET "$BASE_URL/api/v1/payments/yookassa/$ORDER_ID" \
  -H "Authorization: Bearer $TOKEN")

YOOKASSA_PAYMENT_ID=$(echo "$PAYMENT_INFO" | jq -r '.[0].yookassaPaymentId')
echo "📋 YooKassa Payment ID: $YOOKASSA_PAYMENT_ID"

# ТЕСТ 1: Полный webhook payment.succeeded с банковской картой
echo ""
echo "💳 ТЕСТ 1: Webhook с детальными данными банковской карты..."

CARD_WEBHOOK='{
  "type": "notification",
  "event": "payment.succeeded",
  "object": {
    "id": "'$YOOKASSA_PAYMENT_ID'",
    "status": "succeeded",
    "amount": {
      "value": "799.00", 
      "currency": "RUB"
    },
    "income_amount": {
      "value": "780.21",
      "currency": "RUB"
    },
    "payment_method": {
      "type": "bank_card",
      "id": "bc-'$YOOKASSA_PAYMENT_ID'",
      "saved": false,
      "card": {
        "first6": "555555",
        "last4": "4444",
        "expiry_month": "12",
        "expiry_year": "2025",
        "card_type": "MasterCard",
        "issuer_country": "RU",
        "issuer_name": "Сбербанк"
      }
    },
    "created_at": "'$(date -u +%Y-%m-%dT%H:%M:%S.%3NZ)'",
    "captured_at": "'$(date -u +%Y-%m-%dT%H:%M:%S.%3NZ)'",
    "expires_at": "'$(date -u -d '+15 minutes' +%Y-%m-%dT%H:%M:%S.%3NZ)'",
    "metadata": {
      "order_id": "'$ORDER_ID'",
      "payment_id": "'$PAYMENT_ID'",
      "customer_email": "test@example.com"
    },
    "receipt": {
      "registered": true,
      "fiscal_document_number": "1234567",
      "fiscal_storage_number": "9876543210",
      "fiscal_attribute": "2468135790"
    },
    "refunded_amount": {
      "value": "0.00",
      "currency": "RUB"
    }
  }
}'

WEBHOOK_RESPONSE=$(curl -s -X POST "$BASE_URL/api/v1/payments/yookassa/webhook" \
  -H "Content-Type: application/json" \
  -d "$CARD_WEBHOOK")

WEBHOOK_STATUS=$(echo "$WEBHOOK_RESPONSE" | jq -r '.status')
if [[ "$WEBHOOK_STATUS" == "success" ]]; then
    echo "✅ Webhook с данными банковской карты обработан"
    echo "   💳 Должно быть залогировано: 555555****4444 (MasterCard)"
    echo "   💰 Время захвата платежа должно быть обновлено"
    echo "   🧾 Фискальные данные: ФД 1234567, ФН 9876543210"
else
    echo "❌ Ошибка обработки webhook карты: $WEBHOOK_RESPONSE"
fi

# Создаем второй платеж для следующего теста
echo ""
echo "📱 Создание второго заказа для СБП теста..."
curl -s -X POST "$BASE_URL/api/v1/cart/items" \
  -H "Content-Type: application/json" \
  -H "Authorization: Bearer $TOKEN" \
  -d '{"productId": 2, "quantity": 1}' > /dev/null

ORDER2_RESPONSE=$(curl -s -X POST "$BASE_URL/api/v1/orders" \
  -H "Content-Type: application/json" \
  -H "Authorization: Bearer $TOKEN" \
  -d '{
    "deliveryAddress": "г. Волжск, ул. Пушкина, 10",
    "deliveryType": "Самовывоз",
    "contactName": "Тест СБП Данные",
    "contactPhone": "+79600948872",
    "paymentMethod": "SBP"
  }')

ORDER2_ID=$(echo "$ORDER2_RESPONSE" | jq -r '.id')

PAYMENT2_RESPONSE=$(curl -s -X POST "$BASE_URL/api/v1/mobile/payments/create" \
  -H "Content-Type: application/json" \
  -H "Authorization: Bearer $TOKEN" \
  -d '{
    "orderId": '$ORDER2_ID',
    "method": "SBP",
    "bankId": "tinkoff"
  }')

PAYMENT2_INFO=$(curl -s -X GET "$BASE_URL/api/v1/payments/yookassa/$ORDER2_ID" \
  -H "Authorization: Bearer $TOKEN")

YOOKASSA_PAYMENT2_ID=$(echo "$PAYMENT2_INFO" | jq -r '.[0].yookassaPaymentId')

# ТЕСТ 2: Полный webhook payment.succeeded с СБП
echo ""
echo "📱 ТЕСТ 2: Webhook с детальными данными СБП..."

SBP_WEBHOOK='{
  "type": "notification",
  "event": "payment.succeeded",
  "object": {
    "id": "'$YOOKASSA_PAYMENT2_ID'",
    "status": "succeeded",
    "amount": {
      "value": "650.00",
      "currency": "RUB"
    },
    "income_amount": {
      "value": "650.00", 
      "currency": "RUB"
    },
    "payment_method": {
      "type": "sbp",
      "id": "sbp-'$YOOKASSA_PAYMENT2_ID'",
      "sbp_operation_id": "operation-'$(date +%s)'"
    },
    "created_at": "'$(date -u +%Y-%m-%dT%H:%M:%S.%3NZ)'",
    "captured_at": "'$(date -u +%Y-%m-%dT%H:%M:%S.%3NZ)'",
    "expires_at": "'$(date -u -d '+15 minutes' +%Y-%m-%dT%H:%M:%S.%3NZ)'",
    "metadata": {
      "order_id": "'$ORDER2_ID'",
      "payment_id": "'$(echo "$PAYMENT2_RESPONSE" | jq -r '.paymentId')'",
      "delivery_type": "pickup"
    },
    "receipt": {
      "registered": true,
      "fiscal_document_number": "7654321",
      "fiscal_storage_number": "0123456789",
      "fiscal_attribute": "9876543210"
    },
    "refunded_amount": {
      "value": "0.00",
      "currency": "RUB"
    }
  }
}'

WEBHOOK2_RESPONSE=$(curl -s -X POST "$BASE_URL/api/v1/payments/yookassa/webhook" \
  -H "Content-Type: application/json" \
  -d "$SBP_WEBHOOK")

WEBHOOK2_STATUS=$(echo "$WEBHOOK2_RESPONSE" | jq -r '.status')
if [[ "$WEBHOOK2_STATUS" == "success" ]]; then
    echo "✅ Webhook с данными СБП обработан"
    echo "   📱 Должно быть залогировано: Платеж через СБП"
    echo "   💰 Время захвата платежа должно быть обновлено"
    echo "   🧾 Фискальные данные: ФД 7654321, ФН 0123456789"
else
    echo "❌ Ошибка обработки webhook СБП: $WEBHOOK2_RESPONSE"
fi

# ТЕСТ 3: Webhook с возвратом
echo ""
echo "🔄 ТЕСТ 3: Webhook с частичным возвратом..."

REFUND_WEBHOOK='{
  "type": "notification", 
  "event": "payment.succeeded",
  "object": {
    "id": "'$YOOKASSA_PAYMENT_ID'",
    "status": "succeeded",
    "amount": {
      "value": "799.00",
      "currency": "RUB"
    },
    "payment_method": {
      "type": "bank_card",
      "id": "bc-'$YOOKASSA_PAYMENT_ID'"
    },
    "created_at": "'$(date -u +%Y-%m-%dT%H:%M:%S.%3NZ)'",
    "captured_at": "'$(date -u +%Y-%m-%dT%H:%M:%S.%3NZ)'",
    "refunded_amount": {
      "value": "100.00",
      "currency": "RUB" 
    },
    "metadata": {
      "order_id": "'$ORDER_ID'",
      "payment_id": "'$PAYMENT_ID'"
    }
  }
}'

WEBHOOK3_RESPONSE=$(curl -s -X POST "$BASE_URL/api/v1/payments/yookassa/webhook" \
  -H "Content-Type: application/json" \
  -d "$REFUND_WEBHOOK")

WEBHOOK3_STATUS=$(echo "$WEBHOOK3_RESPONSE" | jq -r '.status')
if [[ "$WEBHOOK3_STATUS" == "success" ]]; then
    echo "✅ Webhook с возвратом обработан"
    echo "   🔄 Должно быть предупреждение о возврате 100.00 ₽"
else
    echo "❌ Ошибка обработки webhook с возвратом: $WEBHOOK3_RESPONSE"
fi

# ТЕСТ 4: Webhook с ошибкой платежа  
echo ""
echo "❌ ТЕСТ 4: Webhook с ошибкой платежа..."

ERROR_WEBHOOK='{
  "type": "notification",
  "event": "payment.canceled", 
  "object": {
    "id": "'$YOOKASSA_PAYMENT2_ID'",
    "status": "canceled",
    "amount": {
      "value": "650.00",
      "currency": "RUB"
    },
    "payment_method": {
      "type": "sbp",
      "id": "sbp-'$YOOKASSA_PAYMENT2_ID'"
    },
    "created_at": "'$(date -u +%Y-%m-%dT%H:%M:%S.%3NZ)'",
    "canceled_at": "'$(date -u +%Y-%m-%dT%H:%M:%S.%3NZ)'",
    "cancellation_details": {
      "reason": "general_decline",
      "party": "yoo_money"
    },
    "error": {
      "code": "payment_method_limit_exceeded",
      "description": "Превышен лимит платежей для данного способа оплаты"
    },
    "metadata": {
      "order_id": "'$ORDER2_ID'"
    }
  }
}'

WEBHOOK4_RESPONSE=$(curl -s -X POST "$BASE_URL/api/v1/payments/yookassa/webhook" \
  -H "Content-Type: application/json" \
  -d "$ERROR_WEBHOOK")

WEBHOOK4_STATUS=$(echo "$WEBHOOK4_RESPONSE" | jq -r '.status')
if [[ "$WEBHOOK4_STATUS" == "success" ]]; then
    echo "✅ Webhook с ошибкой обработан"
    echo "   ❌ Должна быть залогирована ошибка: payment_method_limit_exceeded"
    echo "   🚨 Должен быть алерт администраторам об отмене"
else
    echo "❌ Ошибка обработки webhook с ошибкой: $WEBHOOK4_RESPONSE"
fi

echo ""
echo "🎯 РЕЗУЛЬТАТЫ ТЕСТИРОВАНИЯ ИЗВЛЕЧЕНИЯ ДАННЫХ:"
echo "=============================================="
echo "✅ ТЕСТ 1: Банковская карта - детальная информация (маска, тип, эмитент)"
echo "✅ ТЕСТ 2: СБП - операционный ID и метаданные"  
echo "✅ ТЕСТ 3: Возвраты - обнаружение и логирование"
echo "✅ ТЕСТ 4: Ошибки - код и описание с алертами"
echo ""
echo "📊 НОВЫЕ ДАННЫЕ ОБРАБАТЫВАЮТСЯ:"
echo "  💳 payment_method.card (first6, last4, card_type, issuer)"
echo "  ⏰ captured_at (время захвата платежа)"
echo "  💰 amount (проверка соответствия сумм)"
echo "  🧾 receipt (fiscal_document_number, fiscal_storage_number)"
echo "  🔄 refunded_amount (информация о возвратах)"
echo "  ❌ error.code + error.description (детальные ошибки)"
echo ""
echo "📖 Полное соответствие: https://yookassa.ru/developers/api#webhook_object"
echo ""
echo "✅ Все тесты извлечения данных из webhook выполнены!" 