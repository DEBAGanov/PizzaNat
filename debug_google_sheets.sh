#!/bin/bash

echo "🔧 ДИАГНОСТИКА GOOGLE SHEETS DIMBOPIZZA"
echo "======================================="

echo ""
echo "📋 Текущая конфигурация:"
echo "   SPREADSHEET_ID: 1K_g-EGPQggu4aFv4bIPP6yE_raHyUhbhbhjdfdrlr6GYi-MTEJtu4"
echo "   SHEET_NAME: Лист1"
echo "   SERVICE_ACCOUNT: dimbopizza@dimbo-468117.iam.gserviceaccount.com"

echo ""
echo "🔗 Ссылка на таблицу:"
echo "   https://docs.google.com/spreadsheets/d/1K_g-EGPQgu4vkjklaFv4bIPP6987yE_raHyUrlr6GYi-MTEJtu4/edit"

echo ""
echo "⚠️  ВАЖНО! Убедитесь что:"
echo "   1. Таблица открыта по ссылке выше"
echo "   2. Service Account добавлен с правами 'Редактор':"
echo "      Email: dimbopizza@dimbo-468117.iam.gserviceaccount.com"
echo "   3. Лист называется 'Лист1' (не 'Dimbopizza')"

echo ""
echo "🚀 Запуск проверки..."

# Остановка контейнеров
echo "1. Остановка контейнеров..."
docker-compose down --remove-orphans > /dev/null 2>&1

# Пересборка образа
echo "2. Пересборка образа с новыми credentials..."
docker-compose build --no-cache > /dev/null 2>&1

# Запуск
echo "3. Запуск приложения..."
docker-compose up -d

# Ожидание инициализации
echo "4. Ожидание инициализации (30 сек)..."
sleep 30

# Проверка статуса
echo "5. Проверка статуса контейнера..."
docker-compose ps

echo ""
echo "6. Проверка логов Google Sheets (последние 20 строк)..."
docker-compose logs app 2>/dev/null | grep -i "google\|sheets\|credentials" | tail -20

echo ""
echo "🔍 Проверка health check..."
curl -s http://localhost:8080/actuator/health | jq . 2>/dev/null || echo "API недоступен"

echo ""
echo "✅ Диагностика завершена!"
echo ""
echo "Если есть ошибки - запустите полные логи:"
echo "   docker-compose logs app"