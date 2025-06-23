#!/bin/bash

echo "=== Тестирование SMS авторизации через Exolve API (Обновленный) ==="
echo

# Данные для тестирования
API_KEY="eyJhbGciOiJSUzI1NiIsInR5cCIgOiAiSldUIiwia2lkIiA6ICJRV05sMENiTXY1SHZSV29CVUpkWjVNQURXSFVDS0NWODRlNGMzbEQtVHA0In0.eyJleHAiOjIwNjU1MTM0MTMsImlhdCI6MTc1MDE1MzQxMywianRpIjoiMzIyNDBhZTAtNzU2Ni00NDhkLWEzZGUtYjFjZDBjODlkNTU0IiwiaXNzIjoiaHR0cHM6Ly9zc28uZXhvbHZlLnJ1L3JlYWxtcy9FeG9sdmUiLCJhdWQiOiJhY2NvdW50Iiwic3ViIjoiZDZkYjE4ZDEtOWRhNS00NjRmLWI0ODYtMjI5NGQzMDk2ODI5IiwidHlwIjoiQmVhcmVyIiwiYXpwIjoiY2IxNGFjMTQtODU4OS00MjkzLWJkZjktNGE3M2VkYTRmMzQxIiwic2Vzc2lvbl9zdGF0ZSI6ImUzM2EwYzY1LWFkYTctNGU1My1iYmRmLTQzNDJhNTk0OTE1OCIsImFjciI6IjEiLCJyZWFsbV9hY2Nlc3MiOnsicm9sZXMiOlsiZGVmYXVsdC1yb2xlcy1leG9sdmUiLCJvZmZsaW5lX2FjY2VzcyIsInVtYV9hdXRob3JpemF0aW9uIl19LCJyZXNvdXJjZV9hY2Nlc3MiOnsiYWNjb3VudCI6eyJyb2xlcyI6WyJtYW5hZ2UtYWNjb3VudCIsIm1hbmFnZS1hY2NvdW50LWxpbmtzIiwidmlldy1wcm9maWxlIl19fSwic2NvcGUiOiJleG9sdmVfYXBwIHByb2ZpbGUgZW1haWwiLCJzaWQiOiJlMzNhMGM2NS1hZGE3LTRlNTMtYmJkZi00MzQyYTU5NDkxNTgiLCJ1c2VyX3V1aWQiOiI4NDY2MzRkNy0zYTNlLTRiMzMtODdkNy01MDgzZGRlNmYxOWIiLCJjbGllbnRJZCI6ImNiMTRhYzE0LTg1ODktNDI5My1iZGY5LTRhNzNlZGE0ZjM0MSIsImVtYWlsX3ZlcmlmaWVkIjpmYWxzZSwiY2xpZW50SG9zdCI6IjE3Mi4xNi4xNjEuMTkiLCJhcGlfa2V5Ijp0cnVlLCJhcGlmb25pY2Ffc2lkIjoiY2IxNGFjMTQtODU4OS00MjkzLWJkZjktNGE3M2VkYTRmMzQxIiwiYmlsbGluZ19udW1iZXIiOiIxMzMyNTgzIiwiYXBpZm9uaWNhX3Rva2VuIjoiYXV0ZDJlYTgxNGItMWM4Zi00ODRkLWE0MjgtMjY5YTZjOWM2NmY2IiwicHJlZmVycmVkX3VzZXJuYW1lIjoic2VydmljZS1hY2NvdW50LWNiMTRhYzE0LTg1ODktNDI5My1iZGY5LTRhNzNlZGE0ZjM0MSIsImN1c3RvbWVyX2lkIjoiMTM1ODk5IiwiY2xpZW50QWRkcmVzcyI6IjE3Mi4xNi4xNjEuMTkifQ.AFj1waE8M77SL26g9poSbRYEWeiV9Wy2ZonUnI4JJDF4uBP1D90YjTUOayHCPRbryBp6gU-cszAndQMlQsT5JLNhs88X7uo08XTY52Q9ghfdpEH22uG5AFxtWTr5450TfgLyl38goA76Xpd88xu3SHUJFEaScSGUjLaoZ1TKmvDnzdG1ZExtiARhUNRQ0eqlfkkfmYDiq_injddMk1Qub6TfC4zH4O2C0o4rUr9hIruXZe9ciKZAzZ_2hdys52vV8dN99OY5ghVRyysPAo05lScPDDMEpT2F6BwfZEQSH8r7WqOU3acxSI64gqmOFTczGZlsE7o09b_NlehqXIuHDg"
SENDER_NUMBER="+79304410750"
CLIENT_NUMBER="+79818279564"
EXOLVE_API_URL="https://api.exolve.ru/messaging/v1/SendSMS"
APP_API_URL="http://localhost:8080"

echo "📱 Данные для тестирования:"
echo "   Исходящий номер: $SENDER_NUMBER"
echo "   Номер клиента: $CLIENT_NUMBER"
echo "   API ключ: ${API_KEY:0:50}..."
echo

# Функция для проверки доступности сервера
check_server() {
    echo "🔍 1. Проверка доступности сервера приложения..."
    if curl -s "$APP_API_URL/actuator/health" > /dev/null 2>&1; then
        echo "   ✅ Сервер доступен"
        return 0
    else
        echo "   ❌ Сервер недоступен на $APP_API_URL"
        return 1
    fi
}

# Функция для тестирования прямого вызова Exolve API
test_direct_exolve_api() {
    echo "🌐 2. Тестирование прямого вызова Exolve API..."

    # Нормализуем номера для Exolve (формат 79XXXXXXXXX)
    NORMALIZED_SENDER=$(echo "$SENDER_NUMBER" | sed 's/[^0-9]//g' | sed 's/^8/7/')
    NORMALIZED_CLIENT=$(echo "$CLIENT_NUMBER" | sed 's/[^0-9]//g' | sed 's/^8/7/')

    echo "   Нормализованные номера:"
    echo "   Отправитель: $NORMALIZED_SENDER"
    echo "   Получатель: $NORMALIZED_CLIENT"

         # Подготавливаем JSON запрос
     JSON_PAYLOAD=$(cat <<EOF
{
    "number": "$NORMALIZED_SENDER",
    "destination": "$NORMALIZED_CLIENT",
    "text": "PizzaNat: Ваш код подтверждения: 1234"
}
EOF
)

    echo "   JSON запрос:"
    echo "$JSON_PAYLOAD" | python3 -m json.tool

    # Отправляем запрос
    response=$(curl -s -w "HTTP_CODE:%{http_code}" \
        -X POST "$EXOLVE_API_URL" \
        -H "Content-Type: application/json" \
        -H "Authorization: Bearer $API_KEY" \
        -d "$JSON_PAYLOAD")

    http_code=$(echo "$response" | grep -o "HTTP_CODE:[0-9]*" | cut -d: -f2)
    response_body=$(echo "$response" | sed 's/HTTP_CODE:[0-9]*$//')

    echo "   HTTP код: $http_code"

    if [ "$http_code" = "200" ] || [ "$http_code" = "201" ]; then
        echo "   ✅ Прямой вызов Exolve API успешен"
        if echo "$response_body" | python3 -m json.tool > /dev/null 2>&1; then
            echo "   📄 Ответ API:"
            echo "$response_body" | python3 -m json.tool
        else
            echo "   📄 Ответ (не JSON): $response_body"
        fi
        return 0
    else
        echo "   ❌ Ошибка прямого вызова Exolve API"
        echo "   📄 Ответ: $response_body"
        return 1
    fi
}

# Функция для тестирования отправки SMS через наше приложение
test_app_sms_send() {
    echo "📲 3. Тестирование отправки SMS кода через приложение..."

    response=$(curl -s -w "HTTP_CODE:%{http_code}" \
        -X POST "$APP_API_URL/api/v1/auth/sms/send-code" \
        -H "Content-Type: application/json" \
        -d "{\"phoneNumber\": \"$CLIENT_NUMBER\"}")

    http_code=$(echo "$response" | grep -o "HTTP_CODE:[0-9]*" | cut -d: -f2)
    response_body=$(echo "$response" | sed 's/HTTP_CODE:[0-9]*$//')

    echo "   HTTP код: $http_code"

    if [ "$http_code" = "200" ]; then
        echo "   ✅ SMS код отправлен успешно"
        if echo "$response_body" | python3 -m json.tool > /dev/null 2>&1; then
            echo "   📄 Ответ приложения:"
            echo "$response_body" | python3 -m json.tool

            # Извлекаем маскированный номер для проверки
            masked_phone=$(echo "$response_body" | python3 -c "
import sys, json
try:
    data = json.load(sys.stdin)
    print(data.get('maskedPhoneNumber', 'не найден'))
except:
    print('ошибка парсинга')
" <<< "$response_body")
            echo "   📱 Маскированный номер: $masked_phone"
        else
            echo "   📄 Ответ (не JSON): $response_body"
        fi
        return 0
    else
        echo "   ❌ Ошибка отправки SMS кода"
        echo "   📄 Ответ: $response_body"
        return 1
    fi
}

# Функция для тестирования верификации кода
test_app_sms_verify() {
    echo "🔐 4. Тестирование верификации SMS кода..."

    # Тестируем с неправильным кодом
    response=$(curl -s -w "HTTP_CODE:%{http_code}" \
        -X POST "$APP_API_URL/api/v1/auth/sms/verify-code" \
        -H "Content-Type: application/json" \
        -d "{\"phoneNumber\": \"$CLIENT_NUMBER\", \"code\": \"9999\"}")

    http_code=$(echo "$response" | grep -o "HTTP_CODE:[0-9]*" | cut -d: -f2)
    response_body=$(echo "$response" | sed 's/HTTP_CODE:[0-9]*$//')

    echo "   Тест с неправильным кодом (9999):"
    echo "   HTTP код: $http_code"

    if [ "$http_code" = "400" ] || [ "$http_code" = "401" ]; then
        echo "   ✅ Неправильный код корректно отклонен"
    else
        echo "   ⚠️  Неожиданный ответ на неправильный код"
    fi

    if echo "$response_body" | python3 -m json.tool > /dev/null 2>&1; then
        echo "   📄 Ответ:"
        echo "$response_body" | python3 -m json.tool
    else
        echo "   📄 Ответ (не JSON): $response_body"
    fi
}

# Функция для проверки логов приложения
check_app_logs() {
    echo "📋 5. Проверка логов приложения..."

    if [ -f "app.log" ]; then
        echo "   📄 Последние записи логов (SMS/Exolve):"
        tail -20 app.log | grep -i -E "(sms|exolve|отправ)" | tail -10
    else
        echo "   ⚠️  Файл логов app.log не найден"
    fi
}

# Основная функция тестирования
main() {
    echo "🚀 Начинаем комплексное тестирование SMS авторизации..."
    echo

    # Счетчики успешных тестов
    total_tests=0
    passed_tests=0

    # Тест 1: Проверка доступности сервера
    total_tests=$((total_tests + 1))
    if check_server; then
        passed_tests=$((passed_tests + 1))
    fi
    echo

    # Тест 2: Прямой вызов Exolve API
    total_tests=$((total_tests + 1))
    if test_direct_exolve_api; then
        passed_tests=$((passed_tests + 1))
    fi
    echo

    # Тест 3: Отправка SMS через приложение (только если сервер доступен)
    if curl -s "$APP_API_URL/actuator/health" > /dev/null 2>&1; then
        total_tests=$((total_tests + 1))
        if test_app_sms_send; then
            passed_tests=$((passed_tests + 1))
        fi
        echo

        # Тест 4: Верификация кода
        total_tests=$((total_tests + 1))
        if test_app_sms_verify; then
            passed_tests=$((passed_tests + 1))
        fi
        echo

        # Проверка логов
        check_app_logs
        echo
    else
        echo "   ⚠️  Пропускаем тесты приложения - сервер недоступен"
        echo
    fi

    # Итоговый отчет
    echo "=== 📊 ИТОГОВЫЙ ОТЧЕТ ==="
    echo "   Выполнено тестов: $passed_tests из $total_tests"
    echo "   Процент успеха: $((passed_tests * 100 / total_tests))%"
    echo

    if [ "$passed_tests" -eq "$total_tests" ]; then
        echo "   🎉 ВСЕ ТЕСТЫ ПРОЙДЕНЫ УСПЕШНО!"
        echo "   ✅ SMS авторизация работает корректно"
    elif [ "$passed_tests" -gt 0 ]; then
        echo "   ⚠️  ЧАСТИЧНЫЙ УСПЕХ"
        echo "   🔧 Некоторые компоненты требуют настройки"
    else
        echo "   ❌ ВСЕ ТЕСТЫ ПРОВАЛИЛИСЬ"
        echo "   🚨 Требуется диагностика проблем"
    fi

    echo
    echo "📋 Рекомендации:"
    echo "   1. Убедитесь, что приложение запущено: ./gradlew bootRun"
    echo "   2. Проверьте конфигурацию Exolve API в application.properties"
    echo "   3. Проверьте логи приложения на наличие ошибок"
    echo "   4. Убедитесь, что номера телефонов корректны"
    echo
    echo "📱 Для получения реального SMS кода:"
    echo "   - Запустите приложение"
    echo "   - Вызовите POST /api/v1/auth/sms/send-code"
    echo "   - Проверьте SMS на номере $CLIENT_NUMBER"
    echo "   - Используйте полученный код в POST /api/v1/auth/sms/verify-code"
}

# Запуск тестирования
main