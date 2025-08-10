#!/bin/bash

# Тест статических файлов Mini App
echo "📱 Тестирование статических файлов Mini App..."

BASE_URL="${1:-https://api.dimbopizza.ru}"
echo "Используем URL: $BASE_URL"

# Цвета для вывода
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
NC='\033[0m' # No Color

# Функция для тестирования файла
test_file() {
    local file_path="$1"
    local description="$2"
    
    echo -e "${BLUE}Testing: $description${NC}"
    echo "URL: $BASE_URL/$file_path"
    
    local response=$(curl -s -I "$BASE_URL/$file_path")
    
    if echo "$response" | grep -q "200 OK"; then
        echo -e "${GREEN}✅ SUCCESS - файл доступен${NC}"
        # Получаем размер файла если есть
        local size=$(echo "$response" | grep -i "content-length:" | cut -d' ' -f2 | tr -d '\r\n')
        if [ -n "$size" ]; then
            echo "Размер: $size байт"
        fi
    elif echo "$response" | grep -q "302"; then
        local location=$(echo "$response" | grep -i "location:" | cut -d' ' -f2- | tr -d '\r\n')
        echo -e "${YELLOW}📍 REDIRECT → $location${NC}"
    else
        echo -e "${RED}❌ FAILED - файл недоступен${NC}"
        echo "Response headers:"
        echo "$response" | head -5
    fi
    echo "---"
}

echo ""
echo "🔍 1. ОСНОВНЫЕ СТРАНИЦЫ MINI APP"

test_file "miniapp" "Главная страница Mini App (должен быть редирект)"
test_file "miniapp/" "Главная страница Mini App с слешем"
test_file "miniapp/menu" "Страница меню (должен быть редирект)"

echo ""
echo "📄 2. HTML ФАЙЛЫ"

test_file "miniapp/index.html" "Главная HTML страница"
test_file "miniapp/menu.html" "HTML страница меню"

echo ""
echo "🎨 3. CSS ФАЙЛЫ"

test_file "miniapp/styles.css" "Основные стили"
test_file "miniapp/menu-styles.css" "Стили для меню"

echo ""
echo "📜 4. JAVASCRIPT ФАЙЛЫ"

test_file "miniapp/api.js" "API модуль"
test_file "miniapp/app.js" "Основной JS файл"
test_file "miniapp/menu-app.js" "JS файл для меню"

echo ""
echo "🖼️ 5. СТАТИЧЕСКИЕ РЕСУРСЫ"

test_file "static/images/categories/pizza.png" "Иконка пиццы"
test_file "static/images/products/pizza_4_chees.png" "Изображение продукта"

echo ""
echo "🔧 6. API ЭНДПОИНТЫ"

# Тестируем основные API
echo -e "${BLUE}Тестируем API эндпоинты...${NC}"

for endpoint in "api/v1/categories" "api/v1/products?page=0&size=3" "api/health"; do
    echo "Testing: $BASE_URL/$endpoint"
    status=$(curl -s -o /dev/null -w "%{http_code}" "$BASE_URL/$endpoint")
    if [ "$status" = "200" ]; then
        echo -e "${GREEN}✅ $endpoint${NC}"
    else
        echo -e "${RED}❌ $endpoint (HTTP $status)${NC}"
    fi
done

echo ""
echo "📋 РЕЗЮМЕ:"
echo "🔗 Основные ссылки:"
echo "  - Главная: $BASE_URL/miniapp"
echo "  - Меню: $BASE_URL/miniapp/menu"
echo "  - Telegram: https://t.me/DIMBOpizzaBot/DIMBO"
echo "  - Меню в Telegram: https://t.me/DIMBOpizzaBot/menu"

echo ""
echo "💡 Для локального тестирования:"
echo "  ./scripts/test_miniapp_static_files.sh http://localhost:8080"

echo ""
if [ "$BASE_URL" = "https://api.dimbopizza.ru" ]; then
    echo "🚀 Тестирование завершено. Попробуйте открыть Mini App в Telegram!"
else
    echo "🔧 Локальное тестирование завершено."
fi
