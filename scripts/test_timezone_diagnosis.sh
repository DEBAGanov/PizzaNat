#!/bin/bash

echo "🔍 Диагностика проблемы с временными зонами в PizzaNat"
echo "============================================================"

# Проверяем время в контейнере приложения
echo ""
echo "📅 Время в контейнере приложения:"
docker exec pizzanat-app sh -c "date && echo 'TZ env:' && echo \$TZ && echo 'Java timezone:' && java -XshowSettings:properties -version 2>&1 | grep timezone || echo 'Не найдено'"

# Проверяем время в базе данных
echo ""
echo "🗄️ Время в базе данных PostgreSQL:"
echo "SELECT NOW() as db_time, CURRENT_TIMESTAMP as current_timestamp;" | psql -h 45.10.41.59 -U gen_user -d default_db -p 5432 || echo "Ошибка подключения к БД"

# Проверяем настройки временной зоны в PostgreSQL
echo ""
echo "⚙️ Настройки временной зоны PostgreSQL:"
echo "SHOW timezone; SELECT version();" | psql -h 45.10.41.59 -U gen_user -d default_db -p 5432 || echo "Ошибка подключения к БД"

# Тестируем создание заказа
echo ""
echo "🛒 Тестируем создание заказа:"

# Сначала получаем JWT токен
echo "Получаем JWT токен..."
LOGIN_RESPONSE=$(curl -s -X POST "http://localhost:8080/api/v1/auth/login" \
  -H "Content-Type: application/json" \
  -d '{
    "username": "admin@pizzanat.com",
    "password": "admin123"
  }')

TOKEN=$(echo $LOGIN_RESPONSE | jq -r '.token')

if [ "$TOKEN" != "null" ] && [ "$TOKEN" != "" ]; then
    echo "✅ Токен получен: ${TOKEN:0:20}..."
    
    # Создаем тестовый заказ
    echo "Создаем тестовый заказ..."
    ORDER_RESPONSE=$(curl -s -X POST "http://localhost:8080/api/v1/orders" \
      -H "Authorization: Bearer $TOKEN" \
      -H "Content-Type: application/json" \
      -d '{
        "deliveryLocationId": 1,
        "contactName": "Тест Временной Зоны",
        "contactPhone": "+79818279564",
        "comment": "Тест временной зоны - ' $(date) '"
      }')
    
    echo "Ответ создания заказа:"
    echo $ORDER_RESPONSE | jq .
    
    ORDER_ID=$(echo $ORDER_RESPONSE | jq -r '.id')
    
    if [ "$ORDER_ID" != "null" ] && [ "$ORDER_ID" != "" ]; then
        echo "✅ Заказ создан с ID: $ORDER_ID"
        
        # Проверяем время создания в базе данных
        echo ""
        echo "🕐 Время создания заказа в базе данных:"
        echo "SELECT id, created_at, updated_at, created_at AT TIME ZONE 'UTC' as utc_time, created_at AT TIME ZONE 'Europe/Moscow' as moscow_time FROM orders WHERE id = $ORDER_ID;" | psql -h 45.10.41.59 -U gen_user -d default_db -p 5432 || echo "Ошибка запроса к БД"
        
        # Получаем заказ через API
        echo ""
        echo "📋 Получаем заказ через API:"
        API_ORDER=$(curl -s -X GET "http://localhost:8080/api/v1/orders/$ORDER_ID" \
          -H "Authorization: Bearer $TOKEN")
        
        echo $API_ORDER | jq .
        
        # Сравниваем времена
        echo ""
        echo "⏰ Сравнение времен:"
        echo "Текущее локальное время: $(date)"
        echo "Текущее UTC время: $(date -u)"
        
        CREATED_AT=$(echo $API_ORDER | jq -r '.createdAt')
        echo "Время создания из API: $CREATED_AT"
        
    else
        echo "❌ Ошибка создания заказа"
    fi
    
else
    echo "❌ Ошибка получения токена"
    echo "Ответ: $LOGIN_RESPONSE"
fi

echo ""
echo "🔧 Рекомендации по исправлению:"
echo "1. Добавить переменную окружения TZ=Europe/Moscow в docker-compose.yml"
echo "2. Настроить spring.jpa.properties.hibernate.jdbc.time_zone=Europe/Moscow"
echo "3. Проверить настройки временной зоны в PostgreSQL"
echo "4. Убедиться что все LocalDateTime.now() используют правильную зону" 