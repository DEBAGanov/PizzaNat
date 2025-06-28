# Руководство по настройке доменов для PizzaNat

## Обзор архитектуры доменов

После обновления CORS настроек система поддерживает следующую структуру доменов:

```
Frontend Service (React)     Backend Service (Spring Boot)
├── pizzanat.ru             ├── api.pizzanat.ru  
├── Port: 80/443             ├── Port: 8080
├── Nginx                    ├── REST API
└── Static files             └── Database, Business Logic
```

## Настройка DNS в Timeweb Cloud

### Шаг 1: Основной домен (pizzanat.ru)
1. Войдите в панель управления Timeweb Cloud
2. Перейдите в раздел "Домены"
3. Убедитесь, что домен `pizzanat.ru` настроен и указывает на ваш сервер

### Шаг 2: Создание поддомена (api.pizzanat.ru)
1. В разделе "Домены" выберите `pizzanat.ru`
2. Перейдите в "DNS записи"
3. Добавьте A-запись:
   - **Имя**: `api`
   - **Тип**: `A`
   - **Значение**: IP адрес вашего сервера с backend
   - **TTL**: `3600`

### Шаг 3: SSL сертификаты
1. Для `pizzanat.ru` - настройте SSL для фронтенда
2. Для `api.pizzanat.ru` - настройте SSL для backend API

## Обновленные CORS настройки

### Переменные окружения
```bash
# Production настройки
CORS_ALLOWED_ORIGINS=https://pizzanat.ru,https://api.pizzanat.ru
CORS_ALLOWED_METHODS=GET,POST,PUT,DELETE,OPTIONS,PATCH
CORS_ALLOWED_HEADERS=Authorization,Content-Type,X-Requested-With,Accept,Origin,X-Auth-Token,Cache-Control
CORS_EXPOSED_HEADERS=Authorization,Content-Type,X-Total-Count,X-Pagination-Page,X-Pagination-Size
CORS_ALLOW_CREDENTIALS=true
CORS_MAX_AGE=3600

# Development настройки (дополнительно)
# CORS_ALLOWED_ORIGINS=https://pizzanat.ru,https://api.pizzanat.ru,http://localhost:3000,http://localhost:8080
```

### Конфигурация в коде
Система автоматически использует переменные окружения из:
- `WebConfig.java` - для Spring MVC CORS
- `SecurityConfig.java` - для Spring Security CORS

## Структура URL после настройки

### Фронтенд (React)
- **Основной сайт**: `https://pizzanat.ru`
- **Статические файлы**: `https://pizzanat.ru/static/*`
- **Роутинг**: `https://pizzanat.ru/*` (SPA routing)

### Backend (Spring Boot API)
- **API**: `https://api.pizzanat.ru/api/v1/*`
- **Health check**: `https://api.pizzanat.ru/api/v1/health`
- **Swagger UI**: `https://api.pizzanat.ru/swagger-ui.html`
- **API docs**: `https://api.pizzanat.ru/v3/api-docs`

### Мобильное приложение
- **API URL**: `https://api.pizzanat.ru/api/v1/`
- Обновите настройки в Android приложении

## Nginx конфигурация

### Для фронтенда (pizzanat.ru)
```nginx
server {
    listen 80;
    server_name pizzanat.ru;
    return 301 https://$server_name$request_uri;
}

server {
    listen 443 ssl http2;
    server_name pizzanat.ru;
    
    ssl_certificate /path/to/ssl/cert.pem;
    ssl_certificate_key /path/to/ssl/key.pem;
    
    root /usr/share/nginx/html;
    index index.html;

    # React Router поддержка
    location / {
        try_files $uri $uri/ /index.html;
    }

    # Оптимизация статических файлов
    location /static/ {
        expires 1y;
        add_header Cache-Control "public, immutable";
        gzip_static on;
    }
}
```

### Для backend (api.pizzanat.ru)
```nginx
server {
    listen 80;
    server_name api.pizzanat.ru;
    return 301 https://$server_name$request_uri;
}

server {
    listen 443 ssl http2;
    server_name api.pizzanat.ru;
    
    ssl_certificate /path/to/ssl/cert.pem;
    ssl_certificate_key /path/to/ssl/key.pem;

    location / {
        proxy_pass http://localhost:8080;
        proxy_set_header Host $host;
        proxy_set_header X-Real-IP $remote_addr;
        proxy_set_header X-Forwarded-For $proxy_add_x_forwarded_for;
        proxy_set_header X-Forwarded-Proto $scheme;
        
        # CORS headers (дублирование для надежности)
        add_header 'Access-Control-Allow-Origin' 'https://pizzanat.ru' always;
        add_header 'Access-Control-Allow-Methods' 'GET, POST, PUT, DELETE, OPTIONS, PATCH' always;
        add_header 'Access-Control-Allow-Headers' 'Authorization, Content-Type, X-Requested-With, Accept, Origin, X-Auth-Token, Cache-Control' always;
        add_header 'Access-Control-Allow-Credentials' 'true' always;
        
        # Preflight requests
        if ($request_method = 'OPTIONS') {
            add_header 'Access-Control-Allow-Origin' 'https://pizzanat.ru';
            add_header 'Access-Control-Allow-Methods' 'GET, POST, PUT, DELETE, OPTIONS, PATCH';
            add_header 'Access-Control-Allow-Headers' 'Authorization, Content-Type, X-Requested-With, Accept, Origin, X-Auth-Token, Cache-Control';
            add_header 'Access-Control-Allow-Credentials' 'true';
            add_header 'Access-Control-Max-Age' 3600;
            add_header 'Content-Type' 'text/plain charset=UTF-8';
            add_header 'Content-Length' 0;
            return 204;
        }
    }
}
```

## Тестирование CORS

### Проверка настроек
```bash
# Проверка preflight запроса
curl -X OPTIONS https://api.pizzanat.ru/api/v1/health \
  -H "Origin: https://pizzanat.ru" \
  -H "Access-Control-Request-Method: GET" \
  -H "Access-Control-Request-Headers: Authorization,Content-Type" \
  -v

# Проверка обычного запроса
curl -X GET https://api.pizzanat.ru/api/v1/health \
  -H "Origin: https://pizzanat.ru" \
  -v
```

### Ожидаемые заголовки ответа
```
Access-Control-Allow-Origin: https://pizzanat.ru
Access-Control-Allow-Methods: GET, POST, PUT, DELETE, OPTIONS, PATCH
Access-Control-Allow-Headers: Authorization, Content-Type, X-Requested-With, Accept, Origin, X-Auth-Token, Cache-Control
Access-Control-Allow-Credentials: true
Access-Control-Max-Age: 3600
```

## Развертывание

### 1. Обновление backend
```bash
# Пересобрать и развернуть backend с новыми CORS настройками
docker-compose -f docker-compose.production.yml up -d --build
```

### 2. Создание фронтенда
```bash
# Создать React приложение
npx create-react-app pizzanat-frontend
cd pizzanat-frontend

# Настроить API URL
echo "REACT_APP_API_URL=https://api.pizzanat.ru" > .env.production
```

### 3. Обновление мобильного приложения
Обновите базовый URL API в Android приложении:
```java
// Было: https://pizzanat.ru/api/v1/
// Стало: https://api.pizzanat.ru/api/v1/
```

## Мониторинг

### Логи CORS ошибок
```bash
# Проверка логов Spring Boot
docker logs pizzanat-app | grep -i cors

# Проверка логов Nginx
tail -f /var/log/nginx/error.log | grep -i cors
```

### Health checks
- **Frontend**: `https://pizzanat.ru`
- **Backend**: `https://api.pizzanat.ru/api/v1/health`

## Безопасность

### Рекомендации
1. **Не используйте wildcard (*) в production**
2. **Указывайте конкретные домены** в CORS_ALLOWED_ORIGINS
3. **Ограничьте методы** только необходимыми
4. **Используйте HTTPS** для всех доменов
5. **Настройте CSP заголовки** для дополнительной защиты

### CSP заголовки для фронтенда
```nginx
add_header Content-Security-Policy "default-src 'self'; connect-src 'self' https://api.pizzanat.ru; img-src 'self' data: https:; script-src 'self' 'unsafe-inline'; style-src 'self' 'unsafe-inline';" always;
``` 