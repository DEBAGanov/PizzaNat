#!/bin/bash

# Тестирование рабочего пути Mini App
# Дата создания: 27.01.2025

echo "🧪 Тестирование РАБОЧЕГО пути Mini App: /miniapp/"
echo "=============================================="

BASE_URL="https://api.dimbopizza.ru"

echo
echo "✅ РАБОЧИЕ пути (должны быть HTTP 200):"
echo "----------------------------------------"

echo "📱 Mini App checkout.html:"
curl -s -w "HTTP Status: %{http_code}\n" $BASE_URL/miniapp/checkout.html | tail -1

echo "🔧 API Test page:"
curl -s -w "HTTP Status: %{http_code}\n" $BASE_URL/miniapp/test-api.html | tail -1

echo "📜 JavaScript файлы:"
curl -s -w "api.js: HTTP %{http_code}\n" $BASE_URL/miniapp/api.js
curl -s -w "checkout-app.js: HTTP %{http_code}\n" $BASE_URL/miniapp/checkout-app.js  
curl -s -w "telegram-web-app.js: HTTP %{http_code}\n" $BASE_URL/miniapp/telegram-web-app.js

echo
echo "❌ НЕ РАБОТАЮЩИЕ пути (HTTP 403):"
echo "----------------------------------"

echo "📱 Старый путь checkout.html:"
curl -s -w "HTTP Status: %{http_code}\n" $BASE_URL/static/miniapp/checkout.html | tail -1

echo
echo "🔍 Проверка содержимого рабочего Mini App:"
echo "==========================================="
echo "Заголовок страницы:"
curl -s $BASE_URL/miniapp/checkout.html | grep -o '<title>.*</title>'

echo
echo "Проверка наличия нашего кода авторизации:"
curl -s $BASE_URL/miniapp/checkout-app.js | grep -o "enhancedAuthenticateWebApp" | head -1

echo
echo "📋 РЕЗЮМЕ:"
echo "=========="
echo "✅ Mini App ДОСТУПЕН по адресу:"
echo "   🌐 https://api.dimbopizza.ru/miniapp/checkout.html"
echo
echo "❌ Mini App НЕ ДОСТУПЕН по старому адресу:"  
echo "   🚫 https://api.dimbopizza.ru/static/miniapp/checkout.html"
echo
echo "🔧 РЕШЕНИЕ:"
echo "   1. Обновить ссылку в Telegram боте на рабочий путь"
echo "   2. Или исправить доступ к /static/miniapp/"
echo
echo "🎯 ПОСЛЕ ИСПРАВЛЕНИЯ:"
echo "   ✅ Пользователи смогут открыть Mini App"
echo "   ✅ JavaScript код авторизации будет работать"
echo "   ✅ Записи в telegram_auth_tokens будут создаваться"

