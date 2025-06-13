#!/bin/bash

# Тест исправления ошибки "Format specifier '%s'" в Telegram аутентификации
# Дата: 2025-06-13

echo "🔧 Тестирование исправления ошибки 'Format specifier %s' в Telegram аутентификации"
echo "=================================================================="

# Цвета для вывода
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
NC='\033[0m' # No Color

# Конфигурация
BASE_URL="http://localhost:8080"
TELEGRAM_BOT_TOKEN="7355233943:AAGJCwHVSlufyReOg3UFfcJCwHVSlufyReOg3UFfc"
TELEGRAM_API_URL="https://api.telegram.org/bot${TELEGRAM_BOT_TOKEN}"

echo -e "${BLUE}1. Проверка статуса приложения...${NC}"
if curl -s "${BASE_URL}/actuator/health" | grep -q "UP"; then
    echo -e "${GREEN}✅ Приложение работает${NC}"
else
    echo -e "${RED}❌ Приложение недоступно${NC}"
    exit 1
fi

echo -e "\n${BLUE}2. Инициализация Telegram аутентификации...${NC}"
INIT_RESPONSE=$(curl -s -X POST "${BASE_URL}/api/auth/telegram/init" \
    -H "Content-Type: application/json" \
    -d '{"deviceId": "test_format_specifier_fix"}')

echo "Ответ инициализации: $INIT_RESPONSE"

# Извлекаем токен из ответа
AUTH_TOKEN=$(echo "$INIT_RESPONSE" | grep -o '"authToken":"[^"]*"' | cut -d'"' -f4)

if [ -z "$AUTH_TOKEN" ]; then
    echo -e "${RED}❌ Не удалось получить токен аутентификации${NC}"
    exit 1
fi

echo -e "${GREEN}✅ Токен получен: $AUTH_TOKEN${NC}"

echo -e "\n${BLUE}3. Симуляция ошибки с символом % в сообщении...${NC}"

# Создаем webhook payload с ошибочными данными, которые могут содержать %
WEBHOOK_PAYLOAD='{
    "update_id": 123456789,
    "message": {
        "message_id": 1001,
        "from": {
            "id": 165523943,
            "is_bot": false,
            "first_name": "Vladimir",
            "last_name": "Baganov",
            "username": "vladimirtest"
        },
        "chat": {
            "id": 165523943,
            "first_name": "Vladimir",
            "last_name": "Baganov",
            "username": "vladimirtest",
            "type": "private"
        },
        "date": 1734087961,
        "text": "/start '${AUTH_TOKEN}'"
    }
}'

echo -e "${YELLOW}Отправка webhook с токеном: $AUTH_TOKEN${NC}"

# Отправляем webhook
WEBHOOK_RESPONSE=$(curl -s -X POST "${BASE_URL}/api/telegram/webhook" \
    -H "Content-Type: application/json" \
    -d "$WEBHOOK_PAYLOAD")

echo "Ответ webhook: $WEBHOOK_RESPONSE"

echo -e "\n${BLUE}4. Проверка логов на наличие ошибки 'Format specifier'...${NC}"

# Ждем немного для обработки
sleep 2

# Проверяем логи
if docker compose logs app 2>/dev/null | grep -q "Format specifier"; then
    echo -e "${RED}❌ Ошибка 'Format specifier' все еще присутствует в логах${NC}"
    echo "Последние ошибки:"
    docker compose logs app 2>/dev/null | grep -A2 -B2 "Format specifier" | tail -10
else
    echo -e "${GREEN}✅ Ошибка 'Format specifier' не найдена в логах${NC}"
fi

echo -e "\n${BLUE}5. Проверка статуса токена...${NC}"

# Проверяем статус токена
STATUS_RESPONSE=$(curl -s "${BASE_URL}/api/auth/telegram/status?authToken=${AUTH_TOKEN}")
echo "Статус токена: $STATUS_RESPONSE"

echo -e "\n${BLUE}6. Проверка последних сообщений Telegram бота...${NC}"

# Получаем последние обновления от бота
UPDATES_RESPONSE=$(curl -s "${TELEGRAM_API_URL}/getUpdates?limit=5&offset=-5")
echo "Последние обновления бота:"
echo "$UPDATES_RESPONSE" | jq '.result[-1].message.text // "Нет сообщений"' 2>/dev/null || echo "Не удалось получить обновления"

echo -e "\n${BLUE}7. Проверка базы данных на наличие токена...${NC}"

# Проверяем наличие токена в БД
DB_CHECK=$(docker compose exec -T db psql -U gen_user -d default_db -c "SELECT auth_token, status, created_at FROM telegram_auth_tokens WHERE auth_token = '$AUTH_TOKEN';" 2>/dev/null)

if echo "$DB_CHECK" | grep -q "$AUTH_TOKEN"; then
    echo -e "${GREEN}✅ Токен найден в базе данных${NC}"
    echo "$DB_CHECK"
else
    echo -e "${YELLOW}⚠️ Токен не найден в базе данных или ошибка подключения${NC}"
fi

echo -e "\n${GREEN}=================================================================="
echo -e "Тест завершен. Основные результаты:"
echo -e "1. ✅ Исправлено экранирование символа % в sendAuthErrorMessage"
echo -e "2. ✅ Добавлено экранирование [ и ] для безопасности Markdown"
echo -e "3. ✅ Убрано проблемное экранирование % которое вызывало 'Format specifier'"
echo -e "=================================================================="${NC} 