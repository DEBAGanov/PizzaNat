#!/bin/bash

# Тестирование исправлений валидации телефона и сохранения в БД
# Дата создания: 27.01.2025

echo "🧪 Тестирование исправлений валидации телефона и сохранения в БД"
echo "==============================================================="

echo
echo "✅ ВЫПОЛНЕННЫЕ ИСПРАВЛЕНИЯ:"
echo "=========================="
echo "🔧 Backend (TelegramWebAppService.java):"
echo "  - Улучшена валидация номера телефона в formatPhoneNumber()"
echo "  - Добавлено подробное логирование процесса форматирования"
echo "  - Добавлен флаг isPhoneVerified при сохранении номера"
echo "  - Возврат null при некорректном формате для валидации"

echo
echo "🔧 Frontend (checkout-app.js):"
echo "  - Добавлен метод validatePhoneNumber() с проверкой российских номеров"
echo "  - Валидация номера от requestContact() перед сохранением"
echo "  - Валидация ручного ввода номера перед отправкой"
echo "  - Автоматический вызов performEnhancedAuth() для сохранения в БД"
echo "  - Форматирование номера в +7XXXXXXXXXX формат"

echo
echo "📱 ПОДДЕРЖИВАЕМЫЕ ФОРМАТЫ НОМЕРОВ:"
echo "================================="
echo "✅ +79161234567 (уже правильный)"
echo "✅ 79161234567 (добавляется +)"
echo "✅ 89161234567 (8 заменяется на +7)"
echo "✅ 9161234567 (добавляется +7)"
echo "❌ Другие форматы отклоняются с пояснением"

echo
echo "🔄 ПОТОК СОХРАНЕНИЯ НОМЕРА:"
echo "=========================="
echo "1. 📱 Пользователь предоставляет номер (requestContact или ручной ввод)"
echo "2. ✅ JavaScript валидация и форматирование"
echo "3. 🔐 Вызов performEnhancedAuth(formattedPhone)"
echo "4. 📡 POST /api/v1/telegram-webapp/enhanced-auth"
echo "5. 🔧 Backend валидация formatPhoneNumber()"
echo "6. 💾 Сохранение в таблице users (phone, isPhoneVerified=true, isTelegramVerified=true)"
echo "7. 📋 Создание записи в telegram_auth_tokens"

echo
echo "🧪 ТЕСТИРОВАНИЕ НОМЕРОВ:"
echo "========================"

# Функция тестирования форматирования номера (имитация JavaScript)
test_phone_format() {
    local input="$1"
    local expected="$2"
    
    # Убираем все кроме цифр и +
    cleaned=$(echo "$input" | sed 's/[^0-9+]//g')
    digits_only=$(echo "$cleaned" | sed 's/[^0-9]//g')
    
    local result=""
    
    if [[ "$digits_only" =~ ^7.{10}$ ]]; then
        result="+$digits_only"
    elif [[ "$digits_only" =~ ^8.{10}$ ]]; then
        result="+7${digits_only:1}"
    elif [[ ${#digits_only} -eq 10 ]]; then
        result="+7$digits_only"
    elif [[ "$cleaned" =~ ^\+7 && ${#digits_only} -eq 11 ]]; then
        result="+$digits_only"
    else
        result="INVALID"
    fi
    
    if [ "$result" = "$expected" ]; then
        echo "  ✅ '$input' → '$result'"
    else
        echo "  ❌ '$input' → '$result' (ожидалось: '$expected')"
    fi
}

echo "Тестирование JavaScript валидации:"
test_phone_format "+79161234567" "+79161234567"
test_phone_format "79161234567" "+79161234567"
test_phone_format "89161234567" "+79161234567"
test_phone_format "9161234567" "+79161234567"
test_phone_format "+7 916 123-45-67" "+79161234567"
test_phone_format "8 (916) 123-45-67" "+79161234567"
test_phone_format "123456789" "INVALID"
test_phone_format "+1234567890" "INVALID"

echo
echo "🌐 ПРОВЕРКА API ЭНДПОИНТОВ:"
echo "==========================="

BASE_URL="https://api.dimbopizza.ru"

echo "🔧 Enhanced-auth эндпоинт:"
enhanced_auth_status=$(curl -s -o /dev/null -w "%{http_code}" \
    -X POST $BASE_URL/api/v1/telegram-webapp/enhanced-auth \
    -H "Content-Type: application/json" \
    -d '{"initDataRaw": "test", "phoneNumber": "+79161234567", "deviceId": "test"}')

if [ "$enhanced_auth_status" = "400" ]; then
    echo "  ✅ Enhanced-auth API доступен (HTTP $enhanced_auth_status - ожидаемо для тестовых данных)"
else
    echo "  ❌ Enhanced-auth API недоступен (HTTP $enhanced_auth_status)"
fi

echo
echo "📋 ПРОВЕРКА В БД ПОСЛЕ ТЕСТИРОВАНИЯ:"
echo "===================================="
echo "После пересборки и тестирования проверьте:"
echo
echo "1. Таблица users:"
echo "   SELECT id, username, phone, is_phone_verified, is_telegram_verified"
echo "   FROM users WHERE telegram_id IS NOT NULL"
echo "   ORDER BY updated_at DESC;"
echo
echo "2. Таблица telegram_auth_tokens:"
echo "   SELECT auth_token, telegram_id, status, created_at, expires_at"
echo "   FROM telegram_auth_tokens"
echo "   WHERE created_at > NOW() - INTERVAL '1 hour';"

echo
echo "🚀 КОМАНДЫ ДЛЯ РАЗВЕРТЫВАНИЯ:"
echo "============================"
echo "# Локальная пересборка для тестирования"
echo "docker-compose -f docker-compose.dev.yml up --build -d"
echo
echo "# Продакшн развертывание"
echo "docker-compose -f docker-compose.production.yml up --build -d"

echo
echo "🎯 ОЖИДАЕМЫЙ РЕЗУЛЬТАТ:"
echo "======================"
echo "✅ Номера телефонов валидируются и форматируются в +7XXXXXXXXXX"
echo "✅ Некорректные номера отклоняются с понятным сообщением"
echo "✅ Номера сохраняются в таблице users при получении от Telegram"
echo "✅ Номера сохраняются в таблице users при ручном вводе"
echo "✅ Устанавливаются флаги is_phone_verified и is_telegram_verified"
echo "✅ Создаются записи в telegram_auth_tokens для кросс-платформенного доступа"

echo
echo "🔧 Исправления готовы к тестированию!"
