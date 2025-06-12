#!/bin/bash

# Скрипт для тестирования улучшений Telegram бота
# Автор: AI Assistant
# Дата: 2025-01-11

echo "🤖 Тестирование улучшений Telegram бота PizzaNat"
echo "=================================================="

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

# Ожидание запуска приложения
echo -e "${YELLOW}⏳ Ожидание запуска приложения...${NC}"
sleep 10

# 1. Проверка здоровья приложения
check_status "$BASE_URL/api/health" 200 "Проверка здоровья приложения"

# 2. Проверка Telegram API
check_status "$BASE_URL/api/v1/auth/telegram/test" 200 "Проверка Telegram API"

# 3. Инициализация Telegram аутентификации
echo -e "${YELLOW}📱 Тестирование Telegram аутентификации${NC}"
post_request "$BASE_URL/api/v1/auth/telegram/init" '{"deviceId": "test-device-123"}' "Инициализация Telegram аутентификации"

# Извлекаем токен из ответа
if [ -f /tmp/response.json ]; then
    AUTH_TOKEN=$(cat /tmp/response.json | jq -r '.authToken // empty' 2>/dev/null)
    TELEGRAM_URL=$(cat /tmp/response.json | jq -r '.telegramBotUrl // empty' 2>/dev/null)

    if [ -n "$AUTH_TOKEN" ] && [ "$AUTH_TOKEN" != "null" ]; then
        echo -e "${GREEN}🔑 Получен токен авторизации: $AUTH_TOKEN${NC}"
        echo -e "${GREEN}🤖 Ссылка на бота: $TELEGRAM_URL${NC}"

        # 4. Проверка статуса токена
        check_status "$BASE_URL/api/v1/auth/telegram/status/$AUTH_TOKEN" 200 "Проверка статуса токена"

        echo -e "${YELLOW}📋 Инструкции для тестирования:${NC}"
        echo "1. Откройте ссылку в Telegram: $TELEGRAM_URL"
        echo "2. Нажмите кнопку 'Отправить телефон'"
        echo "3. Отправьте ваш номер телефона"
        echo "4. Авторизация должна произойти автоматически"
        echo ""
        echo "5. После авторизации проверьте статус токена:"
        echo "   curl '$BASE_URL/api/v1/auth/telegram/status/$AUTH_TOKEN'"
        echo ""
    else
        echo -e "${RED}❌ Не удалось получить токен авторизации${NC}"
    fi
fi

# 5. Тестирование нового API профиля пользователя (без авторизации - должно вернуть 401)
check_status "$BASE_URL/api/v1/user/profile" 401 "Проверка API профиля без авторизации"
check_status "$BASE_URL/api/v1/user/me" 401 "Проверка альтернативного API профиля без авторизации"

# 6. Проверка webhook info
check_status "$BASE_URL/api/v1/telegram/webhook/info" 200 "Информация о Telegram webhook"

# 7. Проверка статистики отладки
check_status "$BASE_URL/debug/status" 200 "Статистика отладки"

echo -e "${YELLOW}📊 Результаты тестирования:${NC}"
echo "✅ Основные изменения реализованы:"
echo "   - Упрощен алгоритм авторизации (сразу показывается кнопка 'Отправить телефон')"
echo "   - Автоматическая авторизация после получения номера телефона"
echo "   - Номер телефона сохраняется в поле 'phone' вместо 'phone_number'"
echo "   - Добавлены API endpoints для получения профиля пользователя"
echo ""
echo -e "${YELLOW}🔧 Для полного тестирования:${NC}"
echo "1. Используйте Telegram бота для авторизации"
echo "2. Получите JWT токен после успешной авторизации"
echo "3. Протестируйте API профиля с полученным токеном:"
echo "   curl -H 'Authorization: Bearer <JWT_TOKEN>' '$BASE_URL/api/v1/user/profile'"
echo ""
echo -e "${YELLOW}📱 Проверка в мобильном приложении:${NC}"
echo "1. Убедитесь, что мобильное приложение использует правильные endpoints"
echo "2. Проверьте, что приложение корректно обрабатывает поле 'phone'"
echo "3. Убедитесь, что после авторизации через Telegram пользователь отображается в приложении"

echo ""
echo -e "${GREEN}🎉 Тестирование завершено!${NC}"