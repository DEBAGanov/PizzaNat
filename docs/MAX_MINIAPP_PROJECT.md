# MAX Mini App - Проект интеграции

## Содержание
1. [Обзор проекта](#обзор-проекта)
2. [Архитектура](#архитектура)
3. [Сравнение Telegram vs MAX](#сравнение-telegram-vs-max)
4. [Компоненты системы](#компоненты-системы)
5. [API](#api)
6. [Frontend](#frontend)
7. [Backend](#backend)
8. [Безопасность](#безопасность)
9. [Этапы разработки](#этапы-разработки)
10. [Тестирование](#тестирование)
11. [Деплой](#деплой)

---

## Обзор проекта

### Цель
Создание полнофункционального Mini App для мессенджера MAX, идентичного существующему Telegram Mini App, для расширения охвата аудитории и увеличения заказов.

### Боты MAX
| Бот | Назначение | URL |
|-----|------------|-----|
| **ДИМБО** (id121603899498_bot) | Пользовательский бот с Mini App | https://max.ru/id121603899498_bot |
| **ДИМБО Админ** (id121603899498_1_bot) | Административные уведомления о заказах | https://max.ru/id121603899498_1_bot |

### Ключевые функции
- ✅ Каталог товаров с категориями
- ✅ Корзина с добавлением/удалением товаров
- ✅ Оформление заказа с доставкой/самовывозом
- ✅ Оплата через СБП (YooKassa)
- ✅ Авторизация через MAX WebApp
- ✅ Синхронизация с существующим API

---

## Архитектура

### Общая схема
```
┌─────────────────────────────────────────────────────────────────┐
│                        MAX Messenger                              │
├─────────────────────────────────────────────────────────────────┤
│  ┌─────────────────┐    ┌─────────────────┐                     │
│  │  ДИМБО Bot      │    │  ДИМБО Админ    │                     │
│  │  (пользователи) │    │  (сотрудники)   │                     │
│  └────────┬────────┘    └────────┬────────┘                     │
│           │                      │                               │
│           ▼                      ▼                               │
│  ┌─────────────────┐    ┌─────────────────┐                     │
│  │  MAX Mini App   │    │  Уведомления    │                     │
│  │  (WebApp)       │    │  о заказах      │                     │
│  └────────┬────────┘    └─────────────────┘                     │
└───────────┼─────────────────────────────────────────────────────┘
            │
            ▼ HTTPS
┌─────────────────────────────────────────────────────────────────┐
│                    PizzaNat Backend                              │
├─────────────────────────────────────────────────────────────────┤
│  ┌─────────────────┐    ┌─────────────────┐                     │
│  │ /max-miniapp/*  │    │ /api/v1/*       │                     │
│  │ (статика)       │    │ (REST API)      │                     │
│  └────────┬────────┘    └────────┬────────┘                     │
│           │                      │                               │
│  ┌────────▼────────────────────────▼────────┐                   │
│  │         MaxWebAppService                  │                   │
│  │  - Валидация initData                     │                   │
│  │  - Авторизация пользователей              │                   │
│  │  - Генерация JWT токенов                  │                   │
│  └───────────────────────────────────────────┘                   │
│                                                                   │
│  ┌───────────────────────────────────────────┐                   │
│  │         MaxAdminNotificationService       │                   │
│  │  - Уведомления о новых заказах            │                   │
│  │  - Уведомления об оплатах                 │                   │
│  │  - Уведомления о статусах                 │                   │
│  └───────────────────────────────────────────┘                   │
└─────────────────────────────────────────────────────────────────┘
```

### URL структура
```
https://api.dimbopizza.ru/
├── /max-miniapp/           # Статические файлы MAX Mini App
│   ├── menu.html           # Главная страница меню
│   ├── checkout.html       # Оформление заказа
│   ├── max-bridge.js       # MAX SDK
│   ├── max-app.js          # Общая логика
│   ├── max-menu-app.js     # Логика меню
│   └── max-styles.css      # Стили
│
├── /api/v1/
│   ├── /max-webapp/        # API для MAX авторизации
│   │   ├── POST /auth      # Авторизация
│   │   └── POST /validate  # Валидация
│   │
│   ├── /products           # Товары (общий с Telegram)
│   ├── /categories         # Категории (общий с Telegram)
│   ├── /cart               # Корзина (общий с Telegram)
│   ├── /orders             # Заказы (общий с Telegram)
│   └── /payments           # Платежи (общий с Telegram)
│
└── /max-admin/             # Webhook для админ-бота MAX
```

---

## Сравнение Telegram vs MAX

### SDK
| Аспект | Telegram | MAX |
|--------|----------|-----|
| SDK файл | `telegram-web-app.js` | `max-bridge.js` |
| Глобальный объект | `window.Telegram.WebApp` | `window.WebApp` |
| Версия | 7.7+ | Текущая |

### InitData формат
| Параметр | Telegram | MAX |
|----------|----------|-----|
| user | `{"id": 279058397, "first_name": "Name"...}` | `{"id": 400, "first_name": "Вася"...}` |
| auth_date | Unix timestamp (секунды) | Unix timestamp (миллисекунды) |
| query_id | Есть | Есть |
| hash | HMAC-SHA256 | HMAC-SHA256 |

### Валидация
**Telegram:**
```javascript
// 1. Парсим query string
const params = new URLSearchParams(initData);
const hash = params.get('hash');

// 2. Строим data-check-string (без hash, по алфавиту)
const dataCheckString = [...params.entries()]
    .filter(([key]) => key !== 'hash')
    .sort(([a], [b]) => a.localeCompare(b))
    .map(([key, value]) => `${key}=${value}`)
    .join('\n');

// 3. Вычисляем secret key
const secretKey = HMAC_SHA256("WebAppData" + botToken, "");

// 4. Вычисляем hash
const computedHash = HMAC_SHA256(dataCheckString, secretKey);

// 5. Сравниваем
return computedHash === hash;
```

**MAX:**
```javascript
// 1. Парсим query string
const params = new URLSearchParams(initData);
const hash = params.get('hash');

// 2. Строим data-check-string (без hash, по алфавиту)
const dataCheckString = [...params.entries()]
    .filter(([key]) => key !== 'hash')
    .sort(([a], [b]) => a.localeCompare(b))
    .map(([key, value]) => `${key}=${value}`)
    .join('\n');

// 3. Вычисляем secret key (без конкатенации!)
const secretKey = HMAC_SHA256("WebAppData", botToken);

// 4. Вычисляем hash
const computedHash = HMAC_SHA256(dataCheckString, secretKey);

// 5. Сравниваем
return computedHash === hash;
```

### API методы
| Функция | Telegram | MAX |
|---------|----------|-----|
| Готовность | `tg.ready()` | `WebApp.ready()` или событие `WebAppReady` |
| Развернуть | `tg.expand()` | Автоматически |
| Закрыть | `tg.close()` | `WebApp.close()` |
| Кнопка назад | `tg.BackButton.show()` | `WebApp.setupBackButton({isVisible: true})` |
| Событие назад | `tg.BackButton.onClick(cb)` | `WebApp.on('WebAppBackButtonPressed', cb)` |
| Запрос телефона | `tg.requestContact()` | `WebApp.requestPhone()` → событие `WebAppRequestPhone` |
| Открыть ссылку | `tg.openLink(url)` | `WebApp.openLink({url})` |
| Шеринг | `tg.switchInlineQuery()` | `WebApp.shareContent({text, link})` |
| Haptic | `tg.HapticFeedback.impactOccurred('light')` | `WebApp.hapticFeedback('impact', 'light')` |

### События
| Событие | Telegram | MAX |
|---------|----------|-----|
| Готовность | `onEvent('ready')` | `on('WebAppReady')` |
| Изменение темы | `onEvent('themeChanged')` | `on('themeChanged')` |
| Кнопка назад | `BackButton.onClick()` | `on('WebAppBackButtonPressed')` |
| Получение контакта | `onEvent('contactRequested')` | Promise-based `requestPhone()` |

---

## Компоненты системы

### Frontend компоненты

#### 1. max-bridge.js
Скопировать из официальной документации MAX или загрузить с CDN:
```html
<script src="https://static.max.ru/webapp/v1/max-bridge.js"></script>
```

#### 2. max-app.js
Общая логика приложения:
- Инициализация MAX WebApp
- Авторизация
- Общие утилиты
- Отслеживание аналитики

#### 3. max-menu-app.js
Логика страницы меню:
- Загрузка товаров
- Отображение категорий
- Управление корзиной
- Навигация к checkout

#### 4. max-checkout-app.js
Логика оформления заказа:
- Форма доставки
- Выбор оплаты
- Создание заказа
- Обработка платежа

#### 5. max-styles.css
Стили, адаптированные под MAX UI

### Backend компоненты

#### 1. MaxWebAppController
```java
@RestController
@RequestMapping("/api/v1/max-webapp")
public class MaxWebAppController {

    @PostMapping("/auth")
    public ResponseEntity<AuthResponse> authenticate(
        @RequestBody MaxWebAppAuthRequest request);

    @PostMapping("/validate")
    public ResponseEntity<Boolean> validate(
        @RequestBody MaxWebAppValidateRequest request);
}
```

#### 2. MaxWebAppService
```java
@Service
public class MaxWebAppService {

    public AuthResponse authenticateUser(String initDataRaw);
    public boolean validateInitData(String initDataRaw, String botToken);
    private Map<String, String> parseQueryString(String queryString);
    private String buildDataCheckString(Map<String, String> params);
    private String computeHMAC(String data, byte[] key);
}
```

#### 3. MaxAdminNotificationService
```java
@Service
public class MaxAdminNotificationService {

    public void sendNewOrderNotification(Order order);
    public void sendPaymentNotification(Payment payment);
    public void sendOrderStatusUpdate(Order order, OrderStatus newStatus);
}
```

#### 4. MaxBotConfig
```java
@Configuration
@ConfigurationProperties(prefix = "max.bot")
public class MaxBotConfig {
    private String userBotToken;
    private String adminBotToken;
    private String userBotUsername;
    private String adminBotUsername;
    private String apiUrl;
}
```

---

## API

### Эндпоинты авторизации MAX

#### POST /api/v1/max-webapp/auth
Авторизация пользователя через MAX WebApp initData

**Request:**
```json
{
    "initDataRaw": "user=%7B%22id%22%3A400%2C...%7D&auth_date=1733485316394&hash=abc123...",
    "deviceId": "max_device_abc123",
    "platform": "max-miniapp"
}
```

**Response:**
```json
{
    "token": "eyJhbGciOiJIUzI1NiJ9...",
    "userId": 123,
    "username": "max_user_400",
    "firstName": "Вася",
    "lastName": ""
}
```

#### POST /api/v1/max-webapp/validate
Валидация MAX initData

**Request:**
```json
{
    "initDataRaw": "user=%7B%22id%22%3A400...&hash=abc123"
}
```

**Response:**
```json
true
```

### Общие эндпоинты (используются и Telegram, и MAX)

Все существующие эндпоинты работают без изменений:
- `GET /api/v1/products` - товары
- `GET /api/v1/categories` - категории
- `POST /api/v1/cart/items` - добавить в корзину
- `GET /api/v1/cart` - получить корзину
- `POST /api/v1/orders` - создать заказ
- `POST /api/v1/payments/yookassa/create` - создать платеж

---

## Frontend

### Структура файлов
```
src/main/resources/static/max-miniapp/
├── menu.html              # Главная страница меню
├── checkout.html          # Оформление заказа
├── max-bridge.js          # MAX SDK (или CDN ссылка)
├── max-app.js             # Общая логика
├── max-menu-app.js        # Логика меню
├── max-checkout-app.js    # Логика оформления
├── max-styles.css         # Стили
├── max-api.js             # API модуль
└── images/                # Изображения
    └── sbp-logo.png       # Логотип СБП
```

### max-app.js - каркас
```javascript
/**
 * PizzaNat MAX Mini App
 */

class PizzaNatMaxApp {
    constructor() {
        this.max = window.WebApp;  // MAX WebApp API
        this.api = new PizzaAPI();  // Общий API класс
        this.cart = { items: [], totalAmount: 0 };
        this.authToken = null;

        this.init();
    }

    async init() {
        console.log('🚀 Initializing PizzaNat MAX Mini App...');

        // 1. Сигнализируем о готовности (MAX требует это!)
        this.max.ready();

        // 2. Авторизация
        await this.authenticate();

        // 3. Загрузка данных
        await this.loadData();

        // 4. Настройка UI
        this.setupUI();
    }

    async authenticate() {
        if (!this.max?.initData) {
            console.warn('No MAX initData available');
            return;
        }

        try {
            const response = await this.api.authenticateMaxWebApp(
                this.max.initData
            );
            this.authToken = response.token;
            this.api.setAuthToken(this.authToken);
            console.log('✅ MAX user authenticated');
        } catch (error) {
            console.error('MAX authentication failed:', error);
        }
    }

    setupBackButton() {
        // MAX использует события вместо методов
        this.max.setupBackButton({ isVisible: true });
        this.max.on('WebAppBackButtonPressed', () => {
            this.handleBackNavigation();
        });
    }

    async requestPhone() {
        // MAX возвращает Promise
        try {
            const result = await this.max.requestPhone();
            return result.phone;
        } catch (error) {
            console.error('Phone request failed:', error);
            return null;
        }
    }
}
```

---

## Backend

### MaxWebAppService.java
```java
@Slf4j
@Service
@RequiredArgsConstructor
public class MaxWebAppService {

    private final UserRepository userRepository;
    private final JwtService jwtService;
    private final PasswordEncoder passwordEncoder;

    @Value("${max.bot.user-token}")
    private String userBotToken;

    /**
     * Авторизация через MAX WebApp initData
     */
    @Transactional
    public AuthResponse authenticateUser(String initDataRaw) {
        // 1. Валидация
        if (!validateInitData(initDataRaw, userBotToken)) {
            throw new IllegalArgumentException("Invalid MAX initData");
        }

        // 2. Парсинг
        MaxWebAppInitData initData = parseInitData(initDataRaw);

        // 3. Поиск/создание пользователя
        User user = findOrCreateUser(initData.getUser());

        // 4. Генерация JWT
        String token = jwtService.generateToken(user);

        return AuthResponse.builder()
            .token(token)
            .userId(user.getId())
            .username(user.getUsername())
            .firstName(user.getFirstName())
            .lastName(user.getLastName())
            .build();
    }

    /**
     * Валидация MAX initData
     * Документация: https://dev.max.ru/docs/webapps/validation
     */
    public boolean validateInitData(String initDataRaw, String botToken) {
        try {
            // 1. Парсим параметры
            Map<String, String> params = parseQueryString(initDataRaw);
            String hash = params.get("hash");

            if (hash == null) return false;

            // 2. Строим data-check-string (без hash, по алфавиту)
            String dataCheckString = params.entrySet().stream()
                .filter(e -> !"hash".equals(e.getKey()))
                .sorted(Map.Entry.comparingByKey())
                .map(e -> e.getKey() + "=" + e.getValue())
                .collect(Collectors.joining("\n"));

            // 3. Вычисляем secret key
            // MAX: HMAC_SHA256("WebAppData", botToken)
            // (отличие от Telegram: нет конкатенации!)
            byte[] secretKey = computeHMAC("WebAppData", botToken.getBytes());

            // 4. Вычисляем hash
            String computedHash = computeHMAC(dataCheckString, secretKey);

            // 5. Сравниваем
            return computedHash.equals(hash);

        } catch (Exception e) {
            log.error("MAX validation error: {}", e.getMessage());
            return false;
        }
    }

    /**
     * Вычисление HMAC-SHA256
     */
    private String computeHMAC(String data, byte[] key) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            SecretKeySpec spec = new SecretKeySpec(key, "HmacSHA256");
            mac.init(spec);
            byte[] hash = mac.doFinal(data.getBytes(StandardCharsets.UTF_8));
            return Hex.encodeHexString(hash);
        } catch (Exception e) {
            throw new RuntimeException("HMAC computation failed", e);
        }
    }

    /**
     * Поиск или создание пользователя
     */
    private User findOrCreateUser(MaxWebAppUser maxUser) {
        return userRepository.findByMaxId(maxUser.getId())
            .orElseGet(() -> createMaxUser(maxUser));
    }

    private User createMaxUser(MaxWebAppUser maxUser) {
        User user = User.builder()
            .username("max_user_" + maxUser.getId())
            .password(passwordEncoder.encode(UUID.randomUUID().toString()))
            .firstName(maxUser.getFirstName())
            .lastName(maxUser.getLastName())
            .maxId(maxUser.getId())
            .isMaxVerified(true)
            .isActive(true)
            .createdAt(LocalDateTime.now())
            .build();
        return userRepository.save(user);
    }
}
```

---

## Безопасность

### Валидация initData

**Отличия MAX от Telegram:**
```java
// Telegram:
byte[] secretKey = HMAC_SHA256("WebAppData" + botToken, new byte[0]);

// MAX:
byte[] secretKey = HMAC_SHA256("WebAppData", botToken.getBytes());
```

### Rate Limiting
```yaml
max:
  rate-limit:
    auth-per-hour: 10      # Максимум 10 авторизаций в час
    requests-per-minute: 60 # Максимум 60 запросов в минуту
```

### Безопасное хранение токенов
```yaml
max:
  bot:
    user-token: ${MAX_USER_BOT_TOKEN}
    admin-token: ${MAX_ADMIN_BOT_TOKEN}
```

---

## Этапы разработки

### День 1: Конфигурация и DTO
- [ ] Создать `MaxBotConfig.java`
- [ ] Создать `MaxWebAppInitData.java`
- [ ] Создать `MaxWebAppUser.java`
- [ ] Создать `MaxWebAppAuthRequest.java`
- [ ] Обновить `application.yml`

### День 2: MaxWebAppService
- [ ] Создать `MaxWebAppService.java`
- [ ] Реализовать валидацию initData
- [ ] Реализовать авторизацию пользователей
- [ ] Добавить поле `maxId` в User entity
- [ ] Создать миграцию БД

### День 3: MaxWebAppController
- [ ] Создать `MaxWebAppController.java`
- [ ] Реализовать `/api/v1/max-webapp/auth`
- [ ] Реализовать `/api/v1/max-webapp/validate`
- [ ] Настроить Spring Security

### День 4: Frontend max-app.js, max-menu.html
- [ ] Создать `src/main/resources/static/max-miniapp/`
- [ ] Создать `max-app.js` - базовый класс
- [ ] Создать `max-menu-app.js` - логика меню
- [ ] Создать `max-menu.html` - страница меню
- [ ] Создать `max-styles.css` - стили

### День 5: Frontend checkout
- [ ] Создать `max-checkout-app.js`
- [ ] Создать `max-checkout.html`
- [ ] Создать `max-api.js` - API модуль

### День 6: MaxAdminNotificationService
- [ ] Создать `MaxAdminNotificationService.java`
- [ ] Интегрировать с OrderService
- [ ] Интегрировать с PaymentService
- [ ] Добавить обработку событий

### День 7: Тестирование и деплой
- [ ] Unit тесты для MaxWebAppService
- [ ] Интеграционные тесты
- [ ] Тестирование в MAX
- [ ] Деплой на продакшн

---

## Тестирование

### Локальное тестирование
```bash
# 1. Запуск backend
./gradlew bootRun

# 2. Проверка статических файлов
curl http://localhost:8080/max-miniapp/menu.html

# 3. Тест авторизации
curl -X POST http://localhost:8080/api/v1/max-webapp/auth \
  -H "Content-Type: application/json" \
  -d '{"initDataRaw": "test_data"}'
```

### Тестирование в MAX
1. Настроить бота в MAX для партнёров
2. Указать URL Mini App: `https://api.dimbopizza.ru/max-miniapp/menu.html`
3. Протестировать полный цикл заказа

### Чек-лист тестирования
- [ ] Авторизация через MAX
- [ ] Загрузка товаров
- [ ] Добавление в корзину
- [ ] Оформление заказа
- [ ] Оплата через СБП
- [ ] Уведомления в админ-бот

---

## Деплой

### Переменные окружения
```bash
# MAX Bot токены
MAX_USER_BOT_TOKEN=id121603899498_bot_token
MAX_ADMIN_BOT_TOKEN=id121603899498_1_bot_token

# MAX API URL
MAX_API_URL=https://api.max.ru
```

### Обновление docker-compose.yml
```yaml
environment:
  # ... существующие переменные ...

  # MAX Bot настройки
  MAX_BOT_ENABLED: ${MAX_BOT_ENABLED:-true}
  MAX_USER_BOT_TOKEN: ${MAX_USER_BOT_TOKEN:-}
  MAX_ADMIN_BOT_TOKEN: ${MAX_ADMIN_BOT_TOKEN:-}
  MAX_API_URL: ${MAX_API_URL:-https://api.max.ru}
```

### Проверка после деплоя
```bash
# 1. Проверка статических файлов
curl -I https://api.dimbopizza.ru/max-miniapp/menu.html

# 2. Проверка API
curl -X POST https://api.dimbopizza.ru/api/v1/max-webapp/validate \
  -H "Content-Type: application/json" \
  -d '{"initDataRaw": "test"}'

# 3. Проверка в MAX
# Открыть бота @id121603899498_bot в MAX и нажать кнопку меню
```

---

## Полезные ссылки

- [MAX WebApp Introduction](https://dev.max.ru/docs/webapps/introduction)
- [MAX Bridge API](https://dev.max.ru/docs/webapps/bridge)
- [MAX Validation](https://dev.max.ru/docs/webapps/validation)
- [MAX для партнёров](https://max.ru/partners)
