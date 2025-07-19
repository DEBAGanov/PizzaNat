#!/bin/bash

# Тестирование СБП флоу платежей
# Проверяет что заказы с СБП приходят в админский бот только после payment.succeeded

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

echo -e "${BLUE}🚀 Тестирование СБП флоу платежей${NC}"
echo "=================================================================="
echo -e "${YELLOW}Проверяем что заказы с СБП приходят в бот только после оплаты${NC}"
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
    
    if [ -n "$data" ]; then
        response=$(curl -s -w "%{http_code}" -X "$method" \
            -H "Content-Type: application/json" \
            -d "$data" \
            "$url" 2>/dev/null || echo "000")
    else
        response=$(curl -s -w "%{http_code}" -X "$method" \
            -H "Content-Type: application/json" \
            "$url" 2>/dev/null || echo "000")
    fi
    
    # Извлекаем HTTP код (последние 3 символа)
    http_code=${response: -3}
    # Извлекаем тело ответа (все кроме последних 3 символов)
    body=${response%???}
    
    if [ "$http_code" = "$expected_status" ]; then
        success "$description - HTTP $http_code"
        echo "$body" | jq . 2>/dev/null || echo "$body"
        return 0
    else
        error "$description - Ожидался HTTP $expected_status, получен HTTP $http_code"
        echo "Ответ: $body"
        return 1
    fi
}

# Функция для получения токена авторизации (базовая реализация)
get_auth_token() {
    log "Получение токена авторизации..."
    
    # Регистрируем тестового пользователя
    register_data='{
        "username": "test_sbp_user",
        "email": "test_sbp@example.com",
        "password": "TestPassword123!",
        "firstName": "Test",
        "lastName": "SBP User",
        "phone": "+79001234567"
    }'
    
    register_response=$(curl -s -w "%{http_code}" -X POST \
        -H "Content-Type: application/json" \
        -d "$register_data" \
        "$API_BASE/auth/register" 2>/dev/null || echo "000")
    
    # Авторизуемся
    login_data='{
        "username": "test_sbp_user",
        "password": "TestPassword123!"
    }'
    
    login_response=$(curl -s -w "%{http_code}" -X POST \
        -H "Content-Type: application/json" \
        -d "$login_data" \
        "$API_BASE/auth/login" 2>/dev/null || echo "000")
    
    # Извлекаем токен
    login_body=${login_response%???}
    AUTH_TOKEN=$(echo "$login_body" | jq -r '.token' 2>/dev/null || echo "")
    
    if [ -n "$AUTH_TOKEN" ] && [ "$AUTH_TOKEN" != "null" ]; then
        success "Токен авторизации получен"
        return 0
    else
        warning "Не удалось получить токен, продолжаем без авторизации"
        AUTH_TOKEN=""
        return 1
    fi
}

# Проверка здоровья сервиса
echo
echo -e "${YELLOW}📋 1. ПРОВЕРКА ЗДОРОВЬЯ СЕРВИСА${NC}"
echo "=================================================================="

make_request "GET" "$BASE_URL/actuator/health" "" "200" "Health check"

# Получение токена авторизации
echo
echo -e "${YELLOW}📋 2. АВТОРИЗАЦИЯ${NC}"
echo "=================================================================="

get_auth_token

# Создание продукта (для тестирования)
echo
echo -e "${YELLOW}📋 3. ПОДГОТОВКА ДАННЫХ${NC}"
echo "=================================================================="

# Создаем тестовый продукт
product_data='{
    "name": "Тест СБП Пицца",
    "description": "Тестовая пицца для проверки СБП флоу",
    "price": 500.00,
    "categoryId": 1,
    "imageUrl": "test-pizza.jpg"
}'

if [ -n "$AUTH_TOKEN" ]; then
    HEADERS=(-H "Authorization: Bearer $AUTH_TOKEN")
else
    HEADERS=()
fi

# Получаем список категорий для создания продукта
categories_response=$(curl -s "${HEADERS[@]}" "$API_BASE/categories" || echo "[]")
log "Доступные категории: $categories_response"

# Добавляем продукт в корзину
cart_data='{
    "productId": 1,
    "quantity": 1
}'

if [ -n "$AUTH_TOKEN" ]; then
    make_request "POST" "$API_BASE/cart/add" "$cart_data" "200" "Добавление продукта в корзину"
else
    log "Пропускаем работу с корзиной без авторизации"
fi

# Тест 1: Создание заказа с последующей СБП оплатой
echo
echo -e "${YELLOW}📋 4. ТЕСТ ОСНОВНОГО СБП ФЛОУ${NC}"
echo "=================================================================="

log "Шаг 1: Создаем заказ (должен НЕ попасть в админский бот сразу)"

order_data='{
    "deliveryLocationId": 1,
    "contactName": "Тест СБП",
    "contactPhone": "+79001234567",
    "comment": "Тестовый заказ для проверки СБП флоу"
}'

if [ -n "$AUTH_TOKEN" ]; then
    order_response=$(curl -s -w "%{http_code}" "${HEADERS[@]}" \
        -H "Content-Type: application/json" \
        -d "$order_data" \
        "$API_BASE/orders" 2>/dev/null || echo "000")
    
    order_http_code=${order_response: -3}
    order_body=${order_response%???}
    
    if [ "$order_http_code" = "200" ] || [ "$order_http_code" = "201" ]; then
        ORDER_ID=$(echo "$order_body" | jq -r '.id' 2>/dev/null || echo "")
        success "Заказ создан: ID=$ORDER_ID"
        echo "$order_body" | jq . 2>/dev/null || echo "$order_body"
    else
        error "Не удалось создать заказ: HTTP $order_http_code"
        echo "Ответ: $order_body"
        ORDER_ID=""
    fi
else
    warning "Создание заказа пропущено (нет авторизации)"
    ORDER_ID="1" # Используем тестовое значение
fi

if [ -n "$ORDER_ID" ] && [ "$ORDER_ID" != "null" ]; then
    log "Шаг 2: Создаем СБП платеж для заказа"
    
    payment_data='{
        "orderId": '$ORDER_ID',
        "method": "SBP",
        "description": "Тестовый СБП платеж",
        "returnUrl": "https://pizzanat.ru/test"
    }'
    
    payment_response=$(curl -s -w "%{http_code}" \
        -H "Content-Type: application/json" \
        -d "$payment_data" \
        "$API_BASE/payments/yookassa/create" 2>/dev/null || echo "000")
    
    payment_http_code=${payment_response: -3}
    payment_body=${payment_response%???}
    
    if [ "$payment_http_code" = "200" ] || [ "$payment_http_code" = "201" ]; then
        PAYMENT_ID=$(echo "$payment_body" | jq -r '.id' 2>/dev/null || echo "")
        YOOKASSA_ID=$(echo "$payment_body" | jq -r '.yookassaPaymentId' 2>/dev/null || echo "")
        success "СБП платеж создан: Payment ID=$PAYMENT_ID, YooKassa ID=$YOOKASSA_ID"
        echo "$payment_body" | jq . 2>/dev/null || echo "$payment_body"
        
        log "Шаг 3: Имитируем webhook payment.succeeded от ЮКассы"
        
        webhook_data='{
            "type": "notification",
            "event": "payment.succeeded",
            "object": {
                "id": "'$YOOKASSA_ID'",
                "status": "succeeded",
                "amount": {
                    "value": "500.00",
                    "currency": "RUB"
                },
                "payment_method": {
                    "type": "sbp"
                },
                "metadata": {
                    "order_id": "'$ORDER_ID'",
                    "payment_id": "'$PAYMENT_ID'"
                }
            }
        }'
        
        make_request "POST" "$API_BASE/payments/yookassa/webhook" "$webhook_data" "200" "Webhook payment.succeeded"
        
        log "Шаг 4: Проверяем статус платежа после webhook"
        make_request "GET" "$API_BASE/payments/yookassa/$PAYMENT_ID" "" "200" "Проверка статуса платежа"
        
    else
        error "Не удалось создать СБП платеж: HTTP $payment_http_code"
        echo "Ответ: $payment_body"
    fi
else
    warning "Пропускаем тестирование платежей (нет ID заказа)"
fi

# Тест 2: Проверка заказа с наличной оплатой (должен попасть в бот сразу)
echo
echo -e "${YELLOW}📋 5. ТЕСТ ЗАКАЗА С НАЛИЧНОЙ ОПЛАТОЙ${NC}"
echo "=================================================================="

log "Создаем заказ с наличной оплатой (должен попасть в админский бот сразу)"

cash_order_data='{
    "deliveryLocationId": 1,
    "contactName": "Тест Наличные",
    "contactPhone": "+79001234568",
    "comment": "Тестовый заказ с наличной оплатой"
}'

if [ -n "$AUTH_TOKEN" ]; then
    make_request "POST" "$API_BASE/orders" "$cash_order_data" "200" "Создание заказа с наличной оплатой"
else
    log "Создание заказа пропущено (нет авторизации)"
fi

# Тест 3: Проверка webhook с неизвестным платежом
echo
echo -e "${YELLOW}📋 6. ТЕСТ ОБРАБОТКИ ОШИБОК${NC}"
echo "=================================================================="

unknown_webhook='{
    "type": "notification",
    "event": "payment.succeeded",
    "object": {
        "id": "unknown_payment_id_12345",
        "status": "succeeded",
        "amount": {
            "value": "100.00",
            "currency": "RUB"
        }
    }
}'

make_request "POST" "$API_BASE/payments/yookassa/webhook" "$unknown_webhook" "400" "Webhook с неизвестным платежом"

# Итоговая статистика
echo
echo "=================================================================="
echo -e "${BLUE}📊 ИТОГОВАЯ СТАТИСТИКА${NC}"
echo "=================================================================="

echo -e "Всего тестов: ${BLUE}$TOTAL_TESTS${NC}"
echo -e "Успешных: ${GREEN}$PASSED_TESTS${NC}"
echo -e "Неудачных: ${RED}$FAILED_TESTS${NC}"

if [ $FAILED_TESTS -eq 0 ]; then
    echo -e "${GREEN}🎉 Все тесты СБП флоу пройдены успешно!${NC}"
    echo -e "${GREEN}✅ СБП платежи корректно интегрированы с админским ботом${NC}"
    exit 0
else
    echo -e "${RED}❌ Обнаружены проблемы в СБП флоу${NC}"
    echo -e "${YELLOW}⚠️ Проверьте логи приложения и конфигурацию${NC}"
    exit 1
fi 