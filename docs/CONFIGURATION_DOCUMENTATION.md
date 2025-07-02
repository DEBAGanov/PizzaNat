# PizzaNat Configuration Documentation

## Overview

This document provides comprehensive documentation for all configuration classes and components in the PizzaNat application. These configurations control various aspects of the application including security, external integrations, storage, and performance optimization.

---

## Core Configuration Classes

### SecurityConfig

**Package**: `com.baganov.pizzanat.config`  
**Description**: Configures Spring Security for authentication and authorization.

#### Key Features
- JWT-based authentication
- Role-based access control (USER, ADMIN)
- CORS configuration
- Rate limiting integration
- Public endpoints configuration

#### Configuration Properties
```yaml
# application.yml
security:
  jwt:
    secret: ${JWT_SECRET:your-secret-key}
    expiration: 86400000  # 24 hours in milliseconds
  cors:
    allowed-origins: "http://localhost:3000,https://yourdomain.com"
    allowed-methods: "GET,POST,PUT,DELETE,OPTIONS"
    allowed-headers: "*"
    allow-credentials: true
```

#### Key Methods
```java
@Bean
public SecurityFilterChain filterChain(HttpSecurity http) throws Exception;

@Bean
public AuthenticationManager authenticationManager(AuthenticationConfiguration config);

@Bean
public PasswordEncoder passwordEncoder();
```

**Usage**: Automatically configured by Spring Boot

---

### YooKassaConfig

**Package**: `com.baganov.pizzanat.config`  
**Description**: Configuration for YooKassa payment gateway integration.

#### Configuration Properties
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
  timeout:
    connect: 10000
    read: 30000
```

#### Key Components
```java
@Bean
@ConditionalOnProperty("yookassa.enabled")
public YooKassaClient yooKassaClient();

@Bean
public RestTemplate yooKassaRestTemplate();

@ConfigurationProperties("yookassa")
public class YooKassaProperties {
    private boolean enabled;
    private String shopId;
    private String secretKey;
    private String apiUrl;
    // ... other properties
}
```

**Usage**: Enable YooKassa payments by setting `yookassa.enabled=true`

---

### TelegramConfig

**Package**: `com.baganov.pizzanat.config`  
**Description**: Configuration for Telegram bot integration.

#### Configuration Properties
```yaml
# application.yml
telegram:
  bot:
    enabled: true
    token: ${TELEGRAM_BOT_TOKEN}
    username: ${TELEGRAM_BOT_USERNAME}
    webhook:
      enabled: true
      url: "${BASE_URL}/api/v1/telegram/webhook"
  auth:
    enabled: true
    token-expiration: 300000  # 5 minutes
    webhook-enabled: true
  admin:
    bot:
      enabled: true
      token: ${TELEGRAM_ADMIN_BOT_TOKEN}
      username: ${TELEGRAM_ADMIN_BOT_USERNAME}
    chat-ids: ${TELEGRAM_ADMIN_CHAT_IDS:}
```

#### Key Components
```java
@Bean
@ConditionalOnProperty("telegram.bot.enabled")
public TelegramBot telegramBot();

@Bean
@ConditionalOnProperty("telegram.admin.bot.enabled")
public TelegramAdminBot telegramAdminBot();

@ConfigurationProperties("telegram")
public class TelegramProperties {
    private BotConfig bot = new BotConfig();
    private AuthConfig auth = new AuthConfig();
    private AdminConfig admin = new AdminConfig();
}
```

**Usage**: Configure Telegram integration with bot tokens

---

### RedisConfig

**Package**: `com.baganov.pizzanat.config`  
**Description**: Redis configuration for caching and session management.

#### Configuration Properties
```yaml
# application.yml
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
  cache:
    type: redis
    redis:
      time-to-live: 3600000  # 1 hour
```

#### Key Components
```java
@Bean
public RedisTemplate<String, Object> redisTemplate();

@Bean
public CacheManager cacheManager();

@Bean
public RedisConnectionFactory redisConnectionFactory();
```

**Cache Names Configured**:
- `products`: Product data cache
- `categories`: Category cache
- `delivery-zones`: Delivery zone cache
- `address-suggestions`: Address suggestion cache
- `auth-tokens`: Authentication token cache

---

### MinioClientConfig

**Package**: `com.baganov.pizzanat.config`  
**Description**: MinIO S3-compatible storage configuration.

#### Configuration Properties
```yaml
# application.yml
minio:
  endpoint: ${MINIO_ENDPOINT:http://localhost:9000}
  access-key: ${MINIO_ACCESS_KEY}
  secret-key: ${MINIO_SECRET_KEY}
  bucket-name: ${MINIO_BUCKET_NAME:pizzanat}
  region: ${MINIO_REGION:us-east-1}
  public-url: ${MINIO_PUBLIC_URL:http://localhost:9000}
```

#### Key Components
```java
@Bean
public MinioClient minioClient();

@Bean
public S3Service s3Service();

@ConfigurationProperties("minio")
public class MinioProperties {
    private String endpoint;
    private String accessKey;
    private String secretKey;
    private String bucketName;
    private String region;
    private String publicUrl;
}
```

**Usage**: File upload and storage service configuration

---

### ExolveConfig

**Package**: `com.baganov.pizzanat.config`  
**Description**: Exolve SMS service configuration.

#### Configuration Properties
```yaml
# application.yml
exolve:
  enabled: true
  api-url: "https://api.exolve.ru"
  username: ${EXOLVE_USERNAME}
  password: ${EXOLVE_PASSWORD}
  from: ${EXOLVE_FROM:PizzaNat}
  timeout:
    connect: 10000
    read: 30000
```

#### Key Components
```java
@Bean
@ConditionalOnProperty("exolve.enabled")
public ExolveClient exolveClient();

@Bean
public RestTemplate exolveRestTemplate();

@ConfigurationProperties("exolve")
public class ExolveProperties {
    private boolean enabled;
    private String apiUrl;
    private String username;
    private String password;
    private String from;
}
```

**Usage**: SMS authentication and notification service

---

### WebConfig

**Package**: `com.baganov.pizzanat.config`  
**Description**: Web MVC configuration including CORS and interceptors.

#### Key Features
- CORS configuration
- Request/response interceptors
- Custom argument resolvers
- Static resource handling

#### Key Components
```java
@Override
public void addCorsMappings(CorsRegistry registry);

@Override
public void addInterceptors(InterceptorRegistry registry);

@Override
public void addArgumentResolvers(List<HandlerMethodArgumentResolver> argumentResolvers);
```

---

### JacksonConfig

**Package**: `com.baganov.pizzanat.config`  
**Description**: JSON serialization/deserialization configuration.

#### Key Features
- Date/time formatting
- Null value handling
- Custom serializers
- Property naming strategy

#### Configuration
```java
@Bean
@Primary
public ObjectMapper objectMapper() {
    ObjectMapper mapper = new ObjectMapper();
    mapper.registerModule(new JavaTimeModule());
    mapper.disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
    mapper.setPropertyNamingStrategy(PropertyNamingStrategies.SNAKE_CASE);
    return mapper;
}
```

---

### ResilienceConfig

**Package**: `com.baganov.pizzanat.config`  
**Description**: Circuit breaker and resilience patterns configuration.

#### Configuration Properties
```yaml
# application.yml
resilience4j:
  circuitbreaker:
    instances:
      yookassa:
        failure-rate-threshold: 50
        wait-duration-in-open-state: 30s
        sliding-window-size: 10
      telegram:
        failure-rate-threshold: 60
        wait-duration-in-open-state: 20s
        sliding-window-size: 5
  retry:
    instances:
      payment:
        max-attempts: 3
        wait-duration: 1s
      sms:
        max-attempts: 2
        wait-duration: 2s
```

#### Key Components
```java
@Bean
public CircuitBreakerRegistry circuitBreakerRegistry();

@Bean
public RetryRegistry retryRegistry();
```

---

### MetricsConfig

**Package**: `com.baganov.pizzanat.config`  
**Description**: Application metrics and monitoring configuration.

#### Configuration Properties
```yaml
# application.yml
management:
  endpoints:
    web:
      exposure:
        include: health,metrics,prometheus
  endpoint:
    health:
      show-details: when-authorized
  metrics:
    export:
      prometheus:
        enabled: true
    distribution:
      percentiles-histogram:
        http.server.requests: true
```

#### Custom Metrics
- Payment success/failure rates
- Order processing times
- API response times
- Cache hit rates
- Telegram message processing

---

## Environment Configuration

### Application Properties

#### Development Environment
```yaml
# application-dev.yml
spring:
  profiles:
    active: dev
  jpa:
    show-sql: true
    hibernate:
      ddl-auto: update
  h2:
    console:
      enabled: true

logging:
  level:
    com.baganov.pizzanat: DEBUG
    org.springframework.security: DEBUG

debug: true
```

#### Production Environment
```yaml
# application-prod.yml
spring:
  profiles:
    active: prod
  jpa:
    show-sql: false
    hibernate:
      ddl-auto: validate
  datasource:
    url: ${DATABASE_URL}
    username: ${DATABASE_USERNAME}
    password: ${DATABASE_PASSWORD}

logging:
  level:
    com.baganov.pizzanat: INFO
    org.springframework.security: WARN

server:
  port: 8080
  compression:
    enabled: true
```

### Environment Variables

#### Required Environment Variables
```bash
# Database
DATABASE_URL=jdbc:postgresql://localhost:5432/pizzanat
DATABASE_USERNAME=pizzanat_user
DATABASE_PASSWORD=secure_password

# JWT Security
JWT_SECRET=your-256-bit-secret-key

# YooKassa Payment
YOOKASSA_SHOP_ID=your-shop-id
YOOKASSA_SECRET_KEY=your-secret-key

# Telegram Bots
TELEGRAM_BOT_TOKEN=your-bot-token
TELEGRAM_BOT_USERNAME=your-bot-username
TELEGRAM_ADMIN_BOT_TOKEN=your-admin-bot-token
TELEGRAM_ADMIN_CHAT_IDS=chat1,chat2

# File Storage
MINIO_ENDPOINT=http://localhost:9000
MINIO_ACCESS_KEY=your-access-key
MINIO_SECRET_KEY=your-secret-key
MINIO_BUCKET_NAME=pizzanat
MINIO_PUBLIC_URL=https://storage.yourdomain.com

# SMS Service
EXOLVE_USERNAME=your-username
EXOLVE_PASSWORD=your-password
EXOLVE_FROM=PizzaNat

# Redis Cache
REDIS_HOST=localhost
REDIS_PORT=6379
REDIS_PASSWORD=your-redis-password

# Application
BASE_URL=https://yourdomain.com
```

#### Optional Environment Variables
```bash
# Rate Limiting
RATE_LIMIT_ENABLED=true
RATE_LIMIT_REQUESTS_PER_MINUTE=100

# Monitoring
PROMETHEUS_ENABLED=true
HEALTH_CHECK_ENABLED=true

# Feature Flags
TELEGRAM_AUTH_ENABLED=true
SMS_AUTH_ENABLED=true
YOOKASSA_ENABLED=true
ROBOKASSA_ENABLED=false

# External Services
DADATA_API_KEY=your-dadata-key
YANDEX_MAPS_API_KEY=your-yandex-maps-key
NOMINATIM_BASE_URL=https://nominatim.openstreetmap.org
```

---

## Docker Configuration

### Docker Compose Configuration

```yaml
# docker-compose.yml
version: '3.8'

services:
  app:
    build: .
    ports:
      - "8080:8080"
    environment:
      - SPRING_PROFILES_ACTIVE=prod
      - DATABASE_URL=jdbc:postgresql://db:5432/pizzanat
      - REDIS_HOST=redis
      - MINIO_ENDPOINT=http://minio:9000
    depends_on:
      - db
      - redis
      - minio

  db:
    image: postgres:15
    environment:
      POSTGRES_DB: pizzanat
      POSTGRES_USER: pizzanat_user
      POSTGRES_PASSWORD: secure_password
    volumes:
      - postgres_data:/var/lib/postgresql/data

  redis:
    image: redis:7-alpine
    command: redis-server --requirepass your-redis-password
    volumes:
      - redis_data:/data

  minio:
    image: minio/minio
    command: server /data --console-address ":9001"
    environment:
      MINIO_ROOT_USER: your-access-key
      MINIO_ROOT_PASSWORD: your-secret-key
    volumes:
      - minio_data:/data
    ports:
      - "9000:9000"
      - "9001:9001"

volumes:
  postgres_data:
  redis_data:
  minio_data:
```

---

## Configuration Best Practices

### Security
1. **Never hardcode secrets** - Use environment variables
2. **Rotate secrets regularly** - Especially JWT secrets and API keys
3. **Use strong passwords** - For database and Redis connections
4. **Enable HTTPS** - In production environments
5. **Configure CORS carefully** - Restrict to known domains

### Performance
1. **Configure connection pools** - For database and Redis
2. **Enable caching** - For frequently accessed data
3. **Set appropriate timeouts** - For external service calls
4. **Configure circuit breakers** - For resilience
5. **Enable compression** - For HTTP responses

### Monitoring
1. **Enable health checks** - For all critical services
2. **Configure metrics** - For business and technical KPIs
3. **Set up alerting** - For critical failures
4. **Log appropriately** - Different levels for different environments
5. **Monitor external dependencies** - Payment gateways, SMS services

### Deployment
1. **Use profiles** - For different environments
2. **Externalize configuration** - Use ConfigMaps in Kubernetes
3. **Validate configuration** - On startup
4. **Document dependencies** - Required services and versions
5. **Plan for rollbacks** - Configuration versioning

---

## Configuration Validation

### Startup Validation
The application validates critical configurations on startup:

```java
@Component
@ConditionalOnProperty("validation.enabled")
public class ConfigurationValidator implements ApplicationRunner {
    
    @Override
    public void run(ApplicationArguments args) {
        validateYooKassaConfig();
        validateTelegramConfig();
        validateDatabaseConfig();
        validateRedisConfig();
        validateMinioConfig();
    }
}
```

### Health Checks
Configuration health is monitored via Spring Boot Actuator:

- `/actuator/health` - Overall health status
- `/actuator/health/db` - Database connectivity
- `/actuator/health/redis` - Redis connectivity
- `/actuator/health/minio` - MinIO storage connectivity
- `/actuator/health/yookassa` - YooKassa API connectivity

---

## Troubleshooting

### Common Configuration Issues

1. **Database Connection Failed**
   ```bash
   # Check environment variables
   echo $DATABASE_URL
   echo $DATABASE_USERNAME
   
   # Test connection
   psql $DATABASE_URL -c "SELECT 1"
   ```

2. **Redis Connection Failed**
   ```bash
   # Test Redis connectivity
   redis-cli -h $REDIS_HOST -p $REDIS_PORT ping
   ```

3. **MinIO Storage Issues**
   ```bash
   # Check MinIO health
   curl $MINIO_ENDPOINT/minio/health/live
   ```

4. **YooKassa Configuration**
   ```bash
   # Validate YooKassa credentials
   curl -u $YOOKASSA_SHOP_ID:$YOOKASSA_SECRET_KEY \
        https://api.yookassa.ru/v3/me
   ```

5. **Telegram Bot Issues**
   ```bash
   # Check bot token
   curl https://api.telegram.org/bot$TELEGRAM_BOT_TOKEN/getMe
   ```

This comprehensive configuration documentation provides developers and operators with detailed information about all configuration options, environment setup, and troubleshooting guidance for the PizzaNat application.