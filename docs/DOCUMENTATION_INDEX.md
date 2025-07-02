# PizzaNat Complete Documentation Index

## Overview

This is the comprehensive documentation suite for the PizzaNat pizza delivery platform. The documentation covers all aspects of the application including APIs, services, data models, configuration, and integration guides.

**PizzaNat** is a sophisticated Spring Boot application providing:
- Complete pizza ordering and delivery management
- Multi-channel authentication (SMS, Telegram, traditional)
- Payment processing with YooKassa and Robokassa
- Real-time order tracking and notifications
- Administrative dashboard and analytics
- File storage and image management
- Address validation and delivery zone management

---

## Documentation Structure

### 📚 Core Documentation

#### 1. [API Documentation](./API_DOCUMENTATION.md)
**Comprehensive REST API reference with examples**

- **120+ API endpoints** across 23 controllers
- Authentication and authorization guides
- Request/response examples for all endpoints
- Error handling and status codes
- Rate limiting and pagination guidelines
- WebSocket support for real-time updates

**Key Sections**:
- System and Health Check APIs
- Authentication (Traditional, SMS, Telegram)
- Product and Category Management
- Shopping Cart Operations
- Order Management and Tracking
- Payment Processing (YooKassa, Robokassa)
- Delivery and Address Services
- Administrative Functions

#### 2. [Services Documentation](./SERVICES_DOCUMENTATION.md)
**Business logic layer documentation**

- **40+ service classes** with public method documentation
- Core business services (Product, Cart, Order, Auth)
- Payment processing services
- Telegram bot integration services
- Utility and helper services
- Caching and transaction management
- Error handling patterns

**Key Services**:
- `ProductService` - Product CRUD and search
- `CartService` - Shopping cart management
- `OrderService` - Order lifecycle management
- `YooKassaPaymentService` - Payment processing
- `TelegramAuthService` - Telegram authentication
- `DeliveryZoneService` - Delivery management

#### 3. [Data Models Documentation](./DATA_MODELS_DOCUMENTATION.md)
**Complete data structure reference**

- **Entity models** for database mapping
- **DTOs** for API communication
- **Request/Response objects** for specific endpoints
- **Enumerations** for status and type management
- **Validation annotations** and rules
- **JSON serialization examples**

**Key Models**:
- User, Product, Order, Payment entities
- Cart, Category, DeliveryZone models
- Authentication and payment DTOs
- Address and delivery models

#### 4. [Configuration Documentation](./CONFIGURATION_DOCUMENTATION.md)
**Application configuration and setup guide**

- **Core configuration classes** (Security, YooKassa, Telegram, etc.)
- **Environment-specific properties** (dev, prod)
- **Docker and deployment configuration**
- **Environment variables reference**
- **Configuration validation and health checks**
- **Best practices and troubleshooting**

**Key Configurations**:
- Security and JWT setup
- Payment gateway configuration
- Telegram bot setup
- File storage (MinIO/S3)
- Caching (Redis) configuration

#### 5. [Integration Guide](./INTEGRATION_GUIDE.md)
**External service integration instructions**

- **Payment gateway setup** (YooKassa, Robokassa)
- **Telegram bot configuration** (Customer and Admin bots)
- **SMS service integration** (Exolve)
- **Address suggestion services** (DaData, Yandex Maps, Nominatim)
- **File storage setup** (MinIO S3)
- **Database and cache setup**
- **Monitoring and metrics integration**

---

## Quick Start Guide

### Prerequisites
- Java 17+
- PostgreSQL 13+
- Redis 6+
- MinIO or S3-compatible storage

### Environment Setup
1. **Clone the repository**
2. **Set environment variables** (see [Configuration Guide](./CONFIGURATION_DOCUMENTATION.md#environment-variables))
3. **Configure external services** (see [Integration Guide](./INTEGRATION_GUIDE.md))
4. **Run with Docker Compose** or build locally

### Essential Environment Variables
```bash
# Database
DATABASE_URL=jdbc:postgresql://localhost:5432/pizzanat
DATABASE_USERNAME=pizzanat_user
DATABASE_PASSWORD=secure_password

# JWT Security
JWT_SECRET=your-256-bit-secret-key

# YooKassa Payment (if enabled)
YOOKASSA_SHOP_ID=your-shop-id
YOOKASSA_SECRET_KEY=your-secret-key

# Telegram Bots (if enabled)
TELEGRAM_BOT_TOKEN=your-bot-token
TELEGRAM_ADMIN_BOT_TOKEN=your-admin-bot-token

# File Storage
MINIO_ENDPOINT=http://localhost:9000
MINIO_ACCESS_KEY=your-access-key
MINIO_SECRET_KEY=your-secret-key
```

---

## Architecture Overview

### Application Layers

```
┌─────────────────────────────────────────┐
│              Controllers                │  ← REST API Endpoints
├─────────────────────────────────────────┤
│               Services                  │  ← Business Logic
├─────────────────────────────────────────┤
│            Repositories                 │  ← Data Access Layer
├─────────────────────────────────────────┤
│           Database/Cache                │  ← PostgreSQL + Redis
└─────────────────────────────────────────┘

External Integrations:
├── Payment Gateways (YooKassa, Robokassa)
├── Telegram Bots (Customer, Admin)
├── SMS Service (Exolve)
├── Address Services (DaData, Yandex Maps)
└── File Storage (MinIO S3)
```

### Key Components

1. **Authentication System**
   - Traditional username/password
   - SMS-based authentication
   - Telegram bot authentication
   - JWT token management

2. **Order Management**
   - Shopping cart operations
   - Order creation and tracking
   - Status management
   - Delivery coordination

3. **Payment Processing**
   - YooKassa integration (primary)
   - Robokassa support
   - SBP (Fast Payment System)
   - Webhook handling

4. **Notification System**
   - Telegram notifications
   - SMS notifications
   - Admin alerts
   - Real-time updates

5. **Delivery Management**
   - Address validation
   - Delivery zones
   - Cost calculation
   - Time estimation

---

## API Quick Reference

### Core Endpoints

| Endpoint | Method | Description | Auth Required |
|----------|--------|-------------|---------------|
| `/api/v1/auth/login` | POST | User authentication | No |
| `/api/v1/products` | GET | Get all products | No |
| `/api/v1/cart` | GET | Get shopping cart | Optional |
| `/api/v1/orders` | POST | Create order | Yes |
| `/api/v1/payments/yookassa/create` | POST | Create payment | Yes |
| `/api/v1/admin/orders` | GET | Get all orders (Admin) | Admin |

### Authentication Headers
```http
Authorization: Bearer <jwt-token>
```

### Rate Limits
- Public endpoints: 100 requests/minute
- Authenticated: 300 requests/minute
- Admin: 1000 requests/minute

---

## Development Workflow

### Local Development Setup

1. **Start Dependencies**
```bash
docker-compose -f docker-compose.dev.yml up -d
```

2. **Run Application**
```bash
./gradlew bootRun --args='--spring.profiles.active=dev'
```

3. **Access Services**
- Application: http://localhost:8080
- Swagger UI: http://localhost:8080/swagger-ui.html
- MinIO Console: http://localhost:9001
- PostgreSQL: localhost:5432

### Testing

```bash
# Run all tests
./gradlew test

# Run integration tests
./gradlew integrationTest

# Run with coverage
./gradlew test jacocoTestReport
```

### Building for Production

```bash
# Build JAR
./gradlew build

# Build Docker image
docker build -t pizzanat:latest .

# Deploy with Docker Compose
docker-compose -f docker-compose.production.yml up -d
```

---

## Security Considerations

### Authentication & Authorization
- JWT-based authentication with configurable expiration
- Role-based access control (USER, ADMIN)
- Multi-factor authentication via SMS/Telegram
- Rate limiting per user/IP

### Data Protection
- Password encryption with BCrypt
- Sensitive data encryption at rest
- HTTPS enforcement in production
- Input validation and sanitization

### External Service Security
- API key management via environment variables
- Webhook signature validation
- Circuit breakers for external dependencies
- Secure communication with all third parties

---

## Monitoring & Observability

### Health Checks
- `/actuator/health` - Overall application health
- `/actuator/health/db` - Database connectivity
- `/actuator/health/redis` - Cache connectivity
- `/actuator/health/yookassa` - Payment gateway status

### Metrics
- `/actuator/metrics` - Application metrics
- `/actuator/prometheus` - Prometheus-format metrics
- Custom business metrics (orders, payments, etc.)

### Logging
- Structured logging with Logback
- Configurable log levels per environment
- External service call logging
- Security event logging

---

## Troubleshooting

### Common Issues

1. **Database Connection Failed**
   - Check `DATABASE_URL` environment variable
   - Verify PostgreSQL is running
   - Test connection with `psql`

2. **Payment Gateway Errors**
   - Verify YooKassa credentials
   - Check webhook URL configuration
   - Review payment gateway logs

3. **Telegram Bot Issues**
   - Validate bot token with Telegram API
   - Verify webhook URL is accessible
   - Check bot permissions and commands

4. **File Upload Problems**
   - Verify MinIO/S3 credentials
   - Check bucket permissions
   - Test storage connectivity

### Debug Mode
```yaml
# application-dev.yml
logging:
  level:
    com.baganov.pizzanat: DEBUG
    org.springframework.security: DEBUG
debug: true
```

---

## Contributing

### Code Standards
- Follow Java coding conventions
- Write comprehensive unit tests
- Document public APIs with JavaDoc
- Use meaningful commit messages

### Documentation Updates
- Update relevant documentation for any API changes
- Include examples for new features
- Maintain backward compatibility notes
- Update integration guides for new services

---

## Support & Resources

### Internal Resources
- **Swagger UI**: `/swagger-ui.html` - Interactive API documentation
- **Actuator Endpoints**: `/actuator/*` - Application monitoring
- **H2 Console** (dev): `/h2-console` - Database management

### External Documentation
- [Spring Boot Documentation](https://docs.spring.io/spring-boot/docs/current/reference/html/)
- [YooKassa API Documentation](https://yookassa.ru/developers)
- [Telegram Bot API](https://core.telegram.org/bots/api)
- [MinIO Documentation](https://docs.min.io/)

### Getting Help
- Check existing documentation first
- Review application logs for errors
- Use health check endpoints for diagnostics
- Consult integration guides for external services

---

This comprehensive documentation suite provides everything needed to understand, develop, deploy, and maintain the PizzaNat application. Each document focuses on a specific aspect while maintaining consistency and cross-references throughout the suite.