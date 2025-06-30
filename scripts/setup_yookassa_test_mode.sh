#!/bin/bash

# Скрипт настройки тестового режима ЮKassa для PizzaNat
# Использует реальные учетные данные для тестирования

set -e

echo "🔧 Настройка тестового режима ЮKassa..."

# Цвета для вывода
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
NC='\033[0m' # No Color

# Функция для логирования
log() {
    echo -e "${BLUE}[$(date +'%Y-%m-%d %H:%M:%S')]${NC} $1"
}

success() {
    echo -e "${GREEN}✅ $1${NC}"
}

warning() {
    echo -e "${YELLOW}⚠️  $1${NC}"
}

error() {
    echo -e "${RED}❌ $1${NC}"
}

# Проверяем наличие конфигурационного файла
if [ ! -f "env-yookassa-stage6.txt" ]; then
    error "Файл env-yookassa-stage6.txt не найден!"
    exit 1
fi

log "Применение тестовой конфигурации ЮKassa..."

# Создаем временный файл docker-compose с YooKassa переменными
cat > docker-compose.yookassa-test.yml << 'EOF'
version: '3.8'

services:
  app:
    environment:
      # ЮKassa тестовые настройки
      - YOOKASSA_ENABLED=true
      - YOOKASSA_SHOP_ID=1116141
      - YOOKASSA_SECRET_KEY=test_grCMbJSK95l5oz0pzlWrl1YeUJsDJusxy9MbxB_0AP0Y
      - YOOKASSA_API_URL=https://api.yookassa.ru/v3
      - YOOKASSA_WEBHOOK_URL=https://debaganov-pizzanat-0177.twc1.net/api/v1/payments/yookassa/webhook
      - YOOKASSA_RETURN_URL=pizzanat://payment/result
      
      # Таймауты
      - YOOKASSA_CONNECTION_TIMEOUT=5000
      - YOOKASSA_READ_TIMEOUT=10000
      - YOOKASSA_WRITE_TIMEOUT=10000
      - YOOKASSA_RETRY_MAX_ATTEMPTS=3
      - YOOKASSA_RETRY_DELAY=1000
      
      # СБП
      - SBP_ENABLED=true
      - SBP_DEFAULT_RETURN_URL=pizzanat://payment/result
      
      # Мониторинг
      - YOOKASSA_METRICS_ENABLED=true
      - YOOKASSA_METRICS_UPDATE_INTERVAL=60
      - YOOKASSA_METRICS_RETENTION_HOURS=24
      
      # Алерты
      - YOOKASSA_ALERTS_ENABLED=true
      - YOOKASSA_ALERTS_LOW_CONVERSION=70.0
      - YOOKASSA_ALERTS_HIGH_FAILURE=10.0
      - YOOKASSA_ALERTS_MAX_PENDING=30
      - YOOKASSA_ALERTS_COOLDOWN=30
      - YOOKASSA_ALERTS_MIN_PAYMENTS=5
      
      # Логирование
      - LOGGING_LEVEL_ROOT=INFO
      - LOGGING_LEVEL_YOOKASSA=DEBUG
EOF

log "Остановка контейнера для применения новой конфигурации..."
docker compose stop app

log "Запуск с тестовой конфигурацией ЮKassa..."
docker compose -f docker-compose.yml -f docker-compose.yookassa-test.yml up -d app

# Ждем запуска приложения
log "Ожидание запуска приложения..."
sleep 10

# Проверяем статус контейнера
if docker compose ps app | grep -q "Up"; then
    success "Контейнер app запущен"
else
    error "Контейнер app не запустился"
    docker compose logs --tail=20 app
    exit 1
fi

# Проверяем логи на наличие ошибок ЮKassa
log "Проверка логов ЮKassa..."
sleep 5

if docker compose logs app | grep -i "yookassa.*error\|yookassa.*exception" > /dev/null; then
    warning "Обнаружены ошибки в логах ЮKassa:"
    docker compose logs app | grep -i "yookassa.*error\|yookassa.*exception" | tail -5
else
    success "Ошибок ЮKassa в логах не обнаружено"
fi

# Проверяем инициализацию ЮKassa
if docker compose logs app | grep -i "yookassa.*webClient\|yookassa.*инициализация" > /dev/null; then
    success "ЮKassa WebClient успешно инициализирован"
else
    warning "Не найдены сообщения об инициализации ЮKassa"
fi

# Выводим информацию о тестовых картах
echo ""
echo "📋 Тестовые карты для проверки:"
echo "  💳 Успешная оплата: 5555555555554444"
echo "  💳 Отклоненная оплата: 4111111111111112"
echo "  💳 3DS аутентификация: 4000000000000002"
echo "  💳 Недостаточно средств: 4000000000000051"
echo "  📅 Срок действия: 12/30"
echo "  🔒 CVV: 123"

echo ""
echo "🔗 Полезные ссылки:"
echo "  📖 Документация тестирования: https://yookassa.ru/docs/support/merchant/payments/implement/test-store"
echo "  💳 Тестовые данные карт: https://yookassa.ru/developers/payment-acceptance/testing-and-going-live/testing#test-bank-card-data"
echo "  🔑 Секретные ключи: https://yookassa.ru/docs/support/merchant/payouts/secret-key"

echo ""
echo "🚀 API эндпоинты для тестирования:"
echo "  📱 Мобильные платежи: POST http://localhost:8080/api/v1/mobile/payments/create"
echo "  📊 Метрики платежей: GET http://localhost:8080/api/v1/payments/metrics"
echo "  🔔 Webhook: POST https://debaganov-pizzanat-0177.twc1.net/api/v1/payments/yookassa/webhook"

success "Тестовый режим ЮKassa настроен и активирован!"

# Очистка временного файла
rm -f docker-compose.yookassa-test.yml

log "Для просмотра логов используйте: docker compose logs -f app"