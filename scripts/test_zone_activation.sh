#!/bin/bash

echo "🚀 АКТИВАЦИЯ И ТЕСТИРОВАНИЕ ЗОНАЛЬНОЙ СИСТЕМЫ ДОСТАВКИ"
echo "======================================================"

BASE_URL="http://localhost:8080"
GREEN='\033[0;32m'
RED='\033[0;31m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
CYAN='\033[0;36m'
NC='\033[0m'

# Функция для проверки API
check_api() {
    local url=$1
    local description=$2
    local expected_zone=$3
    local expected_cost=$4

    echo -e "\n${BLUE}🧪 ТЕСТ: ${description}${NC}"
    
    # Выполняем запрос
    local response=$(curl -s -G "${BASE_URL}/api/v1/delivery/estimate" \
        --data-urlencode "address=${url}" \
        --data-urlencode "orderAmount=500" \
        --connect-timeout 10 \
        --max-time 30 2>/dev/null)
    
    local curl_exit_code=$?
    
    if [ $curl_exit_code -eq 0 ] && [ -n "$response" ]; then
        # Проверяем JSON
        if echo "$response" | jq empty 2>/dev/null; then
            local zone_name=$(echo "$response" | jq -r '.zoneName // "unknown"')
            local delivery_cost=$(echo "$response" | jq -r '.deliveryCost // "null"')
            local city=$(echo "$response" | jq -r '.city // "unknown"')
            local delivery_available=$(echo "$response" | jq -r '.deliveryAvailable // false')
            
            echo "   📍 Зона: $zone_name"
            echo "   💰 Стоимость: ${delivery_cost} ₽"
            echo "   🏙️ Город: $city"
            echo "   ✅ Доступна: $delivery_available"
            
            # Проверяем результат
            if [ "$zone_name" = "$expected_zone" ] && [ "$delivery_cost" = "$expected_cost" ]; then
                echo -e "   ${GREEN}✅ УСПЕХ: Зональная система работает корректно!${NC}"
                return 0
            elif [ "$zone_name" = "Стандартная зона" ]; then
                echo -e "   ${YELLOW}⚠️  FALLBACK: Используется стандартная зона (зональная система неактивна)${NC}"
                return 1
            else
                echo -e "   ${YELLOW}⚠️  ЧАСТИЧНО: Зона определена, но стоимость отличается${NC}"
                echo "   📋 Ожидалось: $expected_zone, $expected_cost ₽"
                return 2
            fi
        else
            echo -e "   ${RED}❌ ОШИБКА: Некорректный JSON ответ${NC}"
            echo "   📋 Ответ: $response"
            return 3
        fi
    else
        echo -e "   ${RED}❌ ОШИБКА: API недоступен (код: $curl_exit_code)${NC}"
        return 4
    fi
}

# Проверка доступности API
echo -e "${BLUE}1. ПРОВЕРКА ДОСТУПНОСТИ API${NC}"
if curl -s "${BASE_URL}/api/v1/health" > /dev/null; then
    echo -e "${GREEN}✅ API доступен${NC}"
else
    echo -e "${RED}❌ API недоступен! Убедитесь, что приложение запущено.${NC}"
    exit 1
fi

# Проверка базы данных
echo -e "\n${BLUE}2. ПРОВЕРКА БАЗЫ ДАННЫХ${NC}"
if docker exec pizzanat-postgres-dev psql -U pizzanat_user -d pizzanat_db -c "SELECT COUNT(*) FROM delivery_zones WHERE is_active = true;" > /dev/null 2>&1; then
    ACTIVE_ZONES=$(docker exec pizzanat-postgres-dev psql -U pizzanat_user -d pizzanat_db -t -c "SELECT COUNT(*) FROM delivery_zones WHERE is_active = true;" | xargs)
    echo -e "${GREEN}✅ База данных доступна${NC}"
    echo -e "${CYAN}📊 Активных зон в БД: ${ACTIVE_ZONES}${NC}"
    
    if [ "$ACTIVE_ZONES" -gt 0 ]; then
        echo -e "${GREEN}✅ Зоны доставки настроены${NC}"
    else
        echo -e "${RED}❌ Нет активных зон доставки в БД${NC}"
        exit 1
    fi
else
    echo -e "${RED}❌ База данных недоступна${NC}"
    exit 1
fi

# Тестирование зональной системы
echo -e "\n${BLUE}3. ТЕСТИРОВАНИЕ ЗОНАЛЬНОЙ СИСТЕМЫ${NC}"
echo "================================================"

# Тест 1: Дружба (должно быть 100₽)
check_api "улица Дружбы, 5" "Район Дружба (самый дешевый)" "Дружба" "100"
RESULT_DRUZHBA=$?

# Тест 2: Центральный (должно быть 200₽)  
check_api "улица Ленина, 15" "Центральный район" "Центральный" "200"
RESULT_CENTER=$?

# Тест 3: Заря (должно быть 250₽)
check_api "улица Заря, 67" "Район Заря" "Заря" "250"
RESULT_ZARYA=$?

# Тест 4: Промузел (должно быть 300₽)
check_api "Промышленная улица, 1" "Промузел (самый дорогой)" "Промузел" "300"
RESULT_PROMUZEL=$?

# Тест бесплатной доставки в Дружбе
echo -e "\n${CYAN}🎁 ТЕСТ БЕСПЛАТНОЙ ДОСТАВКИ${NC}"
check_api_free() {
    local address=$1
    local amount=$2
    local expected_cost=$3
    
    echo -e "\n${BLUE}🧪 ТЕСТ: Бесплатная доставка ${address} (сумма: ${amount}₽)${NC}"
    
    local response=$(curl -s -G "${BASE_URL}/api/v1/delivery/estimate" \
        --data-urlencode "address=${address}" \
        --data-urlencode "orderAmount=${amount}" 2>/dev/null)
    
    if echo "$response" | jq empty 2>/dev/null; then
        local delivery_cost=$(echo "$response" | jq -r '.deliveryCost // "null"')
        local is_free=$(echo "$response" | jq -r '.isDeliveryFree // false')
        local zone_name=$(echo "$response" | jq -r '.zoneName // "unknown"')
        
        echo "   📍 Зона: $zone_name"
        echo "   💰 Стоимость: ${delivery_cost} ₽"
        echo "   🎁 Бесплатная: $is_free"
        
        if [ "$delivery_cost" = "$expected_cost" ]; then
            echo -e "   ${GREEN}✅ УСПЕХ: Бесплатная доставка работает корректно!${NC}"
            return 0
        else
            echo -e "   ${YELLOW}⚠️  Ожидалось: ${expected_cost} ₽${NC}"
            return 1
        fi
    else
        echo -e "   ${RED}❌ ОШИБКА: Некорректный ответ${NC}"
        return 2
    fi
}

check_api_free "улица Дружбы, 5" "900" "0"  # Дружба: бесплатно от 800₽
RESULT_FREE=$?

# Итоговая статистика
echo -e "\n${BLUE}📊 ИТОГОВЫЕ РЕЗУЛЬТАТЫ${NC}"
echo "========================="

TOTAL_TESTS=5
PASSED_TESTS=0

[ $RESULT_DRUZHBA -eq 0 ] && PASSED_TESTS=$((PASSED_TESTS + 1))
[ $RESULT_CENTER -eq 0 ] && PASSED_TESTS=$((PASSED_TESTS + 1))
[ $RESULT_ZARYA -eq 0 ] && PASSED_TESTS=$((PASSED_TESTS + 1))
[ $RESULT_PROMUZEL -eq 0 ] && PASSED_TESTS=$((PASSED_TESTS + 1))
[ $RESULT_FREE -eq 0 ] && PASSED_TESTS=$((PASSED_TESTS + 1))

echo -e "Пройдено тестов: ${GREEN}${PASSED_TESTS}/${TOTAL_TESTS}${NC}"

if [ $PASSED_TESTS -eq $TOTAL_TESTS ]; then
    echo -e "\n${GREEN}🎉 ЗОНАЛЬНАЯ СИСТЕМА ПОЛНОСТЬЮ АКТИВНА!${NC}"
    echo -e "${GREEN}✅ Все районы Волжска работают с правильными тарифами${NC}"
    echo -e "${GREEN}✅ Дифференцированное ценообразование: 100₽-300₽${NC}"
    echo -e "${GREEN}✅ Бесплатная доставка по порогам: 800₽-1500₽${NC}"
elif [ $PASSED_TESTS -gt 0 ]; then
    echo -e "\n${YELLOW}⚠️  ЗОНАЛЬНАЯ СИСТЕМА ЧАСТИЧНО АКТИВНА${NC}"
    echo -e "${YELLOW}📋 Некоторые зоны работают, другие используют fallback${NC}"
    
    # Показываем статус каждого теста
    [ $RESULT_DRUZHBA -ne 0 ] && echo -e "${YELLOW}   • Дружба: fallback или ошибка${NC}"
    [ $RESULT_CENTER -ne 0 ] && echo -e "${YELLOW}   • Центральный: fallback или ошибка${NC}"
    [ $RESULT_ZARYA -ne 0 ] && echo -e "${YELLOW}   • Заря: fallback или ошибка${NC}"
    [ $RESULT_PROMUZEL -ne 0 ] && echo -e "${YELLOW}   • Промузел: fallback или ошибка${NC}"
    [ $RESULT_FREE -ne 0 ] && echo -e "${YELLOW}   • Бесплатная доставка: ошибка${NC}"
else
    echo -e "\n${RED}❌ ЗОНАЛЬНАЯ СИСТЕМА НЕ АКТИВНА${NC}"
    echo -e "${RED}📋 Все тесты используют fallback 'Стандартная зона'${NC}"
    echo -e "\n${YELLOW}🔧 ВОЗМОЖНЫЕ ПРИЧИНЫ:${NC}"
    echo -e "${YELLOW}   1. Неправильные настройки подключения к БД${NC}"
    echo -e "${YELLOW}   2. Ошибки в логике сопоставления адресов${NC}"
    echo -e "${YELLOW}   3. Проблемы с миграциями базы данных${NC}"
    echo -e "${YELLOW}   4. Исключения в коде зональной системы${NC}"
fi

echo -e "\n${BLUE}🔍 ДИАГНОСТИЧЕСКАЯ ИНФОРМАЦИЯ${NC}"
echo "================================"
echo -e "${CYAN}📋 Проверьте логи приложения:${NC} tail -50 app.log | grep -E '(ERROR|Exception|DeliveryZone)'"
echo -e "${CYAN}📋 Проверьте подключение к БД:${NC} docker exec pizzanat-postgres-dev psql -U pizzanat_user -d pizzanat_db -c 'SELECT COUNT(*) FROM delivery_zones;'"
echo -e "${CYAN}📋 Перезапустите приложение:${NC} pkill java && java -jar build/libs/pizzanat-1.0.0.jar --spring.profiles.active=dev"

exit 0 