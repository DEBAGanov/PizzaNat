# 🔧 Настройка Google Credentials для Dimbopizza

## 📋 У вас есть:
- **Файл**: `dimbo-468117-53e3970394b4.json` (скачанный Service Account key)
- **API Key**: `AIzaSyBoCeXNXYlh3u9raxV5WPemGYmrDE1yyJY` (не нужен для интеграции)
- **ID таблицы**: `1K_g-EGPQgu4aFv4bIPP6yE_raHyUrlr6GYi-MTEJtu4`
- **Название таблицы**: `Dimbopizza`
- **Лист**: `Лист1`

## 🚀 Пошаговая настройка:

### 1. Скопируйте содержимое вашего JSON файла

Откройте файл `dimbo-468117-53e3970394b4.json` и скопируйте все его содержимое.

### 2. Замените содержимое файла credentials

Откройте файл `config/google-credentials.json` в проекте и **полностью замените** его содержимое на содержимое вашего скачанного файла.

Файл должен выглядеть примерно так:
```json
{
  "type": "service_account",
  "project_id": "dimbo-468117",
  "private_key_id": "53e3970394b4...",
  "private_key": "-----BEGIN PRIVATE KEY-----\nMIIEvQIBADANBgkqhkiG9w0BAQEFAASCBKcwggSjAgEAAoIBAQC...\n-----END PRIVATE KEY-----\n",
  "client_email": "ваш-сервис-аккаунт@dimbo-468117.iam.gserviceaccount.com",
  "client_id": "1234567890123456789",
  "auth_uri": "https://accounts.google.com/o/oauth2/auth",
  "token_uri": "https://oauth2.googleapis.com/token",
  "auth_provider_x509_cert_url": "https://www.googleapis.com/oauth2/v1/certs",
  "client_x509_cert_url": "https://www.googleapis.com/robot/v1/metadata/x509/..."
}
```

### 3. Проверьте права доступа к таблице

1. Откройте вашу таблицу: https://docs.google.com/spreadsheets/d/1K_g-EGPQgu4aFv4bIPP6yE_raHyUrlr6GYi-MTEJtu4/edit
2. Нажмите **"Настройки доступа"** (кнопка "Поделиться")
3. Добавьте email вашего Service Account (из поля `client_email` в JSON файле)
4. Дайте права **"Редактор"**
5. Снимите галочку **"Уведомлять пользователей"**

### 4. Запустите тестирование

```bash
# Соберите новый Docker образ с credentials
docker-compose build

# Запустите с настройками Dimbopizza
docker-compose --env-file test-dimbopizza-env up

# В другом терминале запустите тест
./scripts/test_dimbopizza_sheets.sh
```

## 🔍 Проверка результата

После запуска теста:

1. **Откройте таблицу**: https://docs.google.com/spreadsheets/d/1K_g-EGPQgu4aFv4bIPP6yE_raHyUrlr6GYi-MTEJtu4/edit

2. **Проверьте заголовки** (должны появиться в первой строке):
   ```
   ID заказа | Дата создания | Имя клиента | Телефон | Email | Состав заказа | ...
   ```

3. **Проверьте данные заказов** (должны появиться начиная со второй строки):
   - Самый новый заказ всегда в строке 2
   - Данные на русском языке отображаются корректно
   - Все 16 колонок заполнены

## ❌ Возможные ошибки

### "403 Forbidden"
- Service Account не имеет доступа к таблице
- **Решение**: Поделитесь таблицей с email из `client_email`

### "400 Invalid credentials"
- Неправильный формат JSON файла
- **Решение**: Проверьте, что скопировали весь файл целиком

### "404 Spreadsheet not found"
- Неправильный ID таблицы
- **Решение**: Проверьте `GOOGLE_SHEETS_SPREADSHEET_ID` в `test-dimbopizza-env`

## 📊 Ожидаемый результат

После успешной настройки в таблице должны появиться:

| A | B | C | D | E | F |
|---|---|---|---|---|---|
| **ID заказа** | **Дата создания** | **Имя клиента** | **Телефон** | **Email** | **Состав заказа** |
| 123 | 28.01.2025 15:30 | Тест Dimbopizza | +79991234567 | dimbopizza_test@... | Маргарита x1 (500₽) |

И так далее для всех 16 колонок.

---

## 🔧 Файлы для редактирования:

1. **`config/google-credentials.json`** - вставьте содержимое вашего JSON файла
2. **`test-dimbopizza-env`** - уже настроен для вашей таблицы
3. **Права доступа** - поделитесь таблицей с Service Account

После этого интеграция должна работать! 🎉