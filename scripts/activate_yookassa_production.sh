#!/bin/bash

# Скрипт активации ЮKassa в продакшене для PizzaNat
# Этап 4: Тестирование и активация ЮKassa интеграции

set -e

# Цвета для вывода
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
PURPLE='\033[0;35m'
CYAN='\033[0;36m'
NC='\033[0m' # No Color

echo -e "${CYAN}🚀 Активация ЮKassa в продакшене для PizzaNat${NC}"
echo -e "${CYAN}=============================================${NC}"
echo ""

# Функция для запроса подтверждения
confirm() {
    local message="$1"
    echo -e "${YELLOW}❓ $message${NC}"
    read -p "Продолжить? (y/n): " -n 1 -r
    echo
    if [[ ! $REPLY =~ ^[Yy]$ ]]; then
        echo -e "${RED}❌ Операция отменена пользователем${NC}"
        exit 1
    fi
}

# Функция для проверки переменной окружения
check_env_var() {
    local var_name="$1"
    local var_value="$2"
    local is_secret="$3"
    
    if [[ -n "$var_value" && "$var_value" != "your_shop_id_here" && "$var_value" != "your_secret_key_here" ]]; then
        if [[ "$is_secret" == "true" ]]; then
            echo -e "${GREEN}✅ $var_name установлена (скрыта)${NC}"
        else
            echo -e "${GREEN}✅ $var_name = $var_value${NC}"
        fi
        return 0
    else
        echo -e "${RED}❌ $var_name не установлена или содержит placeholder${NC}"
        return 1
    fi
}

# Функция для обновления переменной в docker-compose.yml
update_docker_compose_var() {
    local var_name="$1"
    local var_value="$2"
    
    # Экранируем специальные символы для sed
    local escaped_value=$(echo "$var_value" | sed 's/[[\.*^$()+?{|]/\\&/g')
    
    # Обновляем переменную в docker-compose.yml
    if grep -q "^      $var_name:" docker-compose.yml; then
        sed -i.bak "s|^      $var_name:.*|      $var_name: $escaped_value|" docker-compose.yml
        echo -e "${GREEN}✅ Обновлена переменная $var_name в docker-compose.yml${NC}"
    else
        echo -e "${YELLOW}⚠️ Переменная $var_name не найдена в docker-compose.yml${NC}"
    fi
}

# 1. Проверка предварительных условий
echo -e "${BLUE}🔍 Шаг 1: Проверка предварительных условий${NC}"
echo ""

# Проверяем наличие необходимых файлов
if [[ ! -f "docker-compose.yml" ]]; then
    echo -e "${RED}❌ Файл docker-compose.yml не найден${NC}"
    exit 1
fi

if [[ ! -f "env-yookassa-template.txt" ]]; then
    echo -e "${RED}❌ Файл env-yookassa-template.txt не найден${NC}"
    exit 1
fi

echo -e "${GREEN}✅ Необходимые файлы найдены${NC}"
echo ""

# 2. Создание/проверка .env файла
echo -e "${BLUE}🔧 Шаг 2: Настройка переменных окружения${NC}"
echo ""

if [[ ! -f ".env" ]]; then
    echo -e "${YELLOW}📝 Создаем .env файл из шаблона...${NC}"
    cp env-yookassa-template.txt .env
    echo -e "${GREEN}✅ Файл .env создан${NC}"
else
    echo -e "${GREEN}✅ Файл .env уже существует${NC}"
fi

# Загружаем переменные из .env
if [[ -f ".env" ]]; then
    source .env
fi

# Проверяем основные переменные
echo -e "${CYAN}Проверяем переменные ЮKassa:${NC}"
check_env_var "YOOKASSA_ENABLED" "$YOOKASSA_ENABLED" false
check_env_var "YOOKASSA_SHOP_ID" "$YOOKASSA_SHOP_ID" false
check_env_var "YOOKASSA_SECRET_KEY" "$YOOKASSA_SECRET_KEY" true
check_env_var "YOOKASSA_API_URL" "$YOOKASSA_API_URL" false

echo ""

# 3. Запрос учетных данных ЮKassa (если не заполнены)
echo -e "${BLUE}🔑 Шаг 3: Настройка учетных данных ЮKassa${NC}"
echo ""

if [[ -z "$YOOKASSA_SHOP_ID" || "$YOOKASSA_SHOP_ID" == "your_shop_id_here" ]]; then
    echo -e "${YELLOW}Введите SHOP_ID от ЮKassa:${NC}"
    read -p "SHOP_ID: " new_shop_id
    
    if [[ -n "$new_shop_id" ]]; then
        # Обновляем в .env
        if grep -q "^YOOKASSA_SHOP_ID=" .env; then
            sed -i.bak "s|^YOOKASSA_SHOP_ID=.*|YOOKASSA_SHOP_ID=$new_shop_id|" .env
        else
            echo "YOOKASSA_SHOP_ID=$new_shop_id" >> .env
        fi
        
        # Обновляем в docker-compose.yml
        update_docker_compose_var "YOOKASSA_SHOP_ID" "$new_shop_id"
        
        YOOKASSA_SHOP_ID="$new_shop_id"
        echo -e "${GREEN}✅ SHOP_ID обновлен${NC}"
    fi
fi

if [[ -z "$YOOKASSA_SECRET_KEY" || "$YOOKASSA_SECRET_KEY" == "your_secret_key_here" ]]; then
    echo -e "${YELLOW}Введите SECRET_KEY от ЮKassa:${NC}"
    read -s -p "SECRET_KEY: " new_secret_key
    echo
    
    if [[ -n "$new_secret_key" ]]; then
        # Обновляем в .env
        if grep -q "^YOOKASSA_SECRET_KEY=" .env; then
            sed -i.bak "s|^YOOKASSA_SECRET_KEY=.*|YOOKASSA_SECRET_KEY=$new_secret_key|" .env
        else
            echo "YOOKASSA_SECRET_KEY=$new_secret_key" >> .env
        fi
        
        # Обновляем в docker-compose.yml
        update_docker_compose_var "YOOKASSA_SECRET_KEY" "$new_secret_key"
        
        YOOKASSA_SECRET_KEY="$new_secret_key"
        echo -e "${GREEN}✅ SECRET_KEY обновлен${NC}"
    fi
fi

echo ""

# 4. Активация ЮKassa
echo -e "${BLUE}⚡ Шаг 4: Активация ЮKassa${NC}"
echo ""

if [[ "$YOOKASSA_ENABLED" != "true" ]]; then
    confirm "Активировать ЮKassa (установить YOOKASSA_ENABLED=true)?"
    
    # Обновляем в .env
    if grep -q "^YOOKASSA_ENABLED=" .env; then
        sed -i.bak "s|^YOOKASSA_ENABLED=.*|YOOKASSA_ENABLED=true|" .env
    else
        echo "YOOKASSA_ENABLED=true" >> .env
    fi
    
    # Обновляем в docker-compose.yml
    update_docker_compose_var "YOOKASSA_ENABLED" "true"
    
    echo -e "${GREEN}✅ ЮKassa активирована${NC}"
else
    echo -e "${GREEN}✅ ЮKassa уже активирована${NC}"
fi

echo ""

# 5. Проверка конфигурации
echo -e "${BLUE}🔍 Шаг 5: Проверка итоговой конфигурации${NC}"
echo ""

echo -e "${CYAN}Итоговые настройки ЮKassa:${NC}"
source .env
check_env_var "YOOKASSA_ENABLED" "$YOOKASSA_ENABLED" false
check_env_var "YOOKASSA_SHOP_ID" "$YOOKASSA_SHOP_ID" false
check_env_var "YOOKASSA_SECRET_KEY" "$YOOKASSA_SECRET_KEY" true
check_env_var "YOOKASSA_API_URL" "$YOOKASSA_API_URL" false
check_env_var "YOOKASSA_WEBHOOK_URL" "$YOOKASSA_WEBHOOK_URL" false

echo ""

# 6. Перезапуск приложения
echo -e "${BLUE}🔄 Шаг 6: Перезапуск приложения${NC}"
echo ""

confirm "Перезапустить приложение для применения изменений?"

echo -e "${YELLOW}Останавливаем приложение...${NC}"
docker-compose down

echo -e "${YELLOW}Запускаем приложение с новой конфигурацией...${NC}"
docker-compose up -d

echo -e "${GREEN}✅ Приложение перезапущено${NC}"
echo ""

# 7. Проверка работоспособности
echo -e "${BLUE}✅ Шаг 7: Проверка работоспособности${NC}"
echo ""

echo -e "${YELLOW}Ждем запуска приложения (30 секунд)...${NC}"
sleep 30

# Проверяем health endpoint
echo -e "${CYAN}Проверяем health endpoint ЮKassa...${NC}"
health_response=$(curl -s "http://localhost:8080/api/v1/payments/yookassa/health" || echo "{}")

if echo "$health_response" | jq -e '.status == "UP"' > /dev/null 2>&1; then
    echo -e "${GREEN}✅ ЮKassa работает корректно${NC}"
    
    enabled=$(echo "$health_response" | jq -r '.enabled')
    if [[ "$enabled" == "true" ]]; then
        echo -e "${GREEN}✅ ЮKassa включена и готова к использованию${NC}"
    else
        echo -e "${YELLOW}⚠️ ЮKassa отключена${NC}"
    fi
else
    echo -e "${RED}❌ Проблемы с работой ЮKassa${NC}"
    echo -e "${YELLOW}Response: $health_response${NC}"
fi

echo ""

# 8. Итоговая информация
echo -e "${CYAN}🎉 Активация ЮKassa завершена!${NC}"
echo -e "${CYAN}================================${NC}"
echo ""
echo -e "${GREEN}✅ ЮKassa интеграция активирована в продакшене${NC}"
echo ""
echo -e "${CYAN}📋 Что дальше:${NC}"
echo -e "${YELLOW}1. Протестируйте создание платежей через API${NC}"
echo -e "${YELLOW}2. Настройте webhook в личном кабинете ЮKassa:${NC}"
echo -e "${BLUE}   URL: https://debaganov-pizzanat-0177.twc1.net/api/v1/payments/yookassa/webhook${NC}"
echo -e "${YELLOW}3. Протестируйте полный цикл оплаты${NC}"
echo -e "${YELLOW}4. Мониторьте логи: docker-compose logs -f app${NC}"
echo ""
echo -e "${CYAN}📚 Полезные команды:${NC}"
echo -e "${BLUE}• Проверка статуса: curl http://localhost:8080/api/v1/payments/yookassa/health${NC}"
echo -e "${BLUE}• Список банков СБП: curl http://localhost:8080/api/v1/payments/yookassa/sbp/banks${NC}"
echo -e "${BLUE}• Просмотр логов: docker-compose logs app | grep -i yookassa${NC}"
echo ""
echo -e "${GREEN}🚀 ЮKassa готова к приему платежей!${NC}" 