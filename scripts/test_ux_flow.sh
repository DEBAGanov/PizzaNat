#!/bin/bash

echo "🧪 Тестирование Telegram авторизации в ПРОДАКШЕНЕ"
echo "=================================================="

BASE_URL="https://debaganov-pizzanat-0177.twc1.net"

echo "🔍 Проверка webhook статуса..."
curl -s -X GET $BASE_URL/api/v1/telegram/webhook/info | jq
echo ""

# 1. Инициализация авторизации
echo "1️⃣ Инициализация авторизации..."
INIT_RESPONSE=$(curl -s -X POST $BASE_URL/api/v1/auth/telegram/init \
  -H "Content-Type: application/json" \
  -d '{"deviceId":"prod_test_flow"}')

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

echo "📱 Теперь используйте реальный Telegram бот:"
echo "🔗 Ссылка: https://t.me/PizzaNatBot?start=$AUTH_TOKEN"
echo ""
echo "⏳ Нажмите Enter после того как отправите /start в боте..."
read

echo "3️⃣ Проверка статуса после /start..."
curl -s -X GET $BASE_URL/api/v1/auth/telegram/status/$AUTH_TOKEN | jq
echo ""

echo "⏳ Нажмите Enter после того как нажмете 'Подтвердить вход' в боте..."
read

echo "4️⃣ Финальная проверка статуса (должен быть CONFIRMED)..."
curl -s -X GET $BASE_URL/api/v1/auth/telegram/status/$AUTH_TOKEN | jq
echo ""

echo "✅ Тестирование завершено!"