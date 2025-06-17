#!/bin/bash

# Цвета для вывода
RED='\033[0;31m'
GREEN='\033[0;32m'
BLUE='\033[0;34m'
YELLOW='\033[1;33m'
NC='\033[0m' # No Color

# Заголовок
echo -e "${BLUE}========================================${NC}"
echo -e "${BLUE}  Тестирование Long Polling авторизации${NC}"
echo -e "${BLUE}    через @PizzaNatBot${NC}"
echo -e "${BLUE}========================================${NC}"
echo

BASE_URL="http://localhost:8080"

# Функция для создания токена авторизации
create_auth_token() {
    echo -e "${BLUE}1. Создание токена авторизации...${NC}"

    RESPONSE=$(curl -s -X POST \
        "$BASE_URL/api/v1/auth/telegram/init" \
        -H "Content-Type: application/json" \
        -d '{"deviceId": "test_device_longpolling"}')

    echo "Ответ сервера: $RESPONSE"

    # Извлекаем токен из ответа
    AUTH_TOKEN=$(echo "$RESPONSE" | grep -o '"authToken":"[^"]*"' | cut -d'"' -f4)
    TELEGRAM_URL=$(echo "$RESPONSE" | grep -o '"telegramUrl":"[^"]*"' | cut -d'"' -f4)

    if [ -z "$AUTH_TOKEN" ]; then
        echo -e "${RED}❌ Не удалось создать токен авторизации${NC}"
        return 1
    fi

    echo -e "${GREEN}✅ Токен создан: $AUTH_TOKEN${NC}"
    echo -e "${YELLOW}🔗 Telegram URL: $TELEGRAM_URL${NC}"
    echo

    return 0
}

# Функция для проверки статуса токена
check_token_status() {
    local token=$1
    local expected_status=$2

    echo -e "${BLUE}2. Проверка статуса токена (ожидается: $expected_status)...${NC}"

    RESPONSE=$(curl -s "$BASE_URL/api/v1/auth/telegram/status/$token")
    echo "Ответ сервера: $RESPONSE"

    STATUS=$(echo "$RESPONSE" | grep -o '"status":"[^"]*"' | cut -d'"' -f4)

    if [ "$STATUS" = "$expected_status" ]; then
        echo -e "${GREEN}✅ Статус корректный: $STATUS${NC}"
    else
        echo -e "${RED}❌ Неожиданный статус: $STATUS (ожидался: $expected_status)${NC}"
    fi
    echo
}

# Функция для подтверждения авторизации (эмуляция Long Polling)
confirm_auth_longpolling() {
    local token=$1

    echo -e "${BLUE}3. Эмуляция подтверждения через Long Polling...${NC}"
    echo -e "${YELLOW}📱 ИНСТРУКЦИЯ: Теперь в боте @PizzaNatBot:${NC}"
    echo -e "${YELLOW}   1. Нажмите на ссылку: https://t.me/PizzaNatBot?start=$token${NC}"
    echo -e "${YELLOW}   2. Отправьте свой контакт кнопкой '📱 Отправить телефон'${NC}"
    echo -e "${YELLOW}   3. Нажмите '✅ Подтвердить вход'${NC}"
    echo

    echo -e "${BLUE}⏰ Ожидание подтверждения (60 секунд)...${NC}"

    # Ждем подтверждения в течение 60 секунд
    for i in {1..12}; do
        sleep 5
        RESPONSE=$(curl -s "$BASE_URL/api/v1/auth/telegram/status/$token")
        STATUS=$(echo "$RESPONSE" | grep -o '"status":"[^"]*"' | cut -d'"' -f4)

        if [ "$STATUS" = "CONFIRMED" ]; then
            echo -e "${GREEN}✅ Авторизация подтверждена через Long Polling!${NC}"
            echo "Финальный ответ: $RESPONSE"

            # Извлекаем JWT токен
            JWT_TOKEN=$(echo "$RESPONSE" | grep -o '"token":"[^"]*"' | cut -d'"' -f4)
            if [ -n "$JWT_TOKEN" ]; then
                echo -e "${GREEN}🔑 JWT токен получен: ${JWT_TOKEN:0:20}...${NC}"
            fi
            return 0
        elif [ "$STATUS" = "PENDING" ]; then
            echo -e "${YELLOW}⏳ Статус: $STATUS (попытка $i/12)${NC}"
        else
            echo -e "${RED}❌ Неожиданный статус: $STATUS${NC}"
            return 1
        fi
    done

    echo -e "${RED}❌ Таймаут ожидания подтверждения${NC}"
    return 1
}

# Функция для проверки логов
check_logs() {
    echo -e "${BLUE}4. Проверка логов приложения...${NC}"

    echo "Логи Long Polling бота:"
    docker logs pizzanat-app --tail=20 | grep -i "pizzanat.*bot\|longpolling\|auth.*confirm" || echo "Логи не найдены"
    echo
}

# Главная функция
main() {
    echo -e "${YELLOW}🧪 Тестирование Long Polling авторизации через @PizzaNatBot${NC}"
    echo -e "${YELLOW}Убедитесь, что:${NC}"
    echo -e "${YELLOW}  - Приложение запущено (docker-compose up -d)${NC}"
    echo -e "${YELLOW}  - TELEGRAM_BOT_ENABLED=true${NC}"
    echo -e "${YELLOW}  - TELEGRAM_LONGPOLLING_ENABLED=true${NC}"
    echo -e "${YELLOW}  - TELEGRAM_AUTH_ENABLED=false (Webhook отключен)${NC}"
    echo

    # Создаем токен авторизации
    if ! create_auth_token; then
        echo -e "${RED}💥 Тест провален на создании токена${NC}"
        exit 1
    fi

    # Проверяем начальный статус
    check_token_status "$AUTH_TOKEN" "PENDING"

    # Подтверждаем авторизацию через Long Polling
    if confirm_auth_longpolling "$AUTH_TOKEN"; then
        echo -e "${GREEN}🎉 ТЕСТ ПРОЙДЕН: Long Polling авторизация работает!${NC}"

        # Финальная проверка статуса
        check_token_status "$AUTH_TOKEN" "CONFIRMED"

        # Проверяем логи
        check_logs

        echo -e "${GREEN}✅ Все проверки завершены успешно${NC}"
        exit 0
    else
        echo -e "${RED}💥 ТЕСТ ПРОВАЛЕН: Проблема с подтверждением авторизации${NC}"

        # Проверяем логи для диагностики
        check_logs
        exit 1
    fi
}

# Запуск основной функции
main "$@"