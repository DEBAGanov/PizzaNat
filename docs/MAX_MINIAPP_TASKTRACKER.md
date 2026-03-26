# MAX Mini App - Отслеживание задач

> Обновлено: 2026-03-26

## Статус проекта
🟢 **Backend завершен, Frontend создан** - Готов к тестированию

---

## 📋 Общий прогресс

| Этап | Статус | Прогресс |
|------|--------|----------|
| День 1: Конфигурация и DTO | ✅ Завершен | 5/5 |
| День 2: MaxWebAppService | ✅ Завершен | 5/5 |
| День 3: MaxWebAppController | ✅ Завершен | 4/4 |
| День 4: Frontend Menu | ✅ Завершен | 5/5 |
| День 5: Frontend Checkout | ✅ Завершен | 4/4 |
| День 6: Admin Notifications | ✅ Завершен | 4/4 |
| День 7: Тестирование и деплой | ⬜ Не начат | 0/4 |

---

## ✅ Выполненные задачи

### TK-001: Создать MaxBotConfig ✅
- **Приоритет:** Критический
- **Статус:** ✅ Завершен
- **Файл:** `src/main/java/com/baganov/pizzanat/config/MaxBotConfig.java`
- **Описание:** Конфигурационный класс для токенов MAX ботов

### TK-002: Создать MaxWebAppService ✅
- **Приоритет:** Критический
- **Статус:** ✅ Завершен
- **Файл:** `src/main/java/com/baganov/pizzanat/service/MaxWebAppService.java`
- **Описание:** Сервис валидации MAX initData и авторизации

### TK-003: Создать MaxWebAppController ✅
- **Приоритет:** Критический
- **Статус:** ✅ Завершен
- **Файл:** `src/main/java/com/baganov/pizzanat/controller/MaxWebAppController.java`
- **Описание:** REST API для авторизации MAX пользователей

### TK-004: Создать max-menu.html ✅
- **Приоритет:** Критический
- **Статус:** ✅ Завершен
- **Файл:** `src/main/resources/static/max-miniapp/menu.html`
- **Описание:** Главная страница Mini App с каталогом товаров

### TK-005: Создать max-checkout.html ✅
- **Приоритет:** Критический
- **Статус:** ✅ Завершен
- **Файл:** `src/main/resources/static/max-miniapp/checkout.html`
- **Описание:** Страница оформления заказа

### TK-006: Создать MaxAdminNotificationService ✅
- **Приоритет:** Высокий
- **Статус:** ✅ Завершен
- **Файл:** `src/main/java/com/baganov/pizzanat/service/MaxAdminNotificationService.java`
- **Описание:** Сервис уведомлений о заказах в MAX

### TK-007: Обновить docker-compose.yml ✅
- **Приоритет:** Критический
- **Статус:** ✅ Завершен
- **Описание:** Добавлены переменные окружения MAX_BOT_*

---

## 📁 Созданные файлы

### Backend (Java)
- ✅ `config/MaxBotConfig.java` - Конфигурация ботов
- ✅ `model/dto/max/MaxWebAppInitData.java` - DTO initData
- ✅ `model/dto/max/MaxWebAppUser.java` - DTO пользователя
- ✅ `model/dto/max/MaxWebAppAuthRequest.java` - Request авторизации
- ✅ `model/dto/max/MaxWebAppValidateRequest.java` - Request валидации
- ✅ `service/MaxWebAppService.java` - Сервис авторизации
- ✅ `controller/MaxWebAppController.java` - REST API
- ✅ `service/MaxAdminNotificationService.java` - Уведомления

### Frontend (JavaScript/HTML)
- ✅ `static/max-miniapp/index.html` - Точка входа
- ✅ `static/max-miniapp/menu.html` - Страница меню
- ✅ `static/max-miniapp/checkout.html` - Страница оформления
- ✅ `static/max-miniapp/max-api.js` - API модуль
- ✅ `static/max-miniapp/max-menu-app.js` - Логика меню
- ✅ `static/max-miniapp/max-checkout-app.js` - Логика checkout
- ✅ `static/max-miniapp/sbp-logo.png` - Логотип СБП

### Конфигурация
- ✅ `application.yml` - Добавлена секция `max:`
- ✅ `docker-compose.yml` - Добавлены переменные MAX_BOT_*

---

## ⬜ Оставшиеся задачи

### TK-008: Unit тесты MaxWebAppService
- **Приоритет:** Высокий
- **Статус:** ⬜ Ожидает
- **Файл:** `src/test/java/com/baganov/pizzanat/service/MaxWebAppServiceTest.java`

### TK-009: Интеграционные тесты
- **Приоритет:** Высокий
- **Статус:** ⬜ Ожидает
- **Файл:** `src/test/java/com/baganov/pizzanat/controller/MaxWebAppControllerTest.java`

### TK-010: Тестирование в MAX
- **Приоритет:** Критический
- **Статус:** ⬜ Ожидает
- **Описание:**
  1. Открыть бота @id121603899498_bot в MAX
  2. Нажать кнопку меню
  3. Проверить загрузку товаров
  4. Добавить товар в корзину
  5. Оформить заказ
  6. Проверить уведомление в админ-боте

### TK-011: Настройка Mini App URL в MAX для партнёров
- **Приоритет:** Критический
- **Статус:** ⬜ Ожидает
- **Описание:** Настроить URL `https://api.dimbopizza.ru/max-miniapp/menu.html` в платформе MAX

---

## 🔍 Вопросы для уточнения

### Q-001: Токены ботов MAX ✅ ОТВЕЧЕН
- **Ответ:** Токены получены из MAX для партнёров:
  - `MAX_USER_BOT_TOKEN=f9LHodD0cOKMx3YM-LVjPl5fvgW1zrL3Gd8TB0et9Z4AXB1_v0jkTV26MDMTeGZ6sj4TZnOJOif8p7jDxwmZ`
  - `MAX_ADMIN_BOT_TOKEN=f9LHodD0cOKGjBhKOiTerO4tix7cID248uek0EkbqWEbEUALnKMRZMRw8XiZheOVmhVGFW_P6Ge9XMi5Lxph`

### Q-002: SDK MAX Bridge ✅ ОТВЕЧЕН
- **Ответ:** Используем CDN: `<script src="https://st.max.ru/js/max-web-app.js"></script>`

### Q-003: Отличия валидации MAX ✅ РАЗЪЯСНЕНО
- **Ответ:** MAX использует `HMAC_SHA256("WebAppData", botToken)`, без конкатенации

### Q-004: Формат user в MAX initData ✅ РАЗЪЯСНЕНО
- **Ответ:**
```json
{
    "id": 400,
    "first_name": "Вася",
    "last_name": "",
    "photo_url": null,
    "username": null,
    "language_code": "ru"
}
```

---

## 📊 Метрики успеха

| Метрика | Цель | Как проверить |
|---------|------|------------------|
| Авторизации в день | > 10 | Логи сервера |
| Заказы через MAX | > 5/день | Логи БД |
| Время загрузки Mini App | < 2 сек | DevTools Network |
| Успешность авторизации | > 95% | Логи сервера |

---

## 📝 Следующие шаги

1. **Настроить URL Mini App в MAX для партнёров**
   - Зайти в https://max.ru/partners
   - Найти бота id121603899498_bot
   - Настроить URL: `https://api.dimbopizza.ru/max-miniapp/menu.html`

2. **Протестировать в MAX**
   - Открыть бота в приложении MAX
   - Проверить все функции

3. **Деплой на продакшн**
   - Установить переменные окружения на сервере
   - Перезапустить контейнер
