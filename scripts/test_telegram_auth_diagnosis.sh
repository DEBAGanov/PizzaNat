#!/bin/bash

# Диагностический тест проблемы с Telegram аутентификацией
# Дата: 2025-06-13

echo "🔍 Диагностика проблемы с Telegram аутентификацией"
echo "=================================================="

# Цвета для вывода
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
NC='\033[0m' # No Color

# Конфигурация
BASE_URL="http://localhost:8080"

echo -e "${BLUE}1. Проверка статуса приложения...${NC}"
if curl -s "${BASE_URL}/actuator/health" | grep -q "UP"; then
    echo -e "${GREEN}✅ Приложение работает${NC}"
else
    echo -e "${RED}❌ Приложение недоступно${NC}"
    exit 1
fi
echo ""

echo -e "${BLUE}2. Инициализация Telegram аутентификации...${NC}"
INIT_RESPONSE=$(curl -s -X POST "${BASE_URL}/api/v1/auth/telegram/init" \
    -H "Content-Type: application/json" \
    -d '{"deviceId": "diagnosis_test"}')

echo "Ответ инициализации:"
echo "$INIT_RESPONSE" | jq . 2>/dev/null || echo "$INIT_RESPONSE"

AUTH_TOKEN=$(echo "$INIT_RESPONSE" | jq -r '.authToken // empty' 2>/dev/null)
BOT_URL=$(echo "$INIT_RESPONSE" | jq -r '.telegramBotUrl // empty' 2>/dev/null)

if [ -n "$AUTH_TOKEN" ] && [ "$AUTH_TOKEN" != "null" ]; then
    echo -e "${GREEN}✅ Токен получен: $AUTH_TOKEN${NC}"
    echo -e "${GREEN}✅ Bot URL: $BOT_URL${NC}"
else
    echo -e "${RED}❌ Не удалось получить токен${NC}"
    exit 1
fi
echo ""

echo -e "${BLUE}3. Проверка начального статуса токена...${NC}"
STATUS_RESPONSE=$(curl -s "${BASE_URL}/api/v1/auth/telegram/status/${AUTH_TOKEN}")
echo "Статус токена:"
echo "$STATUS_RESPONSE" | jq . 2>/dev/null || echo "$STATUS_RESPONSE"

STATUS=$(echo "$STATUS_RESPONSE" | jq -r '.status // empty' 2>/dev/null)
if [ "$STATUS" = "PENDING" ]; then
    echo -e "${GREEN}✅ Статус корректный: PENDING${NC}"
else
    echo -e "${RED}❌ Неожиданный статус: $STATUS${NC}"
fi
echo ""

echo -e "${BLUE}4. Проверка токена в базе данных...${NC}"
echo "Выполняем SQL запрос для проверки токена:"
echo "SELECT auth_token, telegram_id, status, expires_at FROM telegram_auth_tokens WHERE auth_token = '$AUTH_TOKEN';"
echo ""

echo -e "${BLUE}5. Симуляция подтверждения через webhook...${NC}"
# Создаем тестовые данные пользователя
TELEGRAM_USER_ID=999888777
TELEGRAM_USERNAME="diagnosis_user"
TELEGRAM_FIRST_NAME="Диагностика"
TELEGRAM_LAST_NAME="Тест"

# Симулируем команду /start
START_WEBHOOK=$(cat <<EOF
{
  "update_id": 123456789,
  "message": {
    "message_id": 1001,
    "from": {
      "id": $TELEGRAM_USER_ID,
      "is_bot": false,
      "first_name": "$TELEGRAM_FIRST_NAME",
      "last_name": "$TELEGRAM_LAST_NAME",
      "username": "$TELEGRAM_USERNAME"
    },
    "chat": {
      "id": $TELEGRAM_USER_ID,
      "type": "private"
    },
    "date": $(date +%s),
    "text": "/start $AUTH_TOKEN"
  }
}
EOF
)

echo "Отправляем webhook /start:"
START_RESULT=$(curl -s -X POST "${BASE_URL}/api/v1/telegram/webhook" \
    -H "Content-Type: application/json" \
    -d "$START_WEBHOOK")

echo "Результат /start webhook:"
echo "$START_RESULT" | jq . 2>/dev/null || echo "$START_RESULT"
echo ""

echo -e "${BLUE}6. Симуляция отправки контакта...${NC}"
CONTACT_WEBHOOK=$(cat <<EOF
{
  "update_id": 123456790,
  "message": {
    "message_id": 1002,
    "from": {
      "id": $TELEGRAM_USER_ID,
      "is_bot": false,
      "first_name": "$TELEGRAM_FIRST_NAME",
      "last_name": "$TELEGRAM_LAST_NAME",
      "username": "$TELEGRAM_USERNAME"
    },
    "chat": {
      "id": $TELEGRAM_USER_ID,
      "type": "private"
    },
    "date": $(date +%s),
    "contact": {
      "phone_number": "+79001234567",
      "first_name": "$TELEGRAM_FIRST_NAME",
      "last_name": "$TELEGRAM_LAST_NAME",
      "user_id": $TELEGRAM_USER_ID
    }
  }
}
EOF
)

echo "Отправляем webhook с контактом:"
CONTACT_RESULT=$(curl -s -X POST "${BASE_URL}/api/v1/telegram/webhook" \
    -H "Content-Type: application/json" \
    -d "$CONTACT_WEBHOOK")

echo "Результат contact webhook:"
echo "$CONTACT_RESULT" | jq . 2>/dev/null || echo "$CONTACT_RESULT"
echo ""

echo -e "${BLUE}7. Симуляция подтверждения авторизации...${NC}"
CONFIRM_WEBHOOK=$(cat <<EOF
{
  "update_id": 123456791,
  "callback_query": {
    "id": "callback_diagnosis_123",
    "from": {
      "id": $TELEGRAM_USER_ID,
      "is_bot": false,
      "first_name": "$TELEGRAM_FIRST_NAME",
      "last_name": "$TELEGRAM_LAST_NAME",
      "username": "$TELEGRAM_USERNAME"
    },
    "message": {
      "message_id": 1003,
      "chat": {
        "id": $TELEGRAM_USER_ID,
        "type": "private"
      }
    },
    "data": "confirm_auth_$AUTH_TOKEN"
  }
}
EOF
)

echo "Отправляем webhook подтверждения:"
CONFIRM_RESULT=$(curl -s -X POST "${BASE_URL}/api/v1/telegram/webhook" \
    -H "Content-Type: application/json" \
    -d "$CONFIRM_WEBHOOK")

echo "Результат confirm webhook:"
echo "$CONFIRM_RESULT" | jq . 2>/dev/null || echo "$CONFIRM_RESULT"
echo ""

echo -e "${BLUE}8. Проверка финального статуса токена...${NC}"
FINAL_STATUS_RESPONSE=$(curl -s "${BASE_URL}/api/v1/auth/telegram/status/${AUTH_TOKEN}")
echo "Финальный статус токена:"
echo "$FINAL_STATUS_RESPONSE" | jq . 2>/dev/null || echo "$FINAL_STATUS_RESPONSE"

FINAL_STATUS=$(echo "$FINAL_STATUS_RESPONSE" | jq -r '.status // empty' 2>/dev/null)
SUCCESS=$(echo "$FINAL_STATUS_RESPONSE" | jq -r '.success // false' 2>/dev/null)

if [ "$FINAL_STATUS" = "CONFIRMED" ] && [ "$SUCCESS" = "true" ]; then
    echo -e "${GREEN}✅ Аутентификация успешно завершена!${NC}"

    # Проверяем JWT токен
    JWT_TOKEN=$(echo "$FINAL_STATUS_RESPONSE" | jq -r '.authData.token // empty' 2>/dev/null)
    if [ -n "$JWT_TOKEN" ] && [ "$JWT_TOKEN" != "null" ]; then
        echo -e "${GREEN}✅ JWT токен получен${NC}"
    else
        echo -e "${RED}❌ JWT токен не получен${NC}"
    fi
else
    echo -e "${RED}❌ Проблема с аутентификацией${NC}"
    echo "Статус: $FINAL_STATUS"
    echo "Success: $SUCCESS"
fi
echo ""

echo -e "${BLUE}9. Проверка созданного пользователя...${NC}"
echo "Выполните SQL запрос для проверки пользователя:"
echo "SELECT id, username, email, telegram_id, first_name, last_name, is_active FROM users WHERE telegram_id = $TELEGRAM_USER_ID;"
echo ""

echo -e "${BLUE}10. Проверка обновленного токена...${NC}"
echo "Выполните SQL запрос для проверки обновленного токена:"
echo "SELECT auth_token, telegram_id, status, confirmed_at FROM telegram_auth_tokens WHERE auth_token = '$AUTH_TOKEN';"
echo ""

echo "📋 ДИАГНОСТИКА ЗАВЕРШЕНА"
echo "======================="
echo "Токен: $AUTH_TOKEN"
echo "Telegram User ID: $TELEGRAM_USER_ID"
echo "Финальный статус: $FINAL_STATUS"
echo ""
echo "🔍 Если проблема не решена, проверьте логи приложения на предмет ошибок."