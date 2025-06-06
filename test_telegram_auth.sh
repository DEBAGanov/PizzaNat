#!/bin/bash

# Скрипт для тестирования Telegram аутентификации
# Проверяет все эндпоинты Telegram auth API

echo "🤖 Тестирование Telegram аутентификации PizzaNat"
echo "================================================="

BASE_URL="http://localhost:8080"
SUCCESS_COUNT=0
TOTAL_TESTS=0

# Функция для выполнения HTTP запросов
make_request() {
    local method=$1
    local url=$2
    local data=$3
    local description=$4

    echo "🔍 Тест: $description"
    echo "📍 $method $url"

    ((TOTAL_TESTS++))

    if [[ "$method" == "GET" ]]; then
        response=$(curl -s -w "HTTP_STATUS:%{http_code}" "$url")
    else
        response=$(curl -s -w "HTTP_STATUS:%{http_code}" -X "$method" -H "Content-Type: application/json" -d "$data" "$url")
    fi

    status_code="${response##*HTTP_STATUS:}"
    body="${response%HTTP_STATUS:*}"

    echo "📊 Статус: $status_code"

    if [[ "$status_code" =~ ^2[0-9][0-9]$ ]]; then
        echo "✅ УСПЕШНО"
        echo "📋 Ответ: $(echo "$body" | jq . 2>/dev/null || echo "$body")"
        ((SUCCESS_COUNT++))

        # Извлекаем authToken для последующих тестов
        if [[ "$body" =~ \"authToken\":\"([^\"]+)\" ]]; then
            AUTH_TOKEN="${BASH_REMATCH[1]}"
            echo "🔑 Извлечен authToken: $AUTH_TOKEN"
        fi
    else
        echo "❌ ОШИБКА"
        echo "📋 Ответ: $(echo "$body" | jq . 2>/dev/null || echo "$body")"
    fi

    echo ""
}

echo "🚀 Запуск тестов Telegram аутентификации..."
echo ""

# Тест 1: Health check Telegram auth
make_request "GET" "$BASE_URL/api/v1/auth/telegram/test" "" "Health check Telegram аутентификации"

# Тест 2: Инициализация Telegram аутентификации
make_request "POST" "$BASE_URL/api/v1/auth/telegram/init" '{
    "deviceId": "test_device_123"
}' "Инициализация Telegram аутентификации"

# Тест 3: Проверка статуса токена (если токен был получен)
if [[ -n "$AUTH_TOKEN" ]]; then
    make_request "GET" "$BASE_URL/api/v1/auth/telegram/status/$AUTH_TOKEN" "" "Проверка статуса Telegram токена"
else
    echo "⚠️  Тест статуса пропущен - токен не получен"
    echo ""
fi

# Тест 4: Проверка статуса несуществующего токена
make_request "GET" "$BASE_URL/api/v1/auth/telegram/status/tg_auth_nonexistent123" "" "Проверка несуществующего токена"

# Тест 5: Инициализация без deviceId
make_request "POST" "$BASE_URL/api/v1/auth/telegram/init" '{}' "Инициализация без deviceId"

# Тест 6: Webhook info (если webhook настроен)
make_request "GET" "$BASE_URL/api/v1/telegram/webhook/info" "" "Информация о webhook"

# Тест 7: Некорректный токен
make_request "GET" "$BASE_URL/api/v1/auth/telegram/status/invalid_token" "" "Проверка некорректного формата токена"

# Тест 8: Пустой запрос инициализации
make_request "POST" "$BASE_URL/api/v1/auth/telegram/init" '{
    "deviceId": ""
}' "Инициализация с пустым deviceId"

echo "📊 РЕЗУЛЬТАТЫ ТЕСТИРОВАНИЯ"
echo "=========================="
echo "✅ Успешно: $SUCCESS_COUNT из $TOTAL_TESTS тестов"
echo "📈 Процент успеха: $((SUCCESS_COUNT * 100 / TOTAL_TESTS))%"

if [[ $SUCCESS_COUNT -eq $TOTAL_TESTS ]]; then
    echo "🎉 ВСЕ ТЕСТЫ ПРОШЛИ УСПЕШНО!"
elif [[ $SUCCESS_COUNT -ge $((TOTAL_TESTS * 70 / 100)) ]]; then
    echo "👍 ХОРОШО - большинство тестов прошли"
else
    echo "⚠️  ТРЕБУЕТ ВНИМАНИЯ - много неудачных тестов"
fi

echo ""
echo "📝 ИНСТРУКЦИИ ДЛЯ ПОЛНОГО ТЕСТИРОВАНИЯ:"
echo "1. Настройте TELEGRAM_AUTH_BOT_TOKEN в переменных окружения"
echo "2. Создайте Telegram бота через @BotFather"
echo "3. Настройте webhook URL для обработки команд"
echo "4. Протестируйте полный цикл аутентификации через бота"

echo ""
echo "🔗 Полезные ссылки:"
echo "- Swagger UI: http://localhost:8080/swagger-ui/index.html"
echo "- Telegram Auth API: http://localhost:8080/api/v1/auth/telegram"
echo "- Webhook API: http://localhost:8080/api/v1/telegram"

exit 0