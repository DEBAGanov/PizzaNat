#!/bin/bash

# Тест эндпоинтов Telegram Mini App
echo "🔧 Тестирование эндпоинтов Mini App..."

BASE_URL="http://localhost:8080"
API_BASE="$BASE_URL/api/v1"

# Цвета для вывода
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
NC='\033[0m' # No Color

# Функция для тестирования эндпоинта
test_endpoint() {
    local endpoint="$1"
    local description="$2"
    local method="${3:-GET}"
    local auth_header="$4"
    local data="$5"
    
    echo -e "${BLUE}Testing: $description${NC}"
    echo "Endpoint: $method $endpoint"
    
    local curl_opts="-s -w %{http_code}"
    
    if [ "$method" = "POST" ]; then
        curl_opts="$curl_opts -X POST -H 'Content-Type: application/json'"
        if [ -n "$data" ]; then
            curl_opts="$curl_opts -d '$data'"
        fi
    fi
    
    if [ -n "$auth_header" ]; then
        curl_opts="$curl_opts -H 'Authorization: Bearer $auth_header'"
    fi
    
    local response=$(eval "curl $curl_opts $endpoint")
    local status_code="${response: -3}"
    local body="${response%???}"
    
    if [[ "$status_code" =~ ^2[0-9][0-9]$ ]]; then
        echo -e "${GREEN}✅ SUCCESS ($status_code)${NC}"
        if [ ${#body} -lt 200 ]; then
            echo "Response: $body"
        else
            echo "Response: ${body:0:100}..."
        fi
    else
        echo -e "${RED}❌ FAILED ($status_code)${NC}"
        echo "Error: $body"
    fi
    echo "---"
}

echo ""
echo "🌐 Базовая проверка сервера..."
if ! curl -s "$BASE_URL/api/health" > /dev/null; then
    echo -e "${RED}❌ Сервер не доступен по адресу $BASE_URL${NC}"
    echo "Убедитесь что сервер запущен: ./gradlew bootRun"
    exit 1
fi
echo -e "${GREEN}✅ Сервер доступен${NC}"

echo ""
echo "🔍 1. ПУБЛИЧНЫЕ ЭНДПОИНТЫ (без авторизации)"

# Категории
test_endpoint "$API_BASE/categories" "Получение категорий"

# Продукты
test_endpoint "$API_BASE/products?page=0&size=5" "Получение товаров с пагинацией"
test_endpoint "$API_BASE/products/category/1" "Получение товаров по категории"

# СБП банки
test_endpoint "$API_BASE/payments/yookassa/sbp/banks" "Получение банков СБП"

# Health checks
test_endpoint "$API_BASE/payments/yookassa/health" "ЮКасса Health Check"

echo ""
echo "🔐 2. TELEGRAM WEBAPP АВТОРИЗАЦИЯ"

# Тест валидации (должен вернуть false для тестовых данных)
test_endpoint "$API_BASE/telegram-webapp/validate-init-data" "Валидация initData" "POST" "" '{"initDataRaw":"test"}'

# Тест авторизации (должен вернуть ошибку для тестовых данных)
test_endpoint "$API_BASE/telegram-webapp/auth" "Авторизация WebApp" "POST" "" '{"initDataRaw":"test","deviceId":"test","userAgent":"test","platform":"telegram-miniapp"}'

echo ""
echo "🛒 3. КОРЗИНА И ЗАКАЗЫ (требуют авторизации)"

# Попробуем без токена - должно вернуть 401
test_endpoint "$API_BASE/cart" "Получение корзины без токена"

echo ""
echo "💳 4. ПЛАТЕЖИ (требуют авторизации)"

# Попробуем без токена
test_endpoint "$API_BASE/payments/yookassa/create" "Создание платежа без токена" "POST" "" '{"orderId":1,"method":"SBP"}'

echo ""
echo "📱 5. СТАТИЧЕСКИЕ ФАЙЛЫ MINI APP"

for file in "miniapp/index.html" "miniapp/menu.html" "miniapp/styles.css" "miniapp/menu-styles.css" "miniapp/api.js" "miniapp/app.js" "miniapp/menu-app.js"; do
    if curl -s -I "$BASE_URL/$file" | grep -q "200 OK"; then
        echo -e "${GREEN}✅ $file${NC}"
    else
        echo -e "${RED}❌ $file - не найден${NC}"
    fi
done

echo ""
echo "🔧 6. РЕДИРЕКТЫ"

# Проверяем редиректы
for path in "miniapp" "miniapp/" "miniapp/menu"; do
    echo "Testing redirect: /$path"
    location=$(curl -s -I "$BASE_URL/$path" | grep -i "^location:" | sed 's/location: //i' | tr -d '\r\n')
    if [ -n "$location" ]; then
        echo -e "${GREEN}✅ /$path → $location${NC}"
    else
        echo -e "${RED}❌ /$path - нет редиректа${NC}"
    fi
done

echo ""
echo "📊 РЕЗЮМЕ ИСПРАВЛЕНИЙ:"
echo "✅ Исправлен эндпоинт создания платежа: /mobile/payments/create → /payments/yookassa/create"
echo "✅ Категории и товары сделаны публичными (requiresAuth: false)"
echo "✅ Добавлено подробное логирование ошибок API"
echo "✅ Добавлены редиректы для Mini App страниц"

echo ""
echo "🚀 Для тестирования в Telegram:"
echo "1. Убедитесь что сервер запущен и доступен"
echo "2. Настройте Mini App URL в @BotFather: https://ваш-домен.com/miniapp"
echo "3. Откройте: https://t.me/DIMBOpizzaBot/DIMBO"
echo "4. Для полного меню: https://t.me/DIMBOpizzaBot/menu"

echo ""
echo "🔗 Полезные ссылки:"
echo "- Главная: $BASE_URL/miniapp"
echo "- Меню: $BASE_URL/miniapp/menu"
echo "- API Health: $BASE_URL/api/health"
echo "- Swagger: $BASE_URL/swagger-ui.html"
