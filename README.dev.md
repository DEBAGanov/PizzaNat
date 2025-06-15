# PizzaNat - Локальная разработка

## Запуск в режиме разработки

### Переменные окружения

Создайте файл `.env.dev` в корне проекта:

```bash
# База данных
DB_PASSWORD=strong_password_change_me

# MinIO S3
MINIO_ROOT_USER=accesskey
MINIO_ROOT_PASSWORD=secretkey

# JWT
JWT_SECRET=YTg1MmViNjM0MWY5MTk4NTk1OWM4YzhjZTRlMjIzMTZmNWI1ODk3YmY3YmI3NjhjNWFmYTU1NDYzY2E3OGVmMg==

# Telegram Gateway
TELEGRAM_GATEWAY_ACCESS_TOKEN=AAGCGwAAIlEzNcCeEbrV5r-w65s_0edegXThOhJ2nq-eBw
TELEGRAM_GATEWAY_CALLBACK_URL=http://localhost:8080/api/v1/auth/telegram-gateway/callback

# Telegram Auth Bot (основной)
TELEGRAM_AUTH_BOT_TOKEN=7819187384:AAGJNn0cwfJ7Nsv_N25h75eggEmqmD5WZG4
TELEGRAM_AUTH_BOT_USERNAME=PizzaNatBot
TELEGRAM_AUTH_WEBHOOK_URL=http://localhost:8080/api/v1/telegram/webhook

# Telegram Admin Bot (для уведомлений о заказах)
TELEGRAM_ADMIN_BOT_TOKEN=8052456616:AAEoAzBfr3jRpylDmxR-azNBSqimthPCHeg
TELEGRAM_ADMIN_BOT_USERNAME=PizzaNatOrders_bot
```

### Команды запуска

```bash
# Запуск всех сервисов
docker-compose -f docker-compose.dev.yml --env-file .env.dev up -d

# Просмотр логов
docker-compose -f docker-compose.dev.yml logs -f app

# Остановка
docker-compose -f docker-compose.dev.yml down

# Полная очистка (включая volumes)
docker-compose -f docker-compose.dev.yml down -v
```

### Доступные сервисы

- **Приложение**: http://localhost:8080
- **PostgreSQL**: localhost:5432
- **MinIO Console**: http://localhost:9001 (accesskey/secretkey)
- **MinIO API**: http://localhost:9000

### Особенности dev режима

1. **База данных**: Локальная PostgreSQL вместо удаленной
2. **Профиль**: `SPRING_PROFILES_ACTIVE=dev`
3. **Логирование**: Более подробное (DEBUG уровень)
4. **JPA**: `hibernate.ddl-auto=update` и `show-sql=true`
5. **Health checks**: `show-details=when_authorized`
6. **S3**: Локальный MinIO вместо Timeweb S3

### Тестирование админского бота

После запуска можно тестировать новый функционал:

1. **Регистрация администратора**:
   - Найдите бота `@PizzaNatOrders_bot` в Telegram
   - Отправьте команду `/register`

2. **Тестирование уведомлений**:
   - Создайте заказ через API или приложение
   - Проверьте получение уведомления в админском боте

3. **Управление статусами**:
   - Используйте inline кнопки для изменения статуса заказа
   - Проверьте команды `/stats` и `/orders`

### Отладка

```bash
# Проверка статуса контейнеров
docker-compose -f docker-compose.dev.yml ps

# Подключение к базе данных
docker exec -it pizzanat-postgres-dev psql -U pizzanat_user -d pizzanat_db

# Просмотр логов конкретного сервиса
docker-compose -f docker-compose.dev.yml logs -f postgres
docker-compose -f docker-compose.dev.yml logs -f minio
```