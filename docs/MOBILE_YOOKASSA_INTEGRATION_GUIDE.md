# Руководство по интеграции ЮKassa с мобильным приложением PizzaNat

## Обзор

Данное руководство описывает интеграцию платежной системы ЮKassa с мобильным приложением PizzaNat. Система предоставляет упрощенный API специально адаптированный для Android/iOS приложений.

## Учетные данные для тестирования

### Тестовая среда ЮKassa
- **Shop ID**: `1116141`
- **API ключ**: `test_*grCMbJSK95l5oz0pzlWrl1YeUJsDJusxy9MbxB_0AP0Y`
- **API URL**: `https://api.yookassa.ru/v3`

### Тестовые карты
```
✅ Успешная оплата:
   Номер: 4111 1111 1111 1111
   Срок: 12/24, CVC: 123

❌ Отклоненная оплата:
   Номер: 4000 0000 0000 0002
   Срок: 12/24, CVC: 123

⏳ Требует подтверждения:
   Номер: 4000 0000 0000 3220
   Срок: 12/24, CVC: 123
```

## Mobile API Endpoints

Все endpoints для мобильного приложения находятся по адресу: `/api/v1/mobile/payments`

### 1. Создание платежа
```http
POST /api/v1/mobile/payments/create
Authorization: Bearer {jwt_token}
Content-Type: application/json

{
  "orderId": 123,
  "amount": 1000.00,
  "currency": "RUB",
  "description": "Заказ пиццы #123",
  "paymentMethod": "bank_card",
  "metadata": {
    "orderId": "123",
    "source": "mobile_app"
  }
}
```

**Ответ:**
```json
{
  "success": true,
  "paymentId": "2c5aa890-000f-5000-8000-18db351245c7",
  "paymentUrl": "https://yookassa.ru/checkout/payments/2c5aa890-000f-5000-8000-18db351245c7",
  "status": "pending",
  "amount": 1000.00,
  "currency": "RUB",
  "orderId": 123,
  "description": "Заказ пиццы #123",
  "expiresAt": "2025-01-26T15:30:00Z"
}
```

### 2. Проверка статуса платежа
```http
GET /api/v1/mobile/payments/{paymentId}/status
Authorization: Bearer {jwt_token}
```

**Ответ:**
```json
{
  "success": true,
  "paymentId": "2c5aa890-000f-5000-8000-18db351245c7",
  "status": "succeeded",
  "paid": true,
  "amount": 1000.00,
  "orderId": 123,
  "updatedAt": "2025-01-26T12:00:00Z"
}
```

### 3. Получение банков СБП
```http
GET /api/v1/mobile/payments/sbp/banks
```

**Ответ:**
```json
{
  "success": true,
  "banks": [
    {
      "id": "sberbank",
      "name": "Сбербанк",
      "logoUrl": "https://static.yoomoney.ru/files-front/sberbank_logo.png"
    }
  ],
  "count": 25
}
```

### 4. Отмена платежа
```http
POST /api/v1/mobile/payments/{paymentId}/cancel
Authorization: Bearer {jwt_token}
```

**Ответ:**
```json
{
  "success": true,
  "paymentId": "2c5aa890-000f-5000-8000-18db351245c7",
  "status": "cancelled",
  "cancelled": true,
  "message": "Платеж успешно отменен"
}
```

### 5. Проверка работоспособности
```http
GET /api/v1/mobile/payments/health
```

**Ответ:**
```json
{
  "success": true,
  "status": "healthy",
  "service": "YooKassa Mobile API",
  "timestamp": 1706270400000
}
```

## Интеграция с Android

### Зависимости
```gradle
implementation 'com.squareup.retrofit2:retrofit:2.9.0'
implementation 'com.squareup.retrofit2:converter-gson:2.9.0'
implementation 'com.squareup.okhttp3:logging-interceptor:4.12.0'
```

### Retrofit API интерфейс
```kotlin
interface PaymentApiService {
    @POST("api/v1/mobile/payments/create")
    suspend fun createPayment(
        @Header("Authorization") token: String,
        @Body request: CreatePaymentRequest
    ): Response<PaymentResponse>

    @GET("api/v1/mobile/payments/{paymentId}/status")
    suspend fun getPaymentStatus(
        @Header("Authorization") token: String,
        @Path("paymentId") paymentId: String
    ): Response<PaymentStatusResponse>

    @GET("api/v1/mobile/payments/sbp/banks")
    suspend fun getSbpBanks(): Response<SbpBanksResponse>

    @POST("api/v1/mobile/payments/{paymentId}/cancel")
    suspend fun cancelPayment(
        @Header("Authorization") token: String,
        @Path("paymentId") paymentId: String
    ): Response<CancelPaymentResponse>
}
```

### Модели данных
```kotlin
data class CreatePaymentRequest(
    val orderId: Long,
    val amount: Double,
    val currency: String = "RUB",
    val description: String,
    val paymentMethod: String = "bank_card",
    val metadata: Map<String, String> = emptyMap()
)

data class PaymentResponse(
    val success: Boolean,
    val paymentId: String?,
    val paymentUrl: String?,
    val status: String?,
    val amount: Double?,
    val currency: String?,
    val orderId: Long?,
    val description: String?,
    val expiresAt: String?,
    val error: String?,
    val message: String?
)
```

### Пример использования
```kotlin
class PaymentRepository(private val apiService: PaymentApiService) {

    suspend fun createPayment(
        token: String,
        orderId: Long,
        amount: Double,
        description: String
    ): Result<PaymentResponse> {
        return try {
            val request = CreatePaymentRequest(
                orderId = orderId,
                amount = amount,
                description = description,
                metadata = mapOf(
                    "orderId" to orderId.toString(),
                    "source" to "android_app"
                )
            )

            val response = apiService.createPayment("Bearer $token", request)

            if (response.isSuccessful && response.body()?.success == true) {
                Result.success(response.body()!!)
            } else {
                Result.failure(Exception(response.body()?.message ?: "Ошибка создания платежа"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun checkPaymentStatus(token: String, paymentId: String): Result<PaymentStatusResponse> {
        return try {
            val response = apiService.getPaymentStatus("Bearer $token", paymentId)

            if (response.isSuccessful && response.body()?.success == true) {
                Result.success(response.body()!!)
            } else {
                Result.failure(Exception("Ошибка получения статуса"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}
```

## Интеграция с iOS

### Модель данных (Swift)
```swift
struct CreatePaymentRequest: Codable {
    let orderId: Int
    let amount: Double
    let currency: String
    let description: String
    let paymentMethod: String
    let metadata: [String: String]

    init(orderId: Int, amount: Double, description: String) {
        self.orderId = orderId
        self.amount = amount
        self.currency = "RUB"
        self.description = description
        self.paymentMethod = "bank_card"
        self.metadata = [
            "orderId": String(orderId),
            "source": "ios_app"
        ]
    }
}

struct PaymentResponse: Codable {
    let success: Bool
    let paymentId: String?
    let paymentUrl: String?
    let status: String?
    let amount: Double?
    let currency: String?
    let orderId: Int?
    let description: String?
    let expiresAt: String?
    let error: String?
    let message: String?
}
```

### API Service (Swift)
```swift
class PaymentAPIService {
    private let baseURL = "http://localhost:8080"
    private let session = URLSession.shared

    func createPayment(
        token: String,
        request: CreatePaymentRequest,
        completion: @escaping (Result<PaymentResponse, Error>) -> Void
    ) {
        guard let url = URL(string: "\(baseURL)/api/v1/mobile/payments/create") else {
            completion(.failure(APIError.invalidURL))
            return
        }

        var urlRequest = URLRequest(url: url)
        urlRequest.httpMethod = "POST"
        urlRequest.setValue("application/json", forHTTPHeaderField: "Content-Type")
        urlRequest.setValue("Bearer \(token)", forHTTPHeaderField: "Authorization")

        do {
            urlRequest.httpBody = try JSONEncoder().encode(request)
        } catch {
            completion(.failure(error))
            return
        }

        session.dataTask(with: urlRequest) { data, response, error in
            if let error = error {
                completion(.failure(error))
                return
            }

            guard let data = data else {
                completion(.failure(APIError.noData))
                return
            }

            do {
                let paymentResponse = try JSONDecoder().decode(PaymentResponse.self, from: data)
                completion(.success(paymentResponse))
            } catch {
                completion(.failure(error))
            }
        }.resume()
    }
}
```

## Обработка ошибок

### Типы ошибок
1. **Ошибки аутентификации** (401)
   - Отсутствует или недействительный JWT токен
   - Решение: обновить токен или выполнить повторную аутентификацию

2. **Ошибки валидации** (400)
   - Некорректные данные запроса
   - Решение: проверить формат и содержимое запроса

3. **Ошибки ЮKassa** (500)
   - Проблемы с платежной системой
   - Решение: повторить запрос или уведомить пользователя

### Пример обработки ошибок (Android)
```kotlin
when (result.isSuccess) {
    true -> {
        val payment = result.getOrNull()
        if (payment?.success == true) {
            // Успешное создание платежа
            openPaymentUrl(payment.paymentUrl)
        } else {
            // Ошибка от сервера
            showError(payment?.message ?: "Неизвестная ошибка")
        }
    }
    false -> {
        // Ошибка сети или парсинга
        val error = result.exceptionOrNull()
        when (error) {
            is UnknownHostException -> showError("Проверьте подключение к интернету")
            is SocketTimeoutException -> showError("Превышено время ожидания")
            else -> showError("Ошибка создания платежа")
        }
    }
}
```

## Тестирование

### Запуск тестов
```bash
# Запуск комплексного тестирования
./test_yookassa_stage6_integration.sh

# Настройка тестовой конфигурации
cp env-yookassa-stage6.txt .env
docker-compose restart app
```

### Проверка интеграции
1. Создайте тестовый заказ через мобильное приложение
2. Инициируйте платеж с тестовой картой
3. Проверьте статус платежа
4. Убедитесь в корректной обработке webhook'ов

## Безопасность

### Рекомендации
1. **JWT токены**: храните в защищенном хранилище (Keychain/KeyStore)
2. **HTTPS**: используйте только защищенные соединения в продакшене
3. **Валидация**: проверяйте все данные на клиенте и сервере
4. **Логирование**: не логируйте чувствительные данные платежей

### Настройка CORS для мобильных приложений
```properties
# В application.properties
cors.allowed.origins=android-app://com.pizzanat.app,ios-app://com.pizzanat.app
cors.allowed.methods=GET,POST,PUT,DELETE,OPTIONS
cors.allowed.headers=Authorization,Content-Type,X-Requested-With
cors.allow.credentials=true
```

## Переход в продакшен

### Шаги для активации
1. Получите боевые учетные данные в личном кабинете ЮKassa
2. Обновите конфигурацию:
   ```bash
   YOOKASSA_SHOP_ID=your_production_shop_id
   YOOKASSA_SECRET_KEY=your_production_secret_key
   ```
3. Настройте webhook URL для продакшена
4. Проведите финальное тестирование
5. Обновите мобильное приложение с продакшен URL

### Мониторинг
- Используйте встроенную систему метрик: `/api/v1/payments/metrics/summary`
- Настройте алерты в Telegram для критических событий
- Мониторьте логи приложения и ошибки платежей

## Поддержка

### Контакты
- Документация ЮKassa: https://yookassa.ru/docs/
- Техническая поддержка: https://yookassa.ru/support/
- Тестовые данные: https://yookassa.ru/developers/payment-acceptance/testing-and-going-live/testing

### Полезные ссылки
- [Личный кабинет ЮKassa](https://yookassa.ru/my/)
- [Статусы платежей](https://yookassa.ru/docs/payments/payment-process#payment-status)
- [Webhook уведомления](https://yookassa.ru/docs/payments/solution-for-platforms/notifications)