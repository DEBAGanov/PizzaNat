#!/bin/bash

echo "🚀 Тестирование исправленной секции СБП из comprehensive теста"

BASE_URL="http://localhost:8080"

GREEN='\033[0;32m'
RED='\033[0;31m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
CYAN='\033[0;36m'
NC='\033[0m'

TOTAL_TESTS=0
PASSED_TESTS=0
FAILED_TESTS=0

# Получаем токен авторизации (используем существующего пользователя или создаем нового)
echo -e "${YELLOW}Получение токена авторизации...${NC}"

# Создаем уникальный timestamp для тестового пользователя
timestamp=$(date +%s)

# Регистрируем тестового пользователя
register_data='{
    "username": "test_comp_user_'$timestamp'",
    "email": "test_comp'$timestamp'@example.com",
    "password": "TestPassword123!",
    "firstName": "Test",
    "lastName": "Comprehensive User",
    "phone": "+79001234567"
}'

register_response=$(curl -s -w "%{http_code}" -X POST \
    -H "Content-Type: application/json" \
    -d "$register_data" \
    "$BASE_URL/api/v1/auth/register")

# Авторизуемся
username="test_comp_user_$timestamp"
login_data='{
    "username": "'$username'",
    "password": "TestPassword123!"
}'

login_response=$(curl -s -w "%{http_code}" -X POST \
    -H "Content-Type: application/json" \
    -d "$login_data" \
    "$BASE_URL/api/v1/auth/login")

login_http_code=${login_response: -3}
login_body=${login_response%???}

if [ "$login_http_code" = "200" ]; then
    JWT_TOKEN=$(echo "$login_body" | grep -o '"token":"[^"]*' | cut -d'"' -f4)
    echo -e "${GREEN}✅ Токен авторизации получен${NC}"
else
    echo -e "${RED}❌ Ошибка получения токена (HTTP $login_http_code)${NC}"
    echo "$login_body"
    exit 1
fi

# 11.5. СБП ФЛОУ ТЕСТЫ - исправленная версия из comprehensive
echo -e "${BLUE}📱 11.5. СБП ФЛОУ ТЕСТЫ (исправленная версия)${NC}"
echo -e "${CYAN}Проверяем что заказы с СБП приходят в админский бот только после оплаты${NC}"

if [ -n "$JWT_TOKEN" ]; then
    echo -e "${YELLOW}Тест 1: Создание заказа с СБП (должен НЕ попасть в бот сразу)${NC}"
    
    # Добавляем товар в корзину сначала
    cart_data='{
        "productId": 1,
        "quantity": 1
    }'
    
    cart_response=$(curl -s -w "%{http_code}" -X POST "$BASE_URL/api/v1/cart/items" \
        -H "Content-Type: application/json" \
        -H "Authorization: Bearer $JWT_TOKEN" \
        -d "$cart_data")
    
    cart_http_code=${cart_response: -3}
    
    if [ "$cart_http_code" = "200" ]; then
        echo -e "${GREEN}✅ Товар добавлен в корзину для СБП теста${NC}"
    else
        echo -e "${YELLOW}⚠️ Не удалось добавить товар в корзину (HTTP $cart_http_code), продолжаем...${NC}"
    fi
    
    # Создаем заказ для СБП теста
    sbp_order_data='{
        "deliveryLocationId": 1,
        "contactName": "СБП Тест",
        "contactPhone": "+79001234567",
        "comment": "Тестовый заказ для проверки СБП флоу",
        "paymentMethod": "SBP"
    }'
    
    echo -e "${CYAN}📦 Создание заказа для СБП теста...${NC}"
    sbp_order_response=$(curl -s -w "%{http_code}" -X POST "$BASE_URL/api/v1/orders" \
        -H "Content-Type: application/json" \
        -H "Authorization: Bearer $JWT_TOKEN" \
        -d "$sbp_order_data")
    
    sbp_order_http_code=${sbp_order_response: -3}
    sbp_order_body=${sbp_order_response%???}
    
    if [ "$sbp_order_http_code" = "200" ] || [ "$sbp_order_http_code" = "201" ]; then
        SBP_ORDER_ID=$(echo "$sbp_order_body" | grep -o '"id":[0-9]*' | cut -d':' -f2)
    else
        SBP_ORDER_ID=""
    fi
    
    if [ -n "$SBP_ORDER_ID" ] && [ "$SBP_ORDER_ID" != "null" ]; then
        echo -e "${GREEN}✅ СБП заказ #$SBP_ORDER_ID создан${NC}"
        PASSED_TESTS=$((PASSED_TESTS + 1))
        
        echo -e "${YELLOW}Тест 2: Создание СБП платежа для заказа${NC}"
        
        # Создаем СБП платеж
        sbp_payment_data='{
            "orderId": '$SBP_ORDER_ID',
            "method": "SBP",
            "description": "Тестовый СБП платеж для проверки флоу",
            "returnUrl": "https://pizzanat.ru/test"
        }'
        
        sbp_payment_response=$(curl -s -w "%{http_code}" -X POST "$BASE_URL/api/v1/payments/yookassa/create" \
            -H "Content-Type: application/json" \
            -d "$sbp_payment_data")
        
        sbp_payment_http_code=${sbp_payment_response: -3}
        sbp_payment_body=${sbp_payment_response%???}
        
        if [ "$sbp_payment_http_code" = "200" ] || [ "$sbp_payment_http_code" = "201" ]; then
            SBP_PAYMENT_ID=$(echo "$sbp_payment_body" | grep -o '"id":[0-9]*' | cut -d':' -f2)
            SBP_YOOKASSA_ID=$(echo "$sbp_payment_body" | grep -o '"yookassaPaymentId":"[^"]*' | cut -d'"' -f4)
            
            echo -e "${GREEN}✅ СБП платеж создан: ID=$SBP_PAYMENT_ID, YooKassa ID=$SBP_YOOKASSA_ID${NC}"
            PASSED_TESTS=$((PASSED_TESTS + 1))
            
            echo -e "${YELLOW}Тест 3: Имитация webhook payment.succeeded от ЮКассы${NC}"
            
            # Отправляем webhook payment.succeeded
            webhook_data='{
                "type": "notification",
                "event": "payment.succeeded",
                "object": {
                    "id": "'$SBP_YOOKASSA_ID'",
                    "status": "succeeded",
                    "amount": {
                        "value": "500.00",
                        "currency": "RUB"
                    },
                    "payment_method": {
                        "type": "sbp"
                    },
                    "metadata": {
                        "order_id": "'$SBP_ORDER_ID'",
                        "payment_id": "'$SBP_PAYMENT_ID'"
                    }
                }
            }'
            
            webhook_response=$(curl -s -w "%{http_code}" -X POST "$BASE_URL/api/v1/payments/yookassa/webhook" \
                -H "Content-Type: application/json" \
                -d "$webhook_data")
            
            webhook_http_code=${webhook_response: -3}
            
            if [ "$webhook_http_code" = "200" ]; then
                echo -e "${GREEN}✅ Webhook payment.succeeded обработан успешно${NC}"
                PASSED_TESTS=$((PASSED_TESTS + 1))
            else
                echo -e "${RED}❌ Ошибка обработки webhook (HTTP $webhook_http_code)${NC}"
                echo "Ответ webhook: ${webhook_response%???}"
                FAILED_TESTS=$((FAILED_TESTS + 1))
            fi
        else
            echo -e "${RED}❌ Ошибка создания СБП платежа (HTTP $sbp_payment_http_code)${NC}"
            echo "Ответ: $sbp_payment_body"
            FAILED_TESTS=$((FAILED_TESTS + 2))  # платеж + webhook
        fi
        
        echo -e "${YELLOW}Тест 4: Создание заказа с наличной оплатой (должен попасть в бот сразу)${NC}"
        
        # Добавляем товар в корзину для наличного заказа
        cash_cart_response=$(curl -s -w "%{http_code}" -X POST "$BASE_URL/api/v1/cart/items" \
            -H "Content-Type: application/json" \
            -H "Authorization: Bearer $JWT_TOKEN" \
            -d "$cart_data")
        
        cash_cart_http_code=${cash_cart_response: -3}
        
        if [ "$cash_cart_http_code" = "200" ]; then
            echo -e "${GREEN}✅ Товар добавлен в корзину для наличного заказа${NC}"
        else
            echo -e "${YELLOW}⚠️ Не удалось добавить товар в корзину для наличного заказа (HTTP $cash_cart_http_code)${NC}"
        fi
        
        # Создаем заказ с наличной оплатой для сравнения
        cash_order_data='{
            "deliveryLocationId": 1,
            "contactName": "Наличные Тест",
            "contactPhone": "+79001234568",
            "comment": "Тестовый заказ с наличной оплатой для сравнения с СБП"
        }'
        
        cash_order_response=$(curl -s -w "%{http_code}" -X POST "$BASE_URL/api/v1/orders" \
            -H "Content-Type: application/json" \
            -H "Authorization: Bearer $JWT_TOKEN" \
            -d "$cash_order_data")
        
        cash_order_http_code=${cash_order_response: -3}
        
        if [ "$cash_order_http_code" = "200" ] || [ "$cash_order_http_code" = "201" ]; then
            CASH_ORDER_ID=$(echo "${cash_order_response%???}" | grep -o '"id":[0-9]*' | cut -d':' -f2)
            echo -e "${GREEN}✅ Заказ с наличной оплатой #$CASH_ORDER_ID создан${NC}"
            PASSED_TESTS=$((PASSED_TESTS + 1))
        else
            echo -e "${RED}❌ Ошибка создания заказа с наличной оплатой (HTTP $cash_order_http_code)${NC}"
            echo "Ответ: ${cash_order_response%???}"
            FAILED_TESTS=$((FAILED_TESTS + 1))
        fi
        
        TOTAL_TESTS=$((TOTAL_TESTS + 4))
        
        echo
        echo "=================================================================="
        echo -e "${CYAN}📋 Резюме исправленного СБП флоу теста:${NC}"
        echo -e "${BLUE}• СБП заказ #$SBP_ORDER_ID создан (НЕ должен попасть в бот сразу)${NC}"
        echo -e "${BLUE}• СБП платеж #$SBP_PAYMENT_ID создан и обработан webhook'ом${NC}"
        echo -e "${BLUE}• Заказ с наличными #$CASH_ORDER_ID создан (должен попасть в бот сразу)${NC}"
        echo
        echo -e "${YELLOW}📝 Ручная проверка в админском боте:${NC}"
        echo -e "${YELLOW}1. СБП заказ #$SBP_ORDER_ID должен появиться в боте только после webhook${NC}"
        echo -e "${YELLOW}2. Заказ с наличными #$CASH_ORDER_ID должен появиться в боте сразу${NC}"
        echo -e "${YELLOW}3. В СБП заказе должно отображаться: 💳 СТАТУС ОПЛАТЫ: ✅ Оплачено${NC}"
        echo -e "${YELLOW}4. В СБП заказе должно отображаться: 💰 СПОСОБ ОПЛАТЫ: 📱 СБП${NC}"
        
    else
        echo -e "${RED}❌ Не удалось создать заказ для СБП теста (HTTP $sbp_order_http_code)${NC}"
        echo "Ответ: $sbp_order_body"
        FAILED_TESTS=$((FAILED_TESTS + 4))
        TOTAL_TESTS=$((TOTAL_TESTS + 4))
    fi
else
    echo -e "${RED}❌ Пропуск СБП флоу тестов - нет авторизации${NC}"
    FAILED_TESTS=$((FAILED_TESTS + 4))
    TOTAL_TESTS=$((TOTAL_TESTS + 4))
fi

echo
echo "=================================================================="
echo -e "${BLUE}📊 ИТОГОВАЯ СТАТИСТИКА ИСПРАВЛЕННОГО СБП ФЛОУ${NC}"
echo "=================================================================="

echo -e "Всего тестов: ${BLUE}$TOTAL_TESTS${NC}"
echo -e "Успешных: ${GREEN}$PASSED_TESTS${NC}"
echo -e "Неудачных: ${RED}$FAILED_TESTS${NC}"

if [ $FAILED_TESTS -eq 0 ]; then
    echo -e "${GREEN}🎉 Все исправленные СБП тесты пройдены успешно!${NC}"
    echo -e "${GREEN}✅ Секция comprehensive теста готова к использованию${NC}"
    exit 0
else
    echo -e "${RED}❌ Обнаружены проблемы в исправленном СБП флоу${NC}"
    echo -e "${YELLOW}⚠️ Проверьте логи приложения и конфигурацию${NC}"
    exit 1
fi 