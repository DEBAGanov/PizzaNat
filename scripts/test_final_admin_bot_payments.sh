#!/bin/bash

echo "🎯 ФИНАЛЬНЫЙ ТЕСТ: Проверка интеграции платежей в админский бот"
echo "=============================================================="

BASE_URL="http://localhost:8080/api/v1"

# Получение admin токена
echo "🔑 Авторизация администратора..."
admin_auth_response=$(curl -s -X POST "$BASE_URL/auth/login" \
    -H "Content-Type: application/json" \
    -d '{"username":"admin","password":"admin123"}')

admin_token=$(echo "$admin_auth_response" | jq -r '.token // empty')

if [ -z "$admin_token" ]; then
    echo "❌ Не удалось получить токен администратора"
    exit 1
fi

echo "✅ Токен администратора получен"

# 1. Проверка активных заказов
echo ""
echo "📋 1. Проверка активных заказов через API"
active_orders_response=$(curl -s "$BASE_URL/admin/orders/active" \
    -H "Authorization: Bearer $admin_token")

# Проверяем, что ответ не содержит ошибку
if echo "$active_orders_response" | grep -q '"status":500'; then
    echo "❌ Ошибка 500 при получении активных заказов"
    echo "$active_orders_response"
    exit 1
fi

echo "✅ API активных заказов работает корректно"

# Показываем последние 3 активных заказа
echo "📊 Последние активные заказы:"
echo "$active_orders_response" | jq -r '.[0:3] | .[] | "• Заказ #\(.id) - \(.contactName) - \(.totalAmount) руб (\(.status))"'

# Получаем ID последнего заказа для тестирования
latest_order_id=$(echo "$active_orders_response" | jq -r '.[0].id // empty')

if [ -n "$latest_order_id" ]; then
    echo ""
    echo "🔍 2. Проверка деталей заказа #$latest_order_id"
    
    # Получение деталей заказа
    order_details_response=$(curl -s "$BASE_URL/admin/orders/$latest_order_id" \
        -H "Authorization: Bearer $admin_token")
    
    echo "📄 Детали заказа:"
    echo "$order_details_response" | jq -r '"Заказ: \(.contactName), Сумма: \(.totalAmount) руб, Статус: \(.status)"'
    
    echo ""
    echo "💰 3. Проверка платежей заказа #$latest_order_id"
    
    # Получение платежей заказа
    payments_response=$(curl -s "$BASE_URL/payments/yookassa/order/$latest_order_id" \
        -H "Authorization: Bearer $admin_token")
    
    # Проверяем наличие платежей
    payment_count=$(echo "$payments_response" | jq '. | length')
    
    if [ "$payment_count" -gt 0 ]; then
        echo "✅ Найдено $payment_count платеж(ей) для заказа #$latest_order_id"
        echo "💳 Список платежей:"
        echo "$payments_response" | jq -r '.[] | "• Платеж #\(.id) - \(.method) - \(.status) - \(.amount) руб"'
        
        # Показываем ссылки на проверку платежей
        echo ""
        echo "🔗 Ссылки для проверки платежей:"
        echo "$payments_response" | jq -r '.[] | select(.yookassaPaymentId != null) | "• https://yoomoney.ru/checkout/payments/v2/contract?orderId=\(.yookassaPaymentId)"'
        
        echo ""
        echo "🎉 РЕЗУЛЬТАТ: Заказ #$latest_order_id ИМЕЕТ платежи"
        echo "   В админском боте ДОЛЖНА отображаться информация о платежах"
        
    else
        echo "⚠️  Заказ #$latest_order_id НЕ имеет платежей (наличный заказ)"
        echo "   В админском боте должно отображаться 'Наличными'"
    fi
else
    echo "⚠️  Нет активных заказов для тестирования"
fi

echo ""
echo "🤖 ИНСТРУКЦИИ ДЛЯ ПРОВЕРКИ В TELEGRAM:"
echo "======================================"
echo "1. Откройте Telegram админский бот"
echo "2. Отправьте команду /orders для просмотра активных заказов"
echo "3. Найдите заказ #$latest_order_id в списке"
echo "4. Проверьте, что отображается:"
echo "   • Статус платежа (⏳ Ожидает оплаты, ✅ Оплачено, 💵 Наличными)"
echo "   • Способ оплаты (📱 СБП, 💳 Банковская карта, etc.)"
echo "   • Ссылка на проверку платежа (для онлайн платежей)"
echo "5. Отправьте команду /details $latest_order_id для просмотра деталей"
echo "6. Проверьте расширенную информацию о платежах"
echo ""
echo "✅ Если все отображается корректно - интеграция работает!"
echo "❌ Если показывает только 'Наличными' - нужна дополнительная диагностика" 