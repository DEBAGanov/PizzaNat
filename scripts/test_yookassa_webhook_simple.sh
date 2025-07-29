#!/bin/bash

# Быстрый тест webhook ЮКасса
# Отправляет тестовое webhook уведомление на реальный API

API_BASE="https://api.dimbopizza.ru"

echo "🧪 Быстрый тест webhook ЮКасса"
echo "================================"

# Тестовое webhook уведомление о успешной оплате
PAYMENT_ID="test_payment_$(date +%s)"
CAPTURED_AT=$(date -u +%Y-%m-%dT%H:%M:%S.000Z)

WEBHOOK_DATA=$(cat << EOF
{
    "event": "payment.succeeded",
    "object": {
        "id": "$PAYMENT_ID",
        "status": "succeeded",
        "amount": {
            "value": "500.00",
            "currency": "RUB"
        },
        "captured_at": "$CAPTURED_AT",
        "payment_method": {
            "type": "sbp",
            "id": "test_payment_method_id"
        },
        "metadata": {
            "order_id": "1",
            "payment_id": "test_payment_id"
        },
        "confirmation": {
            "type": "redirect",
            "confirmation_url": "https://test.com"
        }
    }
}
EOF
)

echo "📡 Отправка тестового webhook..."
echo "JSON:"
echo "$WEBHOOK_DATA" | jq '.'

RESPONSE=$(curl -s -w "%{http_code}" -o webhook_response.json \
    -X POST \
    -H "Content-Type: application/json" \
    -H "X-YooKassa-Event-Type: payment.succeeded" \
    -d "$WEBHOOK_DATA" \
    "$API_BASE/api/v1/payments/yookassa/webhook")

STATUS=${RESPONSE: -3}

echo ""
echo "📊 Результат:"
echo "HTTP статус: $STATUS"
echo "Ответ сервера:"
cat webhook_response.json | jq '.'

if [ $STATUS -eq 200 ]; then
    echo ""
    echo "✅ Webhook обработан успешно!"
    echo "🔔 Проверьте админский Telegram бот на наличие уведомлений"
else
    echo ""
    echo "❌ Ошибка обработки webhook (HTTP $STATUS)"
fi

rm -f webhook_response.json 