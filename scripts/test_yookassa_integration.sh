#!/bin/bash

# Тестирование интеграции ЮKassa
# Проверяет все основные функции платежной системы

set -e

# Цвета для вывода
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
NC='\033[0m' # No Color

# Настройки
BASE_URL="http://localhost:8080"
API_BASE="$BASE_URL/api/v1"

# Счетчики
TOTAL_TESTS=0
PASSED_TESTS=0
FAILED_TESTS=0

echo -e "${BLUE}🚀 Тестирование интеграции ЮKassa${NC}"
echo "=================================================================="

# Функция для логирования
log() {
    echo -e "${BLUE}[$(date +'%H:%M:%S')]${NC} $1"
}

success() {
    echo -e "${GREEN}✅ $1${NC}"
    ((PASSED_TESTS++))
}

error() {
    echo -e "${RED}❌ $1${NC}"
    ((FAILED_TESTS++))
}

warning() {
    echo -e "${YELLOW}⚠️ $1${NC}"
}

# Функция для выполнения HTTP запроса
make_request() {
    local method=$1
    local url=$2
    local data=$3
    local expected_status=$4
    local description=$5

    ((TOTAL_TESTS++))
    
    log "Тест: $description"
    log "Запрос: $method $url"
    
    if [ -n "$data" ]; then
        response=$(curl -s -w "\n%{http_code}" -X "$method" \
            -H "Content-Type: application/json" \
            -H "Accept: application/json" \
            -d "$data" \
            "$url" 2>/dev/null || echo -e "\n000")
    else
        response=$(curl -s -w "\n%{http_code}" -X "$method" \
            -H "Accept: application/json" \
            "$url" 2>/dev/null || echo -e "\n000")
    fi
    
    # Разделяем ответ и код статуса
    http_code=$(echo "$response" | tail -n1)
    body=$(echo "$response" | head -n -1)
    
    log "Код ответа: $http_code"
    log "Тело ответа: $body"
    
    if [ "$http_code" = "$expected_status" ]; then
        success "$description - HTTP $http_code"
        echo "$body"
        return 0
    else
        error "$description - Ожидался HTTP $expected_status, получен HTTP $http_code"
        return 1
    fi
}

# Функция для создания тестового заказа
create_test_order() {
    log "Создание тестового заказа для платежа..."
    
    local order_data='{
        "items": [
            {
                "productId": 1,
                "quantity": 2
            }
        ],
        "deliveryAddress": "г. Волжск, ул. Ленина, 15",
        "contactName": "Тест Пользователь",
        "contactPhone": "+79001234567",
        "comment": "Тестовый заказ для ЮKassa"
    }'
    
    local response=$(make_request "POST" "$API_BASE/orders" "$order_data" "201" "Создание тестового заказа")
    
    if [ $? -eq 0 ]; then
        ORDER_ID=$(echo "$response" | jq -r '.id' 2>/dev/null)
        if [ "$ORDER_ID" != "null" ] && [ -n "$ORDER_ID" ]; then
            log "Создан тестовый заказ ID: $ORDER_ID"
            return 0
        fi
    fi
    
    error "Не удалось создать тестовый заказ"
    return 1
}

echo
echo -e "${YELLOW}📋 1. ПРОВЕРКА ДОСТУПНОСТИ СЕРВИСА${NC}"
echo "=================================================================="

# 1. Health check
make_request "GET" "$API_BASE/payments/yookassa/health" "" "200" "Health check ЮKassa"

echo
echo -e "${YELLOW}📋 2. ПОЛУЧЕНИЕ СПИСКА БАНКОВ СБП${NC}"
echo "=================================================================="

# 2. Список банков СБП
banks_response=$(make_request "GET" "$API_BASE/payments/yookassa/sbp/banks" "" "200" "Получение списка банков СБП")

if [ $? -eq 0 ]; then
    bank_count=$(echo "$banks_response" | jq '. | length' 2>/dev/null)
    if [ "$bank_count" -gt 0 ]; then
        success "Получено $bank_count банков СБП"
        echo "$banks_response" | jq '.[] | {id: .bankId, name: .bankName}' 2>/dev/null
    else
        warning "Список банков СБП пуст"
    fi
fi

echo
echo -e "${YELLOW}📋 3. СОЗДАНИЕ ТЕСТОВОГО ЗАКАЗА${NC}"
echo "=================================================================="

# 3. Создание тестового заказа
if ! create_test_order; then
    error "Тестирование прервано - не удалось создать заказ"
    exit 1
fi

echo
echo -e "${YELLOW}📋 4. СОЗДАНИЕ ПЛАТЕЖЕЙ${NC}"
echo "=================================================================="

# 4.1 Создание СБП платежа без указания банка
log "4.1 Создание СБП платежа без банка"
sbp_payment_data='{
    "orderId": '$ORDER_ID',
    "method": "SBP",
    "description": "Тестовый СБП платеж"
}'

sbp_response=$(make_request "POST" "$API_BASE/payments/yookassa/create" "$sbp_payment_data" "200" "Создание СБП платежа")

if [ $? -eq 0 ]; then
    PAYMENT_ID_1=$(echo "$sbp_response" | jq -r '.id' 2>/dev/null)
    YOOKASSA_ID_1=$(echo "$sbp_response" | jq -r '.yookassaPaymentId' 2>/dev/null)
    CONFIRMATION_URL_1=$(echo "$sbp_response" | jq -r '.confirmationUrl' 2>/dev/null)
    
    log "Создан платеж ID: $PAYMENT_ID_1"
    log "ЮKassa ID: $YOOKASSA_ID_1"
    log "URL подтверждения: $CONFIRMATION_URL_1"
fi

# 4.2 Создание СБП платежа с указанием банка
log "4.2 Создание СБП платежа с банком Сбербанк"
sbp_sber_data='{
    "orderId": '$ORDER_ID',
    "method": "SBP",
    "bankId": "sberbank",
    "description": "Тестовый СБП платеж через Сбербанк"
}'

sbp_sber_response=$(make_request "POST" "$API_BASE/payments/yookassa/create" "$sbp_sber_data" "200" "Создание СБП платежа с банком")

if [ $? -eq 0 ]; then
    PAYMENT_ID_2=$(echo "$sbp_sber_response" | jq -r '.id' 2>/dev/null)
    log "Создан платеж с банком ID: $PAYMENT_ID_2"
fi

# 4.3 Попытка создания платежа для уже оплаченного заказа (должна вернуть ошибку)
log "4.3 Попытка создания дублирующего платежа"
duplicate_data='{
    "orderId": '$ORDER_ID',
    "method": "SBP"
}'

make_request "POST" "$API_BASE/payments/yookassa/create" "$duplicate_data" "422" "Создание дублирующего платежа (ожидается ошибка)"

echo
echo -e "${YELLOW}📋 5. ПОЛУЧЕНИЕ ИНФОРМАЦИИ О ПЛАТЕЖАХ${NC}"
echo "=================================================================="

if [ -n "$PAYMENT_ID_1" ]; then
    # 5.1 Получение платежа по ID
    make_request "GET" "$API_BASE/payments/yookassa/$PAYMENT_ID_1" "" "200" "Получение платежа по ID"
    
    # 5.2 Получение платежей для заказа
    make_request "GET" "$API_BASE/payments/yookassa/order/$ORDER_ID" "" "200" "Получение платежей для заказа"
fi

if [ -n "$YOOKASSA_ID_1" ]; then
    # 5.3 Получение платежа по ЮKassa ID
    make_request "GET" "$API_BASE/payments/yookassa/yookassa/$YOOKASSA_ID_1" "" "200" "Получение платежа по ЮKassa ID"
fi

echo
echo -e "${YELLOW}📋 6. ПРОВЕРКА СТАТУСА ПЛАТЕЖЕЙ${NC}"
echo "=================================================================="

if [ -n "$PAYMENT_ID_1" ]; then
    # 6.1 Проверка статуса платежа
    status_response=$(make_request "POST" "$API_BASE/payments/yookassa/$PAYMENT_ID_1/check-status" "" "200" "Проверка статуса платежа")
    
    if [ $? -eq 0 ]; then
        status=$(echo "$status_response" | jq -r '.status' 2>/dev/null)
        log "Текущий статус платежа: $status"
    fi
fi

echo
echo -e "${YELLOW}📋 7. ОТМЕНА ПЛАТЕЖА${NC}"
echo "=================================================================="

if [ -n "$PAYMENT_ID_2" ]; then
    # 7.1 Отмена платежа
    cancel_response=$(make_request "POST" "$API_BASE/payments/yookassa/$PAYMENT_ID_2/cancel" "" "200" "Отмена платежа")
    
    if [ $? -eq 0 ]; then
        cancelled_status=$(echo "$cancel_response" | jq -r '.status' 2>/dev/null)
        log "Статус после отмены: $cancelled_status"
    fi
fi

echo
echo -e "${YELLOW}📋 8. ТЕСТИРОВАНИЕ ОШИБОЧНЫХ ЗАПРОСОВ${NC}"
echo "=================================================================="

# 8.1 Платеж для несуществующего заказа
error_data='{
    "orderId": 999999,
    "method": "SBP"
}'

make_request "POST" "$API_BASE/payments/yookassa/create" "$error_data" "400" "Платеж для несуществующего заказа"

# 8.2 Получение несуществующего платежа
make_request "GET" "$API_BASE/payments/yookassa/999999" "" "404" "Получение несуществующего платежа"

# 8.3 Проверка статуса несуществующего платежа
make_request "POST" "$API_BASE/payments/yookassa/999999/check-status" "" "404" "Проверка статуса несуществующего платежа"

echo
echo -e "${YELLOW}📋 9. ТЕСТИРОВАНИЕ WEBHOOK'А${NC}"
echo "=================================================================="

# 9.1 Тестовый webhook успешного платежа
webhook_data='{
    "type": "notification",
    "event": "payment.succeeded",
    "object": {
        "id": "'$YOOKASSA_ID_1'",
        "status": "succeeded",
        "amount": {
            "value": "1000.00",
            "currency": "RUB"
        },
        "metadata": {
            "order_id": "'$ORDER_ID'",
            "payment_id": "'$PAYMENT_ID_1'"
        }
    }
}'

make_request "POST" "$API_BASE/payments/yookassa/webhook" "$webhook_data" "200" "Webhook успешного платежа"

# 9.2 Тестовый webhook с неизвестным платежом
webhook_unknown='{
    "type": "notification",
    "event": "payment.succeeded",
    "object": {
        "id": "unknown_payment_id",
        "status": "succeeded"
    }
}'

make_request "POST" "$API_BASE/payments/yookassa/webhook" "$webhook_unknown" "400" "Webhook с неизвестным платежом"

echo
echo "=================================================================="
echo -e "${BLUE}📊 ИТОГОВАЯ СТАТИСТИКА${NC}"
echo "=================================================================="

echo -e "Всего тестов: ${BLUE}$TOTAL_TESTS${NC}"
echo -e "Успешных: ${GREEN}$PASSED_TESTS${NC}"
echo -e "Неудачных: ${RED}$FAILED_TESTS${NC}"

if [ $FAILED_TESTS -eq 0 ]; then
    echo -e "${GREEN}🎉 Все тесты пройдены успешно!${NC}"
    echo -e "${GREEN}✅ ЮKassa интеграция работает корректно${NC}"
    exit 0
else
    echo -e "${RED}❌ Обнаружены проблемы в интеграции ЮKassa${NC}"
    echo -e "${YELLOW}⚠️ Проверьте конфигурацию и логи приложения${NC}"
    exit 1
fi 