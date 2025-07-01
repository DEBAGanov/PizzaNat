#!/bin/bash

echo "📱 Тестирование кнопки 'Отправить телефон'"
echo "=========================================="

BASE_URL="https://debaganov-pizzanat-0177.twc1.net"

echo "1️⃣ Создаем новый токен авторизации..."
INIT_RESPONSE=$(curl -s -X POST "$BASE_URL/api/v1/auth/telegram/init" \
  -H "Content-Type: application/json" \
  -d '{"deviceId":"phone_button_test"}')

echo "Ответ инициализации:"
echo $INIT_RESPONSE | jq
echo ""

# Извлекаем токен
AUTH_TOKEN=$(echo $INIT_RESPONSE | jq -r '.authToken')
echo "Токен: $AUTH_TOKEN"
echo ""

echo "2️⃣ Симулируем команду /start с токеном..."
START_WEBHOOK=$(cat <<EOF
{
  "update_id": 888888888,
  "message": {
    "message_id": 888,
    "from": {
      "id": 888888888,
      "is_bot": false,
      "first_name": "TestPhone",
      "last_name": "User",
      "username": "test_phone_user"
    },
    "chat": {
      "id": 888888888,
      "type": "private"
    },
    "date": $(date +%s),
    "text": "/start $AUTH_TOKEN"
  }
}
EOF
)

echo "Отправляем /start webhook:"
curl -s -X POST "$BASE_URL/api/v1/telegram/webhook" \
  -H "Content-Type: application/json" \
  -d "$START_WEBHOOK" | jq
echo ""

echo "3️⃣ Симулируем отправку контакта..."
CONTACT_WEBHOOK=$(cat <<EOF
{
  "update_id": 999999999,
  "message": {
    "message_id": 999,
    "from": {
      "id": 888888888,
      "is_bot": false,
      "first_name": "TestPhone",
      "last_name": "User",
      "username": "test_phone_user"
    },
    "chat": {
      "id": 888888888,
      "type": "private"
    },
    "date": $(date +%s),
    "contact": {
      "phone_number": "+79161234567",
      "first_name": "TestPhone",
      "last_name": "User",
      "user_id": 888888888
    }
  }
}
EOF
)

echo "Отправляем контакт webhook:"
curl -s -X POST "$BASE_URL/api/v1/telegram/webhook" \
  -H "Content-Type: application/json" \
  -d "$CONTACT_WEBHOOK" | jq
echo ""

echo "4️⃣ Симулируем нажатие кнопки 'Подтвердить вход'..."
CONFIRM_WEBHOOK=$(cat <<EOF
{
  "update_id": 111111111,
  "callback_query": {
    "id": "confirm_callback_123",
    "from": {
      "id": 888888888,
      "is_bot": false,
      "first_name": "TestPhone",
      "last_name": "User",
      "username": "test_phone_user"
    },
    "message": {
      "message_id": 999,
      "chat": {
        "id": 888888888,
        "type": "private"
      }
    },
    "data": "confirm_auth_$AUTH_TOKEN"
  }
}
EOF
)

echo "Отправляем подтверждение webhook:"
curl -s -X POST "$BASE_URL/api/v1/telegram/webhook" \
  -H "Content-Type: application/json" \
  -d "$CONFIRM_WEBHOOK" | jq
echo ""

echo "5️⃣ Проверяем финальный статус токена..."
curl -s -X GET "$BASE_URL/api/v1/auth/telegram/status/$AUTH_TOKEN" | jq
echo ""

echo "✅ Тестирование завершено!"
echo ""
echo "🔗 Ссылка для реального тестирования:"
echo "https://t.me/PizzaNatBot?start=$AUTH_TOKEN"