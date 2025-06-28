#!/bin/bash

# Тестирование CORS настроек для микросервисной архитектуры PizzaNat
# Проверка поддержки pizzanat.ru и api.pizzanat.ru

set -e

# Цвета для вывода
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
NC='\033[0m' # No Color

# Счетчики
TOTAL_TESTS=0
PASSED_TESTS=0

echo -e "${BLUE}🌐 Тестирование CORS настроек PizzaNat${NC}"
echo "================================================="
echo

# Функция для проверки CORS
test_cors() {
    local test_name="$1"
    local origin="$2"
    local method="$3"
    local url="$4"
    local expected_origin="$5"
    
    TOTAL_TESTS=$((TOTAL_TESTS + 1))
    echo -e "${YELLOW}🧪 Тест $TOTAL_TESTS: $test_name${NC}"
    
    # Выполняем preflight запрос
    response=$(curl -s -w "\n%{http_code}" \
        -X OPTIONS \
        -H "Origin: $origin" \
        -H "Access-Control-Request-Method: $method" \
        -H "Access-Control-Request-Headers: Authorization,Content-Type" \
        "$url" 2>/dev/null || echo -e "\n000")
    
    # Разделяем ответ и код статуса
    http_code=$(echo "$response" | tail -1)
    headers=$(echo "$response" | head -n -1)
    
    echo "   🔗 Origin: $origin"
    echo "   📡 Method: $method"
    echo "   🌐 URL: $url"
    echo "   📊 HTTP Code: $http_code"
    
    # Проверяем заголовки CORS
    allow_origin=$(echo "$headers" | grep -i "access-control-allow-origin" | head -1 || echo "")
    allow_methods=$(echo "$headers" | grep -i "access-control-allow-methods" | head -1 || echo "")
    allow_headers=$(echo "$headers" | grep -i "access-control-allow-headers" | head -1 || echo "")
    allow_credentials=$(echo "$headers" | grep -i "access-control-allow-credentials" | head -1 || echo "")
    
    # Проверяем результат
    success=true
    
    if [[ "$http_code" != "200" && "$http_code" != "204" ]]; then
        echo -e "   ❌ Неожиданный HTTP код: $http_code"
        success=false
    fi
    
    if [[ -z "$allow_origin" ]]; then
        echo -e "   ❌ Отсутствует заголовок Access-Control-Allow-Origin"
        success=false
    elif [[ "$allow_origin" == *"$expected_origin"* ]]; then
        echo -e "   ✅ Access-Control-Allow-Origin: корректный"
    else
        echo -e "   ❌ Access-Control-Allow-Origin: некорректный ($allow_origin)"
        success=false
    fi
    
    if [[ -n "$allow_methods" && "$allow_methods" == *"$method"* ]]; then
        echo -e "   ✅ Access-Control-Allow-Methods: поддерживается $method"
    elif [[ -n "$allow_methods" ]]; then
        echo -e "   ⚠️  Access-Control-Allow-Methods: $method не найден в ($allow_methods)"
    fi
    
    if [[ -n "$allow_credentials" && "$allow_credentials" == *"true"* ]]; then
        echo -e "   ✅ Access-Control-Allow-Credentials: включен"
    fi
    
    if [[ "$success" == true ]]; then
        PASSED_TESTS=$((PASSED_TESTS + 1))
        echo -e "   🎉 ${GREEN}ТЕСТ ПРОЙДЕН${NC}"
    else
        echo -e "   💥 ${RED}ТЕСТ НЕ ПРОЙДЕН${NC}"
    fi
    
    echo
}

# Функция для обычного запроса (не preflight)
test_simple_cors() {
    local test_name="$1"
    local origin="$2"
    local url="$3"
    
    TOTAL_TESTS=$((TOTAL_TESTS + 1))
    echo -e "${YELLOW}🧪 Тест $TOTAL_TESTS: $test_name${NC}"
    
    response=$(curl -s -w "\n%{http_code}" \
        -X GET \
        -H "Origin: $origin" \
        "$url" 2>/dev/null || echo -e "\n000")
    
    http_code=$(echo "$response" | tail -1)
    headers=$(echo "$response" | head -n -1)
    
    echo "   🔗 Origin: $origin"
    echo "   🌐 URL: $url"
    echo "   📊 HTTP Code: $http_code"
    
    allow_origin=$(echo "$headers" | grep -i "access-control-allow-origin" | head -1 || echo "")
    
    if [[ "$http_code" == "200" ]]; then
        echo -e "   ✅ API доступен"
        PASSED_TESTS=$((PASSED_TESTS + 1))
        
        if [[ -n "$allow_origin" ]]; then
            echo -e "   ✅ CORS заголовки присутствуют"
        else
            echo -e "   ⚠️  CORS заголовки отсутствуют (может быть нормально для простых запросов)"
        fi
    else
        echo -e "   ❌ API недоступен (HTTP: $http_code)"
    fi
    
    echo
}

# Определяем базовый URL
if [[ "${PIZZANAT_URL:-}" ]]; then
    BASE_URL="$PIZZANAT_URL"
elif curl -s "https://pizzanat.ru/api/v1/health" >/dev/null 2>&1; then
    BASE_URL="https://pizzanat.ru"
elif curl -s "https://api.pizzanat.ru/api/v1/health" >/dev/null 2>&1; then
    BASE_URL="https://api.pizzanat.ru"
elif curl -s "http://localhost:8080/api/v1/health" >/dev/null 2>&1; then
    BASE_URL="http://localhost:8080"
else
    echo -e "${RED}❌ Не удалось найти доступный API сервер${NC}"
    echo "Попробуйте установить переменную PIZZANAT_URL:"
    echo "export PIZZANAT_URL=https://api.pizzanat.ru"
    exit 1
fi

echo -e "${BLUE}🎯 Используемый API: $BASE_URL${NC}"
echo

# Тестируем различные сценарии CORS

echo -e "${BLUE}📋 1. PREFLIGHT ЗАПРОСЫ${NC}"
echo "--------------------------------"

# Тест 1: Разрешенный домен pizzanat.ru
test_cors "Фронтенд pizzanat.ru → API" \
    "https://pizzanat.ru" \
    "GET" \
    "$BASE_URL/api/v1/health" \
    "https://pizzanat.ru"

# Тест 2: Разрешенный домен api.pizzanat.ru  
test_cors "API домен api.pizzanat.ru → API" \
    "https://api.pizzanat.ru" \
    "POST" \
    "$BASE_URL/api/v1/auth/login" \
    "https://api.pizzanat.ru"

# Тест 3: Localhost для разработки
test_cors "Разработка localhost:3000 → API" \
    "http://localhost:3000" \
    "PUT" \
    "$BASE_URL/api/v1/products" \
    "http://localhost:3000"

# Тест 4: Неразрешенный домен
test_cors "Неразрешенный домен example.com → API" \
    "https://example.com" \
    "GET" \
    "$BASE_URL/api/v1/health" \
    "SHOULD_FAIL"

echo -e "${BLUE}📋 2. ОБЫЧНЫЕ ЗАПРОСЫ${NC}"
echo "--------------------------------"

# Тест 5: Простой GET запрос health check
test_simple_cors "Health Check" \
    "https://pizzanat.ru" \
    "$BASE_URL/api/v1/health"

# Тест 6: Запрос к API продуктов
test_simple_cors "Список продуктов" \
    "https://pizzanat.ru" \
    "$BASE_URL/api/v1/products"

echo -e "${BLUE}📋 3. ПРОВЕРКА МЕТОДОВ${NC}"
echo "--------------------------------"

# Тестируем поддерживаемые методы
for method in GET POST PUT DELETE PATCH OPTIONS; do
    test_cors "Метод $method" \
        "https://pizzanat.ru" \
        "$method" \
        "$BASE_URL/api/v1/health" \
        "https://pizzanat.ru"
done

echo -e "${BLUE}📋 4. ПРОВЕРКА ЗАГОЛОВКОВ${NC}"
echo "--------------------------------"

# Тест с различными заголовками
TOTAL_TESTS=$((TOTAL_TESTS + 1))
echo -e "${YELLOW}🧪 Тест $TOTAL_TESTS: Поддерживаемые заголовки${NC}"

response=$(curl -s -w "\n%{http_code}" \
    -X OPTIONS \
    -H "Origin: https://pizzanat.ru" \
    -H "Access-Control-Request-Method: POST" \
    -H "Access-Control-Request-Headers: Authorization,Content-Type,X-Requested-With,Accept,Origin,X-Auth-Token,Cache-Control" \
    "$BASE_URL/api/v1/auth/login" 2>/dev/null || echo -e "\n000")

http_code=$(echo "$response" | tail -1)
headers=$(echo "$response" | head -n -1)

allow_headers=$(echo "$headers" | grep -i "access-control-allow-headers" | head -1 || echo "")

echo "   📊 HTTP Code: $http_code"
echo "   📋 Запрошенные заголовки: Authorization, Content-Type, X-Auth-Token"

if [[ -n "$allow_headers" ]]; then
    echo "   ✅ Разрешенные заголовки: $allow_headers"
    PASSED_TESTS=$((PASSED_TESTS + 1))
else
    echo "   ❌ Заголовки CORS не найдены"
fi

echo

# Итоговая статистика
echo "================================================="
echo -e "${BLUE}📊 ИТОГОВАЯ СТАТИСТИКА CORS ТЕСТИРОВАНИЯ${NC}"
echo "================================================="
echo

success_rate=$((PASSED_TESTS * 100 / TOTAL_TESTS))

echo -e "🎯 ${YELLOW}Всего тестов:${NC} $TOTAL_TESTS"
echo -e "✅ ${GREEN}Успешных:${NC} $PASSED_TESTS"
echo -e "❌ ${RED}Неуспешных:${NC} $((TOTAL_TESTS - PASSED_TESTS))"
echo -e "📈 ${BLUE}Процент успеха:${NC} $success_rate%"
echo

if [[ $success_rate -ge 80 ]]; then
    echo -e "🎉 ${GREEN}CORS НАСТРОЙКИ РАБОТАЮТ КОРРЕКТНО!${NC}"
    echo -e "✅ Система готова к микросервисной архитектуре"
    echo -e "🚀 Можно развертывать React фронтенд на pizzanat.ru"
elif [[ $success_rate -ge 60 ]]; then
    echo -e "⚠️  ${YELLOW}CORS НАСТРОЙКИ ЧАСТИЧНО РАБОТАЮТ${NC}"
    echo -e "🔧 Требуется дополнительная настройка"
else
    echo -e "💥 ${RED}CORS НАСТРОЙКИ ТРЕБУЮТ ИСПРАВЛЕНИЯ${NC}"
    echo -e "🚨 Необходимо проверить конфигурацию сервера"
fi

echo
echo -e "${BLUE}📋 РЕКОМЕНДАЦИИ:${NC}"
echo "• Убедитесь, что переменные окружения CORS настроены корректно"
echo "• Проверьте, что домены pizzanat.ru и api.pizzanat.ru настроены в DNS"
echo "• Для production используйте только HTTPS соединения"
echo "• Мониторьте логи на предмет CORS ошибок"
echo

exit 0 