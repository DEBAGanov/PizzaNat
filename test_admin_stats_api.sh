#!/bin/bash

# Тестирование API статистики админ панели
# Проверка ТЗ 1: GET /api/v1/admin/stats

set -e

BASE_URL="http://localhost:8080"
API_URL="$BASE_URL/api/v1"

echo "🧪 Тестирование API статистики админ панели"
echo "=============================================="

# Цвета для вывода
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
NC='\033[0m' # No Color

# Функция для логирования
log() {
    echo -e "${BLUE}[$(date +'%H:%M:%S')]${NC} $1"
}

success() {
    echo -e "${GREEN}✅ $1${NC}"
}

error() {
    echo -e "${RED}❌ $1${NC}"
}

warning() {
    echo -e "${YELLOW}⚠️  $1${NC}"
}

# Проверка доступности сервера
log "Проверка доступности сервера..."
if ! curl -s "$BASE_URL/api/health" > /dev/null; then
    error "Сервер недоступен по адресу $BASE_URL"
    exit 1
fi
success "Сервер доступен"

# Функция для получения JWT токена администратора
get_admin_token() {
    log "Получение JWT токена администратора..."
    
    # Попробуем зарегистрировать админа (если не существует)
    REGISTER_RESPONSE=$(curl -s -X POST "$API_URL/auth/register" \
        -H "Content-Type: application/json" \
        -d '{
            "username": "admin_test",
            "email": "admin@pizzanat.test",
            "password": "AdminPass123!",
            "firstName": "Admin",
            "lastName": "Test"
        }' || echo '{"error": "registration_failed"}')
    
    # Авторизация админа
    LOGIN_RESPONSE=$(curl -s -X POST "$API_URL/auth/login" \
        -H "Content-Type: application/json" \
        -d '{
            "username": "admin_test",
            "password": "AdminPass123!"
        }')
    
    if echo "$LOGIN_RESPONSE" | grep -q '"token"'; then
        TOKEN=$(echo "$LOGIN_RESPONSE" | grep -o '"token":"[^"]*"' | cut -d'"' -f4)
        success "JWT токен получен"
        echo "$TOKEN"
    else
        error "Не удалось получить JWT токен"
        echo "Ответ: $LOGIN_RESPONSE"
        return 1
    fi
}

# Тест 1: Проверка эндпоинта без авторизации
test_unauthorized_access() {
    log "Тест 1: Проверка доступа без авторизации"
    
    RESPONSE=$(curl -s -w "HTTPSTATUS:%{http_code}" -X GET "$API_URL/admin/stats")
    HTTP_STATUS=$(echo $RESPONSE | tr -d '\n' | sed -e 's/.*HTTPSTATUS://')
    BODY=$(echo $RESPONSE | sed -e 's/HTTPSTATUS\:.*//g')
    
    if [ "$HTTP_STATUS" -eq 401 ] || [ "$HTTP_STATUS" -eq 403 ]; then
        success "Неавторизованный доступ корректно заблокирован (HTTP $HTTP_STATUS)"
    else
        error "Ожидался HTTP 401/403, получен HTTP $HTTP_STATUS"
        echo "Ответ: $BODY"
    fi
}

# Тест 2: Проверка эндпоинта с авторизацией
test_authorized_access() {
    log "Тест 2: Проверка доступа с авторизацией"
    
    TOKEN=$(get_admin_token)
    if [ $? -ne 0 ]; then
        error "Не удалось получить токен для тестирования"
        return 1
    fi
    
    RESPONSE=$(curl -s -w "HTTPSTATUS:%{http_code}" -X GET "$API_URL/admin/stats" \
        -H "Authorization: Bearer $TOKEN" \
        -H "Content-Type: application/json")
    
    HTTP_STATUS=$(echo $RESPONSE | tr -d '\n' | sed -e 's/.*HTTPSTATUS://')
    BODY=$(echo $RESPONSE | sed -e 's/HTTPSTATUS\:.*//g')
    
    echo "HTTP Status: $HTTP_STATUS"
    echo "Response Body: $BODY"
    
    if [ "$HTTP_STATUS" -eq 200 ]; then
        success "Авторизованный доступ успешен (HTTP $HTTP_STATUS)"
        
        # Проверяем структуру ответа
        if echo "$BODY" | grep -q '"totalOrders"' && \
           echo "$BODY" | grep -q '"totalRevenue"' && \
           echo "$BODY" | grep -q '"totalProducts"' && \
           echo "$BODY" | grep -q '"totalCategories"' && \
           echo "$BODY" | grep -q '"ordersToday"' && \
           echo "$BODY" | grep -q '"revenueToday"' && \
           echo "$BODY" | grep -q '"popularProducts"' && \
           echo "$BODY" | grep -q '"orderStatusStats"'; then
            success "Структура ответа соответствует ТЗ"
            
            # Красивый вывод JSON
            echo "$BODY" | python3 -m json.tool 2>/dev/null || echo "$BODY"
        else
            error "Структура ответа не соответствует ТЗ"
            echo "Ожидаемые поля: totalOrders, totalRevenue, totalProducts, totalCategories, ordersToday, revenueToday, popularProducts, orderStatusStats"
        fi
    else
        error "Ожидался HTTP 200, получен HTTP $HTTP_STATUS"
        echo "Ответ: $BODY"
    fi
}

# Тест 3: Проверка производительности
test_performance() {
    log "Тест 3: Проверка производительности API"
    
    TOKEN=$(get_admin_token)
    if [ $? -ne 0 ]; then
        error "Не удалось получить токен для тестирования"
        return 1
    fi
    
    START_TIME=$(date +%s%N)
    
    RESPONSE=$(curl -s -w "HTTPSTATUS:%{http_code}" -X GET "$API_URL/admin/stats" \
        -H "Authorization: Bearer $TOKEN" \
        -H "Content-Type: application/json")
    
    END_TIME=$(date +%s%N)
    DURATION=$(( (END_TIME - START_TIME) / 1000000 )) # в миллисекундах
    
    HTTP_STATUS=$(echo $RESPONSE | tr -d '\n' | sed -e 's/.*HTTPSTATUS://')
    
    if [ "$HTTP_STATUS" -eq 200 ]; then
        if [ "$DURATION" -lt 5000 ]; then # менее 5 секунд
            success "Производительность в норме: ${DURATION}ms"
        else
            warning "Медленный ответ: ${DURATION}ms (ожидается < 5000ms)"
        fi
    else
        error "API недоступен для тестирования производительности"
    fi
}

# Запуск всех тестов
echo ""
test_unauthorized_access
echo ""
test_authorized_access
echo ""
test_performance

echo ""
echo "=============================================="
echo "🏁 Тестирование завершено"
echo "==============================================" 