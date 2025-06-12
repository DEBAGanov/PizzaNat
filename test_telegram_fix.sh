#!/bin/bash

# Быстрый тест исправления Telegram бота

echo "🔧 Тестирование исправлений Telegram бота"
echo "=========================================="

BASE_URL="http://localhost:8080"

echo "1. Проверка приложения..."
if curl -s "${BASE_URL}/actuator/health" | grep -q "UP"; then
    echo "   ✅ Приложение работает"
else
    echo "   ❌ Приложение не доступно"
    exit 1
fi

echo "2. Создание токена авторизации..."
response=$(curl -s -X POST "${BASE_URL}/api/v1/auth/telegram/init" \
    -H "Content-Type: application/json" \
    -d '{"deviceId":"test_fix"}' 2>/dev/null)

if echo "$response" | grep -q "authToken"; then
    TOKEN=$(echo "$response" | jq -r '.authToken' 2>/dev/null)
    BOT_URL=$(echo "$response" | jq -r '.telegramBotUrl' 2>/dev/null)
    echo "   ✅ Токен создан: $TOKEN"
    echo "   ✅ Ссылка: $BOT_URL"
else
    echo "   ❌ Ошибка создания токена"
    echo "   Ответ: $response"
    exit 1
fi

echo ""
echo "🚀 Перейдите по ссылке и протестируйте:"
echo "$BOT_URL"
echo ""
echo "Ожидаемое поведение:"
echo "✅ Сразу появится приветствие (НЕ 'Неизвестная команда')"
echo "✅ Будет кнопка '📱 Отправить телефон'"
echo "✅ После отправки телефона - 'Авторизация завершена!'"
echo ""

# Проверка логов
echo "3. Мониторинг логов (нажмите Ctrl+C для выхода)..."
sleep 2
if command -v docker &> /dev/null; then
    docker logs pizzanat-app -f 2>&1 | grep -E "(PizzaNatBot|TelegramWebhook|TelegramAuth)" &
    LOG_PID=$!
    echo "   Логи мониторятся... (PID: $LOG_PID)"
    echo "   Нажмите Ctrl+C для остановки"

    # Ждем прерывания
    trap "kill $LOG_PID 2>/dev/null; exit 0" INT
    wait
else
    echo "   Docker не найден"
fi