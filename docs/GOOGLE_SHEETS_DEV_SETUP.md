# Google Sheets DEV Setup - Локальная разработка

## 🔧 Быстрая настройка для dev окружения

### 1. Подготовка credentials

1. **Получите credentials.json** из Google Cloud Console:
   - Создайте Service Account
   - Скачайте JSON ключи
   - Дайте доступ к вашей Google таблице

2. **Поместите файл**:
   ```bash
   cp /path/to/your/credentials.json config/google-credentials.json
   ```

### 2. Настройте Google таблицу

1. **Создайте таблицу** в Google Sheets
2. **Получите ID** из URL:
   ```
   https://docs.google.com/spreadsheets/d/1ABC123def456/edit
   → ID: 1ABC123def456
   ```
3. **Поделитесь с Service Account** (права: Редактор)

### 3. Обновите конфигурацию

Отредактируйте файл `.env.google-sheets-dev`:
```bash
GOOGLE_SHEETS_ENABLED=true
GOOGLE_SHEETS_SPREADSHEET_ID=ваш_реальный_id_таблицы
GOOGLE_SHEETS_DOWNLOAD_FROM_S3=false
SPRING_PROFILES_ACTIVE=dev
```

### 4. Запустите для dev

```bash
# С Google Sheets интеграцией
docker-compose --env-file .env.google-sheets-dev up

# Или с обычными настройками (без Google Sheets)
docker-compose up
```

### 5. Проверьте работу

```bash
# Проверьте здоровье приложения
curl http://localhost:8080/actuator/health

# Создайте тестовый заказ через Swagger UI
open http://localhost:8080/swagger-ui.html
```

## 🧪 Тестирование интеграции

```bash
# Установите переменные для теста
export GOOGLE_SHEETS_ENABLED=true
export GOOGLE_SHEETS_SPREADSHEET_ID="ваш_id"

# Запустите тест (без S3)
./scripts/test_google_sheets_integration.sh
```

## 📊 Что должно произойти

1. **При запуске приложения**: 
   - Google Sheets API инициализируется
   - Credentials загружаются из `/app/config/google-credentials.json`
   
2. **При создании заказа**:
   - Заказ автоматически добавляется в Google таблицу
   - Новая строка появляется в начале (строка 2)
   - Все 16 колонок заполняются данными
   
3. **При изменении статуса**:
   - Статус заказа обновляется в таблице
   - Статус платежа обновляется в таблице

## 🔧 Отладка проблем

### Проблема: "Credentials file not found"
```bash
# Проверьте наличие файла
ls -la config/google-credentials.json

# Проверьте права доступа
chmod 644 config/google-credentials.json
```

### Проблема: "403 Forbidden"
- Убедитесь, что Service Account имеет доступ к таблице
- Проверьте права (должны быть "Редактор")

### Проблема: "Spreadsheet not found" 
- Проверьте правильность SPREADSHEET_ID
- Убедитесь, что таблица существует

### Логи приложения
```bash
# Смотрите логи Google Sheets
docker-compose logs app | grep -i "google\|sheets"

# Проверьте ошибки инициализации
docker-compose logs app | grep -i "error\|failed"
```

## 📝 Примеры переменных окружения

### Полная конфигурация DEV
```bash
# .env.google-sheets-dev
GOOGLE_SHEETS_ENABLED=true
GOOGLE_SHEETS_SPREADSHEET_ID=1K_g-EGPQgu4aFv4bIPP6yE_raHyUrlr6GYi-MTEJtu4
GOOGLE_SHEETS_SHEET_NAME=Заказы
GOOGLE_SHEETS_DOWNLOAD_FROM_S3=false
GOOGLE_SHEETS_CREDENTIALS_PATH=/app/config/google-credentials.json
SPRING_PROFILES_ACTIVE=dev
```

### Отключение Google Sheets
```bash
# .env.without-sheets
GOOGLE_SHEETS_ENABLED=false
SPRING_PROFILES_ACTIVE=dev
```

## 🎯 Готово!

После настройки:
- ✅ Приложение компилируется без ошибок
- ✅ Google Sheets API инициализируется при старте  
- ✅ Новые заказы автоматически попадают в таблицу
- ✅ Статусы обновляются в реальном времени
- ✅ S3 интеграция отключена для dev (более простая настройка)