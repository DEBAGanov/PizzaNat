#!/bin/bash

# Тестирование интеграции с Яндекс.Карты API
# Скрипт проверяет работу автоподсказок адресов через Яндекс.Карты

BASE_URL="http://localhost:8080"
API_KEY="45047eff-461d-43db-9605-1452d66fa4fe"

echo "🗺️  Тестирование интеграции с Яндекс.Карты API"
echo "=============================================="
echo "API Key: $API_KEY"
echo "Base URL: $BASE_URL"
echo ""

# Функция для выполнения HTTP запросов
make_request() {
    local url="$1"
    local description="$2"
    
    echo "📍 $description"
    echo "URL: $url"
    
    response=$(curl -s -w "\nHTTP_CODE:%{http_code}" "$url")
    http_code=$(echo "$response" | grep "HTTP_CODE:" | cut -d: -f2)
    body=$(echo "$response" | sed '/HTTP_CODE:/d')
    
    if [ "$http_code" = "200" ]; then
        echo "✅ Успешно (HTTP $http_code)"
        echo "Ответ: $body" | jq '.' 2>/dev/null || echo "Ответ: $body"
    else
        echo "❌ Ошибка (HTTP $http_code)"
        echo "Ответ: $body"
    fi
    echo "----------------------------------------"
    echo ""
}

# Тест 1: Проверка статуса приложения
echo "🔍 Тест 1: Проверка статуса приложения"
make_request "$BASE_URL/actuator/health" "Проверка health endpoint"

# Тест 2: Поиск улиц в Волжске
echo "🔍 Тест 2: Поиск улиц в Волжске"
make_request "$BASE_URL/api/v1/address/suggestions?query=ул" "Поиск всех улиц"
make_request "$BASE_URL/api/v1/address/suggestions?query=Ленина" "Поиск улицы Ленина"
make_request "$BASE_URL/api/v1/address/suggestions?query=107" "Поиск улицы 107-й Бригады"

# Тест 3: Поиск конкретных адресов
echo "🔍 Тест 3: Поиск конкретных адресов"
make_request "$BASE_URL/api/v1/address/suggestions?query=Волжск ул Ленина" "Поиск адресов на ул. Ленина"
make_request "$BASE_URL/api/v1/address/suggestions?query=Волжск ул Свердлова" "Поиск адресов на ул. Свердлова"
make_request "$BASE_URL/api/v1/address/suggestions?query=Волжск микрорайон Дубрава" "Поиск в микрорайоне Дубрава"

# Тест 4: Поиск домов на конкретной улице
echo "🔍 Тест 4: Поиск домов на улицах"
make_request "$BASE_URL/api/v1/address/houses?street=ул. Ленина&houseQuery=1" "Поиск домов на ул. Ленина начинающихся с 1"
make_request "$BASE_URL/api/v1/address/houses?street=ул. Свердлова&houseQuery=2" "Поиск домов на ул. Свердлова начинающихся с 2"

# Тест 5: Валидация адресов
echo "🔍 Тест 5: Валидация адресов"
make_request "$BASE_URL/api/v1/address/validate" "Валидация корректного адреса" \
    -X POST \
    -H "Content-Type: application/json" \
    -d '{"address": "Волжск, ул. Ленина, д. 1"}'

make_request "$BASE_URL/api/v1/address/validate" "Валидация некорректного адреса" \
    -X POST \
    -H "Content-Type: application/json" \
    -d '{"address": "Москва, ул. Тверская, д. 1"}'

# Тест 6: Прямое тестирование Яндекс.Карты API
echo "🔍 Тест 6: Прямое тестирование Яндекс.Карты API"
echo "📍 Тестирование прямого запроса к Яндекс.Карты"

YANDEX_URL="https://geocode-maps.yandex.ru/1.x/?apikey=$API_KEY&geocode=Волжск ул Ленина&format=json&results=5&kind=house&rspn=1&ll=48.359,55.866&spn=0.1,0.1"
echo "URL: $YANDEX_URL"

yandex_response=$(curl -s "$YANDEX_URL")
echo "Ответ от Яндекс.Карт:"
echo "$yandex_response" | jq '.' 2>/dev/null || echo "$yandex_response"

echo ""
echo "🎯 Тестирование завершено!"
echo "=============================================="

# Проверка логов приложения
if [ -f "logs/application.log" ]; then
    echo "📋 Последние записи в логах:"
    tail -20 logs/application.log | grep -E "(Yandex|yandex|YANDEX|AddressSuggestion)"
fi 