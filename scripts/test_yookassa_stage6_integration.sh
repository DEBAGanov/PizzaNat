#!/bin/bash

# Комплексное тестирование ЮKassa интеграции - Stage 6
# Тестирование с реальными учетными данными

set -e

# Цвета для вывода
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
CYAN='\033[0;36m'
NC='\033[0m'

echo -e "${CYAN}🧪 Комплексное тестирование ЮKassa - Stage 6${NC}"
echo -e "${CYAN}=============================================${NC}"
echo ""

# Базовый URL
BASE_URL="http://localhost:8080"

# Функции для логирования
log() {
    echo -e "${BLUE}[$(date +'%H:%M:%S')]${NC} $1"
}

success() {
    echo -e "${GREEN}✅ $1${NC}"
}

warning() {
    echo -e "${YELLOW}⚠️  $1${NC}"
}

error() {
    echo -e "${RED}❌ $1${NC}"
}

test_header() {
    echo ""
    echo -e "${CYAN}📋 $1${NC}"
    echo -e "${CYAN}$(printf '=%.0s' {1..50})${NC}"
}

# Проверка доступности API
test_header "Проверка доступности API"

log "Проверка health endpoint..."
HEALTH_RESPONSE=$(curl -s -w "%{http_code}" -o /tmp/health_response.json "$BASE_URL/actuator/health" || echo "000")
if [[ "$HEALTH_RESPONSE" == "200" ]]; then
    success "Health endpoint доступен"
    cat /tmp/health_response.json | jq . 2>/dev/null || cat /tmp/health_response.json
else
    error "Health endpoint недоступен (HTTP: $HEALTH_RESPONSE)"
fi

# Тест 1: Проверка конфигурации ЮKassa
test_header "Тест 1: Проверка конфигурации ЮKassa"

log "Проверка логов инициализации ЮKassa..."
if docker compose logs app | grep -i "yookassa.*webClient\|yookassa.*инициализация" > /dev/null; then
    success "ЮKassa WebClient инициализирован"
else
    warning "Не найдены логи инициализации ЮKassa"
fi

log "Проверка переменных окружения..."
ENV_CHECK=$(docker compose exec -T app printenv | grep YOOKASSA_ || echo "")
if [[ -n "$ENV_CHECK" ]]; then
    success "Переменные ЮKassa найдены:"
    echo "$ENV_CHECK" | while read line; do
        if [[ "$line" == *"SECRET_KEY"* ]]; then
            echo "  ${line%=*}=***СКРЫТО***"
        else
            echo "  $line"
        fi
    done
else
    error "Переменные ЮKassa не найдены"
fi

# Тест 2: Создание мобильного платежа
test_header "Тест 2: Создание мобильного платежа"

log "Создание тестового платежа через мобильное API..."

PAYMENT_REQUEST='{
    "amount": 1000,
    "orderId": "test-order-'$(date +%s)'",
    "description": "Тестовый заказ для проверки ЮKassa",
    "returnUrl": "pizzanat://payment/result",
    "paymentMethod": "bank_card"
}'

echo "📤 Запрос:"
echo "$PAYMENT_REQUEST" | jq .

PAYMENT_RESPONSE=$(curl -s -w "%{http_code}" \
    -H "Content-Type: application/json" \
    -d "$PAYMENT_REQUEST" \
    -o /tmp/payment_response.json \
    "$BASE_URL/api/v1/mobile/payments/create" || echo "000")

echo ""
echo "📥 Ответ (HTTP: $PAYMENT_RESPONSE):"

if [[ "$PAYMENT_RESPONSE" == "200" ]]; then
    success "Платеж успешно создан"
    cat /tmp/payment_response.json | jq . 2>/dev/null || cat /tmp/payment_response.json

    # Извлекаем ID платежа для дальнейших тестов
    PAYMENT_ID=$(cat /tmp/payment_response.json | jq -r '.paymentId // empty' 2>/dev/null)
    CONFIRMATION_URL=$(cat /tmp/payment_response.json | jq -r '.confirmationUrl // empty' 2>/dev/null)

    if [[ -n "$PAYMENT_ID" ]]; then
        success "ID платежа: $PAYMENT_ID"
    fi

    if [[ -n "$CONFIRMATION_URL" ]]; then
        success "URL подтверждения: $CONFIRMATION_URL"
    fi
else
    error "Ошибка создания платежа"
    cat /tmp/payment_response.json 2>/dev/null || echo "Нет ответа от сервера"
fi

# Тест 3: СБП платеж
test_header "Тест 3: СБП платеж"

log "Создание СБП платежа..."

SBP_REQUEST='{
    "amount": 500,
    "orderId": "test-sbp-'$(date +%s)'",
    "description": "Тестовый СБП платеж",
    "returnUrl": "pizzanat://payment/result",
    "paymentMethod": "sbp"
}'

echo "📤 СБП запрос:"
echo "$SBP_REQUEST" | jq .

SBP_RESPONSE=$(curl -s -w "%{http_code}" \
    -H "Content-Type: application/json" \
    -d "$SBP_REQUEST" \
    -o /tmp/sbp_response.json \
    "$BASE_URL/api/v1/mobile/payments/create" || echo "000")

echo ""
echo "📥 СБП ответ (HTTP: $SBP_RESPONSE):"

if [[ "$SBP_RESPONSE" == "200" ]]; then
    success "СБП платеж успешно создан"
    cat /tmp/sbp_response.json | jq . 2>/dev/null || cat /tmp/sbp_response.json
else
    error "Ошибка создания СБП платежа"
    cat /tmp/sbp_response.json 2>/dev/null || echo "Нет ответа от сервера"
fi

# Тест 4: Метрики платежей
test_header "Тест 4: Метрики платежей"

log "Получение метрик платежей..."

METRICS_RESPONSE=$(curl -s -w "%{http_code}" \
    -o /tmp/metrics_response.json \
    "$BASE_URL/api/v1/payments/metrics" || echo "000")

echo "📊 Метрики (HTTP: $METRICS_RESPONSE):"

if [[ "$METRICS_RESPONSE" == "200" ]]; then
    success "Метрики получены"
    cat /tmp/metrics_response.json | jq . 2>/dev/null || cat /tmp/metrics_response.json
else
    warning "Метрики недоступны или пусты"
    cat /tmp/metrics_response.json 2>/dev/null || echo "Нет данных"
fi

# Тест 5: Проверка логов ошибок
test_header "Тест 5: Анализ логов"

log "Поиск ошибок в логах ЮKassa..."
ERROR_LOGS=$(docker compose logs app | grep -i "yookassa.*error\|yookassa.*exception" | tail -5)

if [[ -n "$ERROR_LOGS" ]]; then
    warning "Найдены ошибки в логах:"
    echo "$ERROR_LOGS"
else
    success "Критических ошибок ЮKassa не найдено"
fi

log "Последние логи ЮKassa (информационные)..."
YOOKASSA_LOGS=$(docker compose logs app | grep -i "yookassa" | tail -10)
if [[ -n "$YOOKASSA_LOGS" ]]; then
    echo "$YOOKASSA_LOGS"
else
    warning "Логи ЮKassa не найдены"
fi

# Тест 6: Webhook endpoint
test_header "Тест 6: Webhook endpoint"

log "Проверка webhook endpoint..."

WEBHOOK_TEST='{
    "type": "payment.succeeded",
    "event": "payment.succeeded",
    "object": {
        "id": "test-webhook-payment-id",
        "status": "succeeded",
        "amount": {
            "value": "100.00",
            "currency": "RUB"
        },
        "metadata": {
            "orderId": "test-webhook-order"
        }
    }
}'

WEBHOOK_RESPONSE=$(curl -s -w "%{http_code}" \
    -H "Content-Type: application/json" \
    -d "$WEBHOOK_TEST" \
    -o /tmp/webhook_response.json \
    "$BASE_URL/api/v1/payments/yookassa/webhook" || echo "000")

echo "🔔 Webhook тест (HTTP: $WEBHOOK_RESPONSE):"

if [[ "$WEBHOOK_RESPONSE" == "200" ]]; then
    success "Webhook endpoint работает"
    cat /tmp/webhook_response.json 2>/dev/null || echo "OK"
else
    warning "Webhook endpoint недоступен или возвращает ошибку"
    cat /tmp/webhook_response.json 2>/dev/null || echo "Нет ответа"
fi

# Итоговый отчет
test_header "Итоговый отчет"

echo ""
echo "📋 Результаты тестирования ЮKassa Stage 6:"
echo ""
echo "🔧 Конфигурация:"
echo "  • Shop ID: 1116141"
echo "  • API URL: https://api.yookassa.ru/v3"
echo "  • Webhook URL: https://debaganov-pizzanat-0177.twc1.net/api/v1/payments/yookassa/webhook"
echo ""
echo "💳 Тестовые карты для мобильного приложения:"
echo "  • Успешная оплата: 5555555555554444"
echo "  • Отклоненная оплата: 4111111111111112"
echo "  • 3DS аутентификация: 4000000000000002"
echo "  • Недостаточно средств: 4000000000000051"
echo "  • CVV: 123, срок: 12/30"
echo ""
echo "🚀 API эндпоинты:"
echo "  • Создание платежа: POST $BASE_URL/api/v1/mobile/payments/create"
echo "  • Метрики: GET $BASE_URL/api/v1/payments/metrics"
echo "  • Webhook: POST $BASE_URL/api/v1/payments/yookassa/webhook"
echo ""
echo "📖 Документация:"
echo "  • Тестирование: https://yookassa.ru/docs/support/merchant/payments/implement/test-store"
echo "  • Тестовые карты: https://yookassa.ru/developers/payment-acceptance/testing-and-going-live/testing#test-bank-card-data"
echo ""

if [[ "$PAYMENT_RESPONSE" == "200" && "$WEBHOOK_RESPONSE" == "200" ]]; then
    success "Все основные тесты пройдены! ЮKassa готова к использованию в мобильном приложении."
elif [[ "$PAYMENT_RESPONSE" == "200" ]]; then
    warning "Создание платежей работает, но есть проблемы с webhook."
else
    error "Обнаружены критические проблемы. Проверьте конфигурацию и логи."
fi

echo ""
log "Для просмотра подробных логов: docker compose logs -f app | grep -i yookassa"

# Очистка временных файлов
rm -f /tmp/health_response.json /tmp/payment_response.json /tmp/sbp_response.json /tmp/metrics_response.json /tmp/webhook_response.json