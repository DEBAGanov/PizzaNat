#!/bin/bash

# Диагностика ошибок в checkout странице мини-приложения
echo "🔍 Диагностика ошибок checkout страницы"
echo "======================================"

BASE_URL="https://dimbopizza.ru/static/miniapp"

# Цвета для вывода
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
NC='\033[0m' # No Color

echo -e "${BLUE}1. Проверка доступности файлов checkout...${NC}"

# Проверяем основные файлы
FILES=("checkout.html" "checkout-app.js" "checkout-styles.css" "api.js" "styles.css")

for file in "${FILES[@]}"; do
    echo -n "Проверяем $file... "
    STATUS=$(curl -s -o /dev/null -w "%{http_code}" "$BASE_URL/$file")
    if [ "$STATUS" = "200" ]; then
        echo -e "${GREEN}✅ OK (HTTP $STATUS)${NC}"
    else
        echo -e "${RED}❌ ОШИБКА (HTTP $STATUS)${NC}"
    fi
done

echo ""
echo -e "${BLUE}2. Проверка синтаксиса checkout.html...${NC}"

# Скачиваем и проверяем HTML
TEMP_HTML=$(mktemp)
curl -s "$BASE_URL/checkout.html" > "$TEMP_HTML"

if [ -s "$TEMP_HTML" ]; then
    echo -e "${GREEN}✅ HTML файл загружен${NC}"
    
    # Проверяем наличие ключевых элементов
    REQUIRED_IDS=("loading-screen" "app" "order-items" "submit-order" "user-name" "user-phone" "items-total" "delivery-cost" "total-amount" "final-total")
    
    echo "Проверяем наличие обязательных элементов:"
    for id in "${REQUIRED_IDS[@]}"; do
        if grep -q "id=\"$id\"" "$TEMP_HTML"; then
            echo -e "  ${GREEN}✅ $id найден${NC}"
        else
            echo -e "  ${RED}❌ $id отсутствует${NC}"
        fi
    done
else
    echo -e "${RED}❌ Не удалось загрузить HTML файл${NC}"
fi

rm -f "$TEMP_HTML"

echo ""
echo -e "${BLUE}3. Проверка загрузки скриптов...${NC}"

# Проверяем JavaScript файлы
JS_FILES=("api.js" "checkout-app.js")

for file in "${JS_FILES[@]}"; do
    echo "Проверяем $file..."
    TEMP_JS=$(mktemp)
    curl -s "$BASE_URL/$file" > "$TEMP_JS"
    
    if [ -s "$TEMP_JS" ]; then
        echo -e "  ${GREEN}✅ Файл загружен${NC}"
        
        # Проверяем на базовые JS ошибки
        if node -c "$TEMP_JS" 2>/dev/null; then
            echo -e "  ${GREEN}✅ Синтаксис корректен${NC}"
        else
            echo -e "  ${RED}❌ Ошибки синтаксиса${NC}"
        fi
    else
        echo -e "  ${RED}❌ Файл не загружен${NC}"
    fi
    
    rm -f "$TEMP_JS"
done

echo ""
echo -e "${BLUE}4. Проверка Telegram WebApp API...${NC}"

# Проверяем доступность Telegram API
if curl -s "https://telegram.org/js/telegram-web-app.js" | head -1 | grep -q "function"; then
    echo -e "${GREEN}✅ Telegram WebApp API доступен${NC}"
else
    echo -e "${RED}❌ Telegram WebApp API недоступен${NC}"
fi

echo ""
echo -e "${BLUE}📝 Исправления для ошибки 'Cannot set properties of null':${NC}"
echo "• Добавлены проверки существования DOM элементов перед установкой textContent"
echo "• Улучшена обработка ошибок в updateTotals() и updateSubmitButtonState()"
echo "• Добавлены безопасные fallback значения для cart данных"
echo "• Добавлена диагностика отсутствующих элементов в showApp()"
echo "• Добавлены setTimeout для отложенного обновления DOM"

echo ""
echo -e "${YELLOW}🛠️ Рекомендации для отладки:${NC}"
echo "1. Откройте консоль разработчика в Telegram"
echo "2. Проверьте наличие ошибок в логах"
echo "3. Убедитесь, что все скрипты загружаются в правильном порядке"
echo "4. Проверьте предупреждения о отсутствующих DOM элементах"

echo ""
echo -e "${GREEN}✅ Диагностика завершена${NC}"
