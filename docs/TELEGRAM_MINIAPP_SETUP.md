# Настройка Telegram Mini App для PizzaNat

## Обзор

Данная документация описывает процесс настройки и развертывания Telegram Mini App для PizzaNat по образцу @DurgerKingBot.

## Что реализовано

### ✅ Бэкенд компоненты
- **TelegramWebAppController** - REST API для авторизации через WebApp
- **TelegramWebAppService** - сервис для обработки и валидации Telegram initData
- **DTO модели** - TelegramWebAppAuthRequest, TelegramWebAppInitData, TelegramWebAppUser
- **Интеграция с существующим API** - корзина, заказы, платежи YooKassa

### ✅ Фронтенд компоненты  
- **HTML интерфейс** (`/miniapp/index.html`) - адаптивный дизайн по образцу @DurgerKingBot
- **CSS стили** (`/miniapp/styles.css`) - темная тема с поддержкой Telegram цветов
- **JavaScript API** (`/miniapp/api.js`) - модуль для взаимодействия с бэкендом
- **JavaScript приложение** (`/miniapp/app.js`) - основная логика Mini App

### ✅ Функциональность
- **Автоматическая авторизация** через Telegram WebApp initData
- **🆕 Запрос номера телефона** через `requestContact()` API (Bot API 6.9+)
- **Каталог товаров** с категориями и поиском
- **Корзина** с добавлением/удалением товаров
- **Оформление заказа** с интеграцией YooKassa
- **Адаптивный UI** с поддержкой тем Telegram

## Настройка бота

### 1. Создание Mini App в @BotFather

Перейдите к @BotFather и выполните следующие команды:

```
/mybots
→ Выберите @DIMBOpizzaBot
→ Bot Settings  
→ Menu Button
→ Configure Menu Button
```

Настройте Menu Button:
- **Button text**: `🍕 Заказать`
- **Web App URL**: `https://ваш-домен.com/miniapp`

### 2. Настройка Web App

```
/mybots
→ Выберите @DIMBOpizzaBot  
→ Bot Settings
→ Configure Mini App
→ Enable Mini App
```

Заполните настройки:
- **Mini App Name**: `PizzaNat`
- **Mini App Description**: `Заказ пиццы и доставка в Волжске`
- **Mini App URL**: `https://ваш-домен.com/miniapp`
- **Mini App Icon**: загрузите иконку приложения (512x512 px)

### 3. Настройка Direct Link

Создайте direct link для доступа к полному каталогу:

```
https://t.me/DIMBOpizzaBot/DIMBO
```

Этот линк будет сразу открывать Mini App с каталогом товаров.

## Развертывание

### 1. Переменные окружения

Убедитесь, что настроены следующие переменные:

```bash
# Telegram Bot
TELEGRAM_BOT_TOKEN=ваш_бот_токен
TELEGRAM_BOT_USERNAME=DIMBOpizzaBot

# База данных
DB_HOST=localhost
DB_PORT=5432
DB_NAME=pizzanat_db
DB_USERNAME=pizzanat_user
DB_PASSWORD=ваш_пароль

# YooKassa (для платежей)
YOOKASSA_SHOP_ID=ваш_shop_id
YOOKASSA_SECRET_KEY=ваш_secret_key
```

### 2. Сборка и запуск

```bash
# Сборка приложения
./gradlew build

# Запуск через Docker Compose
docker-compose -f docker-compose.production.yml up -d

# Или запуск напрямую
java -jar build/libs/PizzaNat-0.0.1-SNAPSHOT.jar
```

### 3. Проверка развертывания

1. **API доступность**:
   ```bash
   curl https://ваш-домен.com/api/health
   ```

2. **Mini App доступность**:
   ```bash
   curl https://ваш-домен.com/miniapp
   ```

3. **Telegram WebApp API**:
   ```bash
   curl -X POST https://ваш-домен.com/api/v1/telegram-webapp/validate-init-data \
        -H "Content-Type: application/json" \
        -d '{"initDataRaw": "test"}'
   ```

## Тестирование

### 1. Локальное тестирование

Для тестирования локально можно использовать ngrok:

```bash
# Запуск приложения локально
./gradlew bootRun

# В другом терминале
ngrok http 8080

# Обновите URL в @BotFather на ваш ngrok URL
# Например: https://abc123.ngrok.io/miniapp
```

### 2. Тестирование в Telegram

1. Откройте @DIMBOpizzaBot
2. Нажмите кнопку `🍕 Заказать` или используйте команду `/start`
3. Mini App должно открыться с каталогом товаров
4. Проверьте:
   - Автоматическую авторизацию
   - Загрузку категорий и товаров
   - Добавление в корзину
   - Оформление заказа
   - Переход к оплате YooKassa

### 3. Direct Link тестирование

Откройте прямую ссылку:
```
https://t.me/DIMBOpizzaBot/DIMBO
```

Mini App должно открыться сразу с полным каталогом.

## Устранение неполадок

### Проблема: Mini App не загружается

**Решение**:
1. Проверьте HTTPS - Mini App работает только по HTTPS
2. Убедитесь, что URL доступен извне
3. Проверьте CORS настройки в SecurityConfig

### Проблема: Ошибка авторизации

**Решение**:
1. Проверьте правильность bot token
2. Убедитесь, что initData валидируется корректно
3. Проверьте логи TelegramWebAppService

### Проблема: Товары не загружаются

**Решение**:
1. Проверьте доступность API эндпоинтов
2. Убедитесь, что категории и продукты созданы в БД
3. Проверьте настройки CORS для API запросов

### Проблема: Платежи не работают

**Решение**:
1. Проверьте настройки YooKassa
2. Убедитесь, что webhook URL настроен
3. Проверьте интеграцию с мобильными платежами

## API эндпоинты Mini App

### Авторизация
- `POST /api/v1/telegram-webapp/auth` - авторизация через initData
- `POST /api/v1/telegram-webapp/validate-init-data` - валидация данных

### Каталог (унаследовано из основного API)
- `GET /api/v1/categories` - получение категорий
- `GET /api/v1/products` - получение товаров
- `GET /api/v1/products/category/{id}` - товары по категории

### Корзина (унаследовано из основного API)
- `GET /api/v1/cart` - получение корзины
- `POST /api/v1/cart/items` - добавление в корзину
- `PUT /api/v1/cart/items/{id}` - обновление количества
- `DELETE /api/v1/cart/items/{id}` - удаление из корзины

### Заказы (унаследовано из основного API)
- `POST /api/v1/orders` - создание заказа
- `GET /api/v1/orders` - получение заказов пользователя

### Платежи (унаследовано из основного API)
- `POST /api/v1/mobile/payments/create` - создание платежа
- `GET /api/v1/payments/yookassa/sbp-banks` - банки СБП

## Получение номера телефона

### ❌ Важно: номер телефона НЕ передается автоматически

По [официальной документации Telegram WebApp](https://core.telegram.org/bots/webapps), номер телефона **НЕ передается** через `initData` автоматически по соображениям приватности.

### ✅ Способы получения номера телефона

#### 1. Через `requestContact()` API (рекомендуется)

Начиная с Bot API 6.9, можно запрашивать контактную информацию:

```javascript
// В Mini App
if (window.Telegram?.WebApp?.requestContact) {
    window.Telegram.WebApp.requestContact();
    
    // Обработка ответа
    window.Telegram.WebApp.onEvent('contactRequested', (data) => {
        if (data.status === 'sent') {
            console.log('Телефон:', data.contact.phone_number);
            console.log('Имя:', data.contact.first_name);
        }
    });
}
```

#### 2. Ручной ввод пользователем

Альтернативный способ - форма ввода в самом Mini App:

```html
<input type="tel" placeholder="+7 (999) 123-45-67" 
       pattern="[+][0-9]{11,15}" required>
```

### 🔧 Реализация в PizzaNat Mini App

В нашей реализации:

1. **При оформлении заказа** автоматически запрашивается `requestContact()`
2. **Если пользователь согласится** - номер сохраняется в профиле
3. **Если откажется** - используются стандартные данные
4. **При повторных заказах** номер уже будет доступен

```javascript
// Автоматический запрос при checkout
async proceedToCheckout() {
    if (this.tg?.requestContact) {
        await this.requestUserContact();
    } else {
        // Fallback - стандартные данные
        await this.createOrderWithStandardData();
    }
}
```

## Особенности реализации

### 1. Валидация initData

Реализована полная валидация данных от Telegram согласно документации:
- Проверка HMAC-SHA256 подписи
- Валидация времени (не старше 24 часов)
- Безопасный парсинг параметров

### 2. Автоматическая авторизация

- При первом запуске создается новый пользователь
- При повторном запуске используется существующий
- JWT токен сохраняется локально для API запросов

### 3. Интеграция с Telegram WebApp API

- Поддержка тем Telegram
- Haptic feedback
- Main Button и Back Button
- Viewport management

### 4. Оплата через YooKassa

- Интеграция с СБП (Система быстрых платежей)
- Поддержка банковских карт
- Возврат в Mini App после оплаты

## Следующие шаги

1. **Тестирование в продакшене** - развернуть на боевом сервере
2. **Мониторинг** - настроить логирование и метрики
3. **Оптимизация** - сжатие ресурсов, кэширование
4. **Расширение функций** - добавление новых возможностей

## Дополнительные ресурсы

- [Telegram Bot API](https://core.telegram.org/bots/api)
- [Telegram WebApp Documentation](https://core.telegram.org/bots/webapps)
- [YooKassa API](https://yookassa.ru/developers)
- [Основная документация проекта](Project.md)

---

**Создано**: 2025-01-23  
**Автор**: PizzaNat Development Team  
**Версия**: 1.0
