#!/bin/bash

# Тест улучшенных Telegram уведомлений с полной информацией о пользователе и контактах
# Дата: 2025-01-20

echo "🧪 ТЕСТ: Улучшенные Telegram уведомления с полной информацией"
echo "============================================================="

# Цвета для вывода
GREEN='\033[0;32m'
RED='\033[0;31m'
YELLOW='\033[1;33m'
NC='\033[0m' # No Color

BASE_URL="http://localhost:8080"

echo -e "${YELLOW}📋 Проверяем состояние приложения...${NC}"

# Проверка здоровья приложения
health_response=$(curl -s "$BASE_URL/actuator/health" || echo "ERROR")
if [[ "$health_response" == *"UP"* ]]; then
    echo -e "${GREEN}✅ Приложение работает${NC}"
else
    echo -e "${RED}❌ Приложение недоступно${NC}"
    exit 1
fi

echo -e "\n${YELLOW}🔐 Получаем токен администратора...${NC}"

# Авторизация администратора
admin_token=$(curl -s -X POST "$BASE_URL/api/auth/login" \
  -H "Content-Type: application/json" \
  -d '{"username":"admin","password":"admin123"}' | \
  grep -o '"token":"[^"]*"' | cut -d'"' -f4)

if [[ -z "$admin_token" ]]; then
    echo -e "${RED}❌ Не удалось получить токен администратора${NC}"
    exit 1
fi

echo -e "${GREEN}✅ Токен получен${NC}"

echo -e "\n${YELLOW}👤 Создаем тестового пользователя с полной информацией...${NC}"

# Создание пользователя с полными данными
user_response=$(curl -s -X POST "$BASE_URL/api/auth/register" \
  -H "Content-Type: application/json" \
  -d '{
    "username": "testuser_enhanced",
    "email": "testuser.enhanced@example.com",
    "password": "password123",
    "firstName": "Иван",
    "lastName": "Тестовый",
    "phone": "+79001234567"
  }')

echo "Ответ создания пользователя: $user_response"

# Получение токена пользователя
user_token=$(curl -s -X POST "$BASE_URL/api/auth/login" \
  -H "Content-Type: application/json" \
  -d '{"username":"testuser_enhanced","password":"password123"}' | \
  grep -o '"token":"[^"]*"' | cut -d'"' -f4)

if [[ -z "$user_token" ]]; then
    echo -e "${RED}❌ Не удалось получить токен пользователя${NC}"
    exit 1
fi

echo -e "${GREEN}✅ Пользователь создан и авторизован${NC}"

echo -e "\n${YELLOW}🛒 Добавляем товар в корзину...${NC}"

# Добавление товара в корзину
cart_response=$(curl -s -X POST "$BASE_URL/api/cart/add" \
  -H "Content-Type: application/json" \
  -H "Authorization: Bearer $user_token" \
  -d '{
    "productId": 1,
    "quantity": 2
  }')

echo "Ответ добавления в корзину: $cart_response"

echo -e "\n${YELLOW}📦 Создаем заказ с контактными данными...${NC}"

# Создание заказа с полными контактными данными
order_response=$(curl -s -X POST "$BASE_URL/api/orders" \
  -H "Content-Type: application/json" \
  -H "Authorization: Bearer $user_token" \
  -d '{
    "deliveryLocationId": 1,
    "deliveryAddress": "ул. Тестовая, д. 123, кв. 45",
    "contactName": "Петр Контактный",
    "contactPhone": "+79009876543",
    "comment": "Тестовый заказ для проверки улучшенных уведомлений. Просьба позвонить за 10 минут до доставки."
  }')

echo "Ответ создания заказа: $order_response"

# Извлечение ID заказа
order_id=$(echo "$order_response" | grep -o '"id":[0-9]*' | cut -d':' -f2)

if [[ -z "$order_id" ]]; then
    echo -e "${RED}❌ Не удалось создать заказ${NC}"
    echo "Полный ответ: $order_response"
    exit 1
fi

echo -e "${GREEN}✅ Заказ #$order_id создан${NC}"

echo -e "\n${YELLOW}📱 Проверяем отправку уведомления о новом заказе...${NC}"
echo "Ожидаемая информация в уведомлении:"
echo "  👤 ПОЛЬЗОВАТЕЛЬ СИСТЕМЫ:"
echo "     - Имя: Иван Тестовый"
echo "     - Username: @testuser_enhanced"
echo "     - Телефон пользователя: +79001234567"
echo "     - Email: testuser.enhanced@example.com"
echo "  📞 КОНТАКТНЫЕ ДАННЫЕ ЗАКАЗА:"
echo "     - Имя: Петр Контактный"
echo "     - Телефон: +79009876543"

echo -e "\n${YELLOW}🔄 Тестируем изменение статуса заказа...${NC}"

# Ждем немного для обработки
sleep 2

# Изменение статуса заказа
status_response=$(curl -s -X PUT "$BASE_URL/api/admin/orders/$order_id/status" \
  -H "Content-Type: application/json" \
  -H "Authorization: Bearer $admin_token" \
  -d '"CONFIRMED"')

echo "Ответ изменения статуса: $status_response"

echo -e "\n${YELLOW}📱 Проверяем отправку уведомления об изменении статуса...${NC}"
echo "Ожидаемая информация в уведомлении об изменении статуса:"
echo "  👤 ПОЛЬЗОВАТЕЛЬ СИСТЕМЫ:"
echo "     - Имя: Иван Тестовый"
echo "     - Username: @testuser_enhanced"
echo "     - Телефон пользователя: +79001234567"
echo "  📞 КОНТАКТНЫЕ ДАННЫЕ ЗАКАЗА:"
echo "     - Имя: Петр Контактный"
echo "     - Телефон: +79009876543"

echo -e "\n${YELLOW}📊 Проверяем список активных заказов...${NC}"

# Ждем немного для обработки
sleep 2

# Проверка отображения в списке заказов
orders_response=$(curl -s -X GET "$BASE_URL/api/admin/orders" \
  -H "Authorization: Bearer $admin_token")

echo "Список заказов: $orders_response"

echo -e "\n${GREEN}✅ ТЕСТ ЗАВЕРШЕН${NC}"
echo "============================================"
echo -e "${YELLOW}📋 ПРОВЕРЬТЕ TELEGRAM УВЕДОМЛЕНИЯ:${NC}"
echo ""
echo "1. 🆕 Уведомление о новом заказе должно содержать:"
echo "   - Полную информацию о пользователе системы"
echo "   - Контактные данные заказа"
echo "   - Четкое разделение блоков информации"
echo ""
echo "2. 🔄 Уведомление об изменении статуса должно содержать:"
echo "   - Информацию о пользователе и контактах"
echo "   - Старый и новый статус"
echo ""
echo "3. 📋 В админском боте должны быть доступны:"
echo "   - Расширенная информация в активных заказах"
echo "   - Детальная информация при нажатии кнопок"
echo ""
echo -e "${YELLOW}🔧 Если уведомления не приходят, проверьте:${NC}"
echo "- Конфигурацию Telegram ботов в docker-compose.yml"
echo "- Логи приложения: docker-compose logs app"
echo "- Статус ботов: они должны работать в режиме Long Polling"