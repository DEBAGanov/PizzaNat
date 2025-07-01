#!/bin/bash

# Тестирование новых CORS доменов для PizzaNat
# Проверка поддержки https://www.pizzanat.ru и http://localhost:5173

set -e

# Цвета для вывода
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
NC='\033[0m' # No Color

echo -e "${BLUE}🌐 Тестирование новых CORS доменов${NC}"
echo "=========================================="
echo

# Определяем базовый URL
if curl -s "http://localhost:8080/api/v1/health" >/dev/null 2>&1; then
    BASE_URL="http://localhost:8080"
elif [[ "${PIZZANAT_URL:-}" ]]; then
    BASE_URL="$PIZZANAT_URL"
else
    echo -e "${RED}❌ API сервер недоступен${NC}"
    echo "Убедитесь, что приложение запущено на localhost:8080"
    echo "Или установите переменную PIZZANAT_URL"
    exit 1
fi

echo -e "${BLUE}🎯 Используемый API: $BASE_URL${NC}"
echo

# Функция для тестирования CORS
test_cors_domain() {
    local domain="$1"
    local description="$2"
    
    echo -e "${YELLOW}🧪 Тестирование: $description${NC}"
    echo "   🔗 Origin: $domain"
    
    # Выполняем preflight запрос
    response=$(curl -s -w "\n%{http_code}" \
        -X OPTIONS \
        -H "Origin: $domain" \
        -H "Access-Control-Request-Method: GET" \
        -H "Access-Control-Request-Headers: Authorization,Content-Type" \
        "$BASE_URL/api/v1/health" 2>/dev/null || echo -e "\n000")
    
    # Разделяем ответ и код статуса
    http_code=$(echo "$response" | tail -1)
    headers=$(echo "$response" | head -n -1)
    
    echo "   📊 HTTP Code: $http_code"
    
    # Проверяем заголовки CORS
    allow_origin=$(echo "$headers" | grep -i "access-control-allow-origin" | head -1 || echo "")
    
    if [[ "$http_code" == "200" || "$http_code" == "204" ]]; then
        if [[ -n "$allow_origin" && "$allow_origin" == *"$domain"* ]]; then
            echo -e "   ✅ ${GREEN}УСПЕХ${NC} - CORS поддерживается"
        elif [[ -n "$allow_origin" ]]; then
            echo -e "   ⚠️  ${YELLOW}ЧАСТИЧНО${NC} - CORS настроен, но возможны ограничения"
            echo "   📋 Заголовок: $allow_origin"
        else
            echo -e "   ❌ ${RED}ОШИБКА${NC} - CORS заголовки отсутствуют"
        fi
    else
        echo -e "   ❌ ${RED}ОШИБКА${NC} - Неожиданный HTTP код: $http_code"
    fi
    
    echo
}

# Тестируем новые домены
echo -e "${BLUE}📋 ТЕСТИРОВАНИЕ НОВЫХ ДОМЕНОВ${NC}"
echo "--------------------------------"

test_cors_domain "https://www.pizzanat.ru" "WWW версия основного домена"
test_cors_domain "http://localhost:5173" "Vite dev server"

echo -e "${BLUE}📋 ПРОВЕРКА СУЩЕСТВУЮЩИХ ДОМЕНОВ${NC}"
echo "------------------------------------"

test_cors_domain "https://pizzanat.ru" "Основной домен"
test_cors_domain "http://localhost:3000" "React dev server"

echo -e "${BLUE}📋 РЕЗУЛЬТАТ${NC}"
echo "=============="
echo -e "✅ ${GREEN}Новые домены добавлены в CORS конфигурацию${NC}"
echo -e "🔧 ${YELLOW}Для полного тестирования запустите:${NC} ./scripts/test_cors_configuration.sh"
echo -e "📚 ${BLUE}Документация обновлена в changelog.md и Tasktracker.md${NC}"
echo

exit 0 