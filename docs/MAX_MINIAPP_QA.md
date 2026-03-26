# MAX Mini App - Вопросы для уточнения

> Создано: 2026-03-26
> Статус: Требуют ответа

---

## 🔴 Критические вопросы

### Q-001: Токены ботов MAX
**Приоритет:** Критический
**Статус:** ❓ Требует ответа

**Вопрос:**
Как получить API токены для ботов в MAX?

**Контекст:**
- Боты уже созданы:
  - ДИМБО: https://max.ru/id121603899498_bot токен f9LHodD0cOKMx3YM-LVjPl5fvgW1zrL3Gd8TB0et9Z4AXB1_v0jkTV26MDMTeGZ6sj4TZnOJOif8p7jDxwmZ 

  - ДИМБО Админ: https://max.ru/id121603899498_1_bot токен f9LHodD0cOKGjBhKOiTerO4tix7cID248uek0EkbqWEbEUALnKMRZMRw8XiZheOVmhVGFW_P6Ge9XMi5Lxph

- Для работы API нужны токены вида `aaa.bbb.ccc` (как в Telegram). указал их выше

**Ожидаемый формат ответа:**
```
MAX_USER_BOT_TOKEN=f9LHodD0cOKMx3YM-LVjPl5fvgW1zrL3Gd8TB0et9Z4AXB1_v0jkTV26MDMTeGZ6sj4TZnOJOif8p7jDxwmZ
MAX_ADMIN_BOT_TOKEN=f9LHodD0cOKGjBhKOiTerO4tix7cID248uek0EkbqWEbEUALnKMRZMRw8XiZheOVmhVGFW_P6Ge9XMi5Lxph
```

---

### Q-002: Настройка Mini App в MAX для партнёров
**Приоритет:** Критический
**Статус:** ❓ Требует ответа

**Вопрос:**
Как настроить URL Mini App для бота в платформе MAX для партнёров?

**Контекст:**
- В Telegram используется BotFather: `/setmenubutton` → URL
- В MAX есть "платформа MAX для партнёров"
- Нужно указать URL: `https://api.dimbopizza.ru/max-miniapp/menu.html`

**Требуется:**
1. Пошаговая инструкция настройки
2. Требования к URL (HTTPS, длина, символы)
3. Как изменить текст кнопки

ответ 
Требования к URL мини-приложения:

Длина: не более 1024 символов
Протокол: только https://
Допустимые символы: буквы (латиница), цифры, точка (.) и дефис (-)
Пробелы не поддерживаются
URL должен быть валидный

Тут документация https://dev.max.ru/docs/webapps/introduction

---

### Q-003: SDK MAX Bridge
**Приоритет:** Высокий
**Статус:** ❓ Требует ответа

**Вопрос:**
Нужно ли скачивать `max-bridge.js` локально или можно использовать CDN?

**Контекст:**
- Telegram использует CDN: `https://telegram.org/js/telegram-web-app.js`
- Документация MAX не указывает CDN URL
- Текущий подход: локальная копия в `/static/max-miniapp/`

**Варианты:**
1. CDN (предпочтительно) - укажите URL
2. Локальная копия - требуется скачать


Ответ <script src="https://st.max.ru/js/max-web-app.js"></script>
Тут подробная документаци по MAX Bridge https://dev.max.ru/docs/webapps/bridge

---

## 🟡 Важные вопросы

### Q-004: Формат user в MAX initData
**Приоритет:** Высокий
**Статус:** ❓ Требует уточнения

**Вопрос:**
Какие поля содержит объект `user` в initData MAX?

**Контекст из документации:**
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

**Требуется уточнение:**
- Есть ли поле `phone_number`?
- Есть ли поле `is_premium`?
- Формат `photo_url` - полный URL или относительный путь?

---

### Q-005: Запрос номера телефона
**Приоритет:** Высокий
**Статус:** ❓ Требует ответа

**Вопрос:**
Как работает `WebApp.requestPhone()` в MAX?

**Контекст:**
- Telegram: `tg.requestContact()` возвращает данные через событие
- MAX: `WebApp.requestPhone()` возвращает Promise

**Требуется уточнение:**
1. Возвращает ли номер телефона напрямую или через событие?
2. Требуется ли разрешение пользователя?
3. Какой формат возвращаемого номера?

ответ:
Документация https://dev.max.ru/docs/webapps/introduction
https://dev.max.ru/docs/webapps/introduction

---

### Q-006: Webhook для MAX бота
**Приоритет:** Средний
**Статус:** ❓ Требует ответа

**Вопрос:**
Как настроить webhook для MAX бота?

**Контекст:**
- Telegram: `https://api.telegram.org/bot<token>/setWebhook?url=...`
- MAX: ?

**Требуется:**
1. URL для установки webhook
2. Формат запроса
3. Формат callback данных

 ответ:
Документация https://dev.max.ru/docs/webapps/validation

---

## 🟢 Дополнительные вопросы

### Q-007: Тестирование Mini App
**Приоритет:** Средний
**Статус:** ❓ Требует ответа

**Вопрос:**
Как тестировать MAX Mini App в процессе разработки?

**Варианты:**
1. Доступна ли тестовая версия MAX?
2. Можно ли использовать эмулятор?
3. Как отлаживать JavaScript в MAX?

---

### Q-008: Метрики и аналитика
**Приоритет:** Низкий
**Статус:** ❓ Требует ответа

**Вопрос:**
Поддерживает ли MAX интеграцию с Яндекс.Метрикой?

**Контекст:**
- Telegram Mini App использует Яндекс.Метрику
- Нужно ли что-то менять для MAX?

ответ: все так же как и для телеграм
---

## 📝 Ответы

### A-003: Валидация MAX initData
**Статус:** ✅ Разъяснено
**Дата:** 2026-03-26

**Вопрос:** Как отличается валидация MAX от Telegram?

**Ответ:**
Ключевое отличие в вычислении secret key:

**Telegram:**
```
secret_key = HMAC_SHA256("WebAppData" + bot_token, empty_string)
```

**MAX:**
```
secret_key = HMAC_SHA256("WebAppData", bot_token_bytes)
```

В MAX нет конкатенации строк, а bot_token передаётся как байты во второй параметр HMAC.

**Реализация:**
```java
// MAX
byte[] secretKey = computeHMAC("WebAppData", botToken.getBytes());

// Telegram
byte[] secretKey = computeHMAC("WebAppData" + botToken, new byte[0]);
```

---

## 📋 Чек-лист перед началом разработки

Перед началом реализации MAX Mini App необходимо:

- [ ] Получить токены ботов (Q-001)
- [ ] Понять процесс настройки Mini App (Q-002)
- [ ] Решить вопрос с SDK (Q-003)
- [ ] Уточнить формат user (Q-004)
- [ ] Понять работу requestPhone (Q-005)
- [ ] Настроить webhook (Q-006)

---

Если что обращайся к документации https://dev.max.ru/docs/webapps/introduction
https://dev.max.ru/help/events
https://dev.max.ru/help/deeplinks



## 📞 Контакты для уточнения

- MAX для партнёров: https://max.ru/partners
- Документация MAX: https://dev.max.ru/docs
- Поддержка разработчиков: ?
