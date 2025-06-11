#!/bin/bash

echo "🧪 Тестирование улучшенного UX flow Telegram авторизации"
echo "=================================================="

BASE_URL="http://localhost:8080"

# 1. Инициализация авторизации
echo "1️⃣ Инициализация авторизации..."
INIT_RESPONSE=$(curl -s -X POST $BASE_URL/api/v1/auth/telegram/init \
  -H "Content-Type: application/json" \
  -d '{"deviceId":"ux_test_flow"}')

echo "Ответ инициализации:"
echo $INIT_RESPONSE | jq
echo ""

# Извлекаем токен
AUTH_TOKEN=$(echo $INIT_RESPONSE | jq -r '.authToken')
echo "Токен: $AUTH_TOKEN"
echo ""

# 2. Проверка статуса (должен быть PENDING)
echo "2️⃣ Проверка начального статуса..."
curl -s -X GET $BASE_URL/api/v1/auth/telegram/status/$AUTH_TOKEN | jq
echo ""

# 3. Симуляция webhook /start
echo "3️⃣ Симуляция команды /start в боте..."
START_WEBHOOK=$(cat <<EOF
{
  "update_id": 555555555,
  "message": {
    "message_id": 1,
    "from": {
      "id": 555555555,
      "username": "test_user",
      "first_name": "Test",
      "last_name": "User"
    },
    "chat": {
      "id": 555555555,
      "type": "private"
    },
    "date": 1645123456,
    "text": "/start $AUTH_TOKEN"
  }
}
EOF
)

curl -s -X POST $BASE_URL/api/v1/telegram/webhook \
  -H "Content-Type: application/json" \
  -d "$START_WEBHOOK" | jq
echo ""

# 4. Симуляция callback query (нажатие кнопки "Подтвердить вход")
echo "4️⃣ Симуляция нажатия кнопки 'Подтвердить вход'..."
CALLBACK_WEBHOOK=$(cat <<EOF
{
  "update_id": 555555556,
  "callback_query": {
    "id": "callback_123",
    "from": {
      "id": 555555555,
      "username": "test_user",
      "first_name": "Test",
      "last_name": "User"
    },
    "message": {
      "message_id": 2,
      "chat": {
        "id": 555555555,
        "type": "private"
      }
    },
    "data": "confirm_auth_$AUTH_TOKEN"
  }
}
EOF
)

curl -s -X POST $BASE_URL/api/v1/telegram/webhook \
  -H "Content-Type: application/json" \
  -d "$CALLBACK_WEBHOOK" | jq
echo ""

# 5. Финальная проверка статуса (должен быть CONFIRMED)
echo "5️⃣ Проверка финального статуса (должен быть CONFIRMED)..."
curl -s -X GET $BASE_URL/api/v1/auth/telegram/status/$AUTH_TOKEN | jq
echo ""

# 6. Последние логи приложения
echo "6️⃣ Последние логи приложения:"
docker logs pizzanat-app --tail=10
echo ""

echo "✅ Тестирование завершено!"