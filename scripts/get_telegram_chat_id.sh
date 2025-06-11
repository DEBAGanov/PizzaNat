#!/bin/bash

# PizzaNat - Получение Telegram Chat ID
# Автор: Backend Team
# Дата: 2025-06-01

echo "🔍 PizzaNat - Получение Telegram Chat ID"
echo "========================================"

TELEGRAM_BOT_TOKEN=7819187384:AAGJNn0cwfJ7Nsv_N25h75eggEmqmD5WZG4
# Цвета для вывода
GREEN='\033[0;32m'
RED='\033[0;31m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
NC='\033[0m' # No Color

# Проверка токена бота
if [ -z "$TELEGRAM_BOT_TOKEN" ]; then
    echo -e "${RED}❌ TELEGRAM_BOT_TOKEN не установлен${NC}"
    echo "Установите: export TELEGRAM_BOT_TOKEN=ваш_токен_бота"
    exit 1
fi

echo -e "${GREEN}✅ Токен бота установлен${NC}"
echo ""

# Получение информации о боте
echo -e "${YELLOW}🤖 Информация о боте:${NC}"
BOT_INFO=$(curl -s "https://api.telegram.org/bot$TELEGRAM_BOT_TOKEN/getMe")
BOT_OK=$(echo "$BOT_INFO" | grep -o '"ok":true')

if [ -n "$BOT_OK" ]; then
    BOT_USERNAME=$(echo "$BOT_INFO" | grep -o '"username":"[^"]*' | cut -d'"' -f4)
    BOT_FIRST_NAME=$(echo "$BOT_INFO" | grep -o '"first_name":"[^"]*' | cut -d'"' -f4)
    echo -e "${GREEN}✅ Имя: ${BOT_FIRST_NAME}${NC}"
    echo -e "${GREEN}✅ Username: @${BOT_USERNAME}${NC}"
else
    echo -e "${RED}❌ Ошибка получения информации о боте${NC}"
    echo "Проверьте токен: $BOT_INFO"
    exit 1
fi

echo ""
echo -e "${BLUE}📨 Получение обновлений (последние сообщения):${NC}"
echo "============================================="

# Получение последних обновлений
UPDATES=$(curl -s "https://api.telegram.org/bot$TELEGRAM_BOT_TOKEN/getUpdates?limit=10&offset=-10")
UPDATES_OK=$(echo "$UPDATES" | grep -o '"ok":true')

if [ -n "$UPDATES_OK" ]; then
    # Проверяем есть ли сообщения
    MESSAGE_COUNT=$(echo "$UPDATES" | grep -o '"message":' | wc -l)

    if [ "$MESSAGE_COUNT" -gt 0 ]; then
        echo -e "${GREEN}✅ Найдены сообщения:${NC}"
        echo ""

        # Извлекаем chat ID и информацию о чатах
        echo "$UPDATES" | jq -r '.result[] | select(.message) | {
            chat_id: .message.chat.id,
            chat_type: .message.chat.type,
            chat_title: (.message.chat.title // .message.chat.first_name // "Личный чат"),
            date: (.message.date | todate),
            text: (.message.text // "без текста")
        } | "Chat ID: \(.chat_id)\nТип: \(.chat_type)\nНазвание: \(.chat_title)\nДата: \(.date)\nТекст: \(.text)\n---"' 2>/dev/null || {
            # Fallback если jq не установлен
            echo -e "${YELLOW}⚠️ jq не установлен, используем простое извлечение${NC}"
            echo ""

            # Простое извлечение chat_id
            CHAT_IDS=$(echo "$UPDATES" | grep -o '"chat":{"id":[^,]*' | grep -o '[0-9-]\+' | sort -u)

            if [ -n "$CHAT_IDS" ]; then
                echo -e "${GREEN}📋 Найденные Chat ID:${NC}"
                echo "$CHAT_IDS" | while read chat_id; do
                    if [[ $chat_id == -* ]]; then
                        echo -e "${BLUE}  $chat_id${NC} (группа/канал)"
                    else
                        echo -e "${BLUE}  $chat_id${NC} (личный чат)"
                    fi
                done
            fi
        }

        echo ""
        echo -e "${YELLOW}💡 Инструкции:${NC}"
        echo "1. Выберите нужный Chat ID из списка выше"
        echo "2. Для групп используйте отрицательные ID (начинающиеся с -)"
        echo "3. Для личных чатов используйте положительные ID"
        echo "4. Установите переменную: export TELEGRAM_CHAT_ID=выбранный_id"

    else
        echo -e "${YELLOW}⚠️ Сообщения не найдены${NC}"
        echo ""
        echo -e "${BLUE}📝 Чтобы получить Chat ID:${NC}"
        echo "1. Отправьте сообщение боту @${BOT_USERNAME} в личке"
        echo "2. ИЛИ добавьте бота в группу и отправьте сообщение"
        echo "3. Затем повторно запустите этот скрипт"
        echo ""
        echo -e "${YELLOW}🔄 Альтернативный способ:${NC}"
        echo "1. Добавьте бота @userinfobot в ваш чат"
        echo "2. Отправьте команду /start"
        echo "3. Скопируйте Chat ID из ответа"
    fi
else
    echo -e "${RED}❌ Ошибка получения обновлений${NC}"
    echo "Ответ: $UPDATES"
fi

echo ""
echo -e "${GREEN}✅ Скрипт завершен${NC}"