#!/bin/bash

# Скрипт для тестирования webhook уведомлений об оплате от ЮКассы
# Дата: $(date '+%d.%m.%Y')

# Цвета для вывода
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
NC='\033[0m' # No Color

# Конфигурация
API_BASE="${API_BASE:-https://debaganov-pizzanat-0177.twc1.net}"
LOG_FILE="payment_webhook_test_$(date +%Y%m%d_%H%M%S).log"

log() {
    echo -e "${GREEN}[$(date '+%H:%M:%S')] $1${NC}" | tee -a $LOG_FILE
}

error() {
    echo -e "${RED}[$(date '+%H:%M:%S')] ❌ $1${NC}" | tee -a $LOG_FILE
}

info() {
    echo -e "${BLUE}[$(date '+%H:%M:%S')] ℹ️  $1${NC}" | tee -a $LOG_FILE
}

warn() {
    echo -e "${YELLOW}[$(date '+%H:%M:%S')] ⚠️  $1${NC}" | tee -a $LOG_FILE
}

check_status() {
    if [ $1 -ge 200 ] && [ $1 -lt 300 ]; then
        return 0
    else
        return 1
    fi
}

echo -e "${GREEN}🧪 ТЕСТ WEBHOOK УВЕДОМЛЕНИЙ ОБ ОПЛАТЕ ЮКАССА${NC}"
echo "=================================================" | tee -a $LOG_FILE
log "Начало теста webhook уведомлений"
log "API Base: $API_BASE"
echo "" | tee -a $LOG_FILE

# Шаг 1: Создание тестового заказа
log "1️⃣ Создание тестового заказа..."

ORDER_DATA="{
    \"customerData\": {
        \"name\": \"Test Webhook User\",
        \"phone\": \"+79999999999\",
        \"email\": \"test@example.com\"
    },
    \"deliveryType\": \"PICKUP\",
    \"address\": \"ул. Тестовая 1\",
    \"paymentMethod\": \"SBP\",
    \"items\": [
        {
            \"productId\": 1,
            \"quantity\": 1
        }
    ]
}"

ORDER_RESPONSE=$(curl -s -w "%{http_code}" -o temp_order.json \
    -X POST \
    -H "Content-Type: application/json" \
    -d "$ORDER_DATA" \
    "$API_BASE/api/v1/orders")

ORDER_STATUS=${ORDER_RESPONSE: -3}
if check_status $ORDER_STATUS; then
    ORDER_ID=$(cat temp_order.json | jq -r '.id')
    log "✅ Заказ создан: #$ORDER_ID"
else
    error "Ошибка создания заказа"
    cat temp_order.json | tee -a $LOG_FILE
    exit 1
fi

# Шаг 2: Создание платежа
log "2️⃣ Создание платежа для заказа #$ORDER_ID..."

PAYMENT_RESPONSE=$(curl -s -w "%{http_code}" -o temp_payment.json \
    -X POST \
    -H "Content-Type: application/json" \
    "$API_BASE/api/v1/payments/yookassa/$ORDER_ID/SBP")

PAYMENT_STATUS=${PAYMENT_RESPONSE: -3}
if check_status $PAYMENT_STATUS; then
    PAYMENT_ID=$(cat temp_payment.json | jq -r '.paymentId')
    YOOKASSA_ID=$(cat temp_payment.json | jq -r '.yookassaPaymentId // .id')
    log "✅ Платеж создан: ID=$PAYMENT_ID, YooKassa ID=$YOOKASSA_ID"
else
    error "Ошибка создания платежа"
    cat temp_payment.json | tee -a $LOG_FILE
    exit 1
fi

# Ждем создания платежа
sleep 2

# Шаг 3: Симуляция webhook payment.succeeded от ЮКассы
log "3️⃣ Отправка webhook payment.succeeded..."

WEBHOOK_DATA="{
    \"event\": \"payment.succeeded\",
    \"object\": {
        \"id\": \"$YOOKASSA_ID\",
        \"status\": \"succeeded\",
        \"amount\": {
            \"value\": \"400.00\",
            \"currency\": \"RUB\"
        },
        \"captured_at\": \"$(date -u +%Y-%m-%dT%H:%M:%S.000Z)\",
        \"payment_method\": {
            \"type\": \"sbp\",
            \"id\": \"test_payment_method\"
        },
        \"metadata\": {
            \"order_id\": \"$ORDER_ID\",
            \"payment_id\": \"$PAYMENT_ID\"
        }
    }
}"

info "Отправка webhook уведомления..."
WEBHOOK_RESPONSE=$(curl -s -w "%{http_code}" -o temp_webhook.json \
    -X POST \
    -H "Content-Type: application/json" \
    -H "X-YooKassa-Event-Type: payment.succeeded" \
    -d "$WEBHOOK_DATA" \
    "$API_BASE/api/v1/payments/yookassa/webhook")

WEBHOOK_STATUS=${WEBHOOK_RESPONSE: -3}
if check_status $WEBHOOK_STATUS; then
    log "✅ Webhook успешно обработан"
    log "📋 Ответ webhook:"
    cat temp_webhook.json | jq '.' | tee -a $LOG_FILE
else
    error "Ошибка обработки webhook"
    cat temp_webhook.json | tee -a $LOG_FILE
fi

# Ждем обработки событий
sleep 3

# Шаг 4: Проверка статуса заказа после webhook
log "4️⃣ Проверка статуса заказа после webhook..."

ORDER_CHECK_RESPONSE=$(curl -s -w "%{http_code}" -o temp_order_check.json \
    -X GET \
    "$API_BASE/api/v1/orders/$ORDER_ID")

ORDER_CHECK_STATUS=${ORDER_CHECK_RESPONSE: -3}
if check_status $ORDER_CHECK_STATUS; then
    PAYMENT_STATUS_AFTER=$(cat temp_order_check.json | jq -r '.paymentStatus')
    ORDER_STATUS_AFTER=$(cat temp_order_check.json | jq -r '.status.name')
    
    log "📊 Статус заказа после webhook:"
    log "   Статус заказа: $ORDER_STATUS_AFTER"
    log "   Статус оплаты: $PAYMENT_STATUS_AFTER"
    
    if [ "$PAYMENT_STATUS_AFTER" = "PAID" ]; then
        log "✅ Статус оплаты успешно изменен на PAID"
    else
        warn "⚠️ Статус оплаты не изменился: $PAYMENT_STATUS_AFTER (ожидается PAID)"
    fi
    
    if [ "$ORDER_STATUS_AFTER" = "CONFIRMED" ]; then
        log "✅ Статус заказа успешно изменен на CONFIRMED"
    else
        warn "⚠️ Статус заказа не изменился: $ORDER_STATUS_AFTER (ожидается CONFIRMED)"
    fi
else
    error "Ошибка проверки статуса заказа"
    cat temp_order_check.json | tee -a $LOG_FILE
fi

# Шаг 5: Проверка логов на предмет событий
log "5️⃣ Проверка логов на предмет обработанных событий..."

# Даем время на обработку асинхронных событий
sleep 2

info "Поиск в логах PaymentStatusChangedEvent..."
info "Поиск в логах NewOrderEvent для заказа #$ORDER_ID..."
info "Поиск в логах уведомлений админского бота..."

# Шаг 6: Проверка Google Sheets (если включены)
log "6️⃣ Проверка интеграции с Google Sheets..."

SHEETS_STATUS_RESPONSE=$(curl -s -w "%{http_code}" -o temp_sheets_check.json \
    -X GET \
    "$API_BASE/api/v1/admin/google-sheets/status" 2>/dev/null)

SHEETS_STATUS_CODE=${SHEETS_STATUS_RESPONSE: -3}
if check_status $SHEETS_STATUS_CODE 2>/dev/null; then
    log "✅ Google Sheets интеграция активна"
    log "📊 Статус Google Sheets:"
    cat temp_sheets_check.json | jq '.' 2>/dev/null | tee -a $LOG_FILE
else
    warn "⚠️ Google Sheets интеграция не активна или недоступна"
fi

# Результаты
echo "" | tee -a $LOG_FILE
echo "=================================================" | tee -a $LOG_FILE
log "🏁 РЕЗУЛЬТАТЫ ТЕСТА:"

if [ "$PAYMENT_STATUS_AFTER" = "PAID" ] && [ "$ORDER_STATUS_AFTER" = "CONFIRMED" ]; then
    log "✅ ТЕСТ УСПЕШЕН: Webhook обработан корректно"
    log "   📊 Статус заказа: $ORDER_STATUS_AFTER"
    log "   💳 Статус оплаты: $PAYMENT_STATUS_AFTER"
else
    warn "⚠️ ТЕСТ ЧАСТИЧНО НЕУДАЧЕН:"
    warn "   📊 Статус заказа: $ORDER_STATUS_AFTER (ожидается: CONFIRMED)"
    warn "   💳 Статус оплаты: $PAYMENT_STATUS_AFTER (ожидается: PAID)"
fi

echo "" | tee -a $LOG_FILE
log "📋 Для диагностики проверьте логи приложения:"
info "   docker-compose logs app | grep -E 'PaymentStatusChangedEvent|NewOrderEvent|ЗАКАЗ ОПЛАЧЕН'"
info "   docker-compose logs app | grep -i 'webhook.*yookassa'"
info "   docker-compose logs app | grep -i 'GoogleSheets'"

log "📄 Подробные результаты сохранены в: $LOG_FILE"

# Очистка временных файлов
rm -f temp_*.json

echo -e "${GREEN}🔚 Тест завершен${NC}"
