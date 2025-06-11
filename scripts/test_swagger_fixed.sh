#!/bin/bash

#
# Тест исправленного Swagger после добавления версии OpenAPI 3.1.0
# Создан: 2025-06-10
#

API_HOST="http://localhost:8080"

echo "🔍 Тест исправленного Swagger API"
echo "================================="

# Функция для проверки HTTP статуса
check_endpoint() {
    local url=$1
    local expected_status=$2
    local description=$3
    
    echo -n "📋 $description... "
    
    status_code=$(curl -s -o /dev/null -w "%{http_code}" "$url")
    
    if [ "$status_code" = "$expected_status" ]; then
        echo "✅ $status_code"
        return 0
    else
        echo "❌ $status_code (ожидался $expected_status)"
        return 1
    fi
}

# Проверка OpenAPI документации
echo -e "\n🔧 Проверка OpenAPI Documentation:"
check_endpoint "$API_HOST/v3/api-docs" "200" "OpenAPI JSON Schema"

# Проверка версии OpenAPI
echo -e "\n📖 Проверка версии OpenAPI:"
VERSION=$(curl -s "$API_HOST/v3/api-docs" | python3 -c "import sys, json, base64; content=sys.stdin.read(); decoded=base64.b64decode(content).decode('utf-8'); data=json.loads(decoded); print(data.get('openapi', 'NOT FOUND'))" 2>/dev/null)
if [ "$VERSION" = "3.1.0" ]; then
    echo "✅ OpenAPI версия: $VERSION"
else
    echo "❌ Неверная версия OpenAPI: $VERSION"
fi

# Проверка Swagger UI
echo -e "\n🌐 Проверка Swagger UI:"
check_endpoint "$API_HOST/swagger-ui.html" "302" "Swagger UI Redirect"
check_endpoint "$API_HOST/swagger-ui/index.html" "200" "Swagger UI Interface"

# Проверка контроллеров
echo -e "\n🎮 Проверка основных контроллеров:"
check_endpoint "$API_HOST/api/health" "200" "Health Endpoint"

echo -e "\n✅ Все тесты завершены!"
echo "Swagger UI должен быть доступен: $API_HOST/swagger-ui.html" 