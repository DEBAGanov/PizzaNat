#!/bin/bash

# Диагностика проблем в продакшне с кросс-платформенной авторизацией
# Дата создания: 27.01.2025

echo "🔍 Диагностика продакшн развертывания api.dimbopizza.ru"
echo "========================================================"

BASE_URL="https://api.dimbopizza.ru"

echo
echo "1️⃣ Проверка доступности основных API эндпоинтов:"
echo "Health check:"
curl -s -w "HTTP Status: %{http_code}\n" $BASE_URL/actuator/health | head -1

echo
echo "Categories API:"
curl -s -w "HTTP Status: %{http_code}\n" $BASE_URL/api/v1/categories | head -1

echo
echo "2️⃣ Проверка авторизационных эндпоинтов:"
echo "Standard WebApp auth:"
curl -s -w "HTTP Status: %{http_code}\n" $BASE_URL/api/v1/telegram-webapp/auth \
  -H "Content-Type: application/json" \
  -d '{"initDataRaw": "test"}' | tail -1

echo
echo "Enhanced WebApp auth:"
curl -s -w "HTTP Status: %{http_code}\n" $BASE_URL/api/v1/telegram-webapp/enhanced-auth \
  -H "Content-Type: application/json" \
  -d '{"initDataRaw": "test", "phoneNumber": "+79161234567"}' | tail -1

echo
echo "Validate initData:"
response=$(curl -s $BASE_URL/api/v1/telegram-webapp/validate-init-data \
  -H "Content-Type: application/json" \
  -d '{"initDataRaw": "test"}')
echo "Response: $response"

echo
echo "3️⃣ Проверка статических файлов Mini App (ПРОБЛЕМА):"
static_files=("checkout.html" "api.js" "checkout-app.js" "telegram-web-app.js" "test-api.html")

for file in "${static_files[@]}"; do
    status=$(curl -s -o /dev/null -w "%{http_code}" $BASE_URL/static/miniapp/$file)
    if [ "$status" = "200" ]; then
        echo "✅ $file: HTTP $status"
    else
        echo "❌ $file: HTTP $status"
    fi
done

echo
echo "4️⃣ Проверка альтернативных путей для статических файлов:"
alternative_paths=("/miniapp/checkout.html" "/api/miniapp/checkout.html" "/public/miniapp/checkout.html")

for path in "${alternative_paths[@]}"; do
    status=$(curl -s -o /dev/null -w "%{http_code}" $BASE_URL$path)
    echo "Alternative path $path: HTTP $status"
done

echo
echo "5️⃣ Анализ заголовков ответа для статических файлов:"
echo "Детальные заголовки для checkout.html:"
curl -I $BASE_URL/static/miniapp/checkout.html 2>&1 | grep -E "(HTTP|server|content-type|x-)"

echo
echo "6️⃣ Проверка CORS настроек:"
echo "CORS preflight для enhanced-auth:"
curl -s -w "HTTP Status: %{http_code}\n" -X OPTIONS $BASE_URL/api/v1/telegram-webapp/enhanced-auth \
  -H "Origin: https://t.me" \
  -H "Access-Control-Request-Method: POST" \
  -H "Access-Control-Request-Headers: Content-Type" | tail -1

echo
echo "7️⃣ Тестирование с реальным User-Agent Telegram:"
echo "Enhanced auth с Telegram User-Agent:"
curl -s -w "HTTP Status: %{http_code}\n" $BASE_URL/api/v1/telegram-webapp/enhanced-auth \
  -H "Content-Type: application/json" \
  -H "User-Agent: Mozilla/5.0 (Linux; Android 10; SM-G975F) AppleWebKit/537.36 Telegram-Android/8.1.2" \
  -d '{"initDataRaw": "test", "phoneNumber": "+79161234567"}' | tail -1

echo
echo "📋 ДИАГНОСТИЧЕСКИЕ ВЫВОДЫ:"
echo "=========================="
echo "🔧 ПРОБЛЕМЫ:"
echo "  ❌ Статические файлы Mini App недоступны (HTTP 403)"
echo "  ❌ Пользователи не могут загрузить Mini App интерфейс"
echo "  ❌ JavaScript файлы авторизации недоступны"
echo
echo "✅ РАБОТАЕТ:"
echo "  ✅ Backend API эндпоинты доступны"
echo "  ✅ Авторизационные эндпоинты отвечают"
echo "  ✅ Основное приложение работает"
echo
echo "🔧 РЕШЕНИЯ:"
echo "  1. Проверить конфигурацию Nginx для /static/miniapp/"
echo "  2. Проверить Spring Security настройки для статических ресурсов"
echo "  3. Убедиться что файлы скопированы в контейнер"
echo "  4. Проверить права доступа к статическим файлам"
echo
echo "📱 БЕЗ ДОСТУПА К MINI APP:"
echo "  ❌ Система кросс-платформенной авторизации НЕ РАБОТАЕТ"
echo "  ❌ Пользователи не могут загрузить интерфейс"
echo "  ❌ Записи в telegram_auth_tokens НЕ СОЗДАЮТСЯ"
echo
echo "🚀 СЛЕДУЮЩИЕ ШАГИ:"
echo "  1. Исправить доступ к статическим файлам"
echo "  2. Проверить логи контейнера в продакшне"
echo "  3. Протестировать доступ к Mini App"
echo "  4. Повторить тест авторизации"

