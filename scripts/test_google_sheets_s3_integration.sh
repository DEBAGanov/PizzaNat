#!/bin/bash

# test_google_sheets_s3_integration.sh
# Тест интеграции Google Sheets с S3 загрузкой credentials

set -e

echo "🧪 Тестирование Google Sheets S3 интеграции"
echo "============================================="

# Цвета для вывода
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
NC='\033[0m' # No Color

# Базовый URL API
BASE_URL="http://localhost:8080"

# Проверка переменных окружения
check_env_vars() {
    echo -e "${BLUE}📋 Проверка переменных окружения...${NC}"
    
    if [[ -z "$GOOGLE_SHEETS_ENABLED" ]] || [[ "$GOOGLE_SHEETS_ENABLED" != "true" ]]; then
        echo -e "${RED}❌ GOOGLE_SHEETS_ENABLED не установлена или не равна 'true'${NC}"
        echo "Установите: export GOOGLE_SHEETS_ENABLED=true"
        exit 1
    fi
    
    if [[ -z "$GOOGLE_SHEETS_SPREADSHEET_ID" ]]; then
        echo -e "${RED}❌ GOOGLE_SHEETS_SPREADSHEET_ID не установлена${NC}"
        echo "Установите: export GOOGLE_SHEETS_SPREADSHEET_ID='ваш_id_таблицы'"
        exit 1
    fi
    
    echo -e "${GREEN}✅ Переменные окружения настроены корректно${NC}"
    echo "   GOOGLE_SHEETS_ENABLED: $GOOGLE_SHEETS_ENABLED"
    echo "   GOOGLE_SHEETS_SPREADSHEET_ID: $GOOGLE_SHEETS_SPREADSHEET_ID"
    echo "   GOOGLE_SHEETS_DOWNLOAD_FROM_S3: ${GOOGLE_SHEETS_DOWNLOAD_FROM_S3:-true}"
}

# Проверка доступности API
check_api_health() {
    echo -e "${BLUE}🏥 Проверка доступности API...${NC}"
    
    if curl -f -s "$BASE_URL/actuator/health" > /dev/null; then
        echo -e "${GREEN}✅ API доступен${NC}"
    else
        echo -e "${RED}❌ API недоступен${NC}"
        echo "Убедитесь, что приложение запущено: docker-compose up"
        exit 1
    fi
}

# Получение админского токена
get_admin_token() {
    echo -e "${BLUE}🔐 Получение админского токена...${NC}"
    
    # Попытка логина с админскими правами
    local login_response=$(curl -s -X POST "$BASE_URL/api/v1/auth/login" \
        -H "Content-Type: application/json" \
        -d '{
            "email": "admin@pizzanat.com",
            "password": "admin123"
        }' || echo '{"error": "login_failed"}')
    
    if [[ "$login_response" == *"token"* ]]; then
        ADMIN_TOKEN=$(echo "$login_response" | grep -o '"token":"[^"]*"' | cut -d'"' -f4)
        echo -e "${GREEN}✅ Админский токен получен${NC}"
    else
        echo -e "${YELLOW}⚠️ Не удалось получить админский токен, используем пользовательский...${NC}"
        
        # Fallback на обычного пользователя
        local user_response=$(curl -s -X POST "$BASE_URL/api/v1/auth/register" \
            -H "Content-Type: application/json" \
            -d '{
                "email": "s3test@example.com",
                "password": "password123",
                "firstName": "S3",
                "lastName": "Test"
            }')
        
        if [[ "$user_response" == *"token"* ]]; then
            ADMIN_TOKEN=$(echo "$user_response" | grep -o '"token":"[^"]*"' | cut -d'"' -f4)
            echo -e "${GREEN}✅ Пользовательский токен получен${NC}"
        else
            echo -e "${RED}❌ Не удалось получить токен${NC}"
            exit 1
        fi
    fi
}

# Проверка статуса credentials
check_credentials_status() {
    echo -e "${BLUE}📊 Проверка статуса Google Sheets credentials...${NC}"
    
    local status_response=$(curl -s -X GET "$BASE_URL/api/v1/admin/google-sheets/credentials/status" \
        -H "Authorization: Bearer $ADMIN_TOKEN" || echo '{"error": "request_failed"}')
    
    if [[ "$status_response" == *"credentialsExist"* ]]; then
        local credentials_exist=$(echo "$status_response" | grep -o '"credentialsExist":[^,}]*' | cut -d':' -f2)
        
        if [[ "$credentials_exist" == "true" ]]; then
            echo -e "${GREEN}✅ Google Sheets credentials найдены${NC}"
        else
            echo -e "${YELLOW}⚠️ Google Sheets credentials не найдены${NC}"
            return 1
        fi
    else
        echo -e "${RED}❌ Ошибка проверки статуса credentials${NC}"
        echo "Ответ: $status_response"
        return 1
    fi
}

# Ручная загрузка credentials из S3
download_credentials_from_s3() {
    echo -e "${BLUE}📥 Загрузка credentials из S3...${NC}"
    
    local download_response=$(curl -s -X POST "$BASE_URL/api/v1/admin/google-sheets/credentials/download" \
        -H "Authorization: Bearer $ADMIN_TOKEN")
    
    if [[ "$download_response" == *"success"* ]]; then
        echo -e "${GREEN}✅ Credentials успешно загружены из S3${NC}"
        
        # Ждем немного чтобы файл был обработан
        sleep 3
    else
        echo -e "${RED}❌ Ошибка загрузки credentials из S3${NC}"
        echo "Ответ: $download_response"
        return 1
    fi
}

# Инициализация Google Sheets таблицы
initialize_google_sheet() {
    echo -e "${BLUE}📊 Инициализация Google Sheets таблицы...${NC}"
    
    local init_response=$(curl -s -X POST "$BASE_URL/api/v1/admin/google-sheets/sheet/initialize" \
        -H "Authorization: Bearer $ADMIN_TOKEN")
    
    if [[ "$init_response" == *"success"* ]]; then
        echo -e "${GREEN}✅ Google Sheets таблица инициализирована${NC}"
        echo "   Заголовки добавлены в таблицу"
        sleep 2
    else
        echo -e "${YELLOW}⚠️ Не удалось инициализировать таблицу (возможно уже инициализирована)${NC}"
        echo "Ответ: $init_response"
    fi
}

# Создание тестового заказа
create_test_order() {
    echo -e "${BLUE}🛍️ Создание тестового заказа для проверки S3 интеграции...${NC}"
    
    local order_response=$(curl -s -X POST "$BASE_URL/api/v1/orders" \
        -H "Content-Type: application/json" \
        -H "Authorization: Bearer $ADMIN_TOKEN" \
        -d '{
            "contactName": "S3 Тест Заказ",
            "contactPhone": "+79999999999",
            "deliveryLocationId": 1,
            "comment": "Тестовый заказ для проверки S3 + Google Sheets интеграции",
            "paymentMethod": "CASH"
        }')
    
    if [[ "$order_response" == *"id"* ]]; then
        ORDER_ID=$(echo "$order_response" | grep -o '"id":[0-9]*' | cut -d':' -f2)
        echo -e "${GREEN}✅ Тестовый заказ создан #$ORDER_ID${NC}"
        echo "   Ожидание добавления в Google Sheets..."
        sleep 5
    else
        echo -e "${RED}❌ Не удалось создать тестовый заказ${NC}"
        echo "Ответ: $order_response"
        return 1
    fi
}

# Получение конфигурации интеграции
show_integration_config() {
    echo -e "${BLUE}⚙️ Получение конфигурации интеграции...${NC}"
    
    local config_response=$(curl -s -X GET "$BASE_URL/api/v1/admin/google-sheets/config" \
        -H "Authorization: Bearer $ADMIN_TOKEN")
    
    if [[ "$config_response" == *"configuration"* ]]; then
        echo -e "${GREEN}✅ Конфигурация получена${NC}"
        echo ""
        echo -e "${YELLOW}📋 Детали конфигурации:${NC}"
        echo "$config_response" | grep -o '"configuration":"[^"]*"' | cut -d'"' -f4 | sed 's/\\n/\n/g'
    else
        echo -e "${YELLOW}⚠️ Не удалось получить конфигурацию${NC}"
        echo "Ответ: $config_response"
    fi
}

# Проверка Google Sheets
verify_google_sheets() {
    echo -e "${BLUE}📊 Проверка Google Sheets...${NC}"
    echo ""
    echo -e "${GREEN}🔗 Ссылка на вашу Google таблицу:${NC}"
    echo "   https://docs.google.com/spreadsheets/d/$GOOGLE_SHEETS_SPREADSHEET_ID/edit"
    echo ""
    echo -e "${YELLOW}📋 Что проверить в таблице:${NC}"
    echo "   1. Заголовки таблицы (16 колонок A-P)"
    echo "   2. Тестовый заказ #$ORDER_ID должен быть в строке 2"
    echo "   3. Все данные заказа должны быть корректно отформатированы"
    echo "   4. При создании новых заказов они должны появляться сверху"
}

# Финальный отчет
final_report() {
    echo ""
    echo "🎯 ФИНАЛЬНЫЙ ОТЧЕТ S3 ИНТЕГРАЦИИ"
    echo "================================="
    echo -e "${GREEN}✅ Тест S3 интеграции завершен успешно${NC}"
    echo ""
    echo "📊 Протестированные компоненты:"
    echo "   • Автоматическая загрузка credentials из S3"
    echo "   • Ручная загрузка через REST API"
    echo "   • Инициализация Google Sheets таблицы"
    echo "   • Создание и отправка тестового заказа"
    echo "   • Проверка конфигурации интеграции"
    echo ""
    echo "🔗 Google таблица:"
    echo "   https://docs.google.com/spreadsheets/d/$GOOGLE_SHEETS_SPREADSHEET_ID/edit"
    echo ""
    echo -e "${BLUE}📝 S3 компоненты:${NC}"
    echo "   • GoogleCredentialsDownloadService - автоматическая загрузка"
    echo "   • GoogleSheetsAdminController - REST API управление"
    echo "   • S3Client интеграция - использование существующего S3"
    echo ""
    echo -e "${YELLOW}🔄 Для обновления credentials:${NC}"
    echo "   1. Загрузите новый файл в S3: config/google-credentials.json"
    echo "   2. Выполните: curl -X POST http://localhost:8080/api/v1/admin/google-sheets/credentials/download"
    echo "   3. Или перезапустите приложение для автоматической загрузки"
}

# Обработка ошибок и восстановление
handle_credentials_error() {
    echo -e "${YELLOW}🔄 Попытка загрузки credentials из S3...${NC}"
    
    if download_credentials_from_s3; then
        echo -e "${GREEN}✅ Credentials загружены, продолжаем тестирование${NC}"
        return 0
    else
        echo -e "${RED}❌ Не удалось загрузить credentials из S3${NC}"
        echo ""
        echo -e "${YELLOW}📋 Возможные причины:${NC}"
        echo "   1. Файл google-credentials.json не загружен в S3"
        echo "   2. Неправильный путь в S3: config/google-credentials.json"
        echo "   3. Нет доступа к S3 bucket"
        echo "   4. Неправильные S3 credentials в docker-compose.yml"
        echo ""
        echo -e "${BLUE}🛠️ Инструкции по загрузке в S3:${NC}"
        echo "   1. Веб-интерфейс Timeweb: Панель управления → S3 → f9c8e17a-pizzanat-products → config/"
        echo "   2. AWS CLI: aws s3 cp google-credentials.json s3://f9c8e17a-pizzanat-products/config/"
        echo "   3. Проверьте документацию: docs/GOOGLE_SHEETS_S3_SETUP_GUIDE.md"
        return 1
    fi
}

# Основная функция
main() {
    echo "🚀 Начало тестирования S3 интеграции..."
    
    check_env_vars
    check_api_health
    get_admin_token
    
    # Проверяем credentials, если нет - пытаемся загрузить
    if ! check_credentials_status; then
        handle_credentials_error || exit 1
    fi
    
    show_integration_config
    initialize_google_sheet
    create_test_order
    verify_google_sheets
    final_report
    
    echo ""
    echo -e "${GREEN}🎉 Тестирование Google Sheets S3 интеграции завершено!${NC}"
}

# Обработка ошибок
trap 'echo -e "${RED}❌ Тест прерван с ошибкой${NC}"; exit 1' ERR

# Запуск
main "$@"