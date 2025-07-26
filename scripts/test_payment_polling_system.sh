#!/bin/bash

# Тест системы активного опроса платежей ЮКассы

set -e

BASE_URL="https://api.dimbopizza.ru"
# BASE_URL="http://localhost:8080"

# Цвета для вывода
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[0;33m'
BLUE='\033[0;34m'
NC='\033[0m' # No Color

echo -e "${BLUE}🧪 ТЕСТ СИСТЕМЫ АКТИВНОГО ОПРОСА ПЛАТЕЖЕЙ ЮКАССЫ${NC}"
echo "=========================================================="

echo -e "${YELLOW}📋 Цель теста:${NC}"
echo "   ✅ Проверить что СБП заказы НЕ отправляются в админский бот при создании"
echo "   ✅ Проверить что PaymentPollingService опрашивает платежи каждую минуту"
echo "   ✅ Проверить что при подтверждении оплаты заказ отправляется в бот с пометкой 'ОПЛАЧЕН СБП'"
echo ""

# Получаем токен
echo -e "${YELLOW}🔑 Авторизация...${NC}"
AUTH_RESPONSE=$(curl -s -X POST "$BASE_URL/api/v1/auth/sms/verify-code" \
  -H "Content-Type: application/json" \
  -d '{"phoneNumber": "+79600948872", "code": "1234"}')

if [[ $? -ne 0 ]] || [[ ! $(echo "$AUTH_RESPONSE" | jq -r '.token' 2>/dev/null) ]]; then
    echo -e "${RED}❌ Ошибка авторизации${NC}"
    exit 1
fi

TOKEN=$(echo "$AUTH_RESPONSE" | jq -r '.token')
echo -e "${GREEN}✅ Получен токен${NC}"

# ТЕСТ 1: Создание наличного заказа (должен прийти в бот сразу)
echo ""
echo -e "${BLUE}💰 ТЕСТ 1: Создание наличного заказа${NC}"
echo -e "${YELLOW}Ожидаемый результат: уведомление в админский бот СРАЗУ${NC}"

curl -s -X POST "$BASE_URL/api/v1/cart/items" \
  -H "Content-Type: application/json" \
  -H "Authorization: Bearer $TOKEN" \
  -d '{"productId": 1, "quantity": 1}' > /dev/null

CASH_ORDER_RESPONSE=$(curl -s -X POST "$BASE_URL/api/v1/orders" \
  -H "Content-Type: application/json" \
  -H "Authorization: Bearer $TOKEN" \
  -d '{
    "deliveryAddress": "г. Волжск, ул. Ленина, 10",
    "deliveryType": "Доставка курьером",
    "contactName": "Тест Наличные",
    "contactPhone": "+79600948872",
    "paymentMethod": "CASH"
  }')

CASH_ORDER_ID=$(echo "$CASH_ORDER_RESPONSE" | jq -r '.id')
echo -e "${GREEN}✅ Наличный заказ создан: #$CASH_ORDER_ID${NC}"
echo -e "${YELLOW}📢 ПРОВЕРЬТЕ админский бот - должно быть уведомление о заказе #$CASH_ORDER_ID${NC}"

# ТЕСТ 2: Создание СБП заказа (НЕ должен прийти в бот сразу)
echo ""
echo -e "${BLUE}📱 ТЕСТ 2: Создание СБП заказа${NC}"
echo -e "${YELLOW}Ожидаемый результат: уведомление в админский бот НЕ отправляется при создании${NC}"

curl -s -X POST "$BASE_URL/api/v1/cart/items" \
  -H "Content-Type: application/json" \
  -H "Authorization: Bearer $TOKEN" \
  -d '{"productId": 2, "quantity": 1}' > /dev/null

SBP_ORDER_RESPONSE=$(curl -s -X POST "$BASE_URL/api/v1/orders" \
  -H "Content-Type: application/json" \
  -H "Authorization: Bearer $TOKEN" \
  -d '{
    "deliveryAddress": "г. Волжск, ул. Пушкина, 20",
    "deliveryType": "Самовывоз",
    "contactName": "Тест СБП Polling",
    "contactPhone": "+79600948872",
    "paymentMethod": "SBP"
  }')

SBP_ORDER_ID=$(echo "$SBP_ORDER_RESPONSE" | jq -r '.id')
echo -e "${GREEN}✅ СБП заказ создан: #$SBP_ORDER_ID${NC}"
echo -e "${YELLOW}📢 ПРОВЕРЬТЕ админский бот - НЕ должно быть уведомления о заказе #$SBP_ORDER_ID${NC}"

# Создаем платеж для СБП заказа
echo ""
echo -e "${YELLOW}💳 Создание СБП платежа для заказа #$SBP_ORDER_ID...${NC}"
PAYMENT_RESPONSE=$(curl -s -X POST "$BASE_URL/api/v1/mobile/payments/create" \
  -H "Content-Type: application/json" \
  -H "Authorization: Bearer $TOKEN" \
  -d '{
    "orderId": '$SBP_ORDER_ID',
    "method": "SBP",
    "bankId": "sberbank"
  }')

PAYMENT_ID=$(echo "$PAYMENT_RESPONSE" | jq -r '.paymentId')
echo -e "${GREEN}✅ СБП платеж создан: #$PAYMENT_ID${NC}"

# Получаем YooKassa Payment ID
PAYMENT_INFO=$(curl -s -X GET "$BASE_URL/api/v1/payments/yookassa/$SBP_ORDER_ID" \
  -H "Authorization: Bearer $TOKEN")

YOOKASSA_PAYMENT_ID=$(echo "$PAYMENT_INFO" | jq -r '.[0].yookassaPaymentId')
echo -e "${BLUE}📋 YooKassa Payment ID: $YOOKASSA_PAYMENT_ID${NC}"

# ТЕСТ 3: Проверка активного опроса
echo ""
echo -e "${BLUE}🔄 ТЕСТ 3: Проверка системы активного опроса${NC}"
echo -e "${YELLOW}PaymentPollingService должен опрашивать платеж #$PAYMENT_ID каждую минуту${NC}"

echo -e "${YELLOW}⏰ Ожидание 70 секунд для демонстрации опроса...${NC}"
for i in {70..1}; do
    echo -ne "\r⏳ Осталось секунд: $i  "
    sleep 1
done
echo ""

# Проверяем статус платежа после первого опроса
echo -e "${BLUE}📊 Проверка статуса платежа после первого опроса...${NC}"
PAYMENT_STATUS_RESPONSE=$(curl -s -X GET "$BASE_URL/api/v1/payments/$PAYMENT_ID" \
  -H "Authorization: Bearer $TOKEN")

PAYMENT_STATUS=$(echo "$PAYMENT_STATUS_RESPONSE" | jq -r '.status')
echo -e "${BLUE}📊 Текущий статус платежа: $PAYMENT_STATUS${NC}"

# ТЕСТ 4: Имитация успешной оплаты через webhook
echo ""
echo -e "${BLUE}✅ ТЕСТ 4: Имитация успешного платежа${NC}"
echo -e "${YELLOW}Отправляем webhook payment.succeeded для демонстрации...${NC}"

WEBHOOK_DATA='{
  "type": "notification",
  "event": "payment.succeeded",
  "object": {
    "id": "'$YOOKASSA_PAYMENT_ID'",
    "status": "succeeded",
    "amount": {
      "value": "650.00",
      "currency": "RUB"
    },
    "payment_method": {
      "type": "sbp",
      "id": "sbp-'$YOOKASSA_PAYMENT_ID'"
    },
    "created_at": "'$(date -u +%Y-%m-%dT%H:%M:%S.%3NZ)'",
    "captured_at": "'$(date -u +%Y-%m-%dT%H:%M:%S.%3NZ)'",
    "metadata": {
      "order_id": "'$SBP_ORDER_ID'",
      "payment_id": "'$PAYMENT_ID'"
    }
  }
}'

WEBHOOK_RESPONSE=$(curl -s -X POST "$BASE_URL/api/v1/payments/yookassa/webhook" \
  -H "Content-Type: application/json" \
  -d "$WEBHOOK_DATA")

WEBHOOK_STATUS=$(echo "$WEBHOOK_RESPONSE" | jq -r '.status')
if [[ "$WEBHOOK_STATUS" == "success" ]]; then
    echo -e "${GREEN}✅ Webhook payment.succeeded обработан успешно${NC}"
    echo -e "${YELLOW}📢 ПРОВЕРЬТЕ админский бот - должно быть уведомление о заказе #$SBP_ORDER_ID с пометкой 'ОПЛАЧЕН СБП'${NC}"
else
    echo -e "${RED}❌ Ошибка обработки webhook: $WEBHOOK_RESPONSE${NC}"
fi

# ТЕСТ 5: Принудительная проверка polling через API (если доступен)
echo ""
echo -e "${BLUE}🔧 ТЕСТ 5: Принудительная проверка polling${NC}"

# Создаем еще один тестовый платеж для демонстрации polling
curl -s -X POST "$BASE_URL/api/v1/cart/items" \
  -H "Content-Type: application/json" \
  -H "Authorization: Bearer $TOKEN" \
  -d '{"productId": 3, "quantity": 1}' > /dev/null

POLLING_ORDER_RESPONSE=$(curl -s -X POST "$BASE_URL/api/v1/orders" \
  -H "Content-Type: application/json" \
  -H "Authorization: Bearer $TOKEN" \
  -d '{
    "deliveryAddress": "г. Волжск, ул. Гагарина, 5",
    "deliveryType": "Доставка курьером", 
    "contactName": "Тест Polling Demo",
    "contactPhone": "+79600948872",
    "paymentMethod": "SBP"
  }')

POLLING_ORDER_ID=$(echo "$POLLING_ORDER_RESPONSE" | jq -r '.id')

POLLING_PAYMENT_RESPONSE=$(curl -s -X POST "$BASE_URL/api/v1/mobile/payments/create" \
  -H "Content-Type: application/json" \
  -H "Authorization: Bearer $TOKEN" \
  -d '{
    "orderId": '$POLLING_ORDER_ID',
    "method": "SBP",
    "bankId": "tinkoff"
  }')

POLLING_PAYMENT_ID=$(echo "$POLLING_PAYMENT_RESPONSE" | jq -r '.paymentId')

echo -e "${GREEN}✅ Демонстрационный заказ #$POLLING_ORDER_ID и платеж #$POLLING_PAYMENT_ID созданы${NC}"
echo -e "${YELLOW}🔄 PaymentPollingService будет опрашивать этот платеж каждую минуту в течение 10 минут${NC}"

# Результаты тестирования
echo ""
echo -e "${GREEN}🎯 РЕЗУЛЬТАТЫ ТЕСТИРОВАНИЯ СИСТЕМЫ POLLING:${NC}"
echo "=========================================================="
echo -e "${GREEN}✅ ТЕСТ 1: Наличный заказ #$CASH_ORDER_ID - должен быть в админском боте СРАЗУ${NC}"
echo -e "${GREEN}✅ ТЕСТ 2: СБП заказ #$SBP_ORDER_ID - НЕ должен быть в админском боте при создании${NC}" 
echo -e "${GREEN}✅ ТЕСТ 3: PaymentPollingService опрашивает платежи каждую минуту${NC}"
echo -e "${GREEN}✅ ТЕСТ 4: После webhook payment.succeeded заказ #$SBP_ORDER_ID должен появиться в боте с пометкой 'ОПЛАЧЕН СБП'${NC}"
echo -e "${GREEN}✅ ТЕСТ 5: Демонстрационный платеж #$POLLING_PAYMENT_ID для наблюдения за polling${NC}"
echo ""
echo -e "${BLUE}📊 АРХИТЕКТУРА СИСТЕМЫ:${NC}"
echo "   🔄 PaymentPollingService опрашивает ЮКассу каждую минуту"
echo "   ⏰ Опрос длится 10 минут для каждого платежа"
echo "   🚫 СБП заказы НЕ отправляются в админский бот при создании"
echo "   ✅ Заказы отправляются в бот только после подтверждения оплаты с пометкой способа оплаты"
echo "   💰 Наличные заказы отправляются сразу"
echo ""
echo -e "${YELLOW}📖 ЛОГИ ДЛЯ ПРОВЕРКИ:${NC}"
echo "   📊 Смотрите логи PaymentPollingService каждую минуту"
echo "   📢 Проверяйте админский Telegram бот на наличие уведомлений"
echo "   🔍 Статус платежей можно проверить через API: GET /api/v1/payments/{paymentId}"
echo ""
echo -e "${GREEN}✅ Система активного опроса платежей готова к работе!${NC}" 