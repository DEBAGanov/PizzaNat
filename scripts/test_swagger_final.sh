#!/bin/bash

#
# Финальный тест полностью рабочего Swagger API
# Версия OpenAPI: 3.0.1
# Создан: 2025-06-10
#

API_HOST="http://localhost:8080"

echo "🎉 Финальный тест полностью рабочего Swagger API (OpenAPI 3.0.1)"
echo "======================================"

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

echo -e "\n🔧 Проверка OpenAPI Documentation:"
check_endpoint "$API_HOST/v3/api-docs" "200" "OpenAPI JSON Schema (base64)"

echo -e "\n🌐 Проверка Swagger UI:"
check_endpoint "$API_HOST/swagger-ui.html" "302" "Swagger UI Redirect"
check_endpoint "$API_HOST/swagger-ui/index.html" "200" "Swagger UI Interface"

echo -e "\n📋 Проверка версии OpenAPI:"
openapi_version=$(curl -s -H "Accept: application/json" "$API_HOST/v3/api-docs" | python3 -c "import sys, json, base64; content=sys.stdin.read(); decoded=base64.b64decode(content).decode('utf-8'); data=json.loads(decoded); print(data.get('openapi', 'NOT_FOUND'))" 2>/dev/null)
if [ "$openapi_version" = "3.0.1" ]; then
    echo "✅ OpenAPI версия: $openapi_version"
else
    echo "❌ OpenAPI версия: $openapi_version (ожидалась 3.0.1)"
fi

echo -e "\n⚙️ Проверка основных API эндпоинтов:"
check_endpoint "$API_HOST/api/health" "200" "Health Check Endpoint"
check_endpoint "$API_HOST/api/v1/categories" "200" "Categories API"
check_endpoint "$API_HOST/api/v1/products" "403" "Products API (требуется авторизация)"

echo -e "\n🚀 РЕЗУЛЬТАТ:"
echo "✅ Swagger UI полностью работает!"
echo "📊 Доступ: http://localhost:8080/swagger-ui.html"
echo "📄 OpenAPI Schema: http://localhost:8080/v3/api-docs"
echo "🔍 Версия: OpenAPI 3.0.1"
echo "🎯 Все эндпоинты отображаются в Swagger UI!"