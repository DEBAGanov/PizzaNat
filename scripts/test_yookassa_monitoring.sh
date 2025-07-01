#!/bin/bash

# Скрипт тестирования системы мониторинга ЮKassa интеграции
# Автор: AI Assistant
# Дата: 26.01.2025

# Цвета для вывода
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
PURPLE='\033[0;35m'
CYAN='\033[0;36m'
NC='\033[0m' # No Color

# Настройки
BASE_URL="http://localhost:8080"
API_PREFIX="/api/v1"
METRICS_ENDPOINT="${API_PREFIX}/payments/metrics"

# Счетчики тестов
TOTAL_TESTS=0
PASSED_TESTS=0
FAILED_TESTS=0

# Функция логирования
log_info() {
    echo -e "${BLUE}[INFO]${NC} $1"
}

log_success() {
    echo -e "${GREEN}[SUCCESS]${NC} $1"
    ((PASSED_TESTS++))
}

log_error() {
    echo -e "${RED}[ERROR]${NC} $1"
    ((FAILED_TESTS++))
}

log_warning() {
    echo -e "${YELLOW}[WARNING]${NC} $1"
}

log_header() {
    echo -e "\n${PURPLE}=== $1 ===${NC}"
}

# Функция выполнения HTTP запроса
make_request() {
    local method=$1
    local endpoint=$2
    local expected_code=${3:-200}
    local auth_header=${4:-""}
    
    ((TOTAL_TESTS++))
    
    local curl_cmd="curl -s -w '%{http_code}' -X $method"
    if [[ -n "$auth_header" ]]; then
        curl_cmd="$curl_cmd -H 'Authorization: $auth_header'"
    fi
    curl_cmd="$curl_cmd -H 'Content-Type: application/json' $BASE_URL$endpoint"
    
    log_info "Выполняю: $method $endpoint"
    
    local response=$(eval $curl_cmd)
    local http_code="${response: -3}"
    local body="${response%???}"
    
    if [[ "$http_code" == "$expected_code" ]]; then
        log_success "HTTP $http_code - OK"
        echo "$body"
        return 0
    else
        log_error "HTTP $http_code (ожидался $expected_code)"
        echo "$body"
        return 1
    fi
}

# Функция проверки JSON структуры
check_json_field() {
    local json=$1
    local field=$2
    local description=$3
    
    ((TOTAL_TESTS++))
    
    if echo "$json" | jq -e ".$field" > /dev/null 2>&1; then
        log_success "$description - поле '$field' присутствует"
        return 0
    else
        log_error "$description - поле '$field' отсутствует"
        return 1
    fi
}

# Функция получения токена (заглушка для тестов)
get_admin_token() {
    # В реальном окружении здесь будет запрос на получение JWT токена
    echo "Bearer test-admin-token"
}

# Основная функция тестирования
main() {
    log_header "🔍 ТЕСТИРОВАНИЕ СИСТЕМЫ МОНИТОРИНГА YOOKASSA"
    echo "Базовый URL: $BASE_URL"
    echo "Дата: $(date '+%d.%m.%Y %H:%M:%S')"
    echo ""
    
    # Проверка доступности сервиса
    log_header "1. ПРОВЕРКА ДОСТУПНОСТИ СЕРВИСА"
    
    if make_request "GET" "/actuator/health" 200; then
        log_success "Сервис доступен"
    else
        log_error "Сервис недоступен"
        exit 1
    fi
    
    # Получение токена администратора
    log_header "2. АУТЕНТИФИКАЦИЯ"
    
    ADMIN_TOKEN=$(get_admin_token)
    log_info "Токен администратора получен: ${ADMIN_TOKEN:0:20}..."
    
    # Тестирование endpoint'ов мониторинга
    log_header "3. ТЕСТИРОВАНИЕ ENDPOINTS МОНИТОРИНГА"
    
    # 3.1 Health check системы метрик
    log_info "3.1 Проверка работоспособности системы метрик"
    health_response=$(make_request "GET" "$METRICS_ENDPOINT/health" 200)
    if [[ $? -eq 0 ]]; then
        check_json_field "$health_response" "status" "Health status"
        check_json_field "$health_response" "service" "Service name"
        check_json_field "$health_response" "timestamp" "Timestamp"
    fi
    
    # 3.2 Конфигурация мониторинга
    log_info "3.2 Получение конфигурации мониторинга"
    config_response=$(make_request "GET" "$METRICS_ENDPOINT/config" 200 "$ADMIN_TOKEN")
    if [[ $? -eq 0 ]]; then
        check_json_field "$config_response" "yookassa_enabled" "ЮKassa enabled status"
        check_json_field "$config_response" "metrics_enabled" "Metrics enabled status"
        check_json_field "$config_response" "monitoring" "Monitoring config"
        check_json_field "$config_response" "endpoints" "Endpoints list"
    fi
    
    # 3.3 Сводка метрик
    log_info "3.3 Получение сводки метрик платежей"
    summary_response=$(make_request "GET" "$METRICS_ENDPOINT/summary" 200 "$ADMIN_TOKEN")
    if [[ $? -eq 0 ]]; then
        check_json_field "$summary_response" "totalPayments" "Total payments"
        check_json_field "$summary_response" "successfulPayments" "Successful payments"
        check_json_field "$summary_response" "failedPayments" "Failed payments"
        check_json_field "$summary_response" "conversionRate" "Conversion rate"
        check_json_field "$summary_response" "totalAmount" "Total amount"
    fi
    
    # 3.4 Детальные метрики
    log_info "3.4 Получение детальных метрик"
    details_response=$(make_request "GET" "$METRICS_ENDPOINT/details" 200 "$ADMIN_TOKEN")
    if [[ $? -eq 0 ]]; then
        check_json_field "$details_response" "summary" "Summary data"
        check_json_field "$details_response" "metrics_collection" "Metrics collection info"
        check_json_field "$details_response" "performance" "Performance data"
    fi
    
    # 3.5 Обновление метрик
    log_info "3.5 Ручное обновление метрик"
    refresh_response=$(make_request "POST" "$METRICS_ENDPOINT/refresh" 200 "$ADMIN_TOKEN")
    if [[ $? -eq 0 ]]; then
        check_json_field "$refresh_response" "status" "Refresh status"
        check_json_field "$refresh_response" "message" "Refresh message"
    fi
    
    # Тестирование Prometheus метрик
    log_header "4. ТЕСТИРОВАНИЕ PROMETHEUS МЕТРИК"
    
    log_info "4.1 Проверка endpoint'а Prometheus"
    prometheus_response=$(make_request "GET" "/actuator/prometheus" 200)
    if [[ $? -eq 0 ]]; then
        # Проверяем наличие ключевых метрик ЮKassa
        if echo "$prometheus_response" | grep -q "yookassa_payments_total"; then
            log_success "Метрика yookassa_payments_total найдена"
            ((PASSED_TESTS++))
        else
            log_error "Метрика yookassa_payments_total не найдена"
            ((FAILED_TESTS++))
        fi
        ((TOTAL_TESTS++))
        
        if echo "$prometheus_response" | grep -q "yookassa_payments_success"; then
            log_success "Метрика yookassa_payments_success найдена"
            ((PASSED_TESTS++))
        else
            log_error "Метрика yookassa_payments_success не найдена"
            ((FAILED_TESTS++))
        fi
        ((TOTAL_TESTS++))
        
        if echo "$prometheus_response" | grep -q "yookassa_payments_failure"; then
            log_success "Метрика yookassa_payments_failure найдена"
            ((PASSED_TESTS++))
        else
            log_error "Метрика yookassa_payments_failure не найдена"
            ((FAILED_TESTS++))
        fi
        ((TOTAL_TESTS++))
    fi
    
    # Тестирование системы алертов
    log_header "5. ТЕСТИРОВАНИЕ СИСТЕМЫ АЛЕРТОВ"
    
    log_info "5.1 Проверка конфигурации алертов"
    if echo "$config_response" | jq -e ".monitoring.alert_thresholds" > /dev/null 2>&1; then
        log_success "Конфигурация алертов найдена"
        ((PASSED_TESTS++))
        
        # Проверяем пороговые значения
        low_conversion=$(echo "$config_response" | jq -r ".monitoring.alert_thresholds.low_conversion_rate")
        high_failure=$(echo "$config_response" | jq -r ".monitoring.alert_thresholds.high_failure_rate")
        max_response=$(echo "$config_response" | jq -r ".monitoring.alert_thresholds.max_response_time_ms")
        
        log_info "Пороги алертов: конверсия < $low_conversion%, ошибки > $high_failure%, время > ${max_response}ms"
    else
        log_error "Конфигурация алертов не найдена"
        ((FAILED_TESTS++))
    fi
    ((TOTAL_TESTS++))
    
    # Тестирование безопасности
    log_header "6. ТЕСТИРОВАНИЕ БЕЗОПАСНОСТИ"
    
    log_info "6.1 Проверка доступа без токена к защищенным endpoint'ам"
    unauthorized_response=$(make_request "GET" "$METRICS_ENDPOINT/summary" 401)
    if [[ $? -eq 0 ]]; then
        log_success "Защищенный endpoint корректно требует аутентификации"
    fi
    
    log_info "6.2 Проверка доступа с неверным токеном"
    invalid_token_response=$(make_request "GET" "$METRICS_ENDPOINT/summary" 401 "Bearer invalid-token")
    if [[ $? -eq 0 ]]; then
        log_success "Неверный токен корректно отклоняется"
    fi
    
    # Тестирование производительности
    log_header "7. ТЕСТИРОВАНИЕ ПРОИЗВОДИТЕЛЬНОСТИ"
    
    log_info "7.1 Измерение времени ответа health endpoint'а"
    start_time=$(date +%s%N)
    make_request "GET" "$METRICS_ENDPOINT/health" 200 > /dev/null
    end_time=$(date +%s%N)
    response_time=$(( (end_time - start_time) / 1000000 )) # в миллисекундах
    
    if [[ $response_time -lt 1000 ]]; then
        log_success "Время ответа health endpoint: ${response_time}ms (< 1000ms)"
        ((PASSED_TESTS++))
    else
        log_warning "Время ответа health endpoint: ${response_time}ms (> 1000ms)"
        ((FAILED_TESTS++))
    fi
    ((TOTAL_TESTS++))
    
    # Итоговая статистика
    log_header "📊 ИТОГОВАЯ СТАТИСТИКА"
    
    success_rate=$(( PASSED_TESTS * 100 / TOTAL_TESTS ))
    
    echo -e "${CYAN}Всего тестов:${NC} $TOTAL_TESTS"
    echo -e "${GREEN}Успешных:${NC} $PASSED_TESTS"
    echo -e "${RED}Неудачных:${NC} $FAILED_TESTS"
    echo -e "${YELLOW}Успешность:${NC} ${success_rate}%"
    echo ""
    
    # Определяем статус готовности
    if [[ $success_rate -ge 90 ]]; then
        echo -e "${GREEN}✅ СИСТЕМА МОНИТОРИНГА ГОТОВА К ПРОДАКШЕНУ${NC}"
        echo -e "${GREEN}Все критические компоненты работают корректно${NC}"
        exit_code=0
    elif [[ $success_rate -ge 70 ]]; then
        echo -e "${YELLOW}⚠️ СИСТЕМА МОНИТОРИНГА ТРЕБУЕТ ДОРАБОТКИ${NC}"
        echo -e "${YELLOW}Есть проблемы, которые нужно исправить${NC}"
        exit_code=1
    else
        echo -e "${RED}❌ СИСТЕМА МОНИТОРИНГА НЕ ГОТОВА${NC}"
        echo -e "${RED}Критические ошибки требуют немедленного исправления${NC}"
        exit_code=2
    fi
    
    # Рекомендации
    log_header "💡 РЕКОМЕНДАЦИИ"
    
    if [[ $FAILED_TESTS -gt 0 ]]; then
        echo "1. Проверьте логи приложения для диагностики ошибок"
        echo "2. Убедитесь, что ЮKassa интеграция включена (YOOKASSA_ENABLED=true)"
        echo "3. Проверьте корректность настроек аутентификации"
        echo "4. Убедитесь, что Micrometer и Actuator корректно настроены"
    fi
    
    if [[ $success_rate -ge 90 ]]; then
        echo "✅ Система мониторинга готова к активации в продакшене"
        echo "✅ Все метрики собираются корректно"
        echo "✅ Система алертов настроена"
        echo "✅ Безопасность обеспечена"
    fi
    
    echo ""
    echo "Для активации в продакшене выполните:"
    echo "1. ./scripts/activate_yookassa_production.sh"
    echo "2. Настройте мониторинг в Grafana/Prometheus"
    echo "3. Настройте Telegram уведомления"
    
    exit $exit_code
}

# Проверка зависимостей
check_dependencies() {
    if ! command -v curl &> /dev/null; then
        log_error "curl не установлен"
        exit 1
    fi
    
    if ! command -v jq &> /dev/null; then
        log_error "jq не установлен"
        exit 1
    fi
}

# Запуск
check_dependencies
main "$@" 