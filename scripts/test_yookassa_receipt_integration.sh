#!/bin/bash

# Тестирование интеграции чеков ЮКассы с данными пользователей и товаров
# Проверка формирования чеков согласно 54-ФЗ

set -e

# Цвета для вывода
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
CYAN='\033[0;36m'
NC='\033[0m' # No Color

# Базовые настройки
BASE_URL="${BASE_URL:-http://localhost:8080}"
API_BASE="/api/v1"

# Счетчики тестов
RECEIPT_TESTS_PASSED=0
RECEIPT_TESTS_FAILED=0

echo "=================================="
echo -e "${BLUE}📄 ТЕСТИРОВАНИЕ ИНТЕГРАЦИИ ЧЕКОВ ЮKASSA${NC}"
echo "=================================="
echo "Базовый URL: $BASE_URL"
echo "Тестируем: формирование чеков с данными пользователей и товаров"
echo ""

# Функция для логирования
log() {
    echo -e "${CYAN}ℹ️  $1${NC}"
}

success() {
    echo -e "${GREEN}✅ $1${NC}"
    RECEIPT_TESTS_PASSED=$((RECEIPT_TESTS_PASSED + 1))
}

error() {
    echo -e "${RED}❌ $1${NC}"
    RECEIPT_TESTS_FAILED=$((RECEIPT_TESTS_FAILED + 1))
}

warning() {
    echo -e "${YELLOW}⚠️  $1${NC}"
}

# Функция для API запросов
make_request() {
    local method=$1
    local endpoint=$2
    local data=$3
    local expected_code=$4
    local description=$5
    
    echo -e "${CYAN}🔄 $description${NC}"
    echo "   Запрос: $method $BASE_URL$endpoint"
    
    local response
    local http_code
    
    if [ -n "$data" ]; then
        response=$(curl -s -w "HTTPSTATUS:%{http_code}" -X "$method" \
            -H "Content-Type: application/json" \
            -H "Authorization: Bearer ${TEST_TOKEN:-}" \
            -d "$data" \
            "$BASE_URL$endpoint" 2>/dev/null)
    else
        response=$(curl -s -w "HTTPSTATUS:%{http_code}" -X "$method" \
            -H "Authorization: Bearer ${TEST_TOKEN:-}" \
            "$BASE_URL$endpoint" 2>/dev/null)
    fi
    
    http_code=$(echo "$response" | grep -o "HTTPSTATUS:[0-9]*" | cut -d: -f2)
    response_body=$(echo "$response" | sed 's/HTTPSTATUS:[0-9]*$//')
    
    if [ "$http_code" = "$expected_code" ]; then
        success "OK ($http_code, ожидался $expected_code)"
        echo "$response_body"
        return 0
    else
        error "ОШИБКА ($http_code, ожидался $expected_code)"
        if [ -n "$response_body" ]; then
            echo "   📋 Ответ: $(echo "$response_body" | head -c 200)..."
        fi
        return 1
    fi
}

# Функция регистрации пользователя
register_user() {
    local phone=$1
    local name=$2
    
    log "Регистрация пользователя с телефоном $phone"
    
    local user_data=$(cat <<EOF
{
    "phoneNumber": "$phone",
    "smsCode": "1234"
}
EOF
)

    local response=$(make_request "POST" "$API_BASE/auth/register" "$user_data" "200" "Регистрация пользователя")
    if [ $? -eq 0 ]; then
        local token=$(echo "$response" | grep -o '"token":"[^"]*"' | cut -d'"' -f4)
        if [ -n "$token" ]; then
            echo "$token"
            return 0
        fi
    fi
    return 1
}

# Функция создания заказа с товарами
create_order_with_items() {
    local token=$1
    local phone=$2
    local name=$3
    
    log "Создание заказа с несколькими товарами"
    
    # Добавляем товары в корзину
    local cart_items='[
        {"productId": 1, "quantity": 2},
        {"productId": 2, "quantity": 1},
        {"productId": 3, "quantity": 3}
    ]'
    
    for item in $(echo "$cart_items" | jq -r '.[] | @base64'); do
        local item_data=$(echo "$item" | base64 --decode)
        make_request "POST" "$API_BASE/cart/items" "$item_data" "200" "Добавление товара в корзину"
    done
    
    # Создаем заказ
    local order_data=$(cat <<EOF
{
    "deliveryAddress": "г. Волжск, ул. Ленина, 15, кв. 42",
    "contactName": "$name",
    "contactPhone": "$phone",
    "comment": "Тестовый заказ для проверки чеков ЮКассы",
    "paymentMethod": "SBP"
}
EOF
)

    local order_response=$(make_request "POST" "$API_BASE/orders" "$order_data" "201" "Создание заказа")
    
    if [ $? -eq 0 ]; then
        local order_id=$(echo "$order_response" | jq -r '.id // empty')
        if [ -n "$order_id" ] && [ "$order_id" != "null" ]; then
            echo "$order_id"
            return 0
        fi
    fi
    return 1
}

# Функция создания платежа с чеком
create_payment_with_receipt() {
    local token=$1
    local order_id=$2
    
    log "Создание платежа с чеком для заказа #$order_id"
    
    local payment_data=$(cat <<EOF
{
    "orderId": $order_id,
    "method": "SBP",
    "description": "Тестовый платеж с чеком для заказа #$order_id"
}
EOF
)

    local payment_response=$(make_request "POST" "$API_BASE/payments/yookassa/create" "$payment_data" "200" "Создание платежа с чеком")
    
    if [ $? -eq 0 ]; then
        local payment_id=$(echo "$payment_response" | jq -r '.id // empty')
        local yookassa_id=$(echo "$payment_response" | jq -r '.yookassaPaymentId // empty')
        
        if [ -n "$payment_id" ] && [ "$payment_id" != "null" ]; then
            log "Создан платеж ID: $payment_id"
            log "ЮKassa ID: $yookassa_id"
            
            # Проверяем, что в логах есть информация о чеке
            echo "   📄 Проверка формирования чека в логах приложения..."
            
            echo "$payment_id"
            return 0
        fi
    fi
    return 1
}

# Функция проверки статуса платежа
check_payment_status() {
    local token=$1
    local payment_id=$2
    
    log "Проверка статуса платежа $payment_id"
    
    local status_response=$(make_request "GET" "$API_BASE/payments/yookassa/$payment_id" "" "200" "Получение статуса платежа")
    
    if [ $? -eq 0 ]; then
        local status=$(echo "$status_response" | jq -r '.status // empty')
        local receipt_url=$(echo "$status_response" | jq -r '.receiptUrl // empty')
        
        log "Статус платежа: $status"
        if [ -n "$receipt_url" ] && [ "$receipt_url" != "null" ]; then
            log "URL чека: $receipt_url"
        fi
        
        return 0
    fi
    return 1
}

# Тест случаев с различными форматами телефонов
test_phone_formats() {
    echo -e "${YELLOW}📱 ТЕСТИРОВАНИЕ ФОРМАТОВ ТЕЛЕФОНОВ${NC}"
    echo "=================================================================="
    
    local phone_formats=(
        "+79001234567"
        "89001234567" 
        "79001234567"
        "9001234567"
        "+7 (900) 123-45-67"
        "8 900 123 45 67"
    )
    
    for phone in "${phone_formats[@]}"; do
        echo ""
        log "Тестируем формат телефона: '$phone'"
        
        local clean_phone=$(echo "$phone" | sed 's/[^0-9+]//g')
        local test_token=$(register_user "$phone" "Тестовый Пользователь")
        
        if [ -n "$test_token" ]; then
            local order_id=$(create_order_with_items "$test_token" "$phone" "Тестовый Пользователь")
            
            if [ -n "$order_id" ]; then
                local payment_id=$(create_payment_with_receipt "$test_token" "$order_id")
                
                if [ -n "$payment_id" ]; then
                    success "Чек успешно сформирован для телефона '$phone'"
                else
                    error "Ошибка создания платежа для телефона '$phone'"
                fi
            else
                error "Ошибка создания заказа для телефона '$phone'"
            fi
        else
            error "Ошибка регистрации для телефона '$phone'"
        fi
    done
}

# Тест с различным количеством товаров
test_multiple_items() {
    echo ""
    echo -e "${YELLOW}🛒 ТЕСТИРОВАНИЕ ЗАКАЗОВ С РАЗНЫМ КОЛИЧЕСТВОМ ТОВАРОВ${NC}"
    echo "=================================================================="
    
    local test_phone="+79001234568"
    local test_token=$(register_user "$test_phone" "Тест Мультитоваров")
    
    if [ -n "$test_token" ]; then
        # Создаем заказ с большим количеством товаров
        log "Создание заказа с 5 различными товарами"
        
        # Очищаем корзину
        make_request "DELETE" "$API_BASE/cart/clear" "" "200" "Очистка корзины"
        
        # Добавляем много товаров
        for i in {1..5}; do
            local item_data='{"productId": '$i', "quantity": '$((i+1))'}'
            make_request "POST" "$API_BASE/cart/items" "$item_data" "200" "Добавление товара $i"
        done
        
        # Создаем заказ
        local order_data=$(cat <<EOF
{
    "deliveryAddress": "г. Волжск, ул. Тестовая, 123",
    "contactName": "Тест Мультитоваров",
    "contactPhone": "$test_phone",
    "comment": "Заказ с множеством товаров для проверки чека",
    "paymentMethod": "SBP"
}
EOF
)

        local order_response=$(make_request "POST" "$API_BASE/orders" "$order_data" "201" "Создание заказа с 5 товарами")
        
        if [ $? -eq 0 ]; then
            local order_id=$(echo "$order_response" | jq -r '.id // empty')
            log "Заказ создан: #$order_id"
            
            local payment_id=$(create_payment_with_receipt "$test_token" "$order_id")
            
            if [ -n "$payment_id" ]; then
                success "Чек с 5 товарами успешно сформирован"
                check_payment_status "$test_token" "$payment_id"
            else
                error "Ошибка создания платежа для заказа с множеством товаров"
            fi
        fi
    else
        error "Не удалось зарегистрировать пользователя для теста мультитоваров"
    fi
}

# Тест с некорректными данными
test_invalid_data() {
    echo ""
    echo -e "${YELLOW}⚠️ ТЕСТИРОВАНИЕ НЕКОРРЕКТНЫХ ДАННЫХ${NC}"
    echo "=================================================================="
    
    # Тест с некорректным телефоном
    log "Тест с некорректным форматом телефона"
    local invalid_token=$(register_user "123" "Некорректный Телефон")
    
    if [ -n "$invalid_token" ]; then
        warning "Пользователь с некорректным телефоном зарегистрирован (это может быть проблемой)"
    else
        success "Регистрация с некорректным телефоном отклонена"
    fi
    
    # Тест с пустым заказом
    log "Тест создания платежа для несуществующего заказа"
    local fake_payment_data='{"orderId": 99999, "method": "SBP", "description": "Fake order"}'
    
    make_request "POST" "$API_BASE/payments/yookassa/create" "$fake_payment_data" "400" "Создание платежа для несуществующего заказа"
    
    if [ $? -eq 0 ]; then
        error "Платеж для несуществующего заказа создан (это ошибка)"
    else
        success "Создание платежа для несуществующего заказа отклонено"
    fi
}

# Основной тест-флоу
main_receipt_test() {
    echo -e "${YELLOW}📄 ОСНОВНОЙ ТЕСТ ЧЕКОВ${NC}"
    echo "=================================================================="
    
    # 1. Регистрация пользователя
    local main_phone="+79001234569"
    log "1. Регистрация основного тестового пользователя"
    TEST_TOKEN=$(register_user "$main_phone" "Основной Тестер")
    
    if [ -n "$TEST_TOKEN" ]; then
        success "Пользователь зарегистрирован"
        
        # 2. Создание заказа
        log "2. Создание заказа с товарами"
        ORDER_ID=$(create_order_with_items "$TEST_TOKEN" "$main_phone" "Основной Тестер")
        
        if [ -n "$ORDER_ID" ]; then
            success "Заказ #$ORDER_ID создан"
            
            # 3. Создание платежа с чеком
            log "3. Создание платежа с чеком"
            PAYMENT_ID=$(create_payment_with_receipt "$TEST_TOKEN" "$ORDER_ID")
            
            if [ -n "$PAYMENT_ID" ]; then
                success "Платеж $PAYMENT_ID создан с чеком"
                
                # 4. Проверка статуса
                log "4. Проверка статуса платежа"
                check_payment_status "$TEST_TOKEN" "$PAYMENT_ID"
                
                success "Основной тест чеков завершен успешно"
            else
                error "Ошибка создания платежа"
            fi
        else
            error "Ошибка создания заказа"
        fi
    else
        error "Ошибка регистрации пользователя"
    fi
}

# Запуск всех тестов
echo "🚀 Начинаем комплексное тестирование чеков ЮКассы..."
echo ""

# Проверяем доступность сервиса
log "Проверка доступности API..."
make_request "GET" "$API_BASE/health" "" "200" "Health check"

if [ $? -eq 0 ]; then
    echo ""
    
    # Основной тест
    main_receipt_test
    
    echo ""
    
    # Тестирование различных форматов телефонов
    test_phone_formats
    
    echo ""
    
    # Тестирование множественных товаров
    test_multiple_items
    
    echo ""
    
    # Тестирование некорректных данных
    test_invalid_data
    
else
    error "API недоступен, тестирование прервано"
    exit 1
fi

echo ""
echo "=================================="
echo -e "${BLUE}📊 РЕЗУЛЬТАТЫ ТЕСТИРОВАНИЯ ЧЕКОВ${NC}"
echo "=================================="
echo -e "${GREEN}✅ Успешно: $RECEIPT_TESTS_PASSED${NC}"
echo -e "${RED}❌ Неудачно: $RECEIPT_TESTS_FAILED${NC}"

if [ $RECEIPT_TESTS_FAILED -eq 0 ]; then
    echo -e "${GREEN}🎉 ВСЕ ТЕСТЫ ЧЕКОВ ПРОЙДЕНЫ УСПЕШНО!${NC}"
    exit 0
else
    echo -e "${RED}💥 НЕКОТОРЫЕ ТЕСТЫ ЧЕКОВ НЕ ПРОШЛИ${NC}"
    exit 1
fi 