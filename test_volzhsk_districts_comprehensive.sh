#!/bin/bash

echo "🗺️ КОМПЛЕКСНОЕ ТЕСТИРОВАНИЕ ЗОНАЛЬНОЙ СИСТЕМЫ ДОСТАВКИ ГОРОДА ВОЛЖСК"
echo "==========================================================================="

GREEN='\033[0;32m'
RED='\033[0;31m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
PURPLE='\033[0;35m'
CYAN='\033[0;36m'
WHITE='\033[1;37m'
NC='\033[0m'

BASE_URL="http://localhost:8080"
TOTAL_TESTS=0
PASSED_TESTS=0

# Функция для URL кодирования
urlencode() {
    local string="${1}"
    local strlen=${#string}
    local encoded=""
    local pos c o

    for (( pos=0 ; pos<strlen ; pos++ )); do
        c=${string:$pos:1}
        case "$c" in
            [-_.~a-zA-Z0-9] ) o="${c}" ;;
            * )               printf -v o '%%%02x' "'$c"
        esac
        encoded+="${o}"
    done
    echo "${encoded}"
}

# Функция для проведения теста
test_delivery_estimate() {
    local address="$1"
    local amount="$2"
    local expected_cost="$3"
    local expected_district="$4"
    local test_name="$5"

    TOTAL_TESTS=$((TOTAL_TESTS + 1))

    echo -e "\n${BLUE}🧪 ТЕСТ ${TOTAL_TESTS}: ${test_name}${NC}"
    echo "   📍 Адрес: $address"
    echo "   💰 Сумма заказа: $amount₽"
    echo "   🎯 Ожидаемый район: $expected_district"
    echo "   💸 Ожидаемая стоимость: $expected_cost₽"

    # URL кодирование адреса
    local encoded_address=$(urlencode "$address")

    # Выполнение запроса
    local response=$(curl -s -X GET "${BASE_URL}/api/v1/delivery/estimate" -G \
        --data-urlencode "address=${address}" \
        --data-urlencode "orderAmount=${amount}" \
        -H "Accept: application/json" \
        -H "Content-Type: application/json" \
        --connect-timeout 10 \
        --max-time 30 2>/dev/null)

    local curl_exit_code=$?

    # Отладочная информация
    echo "   🔍 Кодированный адрес: $encoded_address"

    if [ $curl_exit_code -eq 0 ] && [ -n "$response" ]; then
        # Проверка на ошибки в JSON
        if echo "$response" | jq empty 2>/dev/null; then
            # Парсинг ответа
            local delivery_cost=$(echo "$response" | jq -r '.deliveryCost // "null"')
            local district=$(echo "$response" | jq -r '.zoneName // "unknown"')
            local is_free=$(echo "$response" | jq -r '.isDeliveryFree // false')
            local delivery_available=$(echo "$response" | jq -r '.deliveryAvailable // false')

            echo "   📊 Результат:"
            echo "   📍 Определен район: $district"
            echo "   💰 Стоимость доставки: $delivery_cost₽"
            echo "   🎁 Бесплатная доставка: $is_free"
            echo "   ✅ Доставка доступна: $delivery_available"

            # Проверка результата
            if [ "$delivery_cost" = "$expected_cost" ]; then
                echo -e "   ${GREEN}✅ УСПЕХ: Стоимость доставки корректна${NC}"
                PASSED_TESTS=$((PASSED_TESTS + 1))
            else
                echo -e "   ${RED}❌ ОШИБКА: Ожидалось $expected_cost₽, получено $delivery_cost₽${NC}"
            fi
        else
            echo -e "   ${RED}❌ ОШИБКА: Некорректный JSON ответ${NC}"
            echo "   📋 Ответ сервера: $response"
        fi
    else
        echo -e "   ${RED}❌ ОШИБКА: Не удалось получить ответ от сервера (код: $curl_exit_code)${NC}"
        if [ -n "$response" ]; then
            echo "   📋 Ответ сервера: $response"
        fi
    fi
}

echo -e "${WHITE}📍 ТЕСТИРОВАНИЕ FALLBACK СИСТЕМЫ ДОСТАВКИ ВОЛЖСК${NC}"
echo "================================================================="
echo -e "${YELLOW}⚠️  ПРИМЕЧАНИЕ: Тестируем fallback систему 'Стандартная зона' (200₽, бесплатно от 1000₽)${NC}"

# ТЕСТИРОВАНИЕ FALLBACK СИСТЕМЫ (Стандартная зона: 200₽, бесплатно от 1000₽)
echo -e "\n${GREEN}🏛️ ТЕСТ ЦЕНТРАЛЬНЫХ УЛИЦ (FALLBACK 200₽)${NC}"
test_delivery_estimate "улица Ленина, 15" "500" "200" "Стандартная зона" "Главная улица города"
test_delivery_estimate "Советская улица, 22" "1200" "0" "Стандартная зона" "Бесплатная доставка в центре"
test_delivery_estimate "Комсомольская, 8" "999" "200" "Стандартная зона" "Граничная сумма"
test_delivery_estimate "Пушкина, 12" "800" "200" "Стандартная зона" "Улица поэта"

echo -e "\n${CYAN}🤝 ТЕСТ УЛИЦ РАЙОНА ДРУЖБА (FALLBACK 200₽)${NC}"
test_delivery_estimate "улица Дружбы, 5" "400" "200" "Стандартная зона" "Основная улица района"
test_delivery_estimate "Молодежная, 18" "1100" "0" "Стандартная зона" "Бесплатная доставка"
test_delivery_estimate "Пионерская, 7" "799" "200" "Стандартная зона" "Граничная сумма"
test_delivery_estimate "Спортивная, 11" "600" "200" "Стандартная зона" "Спортивная улица"

echo -e "\n${BLUE}🏭 ТЕСТ УЛИЦ МАШИНОСТРОИТЕЛЕЙ (FALLBACK 200₽)${NC}"
test_delivery_estimate "Машиностроителей, 45" "750" "200" "Стандартная зона" "Главная улица района"
test_delivery_estimate "2-я Машиностроителей, 12" "1100" "0" "Стандартная зона" "Бесплатная доставка"
test_delivery_estimate "Металлургов, 8" "950" "200" "Стандартная зона" "Металлурги"
test_delivery_estimate "Энтузиастов, 23" "1000" "0" "Стандартная зона" "Точно на пороге"

echo -e "\n${PURPLE}✈️ ТЕСТ АВИАЦИОННЫХ УЛИЦ (FALLBACK 200₽)${NC}"
test_delivery_estimate "Гагарина, 33" "650" "200" "Стандартная зона" "Космическая улица"
test_delivery_estimate "Чкалова, 17" "1300" "0" "Стандартная зона" "Авиационная улица"
test_delivery_estimate "Авиации, 9" "800" "200" "Стандартная зона" "Авиационный район"
test_delivery_estimate "Папанина, 14" "999" "200" "Стандартная зона" "Полярник"

echo -e "\n${WHITE}🌲 ТЕСТ СЕВЕРНЫХ УЛИЦ (FALLBACK 200₽)${NC}"
test_delivery_estimate "Северная, 28" "700" "200" "Стандартная зона" "Главная северная улица"
test_delivery_estimate "Лесная, 16" "1150" "0" "Стандартная зона" "Лесная зона"
test_delivery_estimate "Сосновая, 5" "880" "200" "Стандартная зона" "Хвойные улицы"
test_delivery_estimate "Горная, 21" "1000" "0" "Стандартная зона" "Горная местность"

echo -e "\n${YELLOW}⚡ ТЕСТ УЛИЦ ГОРГАЗА (FALLBACK 200₽)${NC}"
test_delivery_estimate "Кооперативная, 42" "550" "200" "Стандартная зона" "Кооператив"
test_delivery_estimate "Учительская, 19" "1250" "0" "Стандартная зона" "Педагогическая улица"
test_delivery_estimate "Тимирязева, 8" "920" "200" "Стандартная зона" "Ученый-аграрий"
test_delivery_estimate "Промбаза, 15" "999" "200" "Стандартная зона" "Промышленная база"

echo -e "\n${YELLOW}🌅 ТЕСТ УЛИЦ ЗАРИ (FALLBACK 200₽)${NC}"
test_delivery_estimate "Заря, 67" "600" "200" "Стандартная зона" "Главная улица района"
test_delivery_estimate "1-я Заринская, 34" "1300" "0" "Стандартная зона" "Бесплатная доставка"
test_delivery_estimate "Заречная, 11" "1100" "0" "Стандартная зона" "За рекой"
test_delivery_estimate "Зеленая, 25" "1199" "0" "Стандартная зона" "Граничная сумма"

# ТЕСТЫ ГРАНИЧНЫХ СЛУЧАЕВ
echo -e "\n${WHITE}🎯 ТЕСТЫ ГРАНИЧНЫХ СЛУЧАЕВ (FALLBACK 1000₽)${NC}"
echo "========================================="

test_delivery_estimate "Дружбы, 99" "1000" "0" "Стандартная зона" "Точно порог бесплатной доставки"
test_delivery_estimate "Дружбы, 99" "999" "200" "Стандартная зона" "На 1₽ меньше порога"
test_delivery_estimate "Ленина, 1" "1000" "0" "Стандартная зона" "Точно порог в центре"
test_delivery_estimate "Ленина, 1" "999" "200" "Стандартная зона" "На 1₽ меньше порога в центре"

# ТЕСТЫ НЕИЗВЕСТНЫХ АДРЕСОВ
echo -e "\n${YELLOW}❓ ТЕСТЫ НЕИЗВЕСТНЫХ АДРЕСОВ${NC}"
echo "======================================"

test_delivery_estimate "Неизвестная улица, 999" "500" "200" "Стандартная зона" "Fallback к стандартному тарифу"
test_delivery_estimate "Выдуманная, 123" "1100" "0" "Стандартная зона" "Неизвестный адрес с большой суммой"

# ИТОГОВЫЕ РЕЗУЛЬТАТЫ
echo -e "\n${WHITE}📊 ИТОГОВЫЕ РЕЗУЛЬТАТЫ ТЕСТИРОВАНИЯ${NC}"
echo "======================================="
echo -e "Всего тестов: ${WHITE}$TOTAL_TESTS${NC}"
echo -e "Успешных: ${GREEN}$PASSED_TESTS${NC}"
echo -e "Неудачных: ${RED}$((TOTAL_TESTS - PASSED_TESTS))${NC}"

if [ $PASSED_TESTS -eq $TOTAL_TESTS ]; then
    echo -e "\n${GREEN}🎉 ВСЕ ТЕСТЫ ПРОЙДЕНЫ УСПЕШНО!${NC}"
    echo -e "${GREEN}✅ Fallback система доставки Волжск работает корректно${NC}"
else
    echo -e "\n${YELLOW}⚠️  НЕКОТОРЫЕ ТЕСТЫ НЕ ПРОЙДЕНЫ${NC}"
    echo -e "${YELLOW}📋 Процент успешности: $(( PASSED_TESTS * 100 / TOTAL_TESTS ))%${NC}"
fi

echo -e "\n${BLUE}🏆 ТЕКУЩАЯ СИСТЕМА ДОСТАВКИ:${NC}"
echo "============================================="
echo -e "${GREEN}🚚 СТАНДАРТНАЯ ЗОНА:${NC} 200₽ (бесплатно от 1000₽)"
echo -e "${YELLOW}🗺️ ЗОНАЛЬНАЯ СИСТЕМА:${NC} В разработке (миграции не применены)"

echo -e "\n${WHITE}📞 Доставка работает: 09:00-22:00${NC}"
echo -e "${WHITE}🕐 Время доставки: 30-45 минут${NC}"
echo -e "${WHITE}🌍 Зона охвата: город Волжск, Республика Марий Эл${NC}"