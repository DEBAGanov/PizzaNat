#!/bin/bash

echo "🔍 Тестирование Telegram авторизации через Long Polling"
echo "======================================================"

BASE_URL="https://debaganov-pizzanat-d8fb.twc1.net"

# Цвета для вывода
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
NC='\033[0m' # No Color

print_status() {
    if [ $1 -eq 0 ]; then
        echo -e "${GREEN}✅ $2${NC}"
    else
        echo -e "${RED}❌ $2${NC}"
    fi
}

print_info() {
    echo -e "${BLUE}ℹ️  $1${NC}"
}

print_warning() {
    echo -e "${YELLOW}⚠️  $1${NC}"
}

# Шаг 1: Инициализация авторизации
print_info "Шаг 1: Инициализация Telegram авторизации..."
INIT_RESPONSE=$(curl -s -X POST "$BASE_URL/api/v1/auth/telegram/init" \
  -H "Content-Type: application/json" \
  -d '{
    "deviceId": "test_device_longpolling_001"
  }')

echo "📤 Запрос инициализации:"
echo '{
  "deviceId": "test_device_longpolling_001"
}'

echo ""
echo "📥 Ответ инициализации:"
echo "$INIT_RESPONSE" | jq

# Проверяем успешность инициализации
SUCCESS=$(echo "$INIT_RESPONSE" | jq -r '.success // false')
if [ "$SUCCESS" = "true" ]; then
    print_status 0 "Инициализация успешна"

    # Извлекаем токен и URL
    AUTH_TOKEN=$(echo "$INIT_RESPONSE" | jq -r '.authToken')
    BOT_URL=$(echo "$INIT_RESPONSE" | jq -r '.telegramBotUrl')
    EXPIRES_AT=$(echo "$INIT_RESPONSE" | jq -r '.expiresAt')

    print_info "Токен: $AUTH_TOKEN"
    print_info "URL бота: $BOT_URL"
    print_info "Истекает: $EXPIRES_AT"

else
    print_status 1 "Ошибка инициализации"
    MESSAGE=$(echo "$INIT_RESPONSE" | jq -r '.message // "Неизвестная ошибка"')
    print_warning "Сообщение: $MESSAGE"
    exit 1
fi

echo ""
echo "============================================================"

# Шаг 2: Проверка начального статуса
print_info "Шаг 2: Проверка начального статуса токена..."
STATUS_RESPONSE=$(curl -s -X GET "$BASE_URL/api/v1/auth/telegram/status/$AUTH_TOKEN")

echo "📥 Ответ статуса:"
echo "$STATUS_RESPONSE" | jq

STATUS=$(echo "$STATUS_RESPONSE" | jq -r '.status // "UNKNOWN"')
if [ "$STATUS" = "PENDING" ]; then
    print_status 0 "Статус корректный: PENDING"
else
    print_status 1 "Неожиданный статус: $STATUS"
fi

echo ""
echo "============================================================"

# Шаг 3: Инструкции для пользователя
print_info "Шаг 3: Инструкции для ручного тестирования"
echo ""
print_warning "ВАЖНО: Теперь нужно вручную протестировать в Telegram:"
echo ""
echo "1️⃣ Перейдите по ссылке: $BOT_URL"
echo "2️⃣ Нажмите 'START' или отправьте команду боту"
echo "3️⃣ Нажмите кнопку '✅ Подтвердить вход' в боте"
echo "4️⃣ Вернитесь сюда и нажмите Enter для проверки статуса"
echo ""
print_info "Ожидание подтверждения в Telegram боте..."
read -p "Нажмите Enter после подтверждения в боте..."

echo ""
echo "============================================================"

# Шаг 4: Проверка финального статуса
print_info "Шаг 4: Проверка финального статуса после подтверждения..."
FINAL_STATUS_RESPONSE=$(curl -s -X GET "$BASE_URL/api/v1/auth/telegram/status/$AUTH_TOKEN")

echo "📥 Финальный ответ статуса:"
echo "$FINAL_STATUS_RESPONSE" | jq

FINAL_STATUS=$(echo "$FINAL_STATUS_RESPONSE" | jq -r '.status // "UNKNOWN"')
FINAL_SUCCESS=$(echo "$FINAL_STATUS_RESPONSE" | jq -r '.success // false')

if [ "$FINAL_SUCCESS" = "true" ] && [ "$FINAL_STATUS" = "CONFIRMED" ]; then
    print_status 0 "Авторизация подтверждена!"

    # Проверяем наличие JWT токена
    JWT_TOKEN=$(echo "$FINAL_STATUS_RESPONSE" | jq -r '.authData.token // null')
    if [ "$JWT_TOKEN" != "null" ] && [ "$JWT_TOKEN" != "" ]; then
        print_status 0 "JWT токен получен"
        print_info "JWT: ${JWT_TOKEN:0:50}..."

        # Проверяем данные пользователя
        USER_DATA=$(echo "$FINAL_STATUS_RESPONSE" | jq -r '.authData')
        echo ""
        print_info "Данные пользователя:"
        echo "$USER_DATA" | jq

    else
        print_status 1 "JWT токен отсутствует"
    fi

elif [ "$FINAL_STATUS" = "PENDING" ]; then
    print_status 1 "Авторизация все еще ожидает подтверждения"
    print_warning "Возможно, вы не подтвердили авторизацию в боте"

elif [ "$FINAL_STATUS" = "EXPIRED" ]; then
    print_status 1 "Токен истек"

else
    print_status 1 "Неожиданный статус: $FINAL_STATUS"
fi

echo ""
echo "============================================================"

# Шаг 5: Проверка health check
print_info "Шаг 5: Проверка health check Telegram Auth..."
HEALTH_RESPONSE=$(curl -s -X GET "$BASE_URL/api/v1/auth/telegram/test")

echo "📥 Health check ответ:"
echo "$HEALTH_RESPONSE" | jq

HEALTH_STATUS=$(echo "$HEALTH_RESPONSE" | jq -r '.status // "UNKNOWN"')
if [ "$HEALTH_STATUS" = "OK" ]; then
    print_status 0 "Health check прошел успешно"
else
    print_status 1 "Health check failed: $HEALTH_STATUS"
fi

echo ""
echo "============================================================"
echo ""
print_info "🎯 РЕЗЮМЕ ТЕСТИРОВАНИЯ:"
echo ""
echo "✅ Архитектура: Long Polling (без webhook)"
echo "✅ Инициализация: $( [ "$SUCCESS" = "true" ] && echo "OK" || echo "FAILED" )"
echo "✅ Начальный статус: $( [ "$STATUS" = "PENDING" ] && echo "OK" || echo "FAILED" )"
echo "✅ Финальный статус: $( [ "$FINAL_STATUS" = "CONFIRMED" ] && echo "OK" || echo "FAILED" )"
echo "✅ JWT токен: $( [ "$JWT_TOKEN" != "null" ] && [ "$JWT_TOKEN" != "" ] && echo "OK" || echo "FAILED" )"
echo "✅ Health check: $( [ "$HEALTH_STATUS" = "OK" ] && echo "OK" || echo "FAILED" )"
echo ""

if [ "$FINAL_SUCCESS" = "true" ] && [ "$FINAL_STATUS" = "CONFIRMED" ] && [ "$JWT_TOKEN" != "null" ]; then
    print_status 0 "🎉 ПОЛНЫЙ УСПЕХ! Авторизация через Long Polling работает!"
else
    print_status 1 "❌ Тестирование не прошло полностью"
fi

echo ""
print_info "Для повторного тестирования запустите скрипт заново"