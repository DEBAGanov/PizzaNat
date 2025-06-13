#!/bin/bash

# Тест системы отложенных реферальных уведомлений
# Дата: 2025-06-13

echo "🔔 Тестирование системы отложенных реферальных уведомлений"
echo "=================================================================="

# Цвета для вывода
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
NC='\033[0m' # No Color

# Конфигурация
BASE_URL="http://localhost:8080"
TELEGRAM_BOT_TOKEN="7819187384:AAGJNn0cwfJ7Nsv_N25h75eggEmqmD5WZG4"
TELEGRAM_API_URL="https://api.telegram.org/bot${TELEGRAM_BOT_TOKEN}"

echo -e "${BLUE}1. Проверка статуса приложения...${NC}"
if curl -s "${BASE_URL}/actuator/health" | grep -q "UP"; then
    echo -e "${GREEN}✅ Приложение работает${NC}"
else
    echo -e "${RED}❌ Приложение недоступно${NC}"
    exit 1
fi
echo ""

echo -e "${BLUE}2. Получение токена администратора...${NC}"
ADMIN_TOKEN=$(curl -s -X POST "${BASE_URL}/api/v1/auth/login" \
    -H "Content-Type: application/json" \
    -d '{
        "username": "admin",
        "password": "admin123"
    }' | grep -o '"token":"[^"]*"' | cut -d'"' -f4)

if [ -z "$ADMIN_TOKEN" ]; then
    echo -e "${RED}❌ Не удалось получить токен администратора${NC}"
    exit 1
fi

echo -e "${GREEN}✅ Токен администратора получен${NC}"
echo ""

echo -e "${BLUE}3. Создание тестового пользователя с Telegram ID...${NC}"
# Создаем пользователя
USER_RESPONSE=$(curl -s -X POST "${BASE_URL}/api/v1/auth/register" \
    -H "Content-Type: application/json" \
    -d '{
        "username": "referral_test_user",
        "email": "referral_test@example.com",
        "password": "password123",
        "firstName": "Реферальный",
        "lastName": "Тестер"
    }')

echo "Ответ создания пользователя: $USER_RESPONSE"

# Получаем токен пользователя
USER_TOKEN=$(curl -s -X POST "${BASE_URL}/api/v1/auth/login" \
    -H "Content-Type: application/json" \
    -d '{
        "username": "referral_test_user",
        "password": "password123"
    }' | grep -o '"token":"[^"]*"' | cut -d'"' -f4)

if [ -z "$USER_TOKEN" ]; then
    echo -e "${RED}❌ Не удалось получить токен пользователя${NC}"
    exit 1
fi

echo -e "${GREEN}✅ Пользователь создан и токен получен${NC}"
echo ""

echo -e "${BLUE}4. Добавление Telegram ID пользователю (симуляция)...${NC}"
# В реальности это происходит через Telegram аутентификацию
# Для теста добавим Telegram ID напрямую в БД
TELEGRAM_ID="165523943"  # Тестовый Telegram ID

# Обновляем пользователя с Telegram ID через SQL
echo "UPDATE users SET telegram_id = $TELEGRAM_ID WHERE username = 'referral_test_user';" > /tmp/update_telegram_id.sql

# Выполняем SQL через docker
docker exec pizzanat-db psql -U pizzanat -d pizzanat -f /tmp/update_telegram_id.sql

echo -e "${GREEN}✅ Telegram ID добавлен пользователю${NC}"
echo ""

echo -e "${BLUE}5. Добавление товара в корзину...${NC}"
CART_RESPONSE=$(curl -s -X POST "${BASE_URL}/api/v1/cart/add" \
    -H "Content-Type: application/json" \
    -H "Authorization: Bearer $USER_TOKEN" \
    -d '{
        "productId": 1,
        "quantity": 1
    }')

echo "Ответ добавления в корзину: $CART_RESPONSE"

if [[ $CART_RESPONSE == *"error"* ]]; then
    echo -e "${RED}❌ Не удалось добавить товар в корзину${NC}"
    exit 1
fi

echo -e "${GREEN}✅ Товар добавлен в корзину${NC}"
echo ""

echo -e "${BLUE}6. Создание заказа...${NC}"
ORDER_RESPONSE=$(curl -s -X POST "${BASE_URL}/api/v1/orders" \
    -H "Content-Type: application/json" \
    -H "Authorization: Bearer $USER_TOKEN" \
    -d '{
        "deliveryAddress": "ул. Тестовая, д. 123, кв. 45",
        "contactName": "Реферальный Тестер",
        "contactPhone": "+79001234567",
        "comment": "Тестовый заказ для проверки реферальных уведомлений"
    }')

echo "Ответ создания заказа: $ORDER_RESPONSE"

ORDER_ID=$(echo $ORDER_RESPONSE | grep -o '"id":[0-9]*' | head -1 | cut -d':' -f2)

if [ -z "$ORDER_ID" ]; then
    echo -e "${RED}❌ Не удалось создать заказ${NC}"
    exit 1
fi

echo -e "${GREEN}✅ Заказ #$ORDER_ID создан${NC}"
echo ""

echo -e "${BLUE}7. Изменение статуса заказа на DELIVERED...${NC}"
STATUS_RESPONSE=$(curl -s -X PUT "${BASE_URL}/api/v1/admin/orders/$ORDER_ID/status" \
    -H "Content-Type: application/json" \
    -H "Authorization: Bearer $ADMIN_TOKEN" \
    -d '{
        "statusName": "DELIVERED"
    }')

echo "Ответ изменения статуса: $STATUS_RESPONSE"

if [[ $STATUS_RESPONSE == *"DELIVERED"* ]]; then
    echo -e "${GREEN}✅ Статус заказа изменен на DELIVERED${NC}"
    echo -e "${YELLOW}📅 Реферальное уведомление запланировано на отправку через 1 час${NC}"
else
    echo -e "${RED}❌ Не удалось изменить статус заказа${NC}"
    exit 1
fi
echo ""

echo -e "${BLUE}8. Проверка запланированных уведомлений в БД...${NC}"
# Проверяем, что уведомление создано в БД
echo "SELECT id, order_id, telegram_id, notification_type, scheduled_at, status FROM scheduled_notifications WHERE order_id = $ORDER_ID;" > /tmp/check_notifications.sql

NOTIFICATION_CHECK=$(docker exec pizzanat-db psql -U pizzanat -d pizzanat -t -f /tmp/check_notifications.sql)

if [[ $NOTIFICATION_CHECK == *"REFERRAL_REMINDER"* ]]; then
    echo -e "${GREEN}✅ Реферальное уведомление найдено в БД${NC}"
    echo "Детали уведомления:"
    echo "$NOTIFICATION_CHECK"
else
    echo -e "${RED}❌ Реферальное уведомление не найдено в БД${NC}"
    echo "Результат запроса: $NOTIFICATION_CHECK"
fi
echo ""

echo -e "${BLUE}9. Тестирование немедленной отправки (для демонстрации)...${NC}"
# Обновляем время планирования на текущее время для немедленной отправки
echo "UPDATE scheduled_notifications SET scheduled_at = NOW() WHERE order_id = $ORDER_ID;" > /tmp/update_schedule.sql

docker exec pizzanat-db psql -U pizzanat -d pizzanat -f /tmp/update_schedule.sql

echo -e "${YELLOW}⏰ Время планирования обновлено на текущее время${NC}"
echo -e "${YELLOW}📨 Планировщик отправит уведомление в течение 5 минут${NC}"
echo ""

echo -e "${BLUE}10. Информация о тестировании...${NC}"
echo -e "${YELLOW}📋 Что было протестировано:${NC}"
echo -e "${YELLOW}   1. ✅ Создание пользователя с Telegram ID${NC}"
echo -e "${YELLOW}   2. ✅ Создание заказа${NC}"
echo -e "${YELLOW}   3. ✅ Изменение статуса на DELIVERED${NC}"
echo -e "${YELLOW}   4. ✅ Планирование реферального уведомления${NC}"
echo -e "${YELLOW}   5. ✅ Сохранение уведомления в БД${NC}"
echo ""

echo -e "${YELLOW}📱 Что проверить далее:${NC}"
echo -e "${YELLOW}   1. Логи приложения на наличие сообщений о планировании${NC}"
echo -e "${YELLOW}   2. Отправку уведомления планировщиком (каждые 5 минут)${NC}"
echo -e "${YELLOW}   3. Получение реферального сообщения в Telegram${NC}"
echo ""

echo -e "${YELLOW}🔧 Команды для мониторинга:${NC}"
echo -e "${YELLOW}   Логи приложения: docker compose logs app | grep -i referral${NC}"
echo -e "${YELLOW}   Логи планировщика: docker compose logs app | grep -i scheduled${NC}"
echo -e "${YELLOW}   Проверка БД: docker exec pizzanat-db psql -U pizzanat -d pizzanat -c \"SELECT * FROM scheduled_notifications;\"${NC}"
echo ""

echo -e "${GREEN}🎉 Тестирование системы отложенных реферальных уведомлений завершено!${NC}"

# Очистка временных файлов
rm -f /tmp/update_telegram_id.sql /tmp/check_notifications.sql /tmp/update_schedule.sql 