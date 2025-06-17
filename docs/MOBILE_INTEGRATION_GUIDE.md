# Руководство по интеграции мобильного приложения PizzaNat с бэкендом

## Оглавление
1. [Общая информация](#общая-информация)
2. [Полный список API эндпойнтов](#полный-список-api-эндпойнтов)
3. [Авторизация и аутентификация](#авторизация-и-аутентификация)
4. [SMS авторизация](#sms-авторизация)
5. [Telegram авторизация](#telegram-авторизация)
6. [Работа с корзиной](#работа-с-корзиной)
7. [Работа с заказами](#работа-с-заказами)
8. [Обработка ошибок](#обработка-ошибок)
9. [Примеры кода для Android](#примеры-кода-для-android)

## Общая информация

### Базовые параметры
- **Базовый URL**: `https://ваш-домен.com` или `http://localhost:8080` для разработки
- **Формат данных**: JSON
- **Кодировка**: UTF-8
- **Аутентификация**: JWT Bearer Token
- **Версия API**: v1

### Заголовки запросов
```http
Content-Type: application/json
Accept: application/json
Authorization: Bearer <JWT_TOKEN> // для защищенных эндпойнтов
```

### Структура ответов
Все ответы имеют следующую структуру:
```json
{
  "success": true/false,
  "data": {...}, // данные ответа
  "message": "Описание результата",
  "error": "Описание ошибки" // только при ошибках
}
```

## Полный список API эндпойнтов

### 🔓 Публичные эндпойнты (не требуют авторизации)

#### Системные
- `GET /` - Перенаправление на Swagger UI
- `GET /api/health` - Проверка состояния сервиса

#### Аутентификация
- `GET /api/v1/auth/test` - Тест доступности API аутентификации
- `POST /api/v1/auth/register` - Регистрация нового пользователя
- `POST /api/v1/auth/login` - Аутентификация пользователя

#### SMS Аутентификация
- `POST /api/v1/auth/sms/send-code` - Отправка SMS кода
- `POST /api/v1/auth/sms/verify-code` - Подтверждение SMS кода
- `GET /api/v1/auth/sms/test` - Тест SMS сервиса

#### Telegram Аутентификация
- `POST /api/v1/auth/telegram/init` - Инициализация Telegram аутентификации
- `GET /api/v1/auth/telegram/status/{authToken}` - Проверка статуса Telegram аутентификации
- `GET /api/v1/auth/telegram/test` - Тест Telegram сервиса

#### Telegram Gateway (альтернативный способ)
- `POST /api/v1/auth/telegram-gateway/send-code` - Отправка кода через Telegram
- `POST /api/v1/auth/telegram-gateway/verify-code` - Подтверждение кода
- `GET /api/v1/auth/telegram-gateway/status/{requestId}` - Проверка статуса
- `DELETE /api/v1/auth/telegram-gateway/revoke/{requestId}` - Отмена запроса

#### Каталог
- `GET /api/v1/categories` - Получение списка активных категорий
- `GET /api/v1/categories/{id}` - Получение категории по ID
- `GET /api/v1/products` - Получение всех продуктов (с пагинацией)
- `GET /api/v1/products/{id}` - Получение продукта по ID
- `GET /api/v1/products/category/{categoryId}` - Получение продуктов по категории
- `GET /api/v1/products/special-offers` - Получение специальных предложений
- `GET /api/v1/products/search` - Поиск продуктов

#### Доставка
- `GET /api/v1/delivery-locations` - Получение списка локаций доставки
- `GET /api/v1/delivery-locations/{id}` - Получение локации по ID

#### Корзина (анонимная)
- `GET /api/v1/cart` - Получение корзины
- `POST /api/v1/cart/items` - Добавление товара в корзину
- `PUT /api/v1/cart/items/{productId}` - Обновление количества товара
- `DELETE /api/v1/cart/items/{productId}` - Удаление товара из корзины
- `DELETE /api/v1/cart` - Очистка корзины

### 🔒 Защищенные эндпойнты (требуют JWT токен)

#### Пользователь
- `GET /api/v1/user/profile` - Получение профиля пользователя
- `GET /api/v1/user/me` - Альтернативный endpoint профиля

#### Корзина (авторизованная)
- `POST /api/v1/cart/merge` - Объединение анонимной корзины с пользовательской

#### Заказы
- `POST /api/v1/orders` - Создание нового заказа
- `GET /api/v1/orders/{orderId}` - Получение заказа по ID
- `GET /api/v1/orders/{orderId}/payment-url` - Получение URL для оплаты
- `GET /api/v1/orders` - Получение заказов пользователя

#### Платежи
- `POST /api/v1/payments/robokassa/notify` - Уведомления от Robokassa
- `GET /api/v1/payments/robokassa/success` - Успешная оплата
- `GET /api/v1/payments/robokassa/fail` - Неуспешная оплата

### 👑 Административные эндпойнты (требуют роль ADMIN)

#### Статистика
- `GET /api/v1/admin/stats` - Получение статистики
- `POST /api/v1/admin/upload` - Загрузка изображения

#### Управление продуктами
- `POST /api/v1/admin/products` - Создание продукта
- `PUT /api/v1/admin/products/{productId}` - Обновление продукта
- `DELETE /api/v1/admin/products/{productId}` - Удаление продукта
- `GET /api/v1/admin/products/{productId}` - Получение продукта для редактирования

#### Управление заказами
- `GET /api/v1/admin/orders` - Получение всех заказов (с пагинацией)
- `GET /api/v1/admin/orders/{orderId}` - Получение заказа по ID
- `PUT /api/v1/admin/orders/{orderId}/status` - Обновление статуса заказа

## Авторизация и аутентификация

### Типы авторизации
PizzaNat поддерживает несколько способов авторизации:

1. **Традиционная** (email/username + пароль)
2. **SMS авторизация** (номер телефона + SMS код)
3. **Telegram авторизация** (через Telegram бот)
4. **Telegram Gateway** (альтернативный способ через Telegram)

### JWT токены
После успешной авторизации сервер возвращает JWT токен, который необходимо:
- Сохранить в безопасном хранилище приложения
- Передавать в заголовке `Authorization: Bearer <token>` для защищенных запросов
- Обновлять при истечении срока действия

### Структура ответа авторизации
```json
{
  "token": "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9...",
  "userId": 123,
  "username": "user@example.com",
  "email": "user@example.com",
  "firstName": "Иван",
  "lastName": "Иванов"
}
```

## SMS авторизация

### Отправка SMS кода

**Endpoint**: `POST /api/v1/auth/sms/send-code`

**Запрос**:
```json
{
  "phoneNumber": "+79061382868"
}
```

**Ответ при успехе**:
```json
{
  "success": true,
  "message": "SMS код отправлен",
  "expiresAt": "2025-01-15T14:30:00",
  "codeLength": 4,
  "maskedPhoneNumber": "+7 (906) ***-**-68"
}
```

**Ответ при ошибке**:
```json
{
  "success": false,
  "message": "Некорректный формат номера телефона"
}
```

### Подтверждение SMS кода

**Endpoint**: `POST /api/v1/auth/sms/verify-code`

**Запрос**:
```json
{
  "phoneNumber": "+79061382868",
  "code": "1234"
}
```

**Ответ при успехе**:
```json
{
  "token": "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9...",
  "userId": 123,
  "username": "+79061382868",
  "email": null,
  "firstName": null,
  "lastName": null
}
```

### Особенности SMS авторизации
- SMS коды имеют длину 4 символа
- Время жизни кода: 10 минут
- Максимум 3 попытки ввода кода
- Rate limiting: не более 3 SMS в час на номер
- Поддерживается формат номеров: +7XXXXXXXXXX

## Telegram авторизация

### Инициализация авторизации

**Endpoint**: `POST /api/v1/auth/telegram/init`

**Запрос**:
```json
{
  "userData": {
    "id": 123456789,
    "username": "john_doe",
    "first_name": "Иван",
    "last_name": "Иванов"
  }
}
```

**Ответ при успехе**:
```json
{
  "success": true,
  "authToken": "tg_auth_abc123xyz789",
  "telegramBotUrl": "https://t.me/pizzanat_auth_bot?start=tg_auth_abc123xyz789",
  "expiresAt": "2025-01-15T14:30:00",
  "message": "Перейдите по ссылке для подтверждения аутентификации в Telegram"
}
```

### Проверка статуса авторизации

**Endpoint**: `GET /api/v1/auth/telegram/status/{authToken}`

**Ответ (ожидание)**:
```json
{
  "status": "PENDING",
  "message": "Ожидание подтверждения в Telegram"
}
```

**Ответ (подтверждено)**:
```json
{
  "status": "CONFIRMED",
  "message": "Аутентификация подтверждена",
  "authResponse": {
    "token": "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9...",
    "userId": 123,
    "username": "john_doe",
    "email": null,
    "firstName": "Иван",
    "lastName": "Иванов"
  }
}
```

### Особенности Telegram авторизации
- Токены действительны 15 минут
- Пользователь должен перейти по ссылке и нажать кнопку в боте
- Автоматическое создание пользователя при первой авторизации
- Поддержка username, first_name, last_name из Telegram

## Работа с корзиной

### Получение корзины

**Endpoint**: `GET /api/v1/cart`

**Ответ**:
```json
{
  "id": 123,
  "userId": 456,
  "sessionId": "cart_session_abc123",
  "items": [
    {
      "id": 1,
      "productId": 10,
      "productName": "Пицца Маргарита",
      "productPrice": 599.00,
      "quantity": 2,
      "totalPrice": 1198.00,
      "productImageUrl": "https://example.com/images/pizza1.jpg"
    }
  ],
  "totalAmount": 1198.00,
  "itemsCount": 2,
  "createdAt": "2025-01-15T10:00:00",
  "updatedAt": "2025-01-15T10:30:00"
}
```

### Добавление товара в корзину

**Endpoint**: `POST /api/v1/cart/items`

**Запрос**:
```json
{
  "productId": 10,
  "quantity": 2
}
```

### Управление сессиями корзины
Для анонимных пользователей корзина привязывается к сессии через cookie `CART_SESSION_ID`. При авторизации корзина автоматически связывается с пользователем.

## Работа с заказами

### Создание заказа

**Endpoint**: `POST /api/v1/orders`

**Запрос**:
```json
{
  "deliveryLocationId": 1,
  "deliveryAddress": "ул. Пушкина, д. 10, кв. 5",
  "phone": "+79061382868",
  "comment": "Домофон не работает, звонить по телефону",
  "paymentMethod": "CARD_ONLINE"
}
```

**Ответ**:
```json
{
  "id": 456,
  "userId": 123,
  "status": "PENDING",
  "items": [...],
  "totalAmount": 1198.00,
  "deliveryLocation": {
    "id": 1,
    "name": "Центральный район",
    "address": "г. Москва, Центральный район"
  },
  "deliveryAddress": "ул. Пушкина, д. 10, кв. 5",
  "phone": "+79061382868",
  "comment": "Домофон не работает",
  "paymentMethod": "CARD_ONLINE",
  "createdAt": "2025-01-15T12:00:00"
}
```

### Получение URL для оплаты

**Endpoint**: `GET /api/v1/orders/{orderId}/payment-url`

**Ответ**:
```json
{
  "paymentUrl": "https://auth.robokassa.ru/Merchant/Index.aspx?...",
  "orderId": 456
}
```

## Обработка ошибок

### Коды ошибок HTTP
- `200` - Успешный запрос
- `201` - Ресурс создан
- `400` - Некорректный запрос
- `401` - Не авторизован
- `403` - Доступ запрещен
- `404` - Ресурс не найден
- `429` - Слишком много запросов
- `500` - Внутренняя ошибка сервера

### Структура ошибок
```json
{
  "success": false,
  "message": "Описание ошибки",
  "error": "VALIDATION_ERROR",
  "details": {
    "field": "phoneNumber",
    "message": "Некорректный формат номера телефона"
  }
}
```

### Обработка rate limiting
При превышении лимитов запросов (например, для SMS):
```json
{
  "success": false,
  "message": "Слишком много запросов. Повторите через некоторое время",
  "retryAfterSeconds": 300
}
```

## Примеры кода для Android

### Настройка HTTP клиента

```kotlin
// build.gradle.kts (app)
dependencies {
    implementation("com.squareup.retrofit2:retrofit:2.9.0")
    implementation("com.squareup.retrofit2:converter-gson:2.9.0")
    implementation("com.squareup.okhttp3:logging-interceptor:4.11.0")
}
```

```kotlin
// ApiClient.kt
class ApiClient {
    companion object {
        private const val BASE_URL = "https://ваш-домен.com/"
        
        private val loggingInterceptor = HttpLoggingInterceptor().apply {
            level = HttpLoggingInterceptor.Level.BODY
        }
        
        private val authInterceptor = Interceptor { chain ->
            val token = TokenManager.getToken()
            val request = if (token != null) {
                chain.request().newBuilder()
                    .addHeader("Authorization", "Bearer $token")
                    .build()
            } else {
                chain.request()
            }
            chain.proceed(request)
        }
        
        private val okHttpClient = OkHttpClient.Builder()
            .addInterceptor(loggingInterceptor)
            .addInterceptor(authInterceptor)
            .build()
        
        val retrofit: Retrofit = Retrofit.Builder()
            .baseUrl(BASE_URL)
            .client(okHttpClient)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
    }
}
```

### API интерфейсы

```kotlin
// AuthApi.kt
interface AuthApi {
    @POST("api/v1/auth/sms/send-code")
    suspend fun sendSmsCode(@Body request: SendSmsCodeRequest): SmsCodeResponse
    
    @POST("api/v1/auth/sms/verify-code")
    suspend fun verifySmsCode(@Body request: VerifySmsCodeRequest): AuthResponse
    
    @POST("api/v1/auth/telegram/init")
    suspend fun initTelegramAuth(@Body request: InitTelegramAuthRequest): TelegramAuthResponse
    
    @GET("api/v1/auth/telegram/status/{authToken}")
    suspend fun getTelegramAuthStatus(@Path("authToken") authToken: String): TelegramStatusResponse
}

// ProductApi.kt
interface ProductApi {
    @GET("api/v1/categories")
    suspend fun getCategories(): List<CategoryDTO>
    
    @GET("api/v1/products")
    suspend fun getProducts(@Query("page") page: Int = 0, @Query("size") size: Int = 20): Page<ProductDTO>
    
    @GET("api/v1/products/{id}")
    suspend fun getProduct(@Path("id") id: Int): ProductDTO
}

// CartApi.kt
interface CartApi {
    @GET("api/v1/cart")
    suspend fun getCart(): CartDTO
    
    @POST("api/v1/cart/items")
    suspend fun addToCart(@Body request: AddToCartRequest): CartDTO
    
    @PUT("api/v1/cart/items/{productId}")
    suspend fun updateCartItem(@Path("productId") productId: Int, @Body request: UpdateCartItemRequest): CartDTO
    
    @DELETE("api/v1/cart/items/{productId}")
    suspend fun removeFromCart(@Path("productId") productId: Int): CartDTO
}
```

### Модели данных

```kotlin
// Auth models
data class SendSmsCodeRequest(
    val phoneNumber: String
)

data class VerifySmsCodeRequest(
    val phoneNumber: String,
    val code: String
)

data class SmsCodeResponse(
    val success: Boolean,
    val message: String,
    val expiresAt: String?,
    val codeLength: Int?,
    val maskedPhoneNumber: String?,
    val retryAfterSeconds: Long?
)

data class AuthResponse(
    val token: String,
    val userId: Int,
    val username: String,
    val email: String?,
    val firstName: String?,
    val lastName: String?
)

// Product models
data class CategoryDTO(
    val id: Int,
    val name: String,
    val description: String?,
    val imageUrl: String?,
    val isActive: Boolean
)

data class ProductDTO(
    val id: Int,
    val name: String,
    val description: String,
    val price: Double,
    val categoryId: Int,
    val categoryName: String,
    val imageUrl: String?,
    val isActive: Boolean,
    val isSpecialOffer: Boolean
)

// Cart models
data class CartDTO(
    val id: Int?,
    val userId: Int?,
    val sessionId: String?,
    val items: List<CartItemDTO>,
    val totalAmount: Double,
    val itemsCount: Int,
    val createdAt: String,
    val updatedAt: String
)

data class CartItemDTO(
    val id: Int,
    val productId: Int,
    val productName: String,
    val productPrice: Double,
    val quantity: Int,
    val totalPrice: Double,
    val productImageUrl: String?
)
```

### Репозитории

```kotlin
// AuthRepository.kt
class AuthRepository {
    private val authApi = ApiClient.retrofit.create(AuthApi::class.java)
    
    suspend fun sendSmsCode(phoneNumber: String): Result<SmsCodeResponse> {
        return try {
            val response = authApi.sendSmsCode(SendSmsCodeRequest(phoneNumber))
            Result.success(response)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
    
    suspend fun verifySmsCode(phoneNumber: String, code: String): Result<AuthResponse> {
        return try {
            val response = authApi.verifySmsCode(VerifySmsCodeRequest(phoneNumber, code))
            TokenManager.saveToken(response.token)
            Result.success(response)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
    
    suspend fun initTelegramAuth(userData: TelegramUserData): Result<TelegramAuthResponse> {
        return try {
            val response = authApi.initTelegramAuth(InitTelegramAuthRequest(userData))
            Result.success(response)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
    
    suspend fun checkTelegramAuthStatus(authToken: String): Result<TelegramStatusResponse> {
        return try {
            val response = authApi.getTelegramAuthStatus(authToken)
            if (response.status == "CONFIRMED" && response.authResponse != null) {
                TokenManager.saveToken(response.authResponse.token)
            }
            Result.success(response)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}
```

### Управление токенами

```kotlin
// TokenManager.kt
object TokenManager {
    private const val PREF_NAME = "auth_prefs"
    private const val TOKEN_KEY = "jwt_token"
    
    private fun getPrefs(context: Context): SharedPreferences {
        return context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
    }
    
    fun saveToken(context: Context, token: String) {
        getPrefs(context).edit().putString(TOKEN_KEY, token).apply()
    }
    
    fun getToken(context: Context): String? {
        return getPrefs(context).getString(TOKEN_KEY, null)
    }
    
    fun clearToken(context: Context) {
        getPrefs(context).edit().remove(TOKEN_KEY).apply()
    }
    
    fun isLoggedIn(context: Context): Boolean {
        return getToken(context) != null
    }
}
```

### Пример использования в Activity/Fragment

```kotlin
// SmsAuthActivity.kt
class SmsAuthActivity : AppCompatActivity() {
    private val authRepository = AuthRepository()
    
    private fun sendSmsCode(phoneNumber: String) {
        lifecycleScope.launch {
            try {
                showLoading(true)
                val result = authRepository.sendSmsCode(phoneNumber)
                
                result.onSuccess { response ->
                    if (response.success) {
                        showCodeInput()
                        showMessage(response.message)
                    } else {
                        showError(response.message)
                    }
                }.onFailure { exception ->
                    showError("Ошибка отправки SMS: ${exception.message}")
                }
            } finally {
                showLoading(false)
            }
        }
    }
    
    private fun verifySmsCode(phoneNumber: String, code: String) {
        lifecycleScope.launch {
            try {
                showLoading(true)
                val result = authRepository.verifySmsCode(phoneNumber, code)
                
                result.onSuccess { authResponse ->
                    // Авторизация успешна
                    navigateToMainScreen()
                }.onFailure { exception ->
                    showError("Неверный код: ${exception.message}")
                }
            } finally {
                showLoading(false)
            }
        }
    }
}
```

### Обработка Telegram авторизации

```kotlin
// TelegramAuthActivity.kt
class TelegramAuthActivity : AppCompatActivity() {
    private val authRepository = AuthRepository()
    private var authToken: String? = null
    private var statusCheckJob: Job? = null
    
    private fun initTelegramAuth() {
        lifecycleScope.launch {
            try {
                // Получаем данные пользователя из Telegram SDK или Intent
                val userData = getTelegramUserData()
                val result = authRepository.initTelegramAuth(userData)
                
                result.onSuccess { response ->
                    if (response.success) {
                        authToken = response.authToken
                        showTelegramButton(response.telegramBotUrl)
                        startStatusChecking()
                    } else {
                        showError(response.message)
                    }
                }.onFailure { exception ->
                    showError("Ошибка инициализации: ${exception.message}")
                }
            } catch (e: Exception) {
                showError("Ошибка: ${e.message}")
            }
        }
    }
    
    private fun startStatusChecking() {
        statusCheckJob = lifecycleScope.launch {
            while (authToken != null) {
                delay(2000) // Проверяем каждые 2 секунды
                
                val result = authRepository.checkTelegramAuthStatus(authToken!!)
                result.onSuccess { statusResponse ->
                    when (statusResponse.status) {
                        "CONFIRMED" -> {
                            // Авторизация подтверждена
                            statusCheckJob?.cancel()
                            navigateToMainScreen()
                            return@launch
                        }
                        "EXPIRED" -> {
                            statusCheckJob?.cancel()
                            showError("Время авторизации истекло")
                            return@launch
                        }
                        "PENDING" -> {
                            // Продолжаем ждать
                        }
                    }
                }.onFailure {
                    // Игнорируем ошибки при проверке статуса
                }
            }
        }
    }
    
    override fun onDestroy() {
        super.onDestroy()
        statusCheckJob?.cancel()
    }
}
```

## Рекомендации по безопасности

1. **Храните JWT токены в безопасном месте** (Android Keystore или EncryptedSharedPreferences)
2. **Проверяйте SSL сертификаты** при HTTPS соединениях
3. **Не логируйте чувствительные данные** (токены, пароли, коды)
4. **Обрабатывайте истечение токенов** и обновляйте их при необходимости
5. **Валидируйте входные данные** на стороне клиента
6. **Используйте ProGuard/R8** для обфускации кода в релизных сборках

## Тестирование

### Swagger UI
Для тестирования API используйте Swagger UI: `https://ваш-домен.com/swagger-ui.html`

### Postman коллекция
В проекте есть готовые скрипты для тестирования API в папке `scripts/`

### Тестовые данные
- **SMS**: Используйте номер `+79061382868` для тестирования
- **Telegram**: Настройте тестового бота согласно документации в `docs/TELEGRAM_SETUP.md`

---

**Дата создания**: 2025-01-17  
**Версия**: 1.0  
**Автор**: PizzaNat Development Team
