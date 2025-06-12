#!/bin/bash

# Тестирование Long Polling Telegram бота PizzaNat
# Проверка обработки команд /start, /help, /menu

echo "🤖 Тестирование PizzaNat Telegram Long Polling Bot"
echo "================================================="

# Цвета для вывода
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
NC='\033[0m' # No Color

# Параметры
BASE_URL="http://localhost:8080"
BOT_TOKEN="7819187384:AAGJNn0cwfJ7Nsv_N25h75eggEmqmD5WZG4"

echo -e "${BLUE}1. Проверка здоровья приложения${NC}"
health_response=$(curl -s "${BASE_URL}/actuator/health")
if [[ $? -eq 0 && $health_response == *"UP"* ]]; then
    echo -e "   ${GREEN}✅ Приложение работает${NC}"
else
    echo -e "   ${RED}❌ Приложение не доступно${NC}"
    echo "   Ответ: $health_response"
    exit 1
fi

echo -e "${BLUE}2. Проверка логов запуска Long Polling бота${NC}"
echo "   Ищем в логах инициализацию бота..."

# Проверяем логи Docker контейнера
if command -v docker &> /dev/null; then
    echo "   Поиск в Docker логах..."
    docker logs pizzanat-app 2>&1 | grep -i "polling\|telegram.*bot\|telegrambot" | tail -10 | while read line; do
        if [[ $line == *"инициализирован"* ]] || [[ $line == *"initialized"* ]]; then
            echo -e "   ${GREEN}✅ $line${NC}"
        elif [[ $line == *"ошибка"* ]] || [[ $line == *"error"* ]]; then
            echo -e "   ${RED}❌ $line${NC}"
        else
            echo -e "   ${YELLOW}ℹ️  $line${NC}"
        fi
    done
else
    echo "   Docker не найден, пропускаем проверку логов"
fi

echo -e "${BLUE}3. Проверка конфигурации Telegram Auth${NC}"
auth_response=$(curl -s "${BASE_URL}/api/v1/auth/telegram/test" 2>/dev/null)
if [[ $? -eq 0 && $auth_response == *"OK"* ]]; then
    echo -e "   ${GREEN}✅ Telegram Auth конфигурация работает${NC}"
    echo "   $(echo $auth_response | jq -r '.service + " - " + .status' 2>/dev/null || echo "$auth_response")"
else
    echo -e "   ${YELLOW}⚠️  Telegram Auth тест недоступен или не настроен${NC}"
fi

echo -e "${BLUE}4. Проверка Telegram Bot API подключения${NC}"
bot_response=$(curl -s "https://api.telegram.org/bot${BOT_TOKEN}/getMe" 2>/dev/null)
if [[ $? -eq 0 && $bot_response == *"ok\":true"* ]]; then
    bot_username=$(echo $bot_response | jq -r '.result.username' 2>/dev/null || echo "unknown")
    echo -e "   ${GREEN}✅ Бот подключен: @${bot_username}${NC}"
else
    echo -e "   ${RED}❌ Ошибка подключения к Telegram Bot API${NC}"
    echo "   Проверьте токен бота"
fi

echo -e "${BLUE}5. Проверка активных обновлений бота${NC}"
updates_response=$(curl -s "https://api.telegram.org/bot${BOT_TOKEN}/getUpdates" 2>/dev/null)
if [[ $? -eq 0 && $updates_response == *"ok\":true"* ]]; then
    updates_count=$(echo $updates_response | jq -r '.result | length' 2>/dev/null || echo "0")
    echo -e "   ${GREEN}✅ Получены обновления: ${updates_count}${NC}"
    
    if [[ $updates_count -gt 0 ]]; then
        echo "   Последние обновления:"
        echo $updates_response | jq -r '.result[] | "   • Update ID: \(.update_id), От: \(.message.from.first_name // "Неизвестно"), Текст: \(.message.text // "контакт/медиа")"' 2>/dev/null | tail -3
    fi
else
    echo -e "   ${RED}❌ Ошибка получения обновлений${NC}"
fi

echo ""
echo -e "${YELLOW}📋 Инструкции для ручного тестирования:${NC}"
echo ""
echo "1. Найдите бота @PizzaNatBot в Telegram"
echo "2. Отправьте команду /start"
echo "3. Проверьте ответ бота и кнопку 'Отправить телефон'"
echo "4. Протестируйте команды /help и /menu"
echo ""
echo "Ожидаемый результат:"
echo "• Бот отвечает на команды"
echo "• Показывает кнопку для отправки телефона"
echo "• Обрабатывает контактные данные"
echo ""

echo -e "${BLUE}6. Проверка переменных окружения${NC}"
if command -v docker &> /dev/null; then
    echo "   Проверка TELEGRAM_LONGPOLLING_ENABLED:"
    longpolling_enabled=$(docker exec pizzanat-app env | grep TELEGRAM_LONGPOLLING_ENABLED 2>/dev/null)
    if [[ -n "$longpolling_enabled" ]]; then
        echo -e "   ${GREEN}✅ $longpolling_enabled${NC}"
    else
        echo -e "   ${RED}❌ TELEGRAM_LONGPOLLING_ENABLED не установлена${NC}"
    fi
    
    echo "   Проверка TELEGRAM_AUTH_BOT_TOKEN:"
    bot_token_env=$(docker exec pizzanat-app env | grep TELEGRAM_AUTH_BOT_TOKEN 2>/dev/null | sed 's/TELEGRAM_AUTH_BOT_TOKEN=\(.*\)/TELEGRAM_AUTH_BOT_TOKEN=***HIDDEN***/')
    if [[ -n "$bot_token_env" ]]; then
        echo -e "   ${GREEN}✅ $bot_token_env${NC}"
    else
        echo -e "   ${RED}❌ TELEGRAM_AUTH_BOT_TOKEN не установлена${NC}"
    fi
else
    echo "   Docker не найден, пропускаем проверку переменных"
fi

echo ""
echo -e "${GREEN}🎉 Тестирование завершено!${NC}"
echo ""
echo "Если бот не отвечает на команды:"
echo "1. Перезапустите приложение: docker compose restart"
echo "2. Проверьте логи: docker logs pizzanat-app -f"
echo "3. Убедитесь что TELEGRAM_LONGPOLLING_ENABLED=true" 