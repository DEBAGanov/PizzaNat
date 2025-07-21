# PizzaNat API Documentation

## Overview

PizzaNat is a comprehensive pizza delivery platform built with Spring Boot, providing REST APIs for order management, payment processing, delivery tracking, and Telegram bot integration.

**Base URL**: `https://your-domain.com/api/v1`  
**API Version**: v1  
**Documentation**: Available at `/swagger-ui.html`

## Authentication

The API uses JWT Bearer token authentication for protected endpoints.

### Authentication Header
```http
Authorization: Bearer <your-jwt-token>
```

### User Roles
- **USER**: Standard customer access
- **ADMIN**: Administrative access to all endpoints

---

## 🏠 System Endpoints

### Health Check
```http
GET /api/health
```

**Description**: Check service availability and health status.

**Response**: `200 OK`
```json
{
  "status": "UP",
  "timestamp": "2025-01-07T10:00:00Z"
}
```

### Detailed Health Check
```http
GET /api/v1/health/detailed
```

**Description**: Get detailed health information including database and external service status.

**Response**: `200 OK`
```json
{
  "status": "UP",
  "components": {
    "database": { "status": "UP" },
    "redis": { "status": "UP" },
    "telegram": { "status": "UP" }
  }
}
```

---

## 🔐 Authentication APIs

### User Registration
```http
POST /api/v1/auth/register
```

**Request Body**:
```json
{
  "username": "user@example.com",
  "password": "securePassword123",
  "fullName": "John Doe",
  "phoneNumber": "+79161234567"
}
```

**Response**: `201 Created`
```json
{
  "id": 1,
  "username": "user@example.com",
  "fullName": "John Doe",
  "phoneNumber": "+79161234567",
  "role": "USER",
  "token": "eyJhbGciOiJIUzI1NiJ9..."
}
```

### User Login
```http
POST /api/v1/auth/login
```

**Request Body**:
```json
{
  "username": "user@example.com",
  "password": "securePassword123"
}
```

**Response**: `200 OK`
```json
{
  "token": "eyJhbGciOiJIUzI1NiJ9...",
  "user": {
    "id": 1,
    "username": "user@example.com",
    "fullName": "John Doe",
    "role": "USER"
  }
}
```

---

## 📱 SMS Authentication

### Send SMS Code
```http
POST /api/v1/auth/sms/send-code
```

**Request Body**:
```json
{
  "phoneNumber": "+79161234567"
}
```

**Response**: `200 OK`
```json
{
  "message": "SMS code sent successfully",
  "requestId": "uuid-request-id"
}
```

### Verify SMS Code
```http
POST /api/v1/auth/sms/verify-code
```

**Request Body**:
```json
{
  "phoneNumber": "+79161234567",
  "code": "123456",
  "requestId": "uuid-request-id"
}
```

**Response**: `200 OK`
```json
{
  "token": "eyJhbGciOiJIUzI1NiJ9...",
  "user": {
    "id": 1,
    "phoneNumber": "+79161234567",
    "role": "USER"
  }
}
```

---

## 📞 Telegram Authentication

### Initialize Telegram Auth
```http
POST /api/v1/auth/telegram/init
```

**Request Body**:
```json
{
  "phoneNumber": "+79161234567"
}
```

**Response**: `200 OK`
```json
{
  "authToken": "tg-auth-token-uuid",
  "qrCodeUrl": "https://t.me/YourBot?start=auth_token",
  "expiresAt": "2025-01-07T10:05:00Z"
}
```

### Check Telegram Auth Status
```http
GET /api/v1/auth/telegram/status/{authToken}
```

**Response**: `200 OK`
```json
{
  "status": "COMPLETED",
  "token": "eyJhbGciOiJIUzI1NiJ9...",
  "user": {
    "id": 1,
    "phoneNumber": "+79161234567",
    "telegramId": 123456789,
    "role": "USER"
  }
}
```

---

## 👤 User Management

### Get User Profile
```http
GET /api/v1/user/profile
```

**Authentication**: Required (USER)

**Response**: `200 OK`
```json
{
  "id": 1,
  "username": "user@example.com",
  "fullName": "John Doe",
  "phoneNumber": "+79161234567",
  "telegramId": 123456789,
  "role": "USER",
  "createdAt": "2025-01-01T00:00:00Z"
}
```

---

## 🍕 Product Management

### Get All Products
```http
GET /api/v1/products?page=0&size=20&sort=name,asc
```

**Query Parameters**:
- `page`: Page number (default: 0)
- `size`: Page size (default: 20, max: 100)
- `sort`: Sort criteria (name, price, category)

**Response**: `200 OK`
```json
{
  "content": [
    {
      "id": 1,
      "name": "Margherita",
      "description": "Classic pizza with tomato sauce, mozzarella, and fresh basil",
      "price": 590.00,
      "discountedPrice": 490.00,
      "categoryId": 1,
      "categoryName": "Pizza",
      "imageUrl": "https://storage.example.com/products/margherita.jpg",
      "weight": 350,
      "isAvailable": true,
      "isSpecialOffer": true,
      "discountPercent": 17
    }
  ],
  "pageable": {
    "pageNumber": 0,
    "pageSize": 20
  },
  "totalElements": 45,
  "totalPages": 3
}
```

### Get Product by ID
```http
GET /api/v1/products/{id}
```

**Response**: `200 OK`
```json
{
  "id": 1,
  "name": "Margherita",
  "description": "Classic pizza with tomato sauce, mozzarella, and fresh basil",
  "price": 590.00,
  "discountedPrice": 490.00,
  "categoryId": 1,
  "categoryName": "Pizza",
  "imageUrl": "https://storage.example.com/products/margherita.jpg",
  "weight": 350,
  "isAvailable": true,
  "isSpecialOffer": true,
  "discountPercent": 17
}
```

### Get Products by Category
```http
GET /api/v1/products/category/{categoryId}?page=0&size=20
```

**Response**: Same format as "Get All Products"

### Get Special Offers
```http
GET /api/v1/products/special-offers
```

**Response**: `200 OK`
```json
[
  {
    "id": 1,
    "name": "Margherita",
    "price": 590.00,
    "discountedPrice": 490.00,
    "discountPercent": 17,
    "isSpecialOffer": true
  }
]
```

### Search Products
```http
GET /api/v1/products/search?query=pizza&categoryId=1&page=0&size=20
```

**Query Parameters**:
- `query`: Search term (required)
- `categoryId`: Filter by category (optional)
- `page`, `size`: Pagination parameters

**Response**: Same format as "Get All Products"

### Create Product (Admin)
```http
POST /api/v1/products
Content-Type: multipart/form-data
```

**Authentication**: Required (ADMIN)

**Form Data**:
- `product`: JSON product data
- `image`: Image file (JPG, PNG)

**Request**:
```json
{
  "name": "New Pizza",
  "description": "Delicious new pizza",
  "price": 650.00,
  "categoryId": 1,
  "weight": 400,
  "isAvailable": true,
  "isSpecialOffer": false
}
```

**Response**: `201 Created` - Returns created product with generated ID and image URL

---

## 📂 Categories

### Get All Categories
```http
GET /api/v1/categories
```

**Response**: `200 OK`
```json
[
  {
    "id": 1,
    "name": "Pizza",
    "description": "Traditional and specialty pizzas",
    "imageUrl": "https://storage.example.com/categories/pizza.jpg",
    "sortOrder": 1,
    "isActive": true
  },
  {
    "id": 2,
    "name": "Drinks",
    "description": "Beverages and soft drinks",
    "imageUrl": "https://storage.example.com/categories/drinks.jpg",
    "sortOrder": 2,
    "isActive": true
  }
]
```

### Get Category by ID
```http
GET /api/v1/categories/{id}
```

**Response**: Returns single category object

---

## 🛒 Cart Management

### Get Cart
```http
GET /api/v1/cart
```

**Authentication**: Optional (supports both authenticated and anonymous users)

**Response**: `200 OK`
```json
{
  "id": 1,
  "sessionId": "anonymous-session-uuid",
  "totalAmount": 1180.00,
  "items": [
    {
      "id": 1,
      "productId": 1,
      "productName": "Margherita",
      "price": 590.00,
      "quantity": 2,
      "totalPrice": 1180.00,
      "imageUrl": "https://storage.example.com/products/margherita.jpg"
    }
  ]
}
```

### Add Item to Cart
```http
POST /api/v1/cart/items
```

**Request Body**:
```json
{
  "productId": 1,
  "quantity": 2
}
```

**Response**: `200 OK` - Returns updated cart

### Update Cart Item
```http
PUT /api/v1/cart/items/{productId}
```

**Request Body**:
```json
{
  "quantity": 3
}
```

**Response**: `200 OK` - Returns updated cart

### Remove Item from Cart
```http
DELETE /api/v1/cart/items/{productId}
```

**Response**: `200 OK` - Returns updated cart

### Clear Cart
```http
DELETE /api/v1/cart
```

**Response**: `204 No Content`

### Merge Anonymous Cart with User Cart
```http
POST /api/v1/cart/merge
```

**Authentication**: Required (USER)

**Description**: Merges anonymous session cart with authenticated user's cart

**Response**: `200 OK` - Returns merged cart

---

## 📋 Order Management

### Create Order
```http
POST /api/v1/orders
```

**Authentication**: Required (USER)

**Request Body**:
```json
{
  "deliveryAddress": {
    "street": "Ленина",
    "house": "10",
    "apartment": "5",
    "entrance": "1",
    "floor": "2",
    "comment": "Звонить в домофон"
  },
  "deliveryTime": "2025-01-07T18:00:00Z",
  "paymentMethod": "YOOKASSA",
  "comment": "Extra cheese please",
  "phoneNumber": "+79161234567",
  "customerName": "John Doe"
}
```

**Response**: `201 Created`
```json
{
  "id": 1001,
  "orderNumber": "ORD-2025-001001",
  "status": "PENDING",
  "totalAmount": 1180.00,
  "deliveryFee": 150.00,
  "finalAmount": 1330.00,
  "items": [
    {
      "id": 1,
      "productId": 1,
      "productName": "Margherita",
      "price": 590.00,
      "quantity": 2,
      "totalPrice": 1180.00
    }
  ],
  "deliveryAddress": {
    "street": "Ленина",
    "house": "10",
    "apartment": "5",
    "fullAddress": "ул. Ленина, д. 10, кв. 5"
  },
  "estimatedDeliveryTime": "2025-01-07T18:30:00Z",
  "createdAt": "2025-01-07T17:00:00Z"
}
```

### Get Order by ID
```http
GET /api/v1/orders/{orderId}
```

**Authentication**: Required (USER - own orders, ADMIN - all orders)

**Response**: `200 OK` - Returns order details

### Get User Orders
```http
GET /api/v1/orders?page=0&size=10&sort=createdAt,desc
```

**Authentication**: Required (USER)

**Response**: `200 OK`
```json
{
  "content": [
    {
      "id": 1001,
      "orderNumber": "ORD-2025-001001",
      "status": "DELIVERED",
      "totalAmount": 1330.00,
      "createdAt": "2025-01-07T17:00:00Z",
      "deliveredAt": "2025-01-07T18:25:00Z"
    }
  ],
  "totalElements": 5,
  "totalPages": 1
}
```

### Get Payment URL for Order
```http
GET /api/v1/orders/{orderId}/payment-url
```

**Authentication**: Required (USER)

**Response**: `200 OK`
```json
{
  "paymentUrl": "https://yookassa.ru/checkout/payments/12345",
  "paymentId": "payment-uuid",
  "expiresAt": "2025-01-07T18:00:00Z"
}
```

---

## 💳 Payment Processing (YooKassa)

### Create Payment
```http
POST /api/v1/payments/yookassa/create
```

**Authentication**: Required (USER)

**Request Body**:
```json
{
  "orderId": 1001,
  "paymentMethod": "bank_card",
  "returnUrl": "https://yourapp.com/payment/success",
  "description": "Payment for order ORD-2025-001001"
}
```

**Response**: `201 Created`
```json
{
  "id": "payment-uuid",
  "status": "PENDING",
  "amount": 1330.00,
  "currency": "RUB",
  "paymentUrl": "https://yookassa.ru/checkout/payments/12345",
  "receiptUrl": null,
  "expiresAt": "2025-01-07T18:00:00Z"
}
```

**🆕 Автоматическое формирование чеков согласно 54-ФЗ:**
- При создании платежа автоматически формируется фискальный чек
- Передаются данные о товарах из заказа (название, количество, цена)
- Используется телефон покупателя для отправки чека
- НДС 0% для доставки еды
- После успешной оплаты в `receiptUrl` будет URL чека

### Get Payment Status
```http
GET /api/v1/payments/yookassa/{paymentId}
```

**Response**: `200 OK`
```json
{
  "id": "payment-uuid",
  "status": "SUCCEEDED",
  "amount": 1330.00,
  "currency": "RUB",
  "capturedAt": "2025-01-07T17:30:00Z",
  "paymentMethod": {
    "type": "bank_card",
    "card": {
      "last4": "1234",
      "cardType": "Visa"
    }
  }
}
```

### Get SBP Banks List
```http
GET /api/v1/payments/yookassa/sbp/banks
```

**Response**: `200 OK`
```json
[
  {
    "id": "sberbank",
    "name": "Сбербанк",
    "logoUrl": "https://yookassa.ru/files/banks/sberbank.png"
  },
  {
    "id": "tinkoff",
    "name": "Тинькофф Банк",
    "logoUrl": "https://yookassa.ru/files/banks/tinkoff.png"
  }
]
```

### Cancel Payment
```http
POST /api/v1/payments/yookassa/{paymentId}/cancel
```

**Authentication**: Required (USER or ADMIN)

**Response**: `200 OK`
```json
{
  "id": "payment-uuid",
  "status": "CANCELLED",
  "cancelledAt": "2025-01-07T17:45:00Z"
}
```

---

## 🚚 Delivery Management

### Get Delivery Zones
```http
GET /api/v1/delivery/zones
```

**Response**: `200 OK`
```json
[
  {
    "id": 1,
    "name": "Центр города",
    "deliveryFee": 150.00,
    "freeDeliveryThreshold": 1000.00,
    "isActive": true,
    "estimatedDeliveryTime": 30
  }
]
```

### Calculate Delivery Cost
```http
GET /api/v1/delivery/cost?address=ул.%20Ленина,%20д.%2010
```

**Query Parameters**:
- `address`: Full delivery address (URL encoded)

**Response**: `200 OK`
```json
{
  "deliveryFee": 150.00,
  "zoneId": 1,
  "zoneName": "Центр города",
  "freeDeliveryThreshold": 1000.00,
  "estimatedDeliveryTime": 30,
  "isDeliveryAvailable": true
}
```

### Validate Address
```http
POST /api/v1/delivery/validate-address
```

**Request Body**:
```json
{
  "street": "Ленина",
  "house": "10",
  "apartment": "5"
}
```

**Response**: `200 OK`
```json
{
  "isValid": true,
  "formattedAddress": "ул. Ленина, д. 10, кв. 5",
  "deliveryZone": {
    "id": 1,
    "name": "Центр города",
    "deliveryFee": 150.00
  },
  "suggestions": []
}
```

---

## 🏠 Address Suggestions

### Get Address Suggestions
```http
GET /api/v1/address/suggestions?query=Ленина&limit=10
```

**Query Parameters**:
- `query`: Address search query
- `limit`: Maximum number of suggestions (default: 10)

**Response**: `200 OK`
```json
[
  {
    "value": "ул. Ленина",
    "unrestricted_value": "Республика Марий Эл, г. Волжск, ул. Ленина",
    "data": {
      "street": "Ленина",
      "street_type": "ул",
      "city": "Волжск",
      "region": "Республика Марий Эл"
    }
  }
]
```

### Get House Suggestions
```http
GET /api/v1/address/houses?street=ул.%20Ленина&limit=10
```

**Response**: `200 OK`
```json
[
  {
    "value": "ул. Ленина, д. 1",
    "house": "1"
  },
  {
    "value": "ул. Ленина, д. 2",
    "house": "2"
  }
]
```

---

## 👨‍💼 Admin Endpoints

### Get Statistics (Admin)
```http
GET /api/v1/admin/stats
```

**Authentication**: Required (ADMIN)

**Response**: `200 OK`
```json
{
  "totalOrders": 1250,
  "totalRevenue": 750000.00,
  "ordersToday": 45,
  "revenueToday": 25000.00,
  "topProducts": [
    {
      "productId": 1,
      "productName": "Margherita",
      "orderCount": 120,
      "revenue": 70800.00
    }
  ],
  "ordersByStatus": {
    "PENDING": 5,
    "IN_PROGRESS": 8,
    "READY": 2,
    "DELIVERED": 1235
  }
}
```

### Get All Orders (Admin)
```http
GET /api/v1/admin/orders?page=0&size=20&status=PENDING&sort=createdAt,desc
```

**Authentication**: Required (ADMIN)

**Query Parameters**:
- `status`: Filter by order status (optional)
- `page`, `size`, `sort`: Pagination and sorting

**Response**: Paginated list of all orders

### Update Order Status (Admin)
```http
PUT /api/v1/admin/orders/{orderId}/status
```

**Authentication**: Required (ADMIN)

**Request Body**:
```json
{
  "status": "IN_PROGRESS",
  "comment": "Order accepted and being prepared"
}
```

**Response**: `200 OK` - Returns updated order

### Create Product (Admin)
```http
POST /api/v1/admin/products
Content-Type: multipart/form-data
```

**Authentication**: Required (ADMIN)

**Form Data**: Same as public product creation endpoint

### Update Product (Admin)
```http
PUT /api/v1/admin/products/{id}
```

**Authentication**: Required (ADMIN)

**Request Body**: JSON product data (image upload optional)

### Delete Product (Admin)
```http
DELETE /api/v1/admin/products/{id}
```

**Authentication**: Required (ADMIN)

**Response**: `204 No Content`

---

## 📊 Payment Metrics (Admin)

### Get Payment Metrics Summary
```http
GET /api/v1/payments/metrics/summary
```

**Authentication**: Required (ADMIN)

**Response**: `200 OK`
```json
{
  "totalPayments": 1180,
  "successfulPayments": 1150,
  "failedPayments": 30,
  "successRate": 97.46,
  "totalRevenue": 850000.00,
  "averagePaymentAmount": 739.13,
  "paymentsToday": 45,
  "revenueToday": 32000.00
}
```

---

## 📍 Delivery Locations

### Get Delivery Locations
```http
GET /api/v1/delivery-locations
```

**Response**: `200 OK`
```json
[
  {
    "id": 1,
    "name": "Пиццерия на Ленина",
    "address": "ул. Ленина, д. 15",
    "latitude": 56.123456,
    "longitude": 48.654321,
    "phone": "+79161234567",
    "workingHours": "10:00-22:00",
    "isActive": true
  }
]
```

---

## Error Responses

### Standard Error Format
```json
{
  "timestamp": "2025-01-07T10:00:00Z",
  "status": 400,
  "error": "Bad Request",
  "message": "Validation failed",
  "path": "/api/v1/orders",
  "details": {
    "field": "deliveryAddress.street",
    "message": "Street is required"
  }
}
```

### HTTP Status Codes
- `200 OK`: Successful GET, PUT requests
- `201 Created`: Successful POST requests
- `204 No Content`: Successful DELETE requests
- `400 Bad Request`: Invalid request data
- `401 Unauthorized`: Authentication required
- `403 Forbidden`: Insufficient permissions
- `404 Not Found`: Resource not found
- `409 Conflict`: Business logic conflict
- `422 Unprocessable Entity`: Validation errors
- `500 Internal Server Error`: Server error

---

## Rate Limiting

API requests are rate-limited per IP address:
- **Public endpoints**: 100 requests per minute
- **Authenticated endpoints**: 300 requests per minute
- **Admin endpoints**: 1000 requests per minute

Rate limit headers are included in responses:
```http
X-RateLimit-Limit: 300
X-RateLimit-Remaining: 299
X-RateLimit-Reset: 1704628800
```

---

## Pagination

Paginated endpoints support the following query parameters:
- `page`: Page number (0-based, default: 0)
- `size`: Page size (default: 20, max: 100)
- `sort`: Sort criteria (e.g., `name,asc` or `createdAt,desc`)

Response format includes pagination metadata:
```json
{
  "content": [...],
  "pageable": {
    "pageNumber": 0,
    "pageSize": 20,
    "sort": {
      "sorted": true,
      "orders": [{"property": "name", "direction": "ASC"}]
    }
  },
  "totalElements": 150,
  "totalPages": 8,
  "first": true,
  "last": false
}
```

---

## WebSocket Support

Real-time order status updates are available via WebSocket:

**Connection**: `wss://your-domain.com/ws/orders`

**Authentication**: Include JWT token in connection headers

**Message Format**:
```json
{
  "type": "ORDER_STATUS_UPDATE",
  "orderId": 1001,
  "status": "IN_PROGRESS",
  "timestamp": "2025-01-07T17:30:00Z"
}
```