#!/bin/bash

# Тест получения контактных данных через Telegram webhook
# Симулирует отправку контакта пользователем

# Цвета для вывода
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
NC='\033[0m' # No Color

echo -e "${BLUE}🔧 Тест получения контактных данных через Telegram webhook${NC}"
echo "================================================"

BASE_URL="http://localhost:8080"
WEBHOOK_URL="$BASE_URL/api/v1/telegram/webhook"

# Данные для тестирования
TELEGRAM_USER_ID=7819187384
CHAT_ID=-4919444764
PHONE_NUMBER="+79199969633"
FIRST_NAME="Владимир"
LAST_NAME="Баганов"

echo -e "${YELLOW}📋 Параметры теста:${NC}"
echo "  Webhook URL: $WEBHOOK_URL"
echo "  Telegram User ID: $TELEGRAM_USER_ID"
echo "  Chat ID: $CHAT_ID"
echo "  Номер телефона: $PHONE_NUMBER"
echo "  Имя: $FIRST_NAME $LAST_NAME"
echo ""

# Тест 1: Отправка контакта (свой контакт)
echo -e "${BLUE}1️⃣ Тест отправки своего контакта${NC}"
echo "-----------------------------------"

contact_update=$(cat <<EOF
{
  "update_id": 123456789,
  "message": {
    "message_id": 100,
    "from": {
      "id": $TELEGRAM_USER_ID,
      "first_name": "$FIRST_NAME",
      "last_name": "$LAST_NAME",
      "username": "vladimir_baganov"
    },
    "chat": {
      "id": $CHAT_ID,
      "type": "private"
    },
    "date": $(date +%s),
    "contact": {
      "phone_number": "$PHONE_NUMBER",
      "first_name": "$FIRST_NAME",
      "last_name": "$LAST_NAME",
      "user_id": $TELEGRAM_USER_ID
    }
  }
}
EOF
)

echo "Отправка контакта..."
contact_response=$(curl -s -X POST "$WEBHOOK_URL" \
  -H "Content-Type: application/json" \
  -d "$contact_update")

if echo "$contact_response" | grep -q '"status":"OK"'; then
    echo -e "${GREEN}✅ Контакт успешно обработан${NC}"
    echo "Ответ: $contact_response"
else
    echo -e "${RED}❌ Ошибка обработки контакта${NC}"
    echo "Ответ: $contact_response"
fi

echo ""

# Тест 2: Отправка чужого контакта
echo -e "${BLUE}2️⃣ Тест отправки чужого контакта${NC}"
echo "-----------------------------------"

foreign_contact_update=$(cat <<EOF
{
  "update_id": 123456790,
  "message": {
    "message_id": 101,
    "from": {
      "id": $TELEGRAM_USER_ID,
      "first_name": "$FIRST_NAME",
      "last_name": "$LAST_NAME",
      "username": "vladimir_baganov"
    },
    "chat": {
      "id": $CHAT_ID,
      "type": "private"
    },
    "date": $(date +%s),
    "contact": {
      "phone_number": "+79161234999",
      "first_name": "Иван",
      "last_name": "Петров",
      "user_id": 987654321
    }
  }
}
EOF
)

echo "Отправка чужого контакта..."
foreign_response=$(curl -s -X POST "$WEBHOOK_URL" \
  -H "Content-Type: application/json" \
  -d "$foreign_contact_update")

if echo "$foreign_response" | grep -q '"status":"OK"'; then
    echo -e "${YELLOW}⚠️ Чужой контакт обработан (ожидается предупреждение)${NC}"
    echo "Ответ: $foreign_response"
else
    echo -e "${RED}❌ Ошибка обработки чужого контакта${NC}"
    echo "Ответ: $foreign_response"
fi

echo ""

# Тест 3: Отправка контакта без номера телефона
echo -e "${BLUE}3️⃣ Тест контакта без номера телефона${NC}"
echo "---------------------------------------"

no_phone_update=$(cat <<EOF
{
  "update_id": 123456791,
  "message": {
    "message_id": 102,
    "from": {
      "id": $TELEGRAM_USER_ID,
      "first_name": "$FIRST_NAME",
      "last_name": "$LAST_NAME",
      "username": "vladimir_baganov"
    },
    "chat": {
      "id": $CHAT_ID,
      "type": "private"
    },
    "date": $(date +%s),
    "contact": {
      "first_name": "$FIRST_NAME",
      "last_name": "$LAST_NAME",
      "user_id": $TELEGRAM_USER_ID
    }
  }
}
EOF
)

echo "Отправка контакта без номера..."
no_phone_response=$(curl -s -X POST "$WEBHOOK_URL" \
  -H "Content-Type: application/json" \
  -d "$no_phone_update")

if echo "$no_phone_response" | grep -q '"status":"OK"'; then
    echo -e "${YELLOW}⚠️ Контакт без номера обработан (ожидается ошибка)${NC}"
    echo "Ответ: $no_phone_response"
else
    echo -e "${RED}❌ Ошибка обработки контакта без номера${NC}"
    echo "Ответ: $no_phone_response"
fi

echo ""

# Тест 4: Команда /start с токеном (должна показывать кнопку контакта)
echo -e "${BLUE}4️⃣ Тест команды /start (должна показать кнопку контакта)${NC}"
echo "--------------------------------------------------------"

start_update=$(cat <<EOF
{
  "update_id": 123456792,
  "message": {
    "message_id": 103,
    "from": {
      "id": $TELEGRAM_USER_ID,
      "first_name": "$FIRST_NAME",
      "last_name": "$LAST_NAME",
      "username": "vladimir_baganov"
    },
    "chat": {
      "id": $CHAT_ID,
      "type": "private"
    },
    "date": $(date +%s),
    "text": "/start tg_auth_abc123def456"
  }
}
EOF
)

echo "Отправка команды /start с токеном..."
start_response=$(curl -s -X POST "$WEBHOOK_URL" \
  -H "Content-Type: application/json" \
  -d "$start_update")

if echo "$start_response" | grep -q '"status":"OK"'; then
    echo -e "${GREEN}✅ Команда /start обработана${NC}"
    echo "Ответ: $start_response"
else
    echo -e "${RED}❌ Ошибка обработки команды /start${NC}"
    echo "Ответ: $start_response"
fi

echo ""
echo -e "${BLUE}📊 Результаты тестирования${NC}"
echo "========================="
echo -e "✅ ${GREEN}Все тесты завершены${NC}"
echo ""
echo -e "${YELLOW}💡 Для полного тестирования:${NC}"
echo "1. Убедитесь, что приложение запущено на localhost:8080"
echo "2. Проверьте логи приложения для деталей обработки"
echo "3. Настройте реального Telegram бота для тестирования"
echo ""
echo -e "${BLUE}🔗 Настройка реального бота:${NC}"
echo "1. Создайте бота через @BotFather"
echo "2. Получите токен и обновите конфигурацию"
echo "3. Установите webhook: POST /api/v1/telegram/webhook/register"
echo "4. Протестируйте с реальными пользователями"