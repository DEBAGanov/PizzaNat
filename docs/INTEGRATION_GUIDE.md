# PizzaNat Integration Guide

## Overview

This guide provides comprehensive instructions for integrating with all external services and components used by the PizzaNat application. It covers setup procedures, authentication, API usage, and troubleshooting for each integration.

---

## Payment Gateway Integrations

### YooKassa Integration

**Description**: Primary payment gateway for card payments and SBP (Fast Payment System).

#### Setup Requirements
1. **YooKassa Account**: Register at [yookassa.ru](https://yookassa.ru)
2. **Shop Configuration**: Complete merchant verification
3. **API Credentials**: Obtain Shop ID and Secret Key
4. **Webhook URL**: Configure webhook endpoint

#### Configuration
```yaml
# application.yml
yookassa:
  enabled: true
  shop-id: ${YOOKASSA_SHOP_ID}
  secret-key: ${YOOKASSA_SECRET_KEY}
  api-url: "https://api.yookassa.ru/v3"
  webhook:
    enabled: true
    url: "${BASE_URL}/api/v1/payments/yookassa/webhook"
  sbp:
    enabled: true
```

#### Integration Steps

1. **Create Payment**
```java
@Autowired
private YooKassaPaymentService yooKassaService;

CreatePaymentRequest request = CreatePaymentRequest.builder()
    .orderId(orderId)
    .amount(totalAmount)
    .currency("RUB")
    .paymentMethod("bank_card")
    .returnUrl("https://yourapp.com/payment/success")
    .description("Payment for order " + orderNumber)
    .build();

PaymentResponse payment = yooKassaService.createPayment(request);
```

2. **Handle Webhook**
```java
@PostMapping("/api/v1/payments/yookassa/webhook")
public ResponseEntity<Void> handleWebhook(@RequestBody WebhookRequest webhook) {
    yooKassaService.processWebhook(webhook);
    return ResponseEntity.ok().build();
}
```

3. **Check Payment Status**
```java
PaymentStatusResponse status = yooKassaService.getPaymentStatus(paymentId);
if ("succeeded".equals(status.getStatus())) {
    // Process successful payment
    orderService.markOrderAsPaid(orderId);
}
```

#### Webhook Setup
Configure webhook in YooKassa dashboard:
- **URL**: `https://yourdomain.com/api/v1/payments/yookassa/webhook`
- **Events**: `payment.succeeded`, `payment.canceled`, `payment.waiting_for_capture`
- **HTTP Method**: POST
- **Format**: JSON

#### SBP (Fast Payment System)
```java
// Get available banks
List<SbpBankResponse> banks = yooKassaService.getSbpBanks();

// Create SBP payment
CreatePaymentRequest sbpRequest = CreatePaymentRequest.builder()
    .orderId(orderId)
    .amount(totalAmount)
    .paymentMethod("sbp")
    .confirmation(ConfirmationRequest.builder()
        .type("redirect")
        .returnUrl("https://yourapp.com/payment/success")
        .build())
    .build();
```

#### Error Handling
```java
try {
    PaymentResponse payment = yooKassaService.createPayment(request);
} catch (PaymentException e) {
    log.error("Payment creation failed: {}", e.getMessage());
    // Handle payment failure
    return ResponseEntity.badRequest().body("Payment failed: " + e.getMessage());
}
```

---

### Robokassa Integration

**Description**: Alternative payment gateway for card payments.

#### Configuration
```yaml
robokassa:
  enabled: false  # Enable if needed
  merchant-login: ${ROBOKASSA_MERCHANT_LOGIN}
  password1: ${ROBOKASSA_PASSWORD1}
  password2: ${ROBOKASSA_PASSWORD2}
  test-mode: false
```

#### Integration
```java
@PostMapping("/api/v1/payments/robokassa/notify")
public ResponseEntity<String> handleNotification(
        @RequestParam Map<String, String> params) {
    
    if (paymentService.validateRobokassaSignature(params)) {
        paymentService.processRobokassaPayment(params);
        return ResponseEntity.ok("OK");
    }
    return ResponseEntity.badRequest().body("Invalid signature");
}
```

---

## Telegram Bot Integration

### Customer Bot Setup

**Description**: Main customer-facing bot for authentication and notifications.

#### Prerequisites
1. **Create Bot**: Message @BotFather on Telegram
2. **Get Token**: Save the bot token
3. **Set Commands**: Configure bot commands
4. **Webhook Setup**: Configure webhook URL

#### Bot Creation Steps
1. Start chat with @BotFather
2. Send `/newbot`
3. Choose bot name and username
4. Save the provided token

#### Configuration
```yaml
telegram:
  bot:
    enabled: true
    token: ${TELEGRAM_BOT_TOKEN}
    username: ${TELEGRAM_BOT_USERNAME}
    webhook:
      enabled: true
      url: "${BASE_URL}/api/v1/telegram/webhook"
```

#### Webhook Setup
```bash
# Set webhook URL
curl -X POST "https://api.telegram.org/bot${BOT_TOKEN}/setWebhook" \
     -H "Content-Type: application/json" \
     -d '{"url": "https://yourdomain.com/api/v1/telegram/webhook"}'

# Verify webhook
curl "https://api.telegram.org/bot${BOT_TOKEN}/getWebhookInfo"
```

#### Message Handling
```java
@PostMapping("/api/v1/telegram/webhook")
public ResponseEntity<Void> handleWebhook(@RequestBody Update update) {
    telegramWebhookService.processUpdate(update);
    return ResponseEntity.ok().build();
}
```

#### Authentication Flow
1. **Initialize Authentication**
```java
TelegramAuthResponse auth = telegramAuthService.initializeAuth(phoneNumber);
// Send QR code or deep link to user
```

2. **Handle Bot Start**
```java
// Bot receives /start command with auth token
@Component
public class TelegramBotHandler {
    
    public void handleStart(Update update, String authToken) {
        Long telegramId = update.getMessage().getFrom().getId();
        telegramAuthService.completeAuth(authToken, telegramId);
    }
}
```

#### Bot Commands
Configure these commands via @BotFather:
```
start - Start authentication process
status - Check order status
help - Show help information
cancel - Cancel current operation
```

---

### Admin Bot Setup

**Description**: Administrative bot for order management and notifications.

#### Configuration
```yaml
telegram:
  admin:
    bot:
      enabled: true
      token: ${TELEGRAM_ADMIN_BOT_TOKEN}
      username: ${TELEGRAM_ADMIN_BOT_USERNAME}
    chat-ids: ${TELEGRAM_ADMIN_CHAT_IDS}
```

#### Admin Notifications
```java
@Component
public class AdminNotificationService {
    
    public void notifyNewOrder(Order order) {
        String message = String.format(
            "🍕 New Order #%s\n" +
            "Customer: %s\n" +
            "Amount: %.2f ₽\n" +
            "Address: %s",
            order.getOrderNumber(),
            order.getCustomerName(),
            order.getFinalAmount(),
            order.getDeliveryAddress()
        );
        
        adminBotService.sendToAllAdmins(message);
    }
}
```

---

## SMS Service Integration

### Exolve SMS Service

**Description**: SMS provider for authentication codes and notifications.

#### Configuration
```yaml
exolve:
  enabled: true
  api-url: "https://api.exolve.ru"
  username: ${EXOLVE_USERNAME}
  password: ${EXOLVE_PASSWORD}
  from: ${EXOLVE_FROM:PizzaNat}
```

#### SMS Sending
```java
@Service
public class SmsService {
    
    public SmsResponse sendVerificationCode(String phoneNumber, String code) {
        String message = String.format(
            "Your PizzaNat verification code: %s. Valid for 5 minutes.", 
            code
        );
        
        return exolveService.sendSms(phoneNumber, message);
    }
}
```

#### Error Handling
```java
try {
    SmsResponse response = smsService.sendVerificationCode(phoneNumber, code);
    if (!response.isSuccess()) {
        throw new SmsException("Failed to send SMS: " + response.getError());
    }
} catch (Exception e) {
    log.error("SMS sending failed for {}: {}", phoneNumber, e.getMessage());
    throw new SmsException("SMS service unavailable");
}
```

---

## Address Suggestion Services

### DaData Integration

**Description**: Russian address autocomplete and validation service.

#### Configuration
```yaml
dadata:
  enabled: true
  api-key: ${DADATA_API_KEY}
  secret-key: ${DADATA_SECRET_KEY}
  api-url: "https://suggestions.dadata.ru/suggestions/api/4_1/rs"
```

#### Address Suggestions
```java
@Service
public class DaDataAddressSuggestionService implements AddressSuggestionService {
    
    @Override
    public List<AddressSuggestion> getSuggestions(String query, int limit) {
        DaDataRequest request = DaDataRequest.builder()
            .query(query)
            .count(limit)
            .locations(List.of(
                Location.builder()
                    .region("Марий Эл")
                    .city("Волжск")
                    .build()
            ))
            .build();
            
        return daDataClient.suggest(request);
    }
}
```

### Yandex Maps Integration

**Description**: Alternative address service using Yandex Maps API.

#### Configuration
```yaml
yandex-maps:
  enabled: true
  api-key: ${YANDEX_MAPS_API_KEY}
  api-url: "https://geocode-maps.yandex.ru/1.x/"
```

#### Geocoding
```java
@Service
public class YandexMapsAddressSuggestionService {
    
    public List<AddressSuggestion> geocodeAddress(String address) {
        String url = String.format(
            "%s?apikey=%s&format=json&geocode=%s",
            apiUrl, apiKey, URLEncoder.encode(address, StandardCharsets.UTF_8)
        );
        
        YandexGeoResponse response = restTemplate.getForObject(url, YandexGeoResponse.class);
        return parseResponse(response);
    }
}
```

### Nominatim Integration

**Description**: Free OpenStreetMap-based geocoding service.

#### Configuration
```yaml
nominatim:
  enabled: true
  base-url: "https://nominatim.openstreetmap.org"
  user-agent: "PizzaNat/1.0"
```

#### Address Search
```java
@Service
public class NominatimAddressSuggestionService {
    
    public List<AddressSuggestion> search(String query) {
        String url = String.format(
            "%s/search?q=%s&format=json&countrycodes=ru&limit=10",
            baseUrl, URLEncoder.encode(query, StandardCharsets.UTF_8)
        );
        
        NominatimResult[] results = restTemplate.getForObject(url, NominatimResult[].class);
        return Arrays.stream(results)
            .map(this::convertToSuggestion)
            .collect(Collectors.toList());
    }
}
```

---

## File Storage Integration

### MinIO S3 Storage

**Description**: S3-compatible object storage for product images and documents.

#### Setup
1. **Install MinIO**: Use Docker or download binary
2. **Create Buckets**: Set up required buckets
3. **Configure Access**: Set access keys and permissions

#### Docker Setup
```bash
docker run -p 9000:9000 -p 9001:9001 \
  -e "MINIO_ROOT_USER=your-access-key" \
  -e "MINIO_ROOT_PASSWORD=your-secret-key" \
  minio/minio server /data --console-address ":9001"
```

#### Configuration
```yaml
minio:
  endpoint: ${MINIO_ENDPOINT:http://localhost:9000}
  access-key: ${MINIO_ACCESS_KEY}
  secret-key: ${MINIO_SECRET_KEY}
  bucket-name: ${MINIO_BUCKET_NAME:pizzanat}
```

#### File Upload
```java
@Service
public class S3Service {
    
    public String uploadImage(MultipartFile file, String folder) {
        try {
            String fileName = generateFileName(file.getOriginalFilename());
            String objectName = folder + "/" + fileName;
            
            minioClient.putObject(
                PutObjectArgs.builder()
                    .bucket(bucketName)
                    .object(objectName)
                    .stream(file.getInputStream(), file.getSize(), -1)
                    .contentType(file.getContentType())
                    .build()
            );
            
            return publicUrl + "/" + bucketName + "/" + objectName;
        } catch (Exception e) {
            throw new StorageException("Failed to upload file", e);
        }
    }
}
```

#### Bucket Setup
```java
@PostConstruct
public void initializeBuckets() {
    try {
        if (!minioClient.bucketExists(BucketExistsArgs.builder().bucket(bucketName).build())) {
            minioClient.makeBucket(MakeBucketArgs.builder().bucket(bucketName).build());
            
            // Set public read policy for product images
            String policy = createPublicReadPolicy(bucketName);
            minioClient.setBucketPolicy(
                SetBucketPolicyArgs.builder()
                    .bucket(bucketName)
                    .config(policy)
                    .build()
            );
        }
    } catch (Exception e) {
        log.error("Failed to initialize MinIO buckets", e);
    }
}
```

---

## Database Integration

### PostgreSQL Setup

**Description**: Primary database for application data.

#### Configuration
```yaml
spring:
  datasource:
    url: ${DATABASE_URL:jdbc:postgresql://localhost:5432/pizzanat}
    username: ${DATABASE_USERNAME:pizzanat_user}
    password: ${DATABASE_PASSWORD:secure_password}
    driver-class-name: org.postgresql.Driver
  jpa:
    hibernate:
      ddl-auto: validate
    show-sql: false
    properties:
      hibernate:
        dialect: org.hibernate.dialect.PostgreSQLDialect
        format_sql: true
```

#### Database Migration
```sql
-- Create database
CREATE DATABASE pizzanat;
CREATE USER pizzanat_user WITH PASSWORD 'secure_password';
GRANT ALL PRIVILEGES ON DATABASE pizzanat TO pizzanat_user;

-- Grant schema permissions
\c pizzanat
GRANT ALL ON SCHEMA public TO pizzanat_user;
GRANT ALL PRIVILEGES ON ALL TABLES IN SCHEMA public TO pizzanat_user;
GRANT ALL PRIVILEGES ON ALL SEQUENCES IN SCHEMA public TO pizzanat_user;
```

---

## Cache Integration

### Redis Setup

**Description**: Caching layer for improved performance.

#### Configuration
```yaml
spring:
  redis:
    host: ${REDIS_HOST:localhost}
    port: ${REDIS_PORT:6379}
    password: ${REDIS_PASSWORD:}
    timeout: 2000ms
    lettuce:
      pool:
        max-active: 8
        max-wait: -1ms
        max-idle: 8
        min-idle: 0
```

#### Cache Usage
```java
@Service
@CacheConfig(cacheNames = "products")
public class ProductService {
    
    @Cacheable(key = "'product:' + #id")
    public ProductDto getProductById(Integer id) {
        // Database query
    }
    
    @CacheEvict(key = "'product:' + #id")
    public void updateProduct(Integer id, ProductDto product) {
        // Update database
    }
    
    @CacheEvict(allEntries = true)
    public void clearProductCache() {
        // Clear all product cache
    }
}
```

---

## Monitoring Integration

### Prometheus Metrics

**Description**: Application metrics collection for monitoring.

#### Configuration
```yaml
management:
  endpoints:
    web:
      exposure:
        include: health,metrics,prometheus
  metrics:
    export:
      prometheus:
        enabled: true
```

#### Custom Metrics
```java
@Component
public class PaymentMetrics {
    
    private final Counter paymentSuccessCounter;
    private final Counter paymentFailureCounter;
    private final Timer paymentProcessingTimer;
    
    public PaymentMetrics(MeterRegistry meterRegistry) {
        this.paymentSuccessCounter = Counter.builder("payments_successful_total")
            .description("Total successful payments")
            .register(meterRegistry);
            
        this.paymentFailureCounter = Counter.builder("payments_failed_total")
            .description("Total failed payments")
            .register(meterRegistry);
            
        this.paymentProcessingTimer = Timer.builder("payment_processing_duration")
            .description("Payment processing time")
            .register(meterRegistry);
    }
    
    public void recordSuccessfulPayment() {
        paymentSuccessCounter.increment();
    }
    
    public void recordFailedPayment() {
        paymentFailureCounter.increment();
    }
    
    public Timer.Sample startPaymentTimer() {
        return Timer.start(paymentProcessingTimer);
    }
}
```

---

## Error Handling and Resilience

### Circuit Breaker Configuration

**Description**: Resilience patterns for external service calls.

#### Configuration
```yaml
resilience4j:
  circuitbreaker:
    instances:
      yookassa:
        failure-rate-threshold: 50
        wait-duration-in-open-state: 30s
        sliding-window-size: 10
        minimum-number-of-calls: 5
      telegram:
        failure-rate-threshold: 60
        wait-duration-in-open-state: 20s
        sliding-window-size: 5
```

#### Usage
```java
@Service
public class PaymentService {
    
    @CircuitBreaker(name = "yookassa", fallbackMethod = "fallbackCreatePayment")
    @Retry(name = "payment")
    public PaymentResponse createPayment(CreatePaymentRequest request) {
        return yooKassaService.createPayment(request);
    }
    
    public PaymentResponse fallbackCreatePayment(CreatePaymentRequest request, Exception ex) {
        log.warn("YooKassa service unavailable, using fallback: {}", ex.getMessage());
        throw new PaymentServiceUnavailableException("Payment service temporarily unavailable");
    }
}
```

---

## Security Considerations

### API Key Management
1. **Environment Variables**: Store all secrets as environment variables
2. **Key Rotation**: Regularly rotate API keys and secrets
3. **Access Control**: Limit API key permissions
4. **Monitoring**: Monitor API key usage

### Webhook Security
1. **Signature Validation**: Verify webhook signatures
2. **IP Whitelisting**: Restrict webhook sources
3. **HTTPS Only**: Use HTTPS for all webhooks
4. **Rate Limiting**: Implement webhook rate limiting

```java
@Component
public class WebhookValidator {
    
    public boolean validateYooKassaSignature(String body, String signature) {
        String expectedSignature = calculateHmacSha256(body, yooKassaSecretKey);
        return constantTimeEquals(signature, expectedSignature);
    }
    
    private boolean constantTimeEquals(String a, String b) {
        return MessageDigest.isEqual(a.getBytes(), b.getBytes());
    }
}
```

---

## Testing Integration

### Integration Tests
```java
@SpringBootTest
@TestPropertySource(properties = {
    "yookassa.enabled=false",
    "telegram.bot.enabled=false",
    "exolve.enabled=false"
})
class IntegrationTest {
    
    @MockBean
    private YooKassaPaymentService yooKassaService;
    
    @Test
    void testOrderCreationWithMockedPayment() {
        // Test with mocked external services
    }
}
```

### External Service Testing
```java
@TestConfiguration
public class TestConfig {
    
    @Bean
    @Primary
    @Profile("test")
    public YooKassaPaymentService mockYooKassaService() {
        return Mockito.mock(YooKassaPaymentService.class);
    }
}
```

This comprehensive integration guide provides developers with detailed instructions for setting up and integrating with all external services and components used by the PizzaNat application.