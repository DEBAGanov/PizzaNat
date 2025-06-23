#!/bin/bash

# Тестирование API автоподсказок адресов для PizzaNat
# Проверяет работу с адресами города Волжск

set -e

# Цвета для вывода
RED='\033[0;31m'
GREEN='\033[0;32m'
BLUE='\033[0;34m'
YELLOW='\033[1;33m'
NC='\033[0m' # No Color

# Базовый URL
BASE_URL="http://localhost:8080"

# Счетчики тестов
TOTAL_TESTS=0
PASSED_TESTS=0
FAILED_TESTS=0

echo -e "${BLUE}🏠 ТЕСТИРОВАНИЕ API АВТОПОДСКАЗОК АДРЕСОВ${NC}"
echo "=================================================="

# Функция для выполнения HTTP запроса
test_endpoint() {
    local url="$1"
    local description="$2"
    local expected_status="${3:-200}"
    
    echo -e "${YELLOW}Тестируем: $description${NC}"
    echo "URL: $BASE_URL$url"
    
    # Выполняем запрос
    local response=$(curl -s -w "HTTPSTATUS:%{http_code}" "$BASE_URL$url" \
        -H "Accept: application/json" \
        -H "Content-Type: application/json")
    
    # Извлекаем HTTP код и тело ответа
    local http_code=$(echo "$response" | grep -o "HTTPSTATUS:[0-9]*" | cut -d: -f2)
    local body=$(echo "$response" | sed 's/HTTPSTATUS:[0-9]*$//')
    
    echo "HTTP код: $http_code"
    
    if [ "$http_code" = "$expected_status" ]; then
        echo -e "${GREEN}✅ УСПЕХ${NC}"
        echo "Ответ: $body" | head -c 200
        if [ ${#body} -gt 200 ]; then
            echo "..."
        fi
        echo
        PASSED_TESTS=$((PASSED_TESTS + 1))
    else
        echo -e "${RED}❌ ОШИБКА - ожидался код $expected_status, получен $http_code${NC}"
        echo "Ответ: $body"
        FAILED_TESTS=$((FAILED_TESTS + 1))
    fi
    
    TOTAL_TESTS=$((TOTAL_TESTS + 1))
    echo "=================================================="
}

# Функция для POST запроса
test_post_endpoint() {
    local url="$1"
    local description="$2"
    local data="$3"
    local expected_status="${4:-200}"
    
    echo -e "${YELLOW}Тестируем: $description${NC}"
    echo "URL: $BASE_URL$url"
    echo "Данные: $data"
    
    # Выполняем POST запрос
    local response=$(curl -s -w "HTTPSTATUS:%{http_code}" -X POST "$BASE_URL$url" \
        -H "Accept: application/json" \
        -H "Content-Type: application/json" \
        -d "$data")
    
    # Извлекаем HTTP код и тело ответа
    local http_code=$(echo "$response" | grep -o "HTTPSTATUS:[0-9]*" | cut -d: -f2)
    local body=$(echo "$response" | sed 's/HTTPSTATUS:[0-9]*$//')
    
    echo "HTTP код: $http_code"
    
    if [ "$http_code" = "$expected_status" ]; then
        echo -e "${GREEN}✅ УСПЕХ${NC}"
        echo "Ответ: $body" | head -c 200
        if [ ${#body} -gt 200 ]; then
            echo "..."
        fi
        echo
        PASSED_TESTS=$((PASSED_TESTS + 1))
    else
        echo -e "${RED}❌ ОШИБКА - ожидался код $expected_status, получен $http_code${NC}"
        echo "Ответ: $body"
        FAILED_TESTS=$((FAILED_TESTS + 1))
    fi
    
    TOTAL_TESTS=$((TOTAL_TESTS + 1))
    echo "=================================================="
}

echo -e "${BLUE}1. АВТОПОДСКАЗКИ УЛИЦ${NC}"

# Тест 1: Поиск улиц по частичному совпадению
test_endpoint "/api/v1/address/suggestions?query=ул" "Поиск улиц по 'ул'"

# Тест 2: Поиск конкретной улицы
test_endpoint "/api/v1/address/suggestions?query=Ленина" "Поиск улицы Ленина"

# Тест 3: Поиск улицы 107-й Бригады
test_endpoint "/api/v1/address/suggestions?query=107" "Поиск улицы 107-й Бригады"

# Тест 4: Поиск несуществующей улицы
test_endpoint "/api/v1/address/suggestions?query=Несуществующая" "Поиск несуществующей улицы"

# Тест 5: Короткий запрос (должен вернуть ошибку)
test_endpoint "/api/v1/address/suggestions?query=л" "Короткий запрос (1 символ)" 400

echo -e "${BLUE}2. АВТОПОДСКАЗКИ ДОМОВ${NC}"

# Тест 6: Поиск домов на улице Ленина
test_endpoint "/api/v1/address/houses?street=улица Ленина" "Дома на улице Ленина"

# Тест 7: Поиск конкретного дома
test_endpoint "/api/v1/address/houses?street=улица Ленина&houseQuery=1" "Дома начинающиеся с '1' на улице Ленина"

# Тест 8: Поиск домов на улице 107-й Бригады
test_endpoint "/api/v1/address/houses?street=улица 107-й Бригады&houseQuery=5" "Дома начинающиеся с '5' на улице 107-й Бригады"

# Тест 9: Пустое название улицы (должен вернуть ошибку)
test_endpoint "/api/v1/address/houses?street=" "Пустое название улицы" 400

echo -e "${BLUE}3. ВАЛИДАЦИЯ АДРЕСОВ${NC}"

# Тест 10: Валидация корректного адреса
test_post_endpoint "/api/v1/address/validate" "Валидация корректного адреса" \
    '{"address": "Республика Марий Эл, Волжск, улица Ленина, 1"}'

# Тест 11: Валидация некорректного адреса
test_post_endpoint "/api/v1/address/validate" "Валидация некорректного адреса" \
    '{"address": "Москва, улица Тверская, 1"}'

# Тест 12: Валидация адреса без города
test_post_endpoint "/api/v1/address/validate" "Валидация адреса без города" \
    '{"address": "улица Ленина, 1"}'

# Тест 13: Валидация пустого адреса
test_post_endpoint "/api/v1/address/validate" "Валидация пустого адреса" \
    '{"address": ""}'

echo -e "${BLUE}4. ДОПОЛНИТЕЛЬНЫЕ ТЕСТЫ${NC}"

# Тест 14: Поиск микрорайонов
test_endpoint "/api/v1/address/suggestions?query=микрорайон" "Поиск микрорайонов"

# Тест 15: Поиск переулков
test_endpoint "/api/v1/address/suggestions?query=переулок" "Поиск переулков"

# Тест 16: Поиск проспектов
test_endpoint "/api/v1/address/suggestions?query=проспект" "Поиск проспектов"

# Итоговая статистика
echo "=============================================="
echo -e "${BLUE}📊 ИТОГОВАЯ СТАТИСТИКА${NC}"
echo -e "Всего тестов: $TOTAL_TESTS"
echo -e "${GREEN}Успешных: $PASSED_TESTS${NC}"
echo -e "${RED}Неудачных: $FAILED_TESTS${NC}"

if [ $FAILED_TESTS -eq 0 ]; then
    echo -e "${GREEN}🎉 ВСЕ ТЕСТЫ ПРОШЛИ УСПЕШНО!${NC}"
    exit 0
else
    echo -e "${RED}❌ НЕКОТОРЫЕ ТЕСТЫ НЕ ПРОШЛИ${NC}"
    exit 1
fi 