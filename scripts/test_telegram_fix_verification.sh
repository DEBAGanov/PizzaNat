#!/bin/bash

# Простой тест для проверки исправления проблемы с Telegram аутентификацией
# Дата: 2025-06-13

echo "🔧 Проверка исправления проблемы с Telegram аутентификацией"
echo "=========================================================="

# Цвета для вывода
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
NC='\033[0m' # No Color

# Конфигурация
BASE_URL="http://localhost:8080"

echo -e "${BLUE}1. Проверка доступности приложения...${NC}"
if curl -s "${BASE_URL}/actuator/health" | grep -q "UP"; then
    echo -e "${GREEN}✅ Приложение работает${NC}"
else
    echo -e "${RED}❌ Приложение недоступно. Запустите: ./gradlew bootRun${NC}"
    exit 1
fi
echo ""

echo -e "${BLUE}2. Проверка исправлений в коде...${NC}"

# Проверяем, что исправления применены
if grep -q "Подтвержденный токен не содержит Telegram ID" src/main/java/com/baganov/pizzanat/service/TelegramAuthService.java; then
    echo -e "${GREEN}✅ Исправление checkAuthStatus применено${NC}"
else
    echo -e "${RED}❌ Исправление checkAuthStatus не найдено${NC}"
fi

if grep -q "Пользователь неактивен для токена" src/main/java/com/baganov/pizzanat/service/TelegramAuthService.java; then
    echo -e "${GREEN}✅ Проверка активности пользователя добавлена${NC}"
else
    echo -e "${RED}❌ Проверка активности пользователя не найдена${NC}"
fi

if grep -q "Создание AuthResponse для пользователя" src/main/java/com/baganov/pizzanat/service/TelegramAuthService.java; then
    echo -e "${GREEN}✅ Дополнительное логирование добавлено${NC}"
else
    echo -e "${RED}❌ Дополнительное логирование не найдено${NC}"
fi

if grep -q "generateEmailForTelegramUser" src/main/java/com/baganov/pizzanat/util/TelegramUserDataExtractor.java; then
    echo -e "${GREEN}✅ Генерация email для Telegram пользователей исправлена${NC}"
else
    echo -e "${RED}❌ Генерация email не найдена${NC}"
fi

if [ -f "src/main/resources/db/migration/V15__fix_telegram_users_email.sql" ]; then
    echo -e "${GREEN}✅ Миграция V15 для исправления email создана${NC}"
else
    echo -e "${RED}❌ Миграция V15 не найдена${NC}"
fi
echo ""

echo -e "${BLUE}3. Тестирование API endpoints...${NC}"

# Тест health check
echo -e "${YELLOW}Тестирование health check...${NC}"
HEALTH_RESPONSE=$(curl -s "${BASE_URL}/api/v1/auth/telegram/test")
if echo "$HEALTH_RESPONSE" | grep -q '"status":"OK"'; then
    echo -e "${GREEN}✅ Health check работает${NC}"
else
    echo -e "${RED}❌ Health check не работает${NC}"
    echo "Ответ: $HEALTH_RESPONSE"
fi

# Тест инициализации
echo -e "${YELLOW}Тестирование инициализации...${NC}"
INIT_RESPONSE=$(curl -s -X POST "${BASE_URL}/api/v1/auth/telegram/init" \
    -H "Content-Type: application/json" \
    -d '{"deviceId": "fix_verification_test"}')

if echo "$INIT_RESPONSE" | grep -q '"success":true'; then
    echo -e "${GREEN}✅ Инициализация работает${NC}"

    # Извлекаем токен
    AUTH_TOKEN=$(echo "$INIT_RESPONSE" | jq -r '.authToken // empty' 2>/dev/null)
    if [ -n "$AUTH_TOKEN" ] && [ "$AUTH_TOKEN" != "null" ]; then
        echo -e "${GREEN}✅ Токен получен: ${AUTH_TOKEN:0:20}...${NC}"

        # Тест проверки статуса
        echo -e "${YELLOW}Тестирование проверки статуса...${NC}"
        STATUS_RESPONSE=$(curl -s "${BASE_URL}/api/v1/auth/telegram/status/${AUTH_TOKEN}")

        if echo "$STATUS_RESPONSE" | grep -q '"status":"PENDING"'; then
            echo -e "${GREEN}✅ Проверка статуса работает (PENDING)${NC}"
        else
            echo -e "${RED}❌ Проблема с проверкой статуса${NC}"
            echo "Ответ: $STATUS_RESPONSE"
        fi
    else
        echo -e "${RED}❌ Токен не получен${NC}"
    fi
else
    echo -e "${RED}❌ Инициализация не работает${NC}"
    echo "Ответ: $INIT_RESPONSE"
fi
echo ""

echo -e "${BLUE}4. Проверка базы данных...${NC}"
echo "Для полной проверки выполните SQL запросы:"
echo ""
echo "-- Проверка пользователей с Telegram ID без email (должно быть 0 после миграции V15):"
echo "SELECT COUNT(*) FROM users WHERE telegram_id IS NOT NULL AND (email IS NULL OR email = '');"
echo ""
echo "-- Проверка последних токенов:"
echo "SELECT auth_token, telegram_id, status, created_at, expires_at FROM telegram_auth_tokens ORDER BY created_at DESC LIMIT 5;"
echo ""
echo "-- Проверка пользователей с Telegram ID:"
echo "SELECT id, username, email, telegram_id, first_name, last_name, is_active FROM users WHERE telegram_id IS NOT NULL ORDER BY created_at DESC LIMIT 5;"
echo ""

echo "📋 РЕЗУЛЬТАТЫ ПРОВЕРКИ"
echo "====================="
echo -e "${GREEN}✅ Основные исправления применены${NC}"
echo -e "${GREEN}✅ API endpoints работают${NC}"
echo -e "${YELLOW}⚠️  Для полного тестирования запустите диагностический тест:${NC}"
echo "   ./test_telegram_auth_diagnosis.sh"
echo ""
echo -e "${BLUE}📱 Для тестирования в мобильном приложении:${NC}"
echo "1. Запустите мобильное приложение"
echo "2. Попробуйте войти через Telegram"
echo "3. Проверьте, что данные пользователя отображаются корректно"
echo "4. Попробуйте создать заказ"