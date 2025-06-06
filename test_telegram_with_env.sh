#!/bin/bash

# Скрипт для тестирования Telegram аутентификации с установкой переменных окружения
echo "🤖 Тестирование Telegram аутентификации PizzaNat (с env)"
echo "====================================================="

# Устанавливаем тестовые переменные окружения
export TELEGRAM_AUTH_BOT_TOKEN="7819187384:AAGJNn0cwfJ7Nsv_N25h75eggEmqmD5WZG4"
export TELEGRAM_AUTH_BOT_USERNAME="-4919444764"
export TELEGRAM_AUTH_WEBHOOK_URL="http://localhost:8080/api/v1/telegram/webhook"

echo "📋 Установленные переменные:"
echo "  TELEGRAM_AUTH_BOT_TOKEN: ${TELEGRAM_AUTH_BOT_TOKEN:0:20}..."
echo "  TELEGRAM_AUTH_BOT_USERNAME: $TELEGRAM_AUTH_BOT_USERNAME"
echo ""

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

# Сначала проверим, что приложение запущено
echo "🔧 Проверка доступности приложения..."
if ! curl -s -f "$BASE_URL/api/health" > /dev/null; then
    echo "❌ Приложение недоступно по адресу $BASE_URL"
    echo "   Запустите приложение: ./gradlew bootRun"
    exit 1
fi
echo "✅ Приложение доступно"
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

# Тест 5: Webhook info
make_request "GET" "$BASE_URL/api/v1/telegram/webhook/info" "" "Информация о webhook"

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
echo "📝 Для полного тестирования:"
echo "1. Создайте Telegram бота через @BotFather"
echo "2. Установите реальный TELEGRAM_AUTH_BOT_TOKEN"
echo "3. Настройте webhook URL для обработки команд"

exit 0