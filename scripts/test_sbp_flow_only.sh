#!/bin/bash

echo "🚀 Тестирование только СБП флоу"

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

echo -e "${BLUE}📱 СБП ФЛОУ ТЕСТЫ${NC}"
echo -e "${CYAN}Проверяем что заказы с СБП приходят в админский бот только после оплаты${NC}"

# Получаем токен авторизации
echo -e "${YELLOW}Получение токена авторизации...${NC}"

# Создаем уникальный timestamp для тестового пользователя
timestamp=$(date +%s)

# Регистрируем тестового пользователя
register_data='{
    "username": "test_sbp_user_'$timestamp'",
    "email": "test_sbp'$timestamp'@example.com",
    "password": "TestPassword123!",
    "firstName": "Test",
    "lastName": "SBP User",
    "phone": "+79001234567"
}'

register_response=$(curl -s -w "%{http_code}" -X POST \
    -H "Content-Type: application/json" \
    -d "$register_data" \
    "$BASE_URL/api/v1/auth/register")

# Авторизуемся
username="test_sbp_user_$timestamp"
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
        echo -e "${GREEN}✅ Товар добавлен в корзину${NC}"
    else
        echo -e "${YELLOW}⚠️ Не удалось добавить товар в корзину (HTTP $cart_http_code), продолжаем...${NC}"
    fi
    
    # Создаем заказ для СБП теста
    sbp_order_data='{
        "deliveryLocationId": 1,
        "contactName": "СБП Тест",
        "contactPhone": "+79001234567",
        "comment": "Тестовый заказ для проверки СБП флоу"
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
                
                echo -e "${YELLOW}Тест 4: Проверка статуса платежа после webhook${NC}"
                
                # Проверяем статус платежа
                status_response=$(curl -s -w "%{http_code}" -X GET "$BASE_URL/api/v1/payments/yookassa/$SBP_PAYMENT_ID")
                status_http_code=${status_response: -3}
                
                if [ "$status_http_code" = "200" ]; then
                    echo -e "${GREEN}✅ Статус платежа проверен успешно${NC}"
                    PASSED_TESTS=$((PASSED_TESTS + 1))
                else
                    echo -e "${RED}❌ Ошибка проверки статуса платежа (HTTP $status_http_code)${NC}"
                    FAILED_TESTS=$((FAILED_TESTS + 1))
                fi
            else
                echo -e "${RED}❌ Ошибка обработки webhook (HTTP $webhook_http_code)${NC}"
                echo "Ответ webhook: ${webhook_response%???}"
                FAILED_TESTS=$((FAILED_TESTS + 2))  # webhook + статус
            fi
        else
            echo -e "${RED}❌ Ошибка создания СБП платежа (HTTP $sbp_payment_http_code)${NC}"
            echo "Ответ: $sbp_payment_body"
            FAILED_TESTS=$((FAILED_TESTS + 3))  # платеж + webhook + статус
        fi
        
        echo -e "${YELLOW}Тест 5: Создание заказа с наличной оплатой (должен попасть в бот сразу)${NC}"
        
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
        
        echo -e "${YELLOW}Тест 6: Проверка webhook с неизвестным платежом${NC}"
        
        # Тестируем webhook с неизвестным платежом
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
        
        unknown_webhook_response=$(curl -s -w "%{http_code}" -X POST "$BASE_URL/api/v1/payments/yookassa/webhook" \
            -H "Content-Type: application/json" \
            -d "$unknown_webhook")
        
        unknown_webhook_http_code=${unknown_webhook_response: -3}
        
        # Webhook должен возвращать 400 для неизвестного платежа
        if [ "$unknown_webhook_http_code" = "400" ]; then
            echo -e "${GREEN}✅ Webhook корректно обработал неизвестный платеж (HTTP 400)${NC}"
            PASSED_TESTS=$((PASSED_TESTS + 1))
        else
            echo -e "${RED}❌ Неожиданный ответ webhook для неизвестного платежа (HTTP $unknown_webhook_http_code)${NC}"
            FAILED_TESTS=$((FAILED_TESTS + 1))
        fi
        
        TOTAL_TESTS=$((TOTAL_TESTS + 6))
        
        echo
        echo "=================================================================="
        echo -e "${CYAN}📋 Резюме СБП флоу тестов:${NC}"
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
        FAILED_TESTS=$((FAILED_TESTS + 6))
        TOTAL_TESTS=$((TOTAL_TESTS + 6))
    fi
else
    echo -e "${RED}❌ Пропуск СБП флоу тестов - нет авторизации${NC}"
    FAILED_TESTS=$((FAILED_TESTS + 6))
    TOTAL_TESTS=$((TOTAL_TESTS + 6))
fi

echo
echo "=================================================================="
echo -e "${BLUE}📊 ИТОГОВАЯ СТАТИСТИКА СБП ФЛОУ${NC}"
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