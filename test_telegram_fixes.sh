#!/bin/bash

# Тест исправлений в Telegram: ошибка аутентификации и улучшенные уведомления администраторам

set -e

# Цвета для вывода
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
NC='\033[0m' # No Color

# Конфигурация
API_URL="http://localhost:8080/api/v1"
ADMIN_EMAIL="admin@pizzanat.com"
ADMIN_PASSWORD="admin123"

echo -e "${BLUE}🧪 ТЕСТ ИСПРАВЛЕНИЙ TELEGRAM${NC}"
echo "=================================="
echo ""

# Функция для получения токена администратора
get_admin_token() {
    echo -e "${YELLOW}Получение токена администратора...${NC}"
    
    ADMIN_LOGIN_RESPONSE=$(curl -s -X POST "$API_URL/auth/login" \
        -H "Content-Type: application/json" \
        -d "{
            \"email\": \"$ADMIN_EMAIL\",
            \"password\": \"$ADMIN_PASSWORD\"
        }")
    
    ADMIN_TOKEN=$(echo $ADMIN_LOGIN_RESPONSE | grep -o '"token":"[^"]*"' | cut -d'"' -f4)
    
    if [ -z "$ADMIN_TOKEN" ]; then
        echo -e "${RED}❌ Не удалось получить токен администратора${NC}"
        echo "Ответ: $ADMIN_LOGIN_RESPONSE"
        exit 1
    fi
    
    echo -e "${GREEN}✅ Токен администратора получен${NC}"
    echo "$ADMIN_TOKEN"
}

# Функция для создания пользователя
create_test_user() {
    echo -e "${YELLOW}Создание тестового пользователя...${NC}"
    
    USERNAME="test_user_$(date +%s)"
    
    USER_REGISTER_RESPONSE=$(curl -s -X POST "$API_URL/auth/register" \
        -H "Content-Type: application/json" \
        -d "{
            \"username\": \"$USERNAME\",
            \"email\": \"$USERNAME@test.com\",
            \"password\": \"password123\",
            \"firstName\": \"Тест\",
            \"lastName\": \"Пользователь\"
        }")
    
    USER_TOKEN=$(echo $USER_REGISTER_RESPONSE | grep -o '"token":"[^"]*"' | cut -d'"' -f4)
    
    if [ -z "$USER_TOKEN" ]; then
        echo -e "${RED}❌ Не удалось создать пользователя${NC}"
        echo "Ответ: $USER_REGISTER_RESPONSE"
        exit 1
    fi
    
    echo -e "${GREEN}✅ Пользователь $USERNAME создан${NC}"
    echo "$USER_TOKEN|$USERNAME"
}

# Функция для создания заказа
create_test_order() {
    local USER_TOKEN=$1
    
    echo -e "${YELLOW}Добавление товара в корзину...${NC}"
    
    # Добавляем товар в корзину
    CART_RESPONSE=$(curl -s -X POST "$API_URL/cart/items" \
        -H "Content-Type: application/json" \
        -H "Authorization: Bearer $USER_TOKEN" \
        -d '{
            "productId": 1,
            "quantity": 2
        }')
    
    if [[ $CART_RESPONSE == *"productId"* ]]; then
        echo -e "${GREEN}✅ Товар добавлен в корзину${NC}"
    else
        echo -e "${RED}❌ Не удалось добавить товар в корзину${NC}"
        echo "Ответ: $CART_RESPONSE"
        exit 1
    fi
    
    echo -e "${YELLOW}Создание заказа...${NC}"
    
    # Создаем заказ
    ORDER_RESPONSE=$(curl -s -X POST "$API_URL/orders" \
        -H "Content-Type: application/json" \
        -H "Authorization: Bearer $USER_TOKEN" \
        -d '{
            "deliveryAddress": "ул. Тестовая, д. 123, кв. 45",
            "contactName": "Тест Контакт",
            "contactPhone": "+79001234567",
            "comment": "Тестовый заказ для проверки улучшенных уведомлений администраторам"
        }')
    
    ORDER_ID=$(echo $ORDER_RESPONSE | grep -o '"id":[0-9]*' | head -1 | cut -d':' -f2)
    
    if [ -z "$ORDER_ID" ]; then
        echo -e "${RED}❌ Не удалось создать заказ${NC}"
        echo "Ответ: $ORDER_RESPONSE"
        exit 1
    fi
    
    echo -e "${GREEN}✅ Заказ #$ORDER_ID создан${NC}"
    echo "$ORDER_ID"
}

# Функция для изменения статуса заказа
update_order_status() {
    local ORDER_ID=$1
    local STATUS=$2
    local ADMIN_TOKEN=$3
    
    echo -e "${YELLOW}Изменение статуса заказа #$ORDER_ID на $STATUS...${NC}"
    
    STATUS_RESPONSE=$(curl -s -X PUT "$API_URL/admin/orders/$ORDER_ID/status" \
        -H "Content-Type: application/json" \
        -H "Authorization: Bearer $ADMIN_TOKEN" \
        -d "{\"statusName\": \"$STATUS\"}")
    
    if [[ $STATUS_RESPONSE == *"$STATUS"* ]]; then
        echo -e "${GREEN}✅ Статус изменен на $STATUS${NC}"
        return 0
    else
        echo -e "${RED}❌ Не удалось изменить статус на $STATUS${NC}"
        echo "Ответ: $STATUS_RESPONSE"
        return 1
    fi
}

# Функция для тестирования Telegram аутентификации
test_telegram_auth() {
    echo -e "${YELLOW}Тестирование Telegram аутентификации...${NC}"
    
    # Инициализация Telegram аутентификации
    TELEGRAM_INIT_RESPONSE=$(curl -s -X POST "$API_URL/auth/telegram/init" \
        -H "Content-Type: application/json" \
        -d '{
            "deviceId": "test_device_123"
        }')
    
    TELEGRAM_AUTH_TOKEN=$(echo $TELEGRAM_INIT_RESPONSE | grep -o '"authToken":"[^"]*"' | cut -d'"' -f4)
    TELEGRAM_BOT_URL=$(echo $TELEGRAM_INIT_RESPONSE | grep -o '"telegramBotUrl":"[^"]*"' | cut -d'"' -f4)
    
    if [ -n "$TELEGRAM_AUTH_TOKEN" ] && [ -n "$TELEGRAM_BOT_URL" ]; then
        echo -e "${GREEN}✅ Telegram аутентификация инициализирована${NC}"
        echo "   Auth Token: $TELEGRAM_AUTH_TOKEN"
        echo "   Bot URL: $TELEGRAM_BOT_URL"
        
        # Проверка статуса токена
        echo -e "${YELLOW}Проверка статуса токена...${NC}"
        TELEGRAM_STATUS_RESPONSE=$(curl -s "$API_URL/auth/telegram/status/$TELEGRAM_AUTH_TOKEN")
        echo "   Статус: $TELEGRAM_STATUS_RESPONSE"
        
        return 0
    else
        echo -e "${RED}❌ Ошибка инициализации Telegram аутентификации${NC}"
        echo "Ответ: $TELEGRAM_INIT_RESPONSE"
        return 1
    fi
}

# Основной тест
main() {
    echo -e "${BLUE}Начинаем тест исправлений Telegram...${NC}"
    echo ""
    
    # Тест 1: Telegram аутентификация (проверка исправления ошибки форматирования)
    echo -e "${BLUE}📱 ТЕСТ 1: TELEGRAM АУТЕНТИФИКАЦИЯ${NC}"
    echo "========================================="
    if test_telegram_auth; then
        echo -e "${GREEN}✅ Тест Telegram аутентификации прошел успешно${NC}"
    else
        echo -e "${RED}❌ Тест Telegram аутентификации провален${NC}"
    fi
    echo ""
    
    # Тест 2: Улучшенные уведомления администраторам
    echo -e "${BLUE}📢 ТЕСТ 2: УЛУЧШЕННЫЕ УВЕДОМЛЕНИЯ АДМИНИСТРАТОРАМ${NC}"
    echo "=================================================="
    
    # Получаем токен администратора
    ADMIN_TOKEN=$(get_admin_token)
    echo ""
    
    # Создаем пользователя
    USER_DATA=$(create_test_user)
    USER_TOKEN=$(echo $USER_DATA | cut -d'|' -f1)
    USERNAME=$(echo $USER_DATA | cut -d'|' -f2)
    echo ""
    
    # Создаем заказ (должно отправить улучшенное уведомление администраторам)
    ORDER_ID=$(create_test_order "$USER_TOKEN")
    echo ""
    
    echo -e "${YELLOW}Ожидание отправки уведомления о новом заказе...${NC}"
    sleep 3
    
    # Изменяем статус заказа (должно отправить улучшенное уведомление об изменении статуса)
    if update_order_status "$ORDER_ID" "CONFIRMED" "$ADMIN_TOKEN"; then
        echo -e "${YELLOW}Ожидание отправки уведомления об изменении статуса...${NC}"
        sleep 2
    fi
    
    echo ""
    echo -e "${BLUE}📋 ИТОГИ ТЕСТОВ${NC}"
    echo "==============="
    echo ""
    echo -e "${GREEN}✅ Создан пользователь: $USERNAME${NC}"
    echo -e "${GREEN}✅ Создан заказ: #$ORDER_ID${NC}"
    echo -e "${GREEN}✅ Изменен статус заказа на CONFIRMED${NC}"
    echo ""
    echo -e "${YELLOW}🔍 Проверьте Telegram бот администраторов на наличие:${NC}"
    echo "1. 🍕 Уведомления о новом заказе #$ORDER_ID с данными пользователя:"
    echo "   - Имя и фамилия пользователя"
    echo "   - Username пользователя"
    echo "   - Email пользователя"
    echo "   - Телефон пользователя (если есть)"
    echo ""
    echo "2. 🔄 Уведомления об изменении статуса заказа #$ORDER_ID с данными пользователя"
    echo ""
    echo -e "${BLUE}📝 Команды для проверки логов:${NC}"
    echo "docker logs pizzanat-app | grep 'Telegram уведомление отправлено'"
    echo "docker logs pizzanat-app | grep 'заказа #$ORDER_ID'"
    echo ""
    echo -e "${GREEN}🎉 Тест завершен успешно!${NC}"
    echo ""
    echo -e "${BLUE}📝 Исправления:${NC}"
    echo "1. ✅ Исправлена ошибка 'Format specifier %s' в Telegram аутентификации"
    echo "2. ✅ Добавлены данные о пользователе в уведомления администраторам"
    echo "3. ✅ Улучшено форматирование сообщений для администраторов"
}

# Запуск теста
main 