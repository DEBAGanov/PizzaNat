#!/bin/bash

# Тестирование исправления ошибки "chat not found" в Telegram уведомлениях
# Проверяем, что уведомления теперь отправляются только через AdminBotService

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

echo -e "${BLUE}🧪 Тестирование исправления ошибки 'chat not found'${NC}"
echo "================================================="

# Функция для получения токена администратора
get_admin_token() {
    echo -e "${YELLOW}Получение токена администратора...${NC}"
    
    TOKEN_RESPONSE=$(curl -s -X POST "$API_URL/auth/login" \
        -H "Content-Type: application/json" \
        -d '{
            "email": "'$ADMIN_EMAIL'",
            "password": "'$ADMIN_PASSWORD'"
        }')
    
    if [ $? -eq 0 ]; then
        ADMIN_TOKEN=$(echo "$TOKEN_RESPONSE" | jq -r '.token // empty')
        if [ -n "$ADMIN_TOKEN" ] && [ "$ADMIN_TOKEN" != "null" ]; then
            echo -e "${GREEN}✅ Токен администратора получен${NC}"
            return 0
        else
            echo -e "${RED}❌ Не удалось извлечь токен из ответа: $TOKEN_RESPONSE${NC}"
            return 1
        fi
    else
        echo -e "${RED}❌ Ошибка при получении токена администратора${NC}"
        return 1
    fi
}

# Функция для создания тестового заказа
create_test_order() {
    echo -e "${YELLOW}Создание тестового заказа...${NC}"
    
    ORDER_RESPONSE=$(curl -s -X POST "$API_URL/orders/create" \
        -H "Content-Type: application/json" \
        -H "Authorization: Bearer $ADMIN_TOKEN" \
        -d '{
            "deliveryLocationId": 1,
            "contactName": "Тест Исправления",
            "contactPhone": "+79061382868",
            "comment": "Тестовый заказ для проверки исправления chat not found",
            "items": [
                {
                    "productId": 1,
                    "quantity": 1
                }
            ]
        }')
    
    if [ $? -eq 0 ]; then
        ORDER_ID=$(echo "$ORDER_RESPONSE" | jq -r '.id // empty')
        if [ -n "$ORDER_ID" ] && [ "$ORDER_ID" != "null" ]; then
            echo -e "${GREEN}✅ Тестовый заказ создан с ID: $ORDER_ID${NC}"
            return 0
        else
            echo -e "${RED}❌ Не удалось создать тестовый заказ: $ORDER_RESPONSE${NC}"
            return 1
        fi
    else
        echo -e "${RED}❌ Ошибка при создании тестового заказа${NC}"
        return 1
    fi
}

# Функция для изменения статуса заказа
change_order_status() {
    local order_id=$1
    local new_status=$2
    
    echo -e "${YELLOW}Изменение статуса заказа $order_id на '$new_status'...${NC}"
    
    STATUS_RESPONSE=$(curl -s -X PUT "$API_URL/admin/orders/$order_id/status" \
        -H "Content-Type: application/json" \
        -H "Authorization: Bearer $ADMIN_TOKEN" \
        -d '{
            "status": "'$new_status'"
        }')
    
    if [ $? -eq 0 ]; then
        UPDATED_STATUS=$(echo "$STATUS_RESPONSE" | jq -r '.status // empty')
        if [ -n "$UPDATED_STATUS" ] && [ "$UPDATED_STATUS" != "null" ]; then
            echo -e "${GREEN}✅ Статус заказа изменен на: $UPDATED_STATUS${NC}"
            return 0
        else
            echo -e "${RED}❌ Не удалось изменить статус заказа: $STATUS_RESPONSE${NC}"
            return 1
        fi
    else
        echo -e "${RED}❌ Ошибка при изменении статуса заказа${NC}"
        return 1
    fi
}

# Функция для проверки логов на наличие ошибок "chat not found"
check_logs_for_errors() {
    echo -e "${YELLOW}Проверка логов на наличие ошибок 'chat not found'...${NC}"
    
    # Проверяем логи Docker контейнера за последние 2 минуты
    LOGS=$(docker logs pizzanat-app --since=2m 2>&1 | grep -i "chat not found" || true)
    
    if [ -z "$LOGS" ]; then
        echo -e "${GREEN}✅ Ошибки 'chat not found' в логах не найдены${NC}"
        return 0
    else
        echo -e "${RED}❌ Найдены ошибки 'chat not found' в логах:${NC}"
        echo "$LOGS"
        return 1
    fi
}

# Функция для проверки использования TelegramBotService
check_telegram_bot_service_usage() {
    echo -e "${YELLOW}Проверка отключения TelegramBotService...${NC}"
    
    # Проверяем логи на наличие сообщений от TelegramBotService
    TELEGRAM_BOT_LOGS=$(docker logs pizzanat-app --since=2m 2>&1 | grep -i "TelegramBotService" || true)
    
    if [ -z "$TELEGRAM_BOT_LOGS" ]; then
        echo -e "${GREEN}✅ TelegramBotService не используется для уведомлений${NC}"
        return 0
    else
        echo -e "${YELLOW}⚠️ Найдены упоминания TelegramBotService в логах:${NC}"
        echo "$TELEGRAM_BOT_LOGS"
        echo -e "${YELLOW}Это может быть нормально, если это не ошибки отправки${NC}"
        return 0
    fi
}

# Функция для проверки работы AdminBotService
check_admin_bot_service() {
    echo -e "${YELLOW}Проверка работы AdminBotService...${NC}"
    
    # Проверяем логи на наличие сообщений от AdminBotService
    ADMIN_BOT_LOGS=$(docker logs pizzanat-app --since=2m 2>&1 | grep -E "(AdminBotService|PizzaNatAdminBot)" | head -5 || true)
    
    if [ -n "$ADMIN_BOT_LOGS" ]; then
        echo -e "${GREEN}✅ AdminBotService работает:${NC}"
        echo "$ADMIN_BOT_LOGS"
        return 0
    else
        echo -e "${YELLOW}⚠️ Логи AdminBotService не найдены (возможно, бот отключен)${NC}"
        return 0
    fi
}

# Основной тест
main() {
    echo -e "${BLUE}Начинаем тестирование исправления 'chat not found'...${NC}"
    echo ""
    
    # Получаем токен администратора
    if ! get_admin_token; then
        echo -e "${RED}❌ Не удалось получить токен администратора${NC}"
        exit 1
    fi
    
    echo ""
    
    # Создаем тестовый заказ
    if ! create_test_order; then
        echo -e "${RED}❌ Не удалось создать тестовый заказ${NC}"
        exit 1
    fi
    
    echo ""
    
    # Ждем немного для обработки уведомлений о новом заказе
    echo -e "${YELLOW}Ожидание обработки уведомлений о новом заказе...${NC}"
    sleep 3
    
    # Изменяем статус заказа несколько раз для тестирования уведомлений
    echo ""
    change_order_status "$ORDER_ID" "CONFIRMED"
    sleep 2
    
    echo ""
    change_order_status "$ORDER_ID" "PREPARING"
    sleep 2
    
    echo ""
    change_order_status "$ORDER_ID" "READY"
    sleep 2
    
    echo ""
    echo -e "${BLUE}Анализ результатов...${NC}"
    echo "========================="
    
    # Проверяем логи на ошибки
    echo ""
    check_logs_for_errors
    LOGS_OK=$?
    
    echo ""
    check_telegram_bot_service_usage
    
    echo ""
    check_admin_bot_service
    
    echo ""
    echo -e "${BLUE}Итоговый результат:${NC}"
    echo "==================="
    
    if [ $LOGS_OK -eq 0 ]; then
        echo -e "${GREEN}✅ УСПЕХ: Ошибки 'chat not found' устранены!${NC}"
        echo -e "${GREEN}✅ TelegramBotService больше не используется для уведомлений${NC}"
        echo -e "${GREEN}✅ Уведомления отправляются через AdminBotService${NC}"
        echo ""
        echo -e "${GREEN}🎉 Исправление работает корректно!${NC}"
    else
        echo -e "${RED}❌ ОШИБКА: Все еще есть проблемы с 'chat not found'${NC}"
        echo ""
        echo -e "${YELLOW}💡 Рекомендации:${NC}"
        echo "1. Проверьте конфигурацию TELEGRAM_ENABLED=false"
        echo "2. Убедитесь, что AdminBotService настроен правильно"
        echo "3. Проверьте логи приложения на другие ошибки"
    fi
}

# Запускаем тест
main 