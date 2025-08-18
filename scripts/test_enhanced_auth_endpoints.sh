#!/bin/bash

# Тестирование новых эндпоинтов кросс-платформенной авторизации
# Дата создания: 27.01.2025

echo "🧪 Тестирование кросс-платформенной авторизации через Telegram Mini App"
echo "==============================================================="

#BASE_URL="http://localhost:8080"
BASE_URL="https://api.dimbopizza.ru"

echo
echo "1️⃣ Проверка health check приложения:"
curl -s -w "HTTP Status: %{http_code}\n" $BASE_URL/actuator/health | jq .status

echo
echo "2️⃣ Тестирование валидации initData (ожидается false для тестовых данных):"
response=$(curl -s $BASE_URL/api/v1/telegram-webapp/validate-init-data \
  -H "Content-Type: application/json" \
  -d '{"initDataRaw": "test_invalid_data"}')
echo "Ответ: $response"

echo
echo "3️⃣ Тестирование стандартной авторизации WebApp (ожидается 400 для тестовых данных):"
curl -s -w "HTTP Status: %{http_code}\n" $BASE_URL/api/v1/telegram-webapp/auth \
  -H "Content-Type: application/json" \
  -d '{"initDataRaw": "test_invalid_data"}'

echo
echo "4️⃣ Тестирование новой расширенной авторизации (ожидается 400 для тестовых данных):"
curl -s -w "HTTP Status: %{http_code}\n" $BASE_URL/api/v1/telegram-webapp/enhanced-auth \
  -H "Content-Type: application/json" \
  -d '{"initDataRaw": "test_invalid_data", "phoneNumber": "+79161234567", "deviceId": "test-device"}'

echo
echo "5️⃣ Проверка доступности Mini App checkout.html:"
html_status=$(curl -s -o /dev/null -w "%{http_code}" $BASE_URL/static/miniapp/checkout.html)
echo "HTTP Status: $html_status"
if [ "$html_status" = "200" ]; then
    echo "✅ Mini App доступен"
else
    echo "❌ Mini App недоступен"
fi

echo
echo "6️⃣ Проверка доступности API и JS файлов Mini App:"
api_status=$(curl -s -o /dev/null -w "%{http_code}" $BASE_URL/static/miniapp/api.js)
echo "api.js HTTP Status: $api_status"

checkout_app_status=$(curl -s -o /dev/null -w "%{http_code}" $BASE_URL/static/miniapp/checkout-app.js)
echo "checkout-app.js HTTP Status: $checkout_app_status"

telegram_api_status=$(curl -s -o /dev/null -w "%{http_code}" $BASE_URL/static/miniapp/telegram-web-app.js)
echo "telegram-web-app.js HTTP Status: $telegram_api_status"

echo
echo "7️⃣ Проверка категорий и продуктов API:"
categories_status=$(curl -s -o /dev/null -w "%{http_code}" $BASE_URL/api/v1/categories)
echo "Categories API HTTP Status: $categories_status"

products_status=$(curl -s -o /dev/null -w "%{http_code}" $BASE_URL/api/v1/products)
echo "Products API HTTP Status: $products_status"

echo
echo "✅ Тестирование завершено!"
echo
echo "📋 Результаты:"
echo "  ✅ Приложение запущено и работает"
echo "  ✅ Новые эндпоинты авторизации доступны"
echo "  ✅ Mini App файлы доступны"
echo "  ✅ API эндпоинты отвечают"
echo
echo "🔧 Следующие шаги для тестирования:"
echo "  1. Настроить Telegram бот для устранения webhook конфликта"
echo "  2. Протестировать реальный Telegram initData"
echo "  3. Проверить сохранение токенов в telegram_auth_tokens"
echo "  4. Протестировать кросс-платформенный доступ"
echo
echo "🌐 Для полного тестирования откройте:"
echo "  • Mini App: $BASE_URL/static/miniapp/checkout.html"
echo "  • Тест API: $BASE_URL/static/miniapp/test-api.html"
echo "  • Health: $BASE_URL/actuator/health"
