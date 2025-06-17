#!/bin/bash

# Цвета для вывода
RED='\033[0;31m'
GREEN='\033[0;32m'
BLUE='\033[0;34m'
YELLOW='\033[1;33m'
NC='\033[0m' # No Color

# Заголовок
echo -e "${BLUE}========================================${NC}"
echo -e "${BLUE}  Тестирование Telegram ботов PizzaNat${NC}"
echo -e "${BLUE}========================================${NC}"
echo
echo -e "${YELLOW}Проверяем работу обоих ботов:${NC}"
echo -e "${YELLOW}  🤖 @PizzaNatBot - пользовательский бот${NC}"
echo -e "${YELLOW}  👨‍💼 @PizzaNatOrders_bot - админский бот${NC}"
echo

# Функция проверки переменных окружения
check_env_vars() {
    echo -e "${BLUE}1. Проверка переменных окружения...${NC}"

    # Проверка пользовательского бота
    echo -e "${YELLOW}   Пользовательский бот @PizzaNatBot:${NC}"

    telegram_enabled=$(docker exec pizzanat-app env | grep TELEGRAM_ENABLED 2>/dev/null || echo "не найдена")
    telegram_bot_enabled=$(docker exec pizzanat-app env | grep TELEGRAM_BOT_ENABLED 2>/dev/null || echo "не найдена")
    telegram_longpolling=$(docker exec pizzanat-app env | grep TELEGRAM_LONGPOLLING_ENABLED 2>/dev/null || echo "не найдена")
    telegram_auth_enabled=$(docker exec pizzanat-app env | grep TELEGRAM_AUTH_ENABLED 2>/dev/null || echo "не найдена")

    echo "     TELEGRAM_ENABLED: $telegram_enabled"
    echo "     TELEGRAM_BOT_ENABLED: $telegram_bot_enabled"
    echo "     TELEGRAM_LONGPOLLING_ENABLED: $telegram_longpolling"
    echo "     TELEGRAM_AUTH_ENABLED: $telegram_auth_enabled"

    # Проверка админского бота
    echo -e "${YELLOW}   Админский бот @PizzaNatOrders_bot:${NC}"

    admin_bot_enabled=$(docker exec pizzanat-app env | grep TELEGRAM_ADMIN_BOT_ENABLED 2>/dev/null || echo "не найдена")
    admin_bot_token=$(docker exec pizzanat-app env | grep TELEGRAM_ADMIN_BOT_TOKEN 2>/dev/null || echo "не найдена")

    echo "     TELEGRAM_ADMIN_BOT_ENABLED: $admin_bot_enabled"
    echo "     TELEGRAM_ADMIN_BOT_TOKEN: ${admin_bot_token:0:50}..."

    echo
}

# Функция проверки логов инициализации
check_initialization_logs() {
    echo -e "${BLUE}2. Проверка логов инициализации ботов...${NC}"

    echo -e "${YELLOW}   Поиск логов пользовательского бота:${NC}"
    user_bot_logs=$(docker logs pizzanat-app 2>&1 | grep -i "PizzaNat Telegram Bot\|основной.*бот\|telegram.*bot.*enabled" | tail -5)
    if [ -n "$user_bot_logs" ]; then
        echo "$user_bot_logs" | while read line; do
            echo "     $line"
        done
    else
        echo -e "     ${RED}❌ Логи пользовательского бота не найдены${NC}"
    fi

    echo -e "${YELLOW}   Поиск логов админского бота:${NC}"
    admin_bot_logs=$(docker logs pizzanat-app 2>&1 | grep -i "админский.*бот\|PizzaNatAdminBot\|admin.*bot" | tail -5)
    if [ -n "$admin_bot_logs" ]; then
        echo "$admin_bot_logs" | while read line; do
            echo "     $line"
        done
    else
        echo -e "     ${RED}❌ Логи админского бота не найдены${NC}"
    fi

    echo
}

# Функция проверки TelegramUserNotificationService
check_notification_service() {
    echo -e "${BLUE}3. Проверка TelegramUserNotificationService...${NC}"

    echo -e "${YELLOW}   Поиск логов персональных уведомлений:${NC}"
    notification_logs=$(docker logs pizzanat-app 2>&1 | grep -i "TelegramUserNotificationService\|персональное уведомление" | tail -5)
    if [ -n "$notification_logs" ]; then
        echo "$notification_logs" | while read line; do
            echo "     $line"
        done
    else
        echo -e "     ${YELLOW}⚠️ Логи персональных уведомлений не найдены (возможно, заказы еще не создавались)${NC}"
    fi

    echo
}

# Функция проверки конфликтов
check_conflicts() {
    echo -e "${BLUE}4. Проверка конфликтов и ошибок...${NC}"

    echo -e "${YELLOW}   Поиск ошибок 409 (конфликт токенов):${NC}"
    conflict_errors=$(docker logs pizzanat-app 2>&1 | grep -i "409\|conflict\|terminated by other" | tail -3)
    if [ -n "$conflict_errors" ]; then
        echo -e "     ${RED}❌ Найдены конфликты:${NC}"
        echo "$conflict_errors" | while read line; do
            echo "       $line"
        done
    else
        echo -e "     ${GREEN}✅ Конфликтов токенов не найдено${NC}"
    fi

    echo -e "${YELLOW}   Поиск других ошибок Telegram:${NC}"
    telegram_errors=$(docker logs pizzanat-app 2>&1 | grep -i "telegram.*error\|telegram.*exception\|ошибка.*telegram" | tail -3)
    if [ -n "$telegram_errors" ]; then
        echo -e "     ${RED}❌ Найдены ошибки Telegram:${NC}"
        echo "$telegram_errors" | while read line; do
            echo "       $line"
        done
    else
        echo -e "     ${GREEN}✅ Критических ошибок Telegram не найдено${NC}"
    fi

    echo
}

# Функция проверки REST Template конфигурации
check_rest_templates() {
    echo -e "${BLUE}5. Проверка конфигурации RestTemplate...${NC}"

    echo -e "${YELLOW}   Поиск логов telegramAuthRestTemplate:${NC}"
    auth_template_logs=$(docker logs pizzanat-app 2>&1 | grep -i "telegramAuthRestTemplate\|telegram.*auth.*template" | tail -3)
    if [ -n "$auth_template_logs" ]; then
        echo "$auth_template_logs" | while read line; do
            echo "     $line"
        done
    else
        echo -e "     ${YELLOW}⚠️ Специфичные логи telegramAuthRestTemplate не найдены${NC}"
    fi

    echo
}

# Функция тестирования авторизации
test_telegram_auth() {
    echo -e "${BLUE}6. Тестирование Telegram авторизации...${NC}"

    echo -e "${YELLOW}   Инициализация авторизации:${NC}"
    auth_response=$(curl -s -X POST http://localhost:8080/api/v1/auth/telegram/init \
        -H "Content-Type: application/json" \
        -d '{}' | jq -r '.authToken // "ERROR"' 2>/dev/null)

    if [ "$auth_response" != "ERROR" ] && [ "$auth_response" != "null" ] && [ -n "$auth_response" ]; then
        echo -e "     ${GREEN}✅ Авторизация инициализирована, токен: ${auth_response:0:20}...${NC}"

        # Проверяем статус токена
        echo -e "${YELLOW}   Проверка статуса токена:${NC}"
        status_response=$(curl -s -X GET "http://localhost:8080/api/v1/auth/telegram/status?token=$auth_response")
        echo "     Ответ: $status_response"
    else
        echo -e "     ${RED}❌ Ошибка инициализации авторизации${NC}"
    fi

    echo
}

# Функция рекомендаций
show_recommendations() {
    echo -e "${BLUE}7. Рекомендации по настройке...${NC}"

    echo -e "${YELLOW}   Для пользовательского бота @PizzaNatBot:${NC}"
    echo "     - Убедитесь что TELEGRAM_BOT_ENABLED=true"
    echo "     - Убедитесь что TELEGRAM_LONGPOLLING_ENABLED=true"
    echo "     - Убедитесь что TELEGRAM_AUTH_ENABLED=true"
    echo "     - Проверьте токен: 7819187384:AAGJNn0cwfJ7Nsv_N25h75eggEmqmD5WZG4"

    echo -e "${YELLOW}   Для админского бота @PizzaNatOrders_bot:${NC}"
    echo "     - Убедитесь что TELEGRAM_ADMIN_BOT_ENABLED=true"
    echo "     - Проверьте токен: 8052456616:AAEoAzBfr3jRpylDmxR-azNBSqimthPCHeg"

    echo -e "${YELLOW}   Команды для диагностики:${NC}"
    echo "     docker logs pizzanat-app | grep -i telegram"
    echo "     docker logs pizzanat-app | grep TelegramUserNotificationService"
    echo "     docker logs pizzanat-app | grep AdminBotService"

    echo -e "${YELLOW}   Тестирование в Telegram:${NC}"
    echo "     - Отправьте /start в @PizzaNatBot"
    echo "     - Отправьте /start в @PizzaNatOrders_bot"
    echo "     - Создайте заказ и проверьте уведомления"

    echo
}

# Основная функция
main() {
    # Проверяем, запущено ли приложение
    if ! docker ps | grep -q pizzanat-app; then
        echo -e "${RED}❌ Контейнер pizzanat-app не запущен${NC}"
        echo "Запустите приложение командой: docker-compose up -d"
        exit 1
    fi

    check_env_vars
    check_initialization_logs
    check_notification_service
    check_conflicts
    check_rest_templates
    test_telegram_auth
    show_recommendations

    echo -e "${BLUE}========================================${NC}"
    echo -e "${GREEN}  Диагностика завершена!${NC}"
    echo -e "${BLUE}========================================${NC}"
    echo
    echo -e "${YELLOW}Следующие шаги:${NC}"
    echo "1. Проверьте настройки в docker-compose.yml"
    echo "2. Перезапустите приложение если изменили настройки"
    echo "3. Протестируйте ботов вручную в Telegram"
    echo "4. Создайте тестовый заказ для проверки уведомлений"
}

# Запуск
main "$@"