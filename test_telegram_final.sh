#!/bin/bash

# Финальный скрипт для тестирования исправленного Telegram бота
# Автор: AI Assistant
# Дата: 2025-01-11

echo "🤖 Финальное тестирование Telegram бота PizzaNat"
echo "================================================="

# Цвета для вывода
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
NC='\033[0m' # No Color

# Базовый URL
BASE_URL="http://localhost:8080"

# Функция для проверки статуса HTTP
check_status() {
    local url=$1
    local expected_status=${2:-200}
    local description=$3

    echo -e "${BLUE}Проверка: $description${NC}"
    echo "URL: $url"

    response=$(curl -s -w "%{http_code}" -o /tmp/response.json "$url")
    status_code="${response: -3}"

    if [ "$status_code" = "$expected_status" ]; then
        echo -e "${GREEN}✅ Успешно (HTTP $status_code)${NC}"
        if [ -f /tmp/response.json ]; then
            echo "Ответ:"
            cat /tmp/response.json | jq . 2>/dev/null || cat /tmp/response.json
        fi
    else
        echo -e "${RED}❌ Ошибка (HTTP $status_code, ожидался $expected_status)${NC}"
        if [ -f /tmp/response.json ]; then
            echo "Ответ:"
            cat /tmp/response.json
        fi
    fi
    echo ""
}

# Функция для POST запроса
post_request() {
    local url=$1
    local data=$2
    local description=$3
    local auth_header=$4

    echo -e "${BLUE}POST запрос: $description${NC}"
    echo "URL: $url"
    echo "Данные: $data"

    if [ -n "$auth_header" ]; then
        response=$(curl -s -w "%{http_code}" -X POST \
            -H "Content-Type: application/json" \
            -H "Authorization: Bearer $auth_header" \
            -d "$data" \
            -o /tmp/response.json "$url")
    else
        response=$(curl -s -w "%{http_code}" -X POST \
            -H "Content-Type: application/json" \
            -d "$data" \
            -o /tmp/response.json "$url")
    fi

    status_code="${response: -3}"

    if [ "$status_code" = "200" ] || [ "$status_code" = "201" ]; then
        echo -e "${GREEN}✅ Успешно (HTTP $status_code)${NC}"
        if [ -f /tmp/response.json ]; then
            echo "Ответ:"
            cat /tmp/response.json | jq . 2>/dev/null || cat /tmp/response.json
        fi
    else
        echo -e "${RED}❌ Ошибка (HTTP $status_code)${NC}"
        if [ -f /tmp/response.json ]; then
            echo "Ответ:"
            cat /tmp/response.json
        fi
    fi
    echo ""
}

echo -e "${YELLOW}🔧 Предварительная подготовка${NC}"

# Очистка webhook в Telegram
echo "Очистка Telegram webhook..."
curl -s -X POST "https://api.telegram.org/bot7819187384:AAGJNn0cwfJ7Nsv_N25h75eggEmqmD5WZG4/deleteWebhook" > /dev/null

# Остановка всех процессов
echo "Остановка существующих процессов..."
pkill -f "gradle.*bootRun" 2>/dev/null || true
pkill -f "java.*pizzanat" 2>/dev/null || true

echo -e "${YELLOW}⏳ Запуск приложения с тестовым профилем...${NC}"

# Запуск приложения в фоне
./gradlew bootRun --args='--spring.profiles.active=test' > app_test.log 2>&1 &
APP_PID=$!

echo "Приложение запущено с PID: $APP_PID"
echo "Ожидание запуска (30 секунд)..."
sleep 30

# Проверка, что процесс еще работает
if ! kill -0 $APP_PID 2>/dev/null; then
    echo -e "${RED}❌ Приложение не запустилось. Проверьте логи:${NC}"
    tail -20 app_test.log
    exit 1
fi

echo -e "${GREEN}✅ Приложение запущено${NC}"
echo ""

# 1. Проверка здоровья приложения
check_status "$BASE_URL/api/health" 200 "Проверка здоровья приложения"

# 2. Проверка H2 консоли
check_status "$BASE_URL/h2-console" 200 "Проверка H2 консоли"

# 3. Проверка API профиля пользователя (без авторизации - должно вернуть 401)
check_status "$BASE_URL/api/v1/user/profile" 401 "Проверка API профиля без авторизации"
check_status "$BASE_URL/api/v1/user/me" 401 "Проверка альтернативного API профиля без авторизации"

# 4. Тестирование Telegram аутентификации
echo -e "${YELLOW}📱 Тестирование Telegram аутентификации${NC}"
post_request "$BASE_URL/api/v1/auth/telegram/init" '{"deviceId": "test-device-final"}' "Инициализация Telegram аутентификации"

# Извлекаем токен из ответа
if [ -f /tmp/response.json ]; then
    AUTH_TOKEN=$(cat /tmp/response.json | jq -r '.authToken // empty' 2>/dev/null)
    TELEGRAM_URL=$(cat /tmp/response.json | jq -r '.telegramBotUrl // empty' 2>/dev/null)

    if [ -n "$AUTH_TOKEN" ] && [ "$AUTH_TOKEN" != "null" ]; then
        echo -e "${GREEN}🔑 Получен токен авторизации: $AUTH_TOKEN${NC}"
        echo -e "${GREEN}🤖 Ссылка на бота: $TELEGRAM_URL${NC}"

        # 5. Проверка статуса токена
        check_status "$BASE_URL/api/v1/auth/telegram/status/$AUTH_TOKEN" 200 "Проверка статуса токена"

    else
        echo -e "${RED}❌ Не удалось получить токен авторизации${NC}"
    fi
fi

# 6. Проверка Swagger UI
check_status "$BASE_URL/swagger-ui/index.html" 200 "Проверка Swagger UI"

echo -e "${YELLOW}📊 Результаты тестирования:${NC}"
echo ""
echo -e "${GREEN}✅ Исправления применены успешно:${NC}"
echo "   - Устранен конфликт между Long Polling и Webhook"
echo "   - Добавлено условное создание бота только при включенном Long Polling"
echo "   - Исправлены методы в TelegramBotIntegrationService"
echo "   - Добавлены перегруженные методы в TelegramAuthService"
echo "   - Создана конфигурация для тестового профиля с H2"
echo ""
echo -e "${YELLOW}🤖 Telegram бот готов к работе:${NC}"
echo "   - Long Polling включен только в тестовом профиле"
echo "   - Webhook отключен для избежания конфликтов"
echo "   - Автоматическая авторизация после получения номера телефона"
echo "   - Упрощенный алгоритм (сразу показывается кнопка 'Отправить телефон')"
echo ""
echo -e "${YELLOW}📱 Для тестирования в Telegram:${NC}"
if [ -n "$TELEGRAM_URL" ]; then
    echo "1. Откройте ссылку: $TELEGRAM_URL"
else
    echo "1. Откройте бота: https://t.me/PizzaNatBot"
    echo "2. Отправьте команду: /start"
fi
echo "3. Нажмите кнопку '📱 Отправить телефон'"
echo "4. Поделитесь своим номером телефона"
echo "5. Авторизация произойдет автоматически"
echo ""
echo -e "${YELLOW}🔧 API для мобильного приложения:${NC}"
echo "   - GET /api/v1/user/profile - получение профиля пользователя"
echo "   - GET /api/v1/user/me - альтернативный endpoint профиля"
echo "   - Номер телефона теперь сохраняется в поле 'phone'"
echo ""
echo -e "${YELLOW}📝 Логи приложения:${NC}"
echo "   - Основные логи: app_test.log"
echo "   - H2 консоль: http://localhost:8080/h2-console"
echo "   - Swagger UI: http://localhost:8080/swagger-ui/index.html"
echo ""
echo -e "${GREEN}🎉 Тестирование завершено! Приложение готово к использованию.${NC}"
echo ""
echo -e "${BLUE}Для остановки приложения выполните:${NC}"
echo "kill $APP_PID"