#!/bin/bash

# Тестирование интеграции webhook ЮКасса с админским ботом
# Этот скрипт создает тестовый заказ и имитирует webhook о успешной оплате

API_BASE="https://api.dimbopizza.ru"
LOG_FILE="yookassa_webhook_test_$(date +%Y%m%d_%H%M%S).log"

echo "🧪 Тест интеграции webhook ЮКасса с админским ботом" | tee $LOG_FILE
echo "=====================================================" | tee -a $LOG_FILE
echo "Время начала: $(date)" | tee -a $LOG_FILE
echo "" | tee -a $LOG_FILE

# Функция для логирования
log() {
    echo "$(date '+%H:%M:%S') $1" | tee -a $LOG_FILE
}

# Функция для проверки HTTP статуса
check_status() {
    if [ $1 -eq 200 ] || [ $1 -eq 201 ]; then
        log "✅ HTTP $1 - Успешно"
        return 0
    else
        log "❌ HTTP $1 - Ошибка"
        return 1
    fi
}

# Шаг 1: Проверка здоровья ЮКасса интеграции
log "1️⃣ Проверка здоровья ЮКасса интеграции..."
HEALTH_RESPONSE=$(curl -s -w "%{http_code}" -o temp_health.json \
    "$API_BASE/api/v1/payments/yookassa/health")

HEALTH_STATUS=${HEALTH_RESPONSE: -3}
if check_status $HEALTH_STATUS; then
    log "📊 Ответ health check:"
    cat temp_health.json | jq '.' | tee -a $LOG_FILE
else
    log "❌ ЮКасса интеграция недоступна"
    exit 1
fi

echo "" | tee -a $LOG_FILE

# Шаг 2: Получение JWT токена для авторизации (имитация)
log "2️⃣ Получение токена авторизации..."

# Используем тестовые данные для входа в систему (или создаем тестового пользователя)
AUTH_DATA='{
    "phone": "+79991234567",
    "verificationCode": "1234"
}'

TOKEN_RESPONSE=$(curl -s -w "%{http_code}" -o temp_token.json \
    -X POST \
    -H "Content-Type: application/json" \
    -d "$AUTH_DATA" \
    "$API_BASE/api/v1/auth/verify-sms")

TOKEN_STATUS=${TOKEN_RESPONSE: -3}
if [ $TOKEN_STATUS -eq 200 ]; then
    TOKEN=$(cat temp_token.json | jq -r '.accessToken // empty')
    if [ -n "$TOKEN" ] && [ "$TOKEN" != "null" ]; then
        log "✅ Токен получен: ${TOKEN:0:20}..."
    else
        log "⚠️ Используем тестовый заказ без авторизации"
        TOKEN=""
    fi
else
    log "⚠️ Не удалось получить токен, используем анонимный заказ"
    TOKEN=""
fi

echo "" | tee -a $LOG_FILE

# Шаг 3: Создание тестового заказа
log "3️⃣ Создание тестового заказа с оплатой через СБП..."

ORDER_DATA='{
    "deliveryLocationId": 1,
    "deliveryAddress": "Тестовый адрес доставки, ул. Пушкина, д. 1",
    "deliveryType": "Доставка курьером",
    "contactName": "Тестовый Пользователь",
    "contactPhone": "+79991234567",
    "comment": "Тестовый заказ для проверки webhook ЮКасса",
    "paymentMethod": "SBP"
}'

# Добавляем авторизацию если есть токен
AUTH_HEADER=""
if [ -n "$TOKEN" ]; then
    AUTH_HEADER="-H \"Authorization: Bearer $TOKEN\""
fi

ORDER_RESPONSE=$(curl -s -w "%{http_code}" -o temp_order.json \
    -X POST \
    -H "Content-Type: application/json" \
    ${AUTH_HEADER} \
    -d "$ORDER_DATA" \
    "$API_BASE/api/v1/orders")

ORDER_STATUS=${ORDER_STATUS: -3}
if check_status $ORDER_STATUS; then
    ORDER_ID=$(cat temp_order.json | jq -r '.id // empty')
    log "📦 Заказ создан с ID: $ORDER_ID"
    log "📊 Данные заказа:"
    cat temp_order.json | jq '.' | tee -a $LOG_FILE
else
    log "❌ Не удалось создать заказ"
    cat temp_order.json | tee -a $LOG_FILE
    exit 1
fi

echo "" | tee -a $LOG_FILE

# Шаг 4: Создание платежа через ЮКасса
log "4️⃣ Создание платежа через ЮКасса..."

PAYMENT_DATA="{
    \"orderId\": $ORDER_ID,
    \"method\": \"SBP\",
    \"bankId\": \"sberbank\",
    \"description\": \"Тестовая оплата заказа #$ORDER_ID\",
    \"returnUrl\": \"https://dimbopizza.ru/orders/$ORDER_ID\"
}"

PAYMENT_RESPONSE=$(curl -s -w "%{http_code}" -o temp_payment.json \
    -X POST \
    -H "Content-Type: application/json" \
    ${AUTH_HEADER} \
    -d "$PAYMENT_DATA" \
    "$API_BASE/api/v1/payments/yookassa/create")

PAYMENT_STATUS=${PAYMENT_RESPONSE: -3}
if check_status $PAYMENT_STATUS; then
    PAYMENT_ID=$(cat temp_payment.json | jq -r '.id // empty')
    YOOKASSA_ID=$(cat temp_payment.json | jq -r '.yookassaPaymentId // empty')
    CONFIRMATION_URL=$(cat temp_payment.json | jq -r '.confirmationUrl // empty')
    
    log "💳 Платеж создан:"
    log "   - ID платежа: $PAYMENT_ID"
    log "   - ЮКасса ID: $YOOKASSA_ID"
    log "   - URL оплаты: $CONFIRMATION_URL"
    
    log "📊 Данные платежа:"
    cat temp_payment.json | jq '.' | tee -a $LOG_FILE
else
    log "❌ Не удалось создать платеж"
    cat temp_payment.json | tee -a $LOG_FILE
    exit 1
fi

echo "" | tee -a $LOG_FILE

# Шаг 5: Имитация webhook уведомления от ЮКасса о успешной оплате
log "5️⃣ Имитация webhook уведомления от ЮКасса о успешной оплате..."

# Формируем реалистичное webhook уведомление согласно документации ЮКасса
WEBHOOK_DATA="{
    \"event\": \"payment.succeeded\",
    \"object\": {
        \"id\": \"$YOOKASSA_ID\",
        \"status\": \"succeeded\",
        \"amount\": {
            \"value\": \"500.00\",
            \"currency\": \"RUB\"
        },
        \"captured_at\": \"$(date -u +%Y-%m-%dT%H:%M:%S.000Z)\",
        \"payment_method\": {
            \"type\": \"sbp\",
            \"id\": \"test_payment_method_id\"
        },
        \"metadata\": {
            \"order_id\": \"$ORDER_ID\",
            \"payment_id\": \"$PAYMENT_ID\"
        },
        \"confirmation\": {
            \"type\": \"redirect\",
            \"confirmation_url\": \"$CONFIRMATION_URL\"
        },
        \"receipt\": {
            \"registered\": true,
            \"fiscal_document_number\": \"123456789\",
            \"fiscal_storage_number\": \"987654321\"
        }
    }
}"

log "📡 Отправка webhook уведомления..."
WEBHOOK_RESPONSE=$(curl -s -w "%{http_code}" -o temp_webhook.json \
    -X POST \
    -H "Content-Type: application/json" \
    -H "X-YooKassa-Event-Type: payment.succeeded" \
    -d "$WEBHOOK_DATA" \
    "$API_BASE/api/v1/payments/yookassa/webhook")

WEBHOOK_STATUS=${WEBHOOK_RESPONSE: -3}
if check_status $WEBHOOK_STATUS; then
    log "✅ Webhook обработан успешно"
    log "📊 Ответ webhook:"
    cat temp_webhook.json | jq '.' | tee -a $LOG_FILE
else
    log "❌ Ошибка обработки webhook"
    cat temp_webhook.json | tee -a $LOG_FILE
fi

echo "" | tee -a $LOG_FILE

# Шаг 6: Проверка статуса платежа после webhook
log "6️⃣ Проверка статуса платежа после webhook..."

sleep 2  # Даем время на обработку

PAYMENT_CHECK_RESPONSE=$(curl -s -w "%{http_code}" -o temp_payment_check.json \
    -X GET \
    ${AUTH_HEADER} \
    "$API_BASE/api/v1/payments/yookassa/$PAYMENT_ID")

PAYMENT_CHECK_STATUS=${PAYMENT_CHECK_RESPONSE: -3}
if check_status $PAYMENT_CHECK_STATUS; then
    FINAL_STATUS=$(cat temp_payment_check.json | jq -r '.status // empty')
    log "📊 Финальный статус платежа: $FINAL_STATUS"
    
    if [ "$FINAL_STATUS" = "SUCCEEDED" ]; then
        log "✅ Платеж успешно завершен!"
    else
        log "⚠️ Платеж не в статусе SUCCEEDED: $FINAL_STATUS"
    fi
    
    log "📊 Полная информация о платеже:"
    cat temp_payment_check.json | jq '.' | tee -a $LOG_FILE
else
    log "❌ Не удалось проверить статус платежа"
    cat temp_payment_check.json | tee -a $LOG_FILE
fi

echo "" | tee -a $LOG_FILE

# Шаг 7: Проверка статуса заказа
log "7️⃣ Проверка статуса заказа после оплаты..."

ORDER_CHECK_RESPONSE=$(curl -s -w "%{http_code}" -o temp_order_check.json \
    -X GET \
    ${AUTH_HEADER} \
    "$API_BASE/api/v1/orders/$ORDER_ID")

ORDER_CHECK_STATUS=${ORDER_CHECK_RESPONSE: -3}
if check_status $ORDER_CHECK_STATUS; then
    FINAL_ORDER_STATUS=$(cat temp_order_check.json | jq -r '.status // empty')
    PAYMENT_METHOD=$(cat temp_order_check.json | jq -r '.paymentMethod // empty')
    
    log "📊 Финальный статус заказа: $FINAL_ORDER_STATUS"
    log "📊 Способ оплаты заказа: $PAYMENT_METHOD"
    
    if [ "$FINAL_ORDER_STATUS" = "CONFIRMED" ]; then
        log "✅ Заказ переведен в статус CONFIRMED после оплаты!"
    else
        log "⚠️ Заказ не в статусе CONFIRMED: $FINAL_ORDER_STATUS"
    fi
else
    log "❌ Не удалось проверить статус заказа"
    cat temp_order_check.json | tee -a $LOG_FILE
fi

echo "" | tee -a $LOG_FILE

# Шаг 8: Резюме теста
log "8️⃣ Резюме тестирования:"
log "════════════════════════"

if [ $WEBHOOK_STATUS -eq 200 ] && [ "$FINAL_STATUS" = "SUCCEEDED" ] && [ "$FINAL_ORDER_STATUS" = "CONFIRMED" ]; then
    log "🎉 ТЕСТ ПРОЙДЕН УСПЕШНО!"
    log "✅ Webhook от ЮКасса обработан корректно"
    log "✅ Платеж переведен в статус SUCCEEDED"
    log "✅ Заказ переведен в статус CONFIRMED"
    log "✅ Должны быть отправлены уведомления в админский бот:"
    log "   1. Уведомление о новом заказе (при создании)"
    log "   2. Уведомление об успешной оплате (после webhook)"
else
    log "❌ ТЕСТ НЕ ПРОЙДЕН!"
    log "   - Webhook статус: $WEBHOOK_STATUS"
    log "   - Статус платежа: $FINAL_STATUS"
    log "   - Статус заказа: $FINAL_ORDER_STATUS"
fi

echo "" | tee -a $LOG_FILE
log "📝 Полный лог теста сохранен в файл: $LOG_FILE"
log "🕐 Время завершения: $(date)"

# Очистка временных файлов
rm -f temp_*.json

echo ""
echo "🎯 ВАЖНО! Проверьте в админском Telegram боте:"
echo "1. Должно прийти уведомление о новом заказе #$ORDER_ID"
echo "2. Должно прийти уведомление об успешной оплате заказа #$ORDER_ID"
echo ""
echo "📁 Лог теста: $LOG_FILE" 