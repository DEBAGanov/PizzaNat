#!/bin/bash

# Быстрое исправление проблемы CORS для PizzaNat
# Применяет все необходимые изменения и перезапускает сервисы

set -e

# Цвета для вывода
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
NC='\033[0m' # No Color

echo -e "${BLUE}🔧 ИСПРАВЛЕНИЕ ПРОБЛЕМЫ CORS${NC}"
echo "================================="
echo

echo -e "${YELLOW}📋 Шаг 1: Остановка текущих контейнеров${NC}"
docker-compose down
echo -e "${GREEN}✅ Контейнеры остановлены${NC}"
echo

echo -e "${YELLOW}📋 Шаг 2: Сборка и запуск с обновленной конфигурацией${NC}"
if [[ -f "docker-compose.production.yml" ]]; then
    echo "Используется production конфигурация..."
    docker-compose -f docker-compose.production.yml up -d --build
else
    echo "Используется стандартная конфигурация..."
    docker-compose up -d --build
fi
echo -e "${GREEN}✅ Сервисы запущены${NC}"
echo

echo -e "${YELLOW}📋 Шаг 3: Ожидание готовности сервисов${NC}"
sleep 30

echo -e "${YELLOW}📋 Шаг 4: Проверка статуса контейнеров${NC}"
docker-compose ps
echo

echo -e "${YELLOW}📋 Шаг 5: Проверка логов nginx${NC}"
if docker ps | grep -q nginx; then
    echo "Логи nginx:"
    docker-compose logs --tail=20 nginx || echo "Nginx логи недоступны"
else
    echo "⚠️  Nginx контейнер не найден, проверяем логи приложения:"
    docker-compose logs --tail=20 pizzanat-app || echo "Логи приложения недоступны"
fi
echo

echo -e "${YELLOW}📋 Шаг 6: Тестирование CORS${NC}"
echo "Проверяем доступность API..."

# Простая проверка API
if curl -s "http://localhost:8080/api/v1/health" >/dev/null 2>&1; then
    echo -e "${GREEN}✅ API доступен на localhost:8080${NC}"
    
    # Тестируем CORS
    echo "Тестируем CORS заголовки..."
    cors_test=$(curl -s -I \
        -H "Origin: https://pizzanat.ru" \
        "http://localhost:8080/api/v1/health" | \
        grep -i "access-control-allow-origin" || echo "")
    
    if [[ -n "$cors_test" ]]; then
        echo -e "${GREEN}✅ CORS заголовки найдены: $cors_test${NC}"
    else
        echo -e "${YELLOW}⚠️  CORS заголовки не найдены в простом запросе${NC}"
        echo "Это нормально, проверим preflight запрос..."
        
        preflight_test=$(curl -s -I \
            -X OPTIONS \
            -H "Origin: https://pizzanat.ru" \
            -H "Access-Control-Request-Method: GET" \
            "http://localhost:8080/api/v1/health" | \
            grep -i "access-control-allow-origin" || echo "")
        
        if [[ -n "$preflight_test" ]]; then
            echo -e "${GREEN}✅ CORS preflight работает: $preflight_test${NC}"
        else
            echo -e "${RED}❌ CORS не настроен правильно${NC}"
        fi
    fi
else
    echo -e "${RED}❌ API недоступен${NC}"
    echo "Проверьте логи контейнеров:"
    docker-compose logs --tail=50
fi

echo
echo -e "${BLUE}🎯 ИТОГОВЫЙ СТАТУС${NC}"
echo "=================="

# Проверяем финальный статус
api_status="❌"
cors_status="❌"

if curl -s "http://localhost:8080/api/v1/health" >/dev/null 2>&1; then
    api_status="✅"
fi

cors_check=$(curl -s -I \
    -X OPTIONS \
    -H "Origin: https://pizzanat.ru" \
    -H "Access-Control-Request-Method: GET" \
    "http://localhost:8080/api/v1/health" 2>/dev/null | \
    grep -i "access-control-allow-origin" || echo "")

if [[ -n "$cors_check" ]]; then
    cors_status="✅"
fi

echo "API доступность: $api_status"
echo "CORS настройки: $cors_status"
echo

if [[ "$api_status" == "✅" && "$cors_status" == "✅" ]]; then
    echo -e "${GREEN}🎉 ПРОБЛЕМА CORS ИСПРАВЛЕНА!${NC}"
    echo -e "${GREEN}Теперь запросы от pizzanat.ru к API должны работать${NC}"
    echo
    echo -e "${BLUE}📋 Для дополнительного тестирования запустите:${NC}"
    echo "./scripts/diagnose_cors_issue.sh"
    echo "./scripts/test_cors_configuration.sh"
else
    echo -e "${RED}⚠️  ТРЕБУЕТСЯ ДОПОЛНИТЕЛЬНАЯ ДИАГНОСТИКА${NC}"
    echo
    echo -e "${YELLOW}Рекомендуемые действия:${NC}"
    echo "1. Проверьте логи: docker-compose logs"
    echo "2. Убедитесь, что nginx запущен: docker ps"
    echo "3. Запустите диагностику: ./scripts/diagnose_cors_issue.sh"
fi

echo 