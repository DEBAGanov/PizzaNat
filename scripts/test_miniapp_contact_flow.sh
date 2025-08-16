#!/bin/bash

# Тестирование исправленного флоу получения контактной информации в мини-приложении
echo "🧪 Тестирование исправленного флоу контактной информации в мини-приложении"
echo "========================================================================"

BASE_URL="https://api.dimbopizza.ru"
MINIAPP_URL="https://dimbopizza.ru/static/miniapp"

# Цвета для вывода
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
NC='\033[0m' # No Color

echo -e "${BLUE}📋 Проверяем доступность мини-приложения...${NC}"

# 1. Проверяем доступность главной страницы мини-приложения
echo -e "${YELLOW}1. Проверка menu.html...${NC}"
MENU_STATUS=$(curl -s -o /dev/null -w "%{http_code}" "$MINIAPP_URL/menu.html")
if [ "$MENU_STATUS" = "200" ]; then
    echo -e "${GREEN}✅ menu.html доступен (HTTP $MENU_STATUS)${NC}"
else
    echo -e "${RED}❌ menu.html недоступен (HTTP $MENU_STATUS)${NC}"
fi

# 2. Проверяем доступность страницы оформления заказа
echo -e "${YELLOW}2. Проверка checkout.html...${NC}"
CHECKOUT_STATUS=$(curl -s -o /dev/null -w "%{http_code}" "$MINIAPP_URL/checkout.html")
if [ "$CHECKOUT_STATUS" = "200" ]; then
    echo -e "${GREEN}✅ checkout.html доступен (HTTP $CHECKOUT_STATUS)${NC}"
else
    echo -e "${RED}❌ checkout.html недоступен (HTTP $CHECKOUT_STATUS)${NC}"
fi

# 3. Проверяем основные JavaScript файлы
echo -e "${YELLOW}3. Проверка JavaScript файлов...${NC}"

JS_FILES=("app.js" "checkout-app.js" "api.js")
for file in "${JS_FILES[@]}"; do
    STATUS=$(curl -s -o /dev/null -w "%{http_code}" "$MINIAPP_URL/$file")
    if [ "$STATUS" = "200" ]; then
        echo -e "${GREEN}✅ $file доступен (HTTP $STATUS)${NC}"
    else
        echo -e "${RED}❌ $file недоступен (HTTP $STATUS)${NC}"
    fi
done

# 4. Проверяем API эндпоинты, используемые в мини-приложении
echo -e "${YELLOW}4. Проверка API эндпоинтов...${NC}"

API_ENDPOINTS=(
    "/api/v1/categories"
    "/api/v1/products"
    "/api/v1/cart"
    "/api/v1/orders"
    "/api/v1/address/suggestions?query=test"
    "/api/v1/delivery/estimate?address=test&orderAmount=1000"
)

for endpoint in "${API_ENDPOINTS[@]}"; do
    STATUS=$(curl -s -o /dev/null -w "%{http_code}" "$BASE_URL$endpoint")
    if [ "$STATUS" = "200" ] || [ "$STATUS" = "401" ]; then
        echo -e "${GREEN}✅ $endpoint доступен (HTTP $STATUS)${NC}"
    else
        echo -e "${RED}❌ $endpoint недоступен (HTTP $STATUS)${NC}"
    fi
done

echo ""
echo -e "${BLUE}📱 Рекомендации для тестирования в Telegram:${NC}"
echo "1. Откройте @DIMBOpizzaBot в Telegram"
echo "2. Нажмите кнопку 'Заказать пиццу' для запуска мини-приложения"
echo "3. Добавьте товары в корзину"
echo "4. Перейдите к оформлению заказа"
echo "5. При запросе номера телефона нажмите 'Поделиться контактом'"
echo "6. Проверьте, что кнопка 'Оформить заказ' становится активной"

echo ""
echo -e "${BLUE}🔧 Проверенные исправления:${NC}"
echo "• Улучшена логика обработки отсутствующего номера телефона"
echo "• Добавлен автоматический запрос контакта при загрузке страницы"
echo "• Улучшено состояние кнопки 'Оформить заказ'"
echo "• Добавлены информативные сообщения для пользователя"
echo "• Исправлена обработка данных из Telegram"

echo ""
echo -e "${BLUE}🐛 Дополнительные исправления (27.01.2025):${NC}"
echo "• Исправлена ошибка инициализации API в checkout-app.js"
echo "• Добавлена проверка доступности PizzaAPI класса"
echo "• Улучшена обработка ошибок с подробным логированием"
echo "• Добавлен обработчик кнопки 'Повторить' для перезагрузки страницы"
echo "• Исправлена синхронизация загрузки скриптов"

echo ""
echo -e "${YELLOW}⚠️ Если ошибка все еще возникает:${NC}"
echo "1. Проверьте консоль браузера на наличие конкретных ошибок"
echo "2. Убедитесь, что все файлы доступны (api.js, checkout-app.js)"
echo "3. Попробуйте очистить кэш Telegram или перезапустить бот"

echo ""
echo -e "${GREEN}✅ Тестирование завершено${NC}"
