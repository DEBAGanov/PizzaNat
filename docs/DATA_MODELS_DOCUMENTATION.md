# PizzaNat Data Models Documentation

## Overview

This document provides comprehensive documentation for all data transfer objects (DTOs) and entity models used in the PizzaNat application. These models define the structure of data exchanged between API endpoints and stored in the database.

---

## Entity Models (Database)

### User Entity

**Package**: `com.baganov.pizzanat.entity`  
**Table**: `users`

```java
@Entity
@Table(name = "users")
public class User {
    private Integer id;              // Primary key
    private String username;         // Email or username (unique)
    private String password;         // Encrypted password
    private String fullName;         // Display name
    private String phoneNumber;      // Phone in international format
    private Long telegramId;         // Telegram user ID (nullable)
    private String telegramUsername; // Telegram username (nullable)
    private Set<Role> roles;         // User roles (USER, ADMIN)
    private LocalDateTime createdAt; // Registration timestamp
    private LocalDateTime updatedAt; // Last update timestamp
    private boolean isActive;        // Account status
}
```

**Relationships**:
- **One-to-Many**: Orders (`orders`)
- **Many-to-Many**: Roles (`user_roles`)
- **One-to-Many**: Carts (`carts`)

---

### Product Entity

**Package**: `com.baganov.pizzanat.entity`  
**Table**: `products`

```java
@Entity
@Table(name = "products")
public class Product {
    private Integer id;                    // Primary key
    private String name;                   // Product name
    private String description;            // Product description
    private BigDecimal price;              // Regular price
    private BigDecimal discountedPrice;    // Sale price (nullable)
    private Integer categoryId;            // Category foreign key
    private Category category;             // Category relationship
    private String imageUrl;               // Product image URL
    private Integer weight;                // Weight in grams
    private boolean isAvailable;           // Availability status
    private boolean isSpecialOffer;        // Special offer flag
    private Integer discountPercent;       // Discount percentage
    private LocalDateTime createdAt;       // Creation timestamp
    private LocalDateTime updatedAt;       // Last update timestamp
}
```

**Relationships**:
- **Many-to-One**: Category (`category`)
- **One-to-Many**: Cart Items (`cart_items`)
- **One-to-Many**: Order Items (`order_items`)

---

### Order Entity

**Package**: `com.baganov.pizzanat.entity`  
**Table**: `orders`

```java
@Entity
@Table(name = "orders")
public class Order {
    private Integer id;                     // Primary key
    private String orderNumber;             // Unique order identifier
    private Integer userId;                 // User foreign key
    private User user;                      // User relationship
    private OrderStatus status;             // Order status enum
    private BigDecimal totalAmount;         // Items total
    private BigDecimal deliveryFee;         // Delivery cost
    private BigDecimal finalAmount;         // Total with delivery
    private String deliveryAddress;         // Full delivery address
    private String customerName;            // Customer name
    private String phoneNumber;             // Contact phone
    private String comment;                 // Special instructions
    private PaymentMethod paymentMethod;    // Payment method enum
    private LocalDateTime deliveryTime;     // Requested delivery time
    private LocalDateTime createdAt;        // Order creation time
    private LocalDateTime updatedAt;        // Last status update
    private List<OrderItem> items;          // Order items
}
```

**Relationships**:
- **Many-to-One**: User (`user`)
- **One-to-Many**: Order Items (`order_items`)
- **One-to-Many**: Payments (`payments`)

---

### Category Entity

**Package**: `com.baganov.pizzanat.entity`  
**Table**: `categories`

```java
@Entity
@Table(name = "categories")
public class Category {
    private Integer id;             // Primary key
    private String name;            // Category name
    private String description;     // Category description
    private String imageUrl;        // Category image URL
    private Integer sortOrder;      // Display order
    private boolean isActive;       // Active status
}
```

**Relationships**:
- **One-to-Many**: Products (`products`)

---

### Cart Entity

**Package**: `com.baganov.pizzanat.entity`  
**Table**: `carts`

```java
@Entity
@Table(name = "carts")
public class Cart {
    private Integer id;                 // Primary key
    private String sessionId;           // Session identifier
    private Integer userId;             // User foreign key (nullable)
    private User user;                  // User relationship
    private BigDecimal totalAmount;     // Cart total
    private LocalDateTime createdAt;    // Creation timestamp
    private LocalDateTime updatedAt;    // Last update timestamp
    private List<CartItem> items;       // Cart items
}
```

**Relationships**:
- **Many-to-One**: User (`user`)
- **One-to-Many**: Cart Items (`cart_items`)

---

### Payment Entity

**Package**: `com.baganov.pizzanat.entity`  
**Table**: `payments`

```java
@Entity
@Table(name = "payments")
public class Payment {
    private Long id;                        // Primary key
    private String paymentId;               // External payment ID
    private String yookassaPaymentId;       // YooKassa payment ID
    private Integer orderId;                // Order foreign key
    private Order order;                    // Order relationship
    private PaymentStatus status;           // Payment status enum
    private PaymentMethod paymentMethod;    // Payment method enum
    private BigDecimal amount;              // Payment amount
    private String currency;                // Currency code (RUB)
    private String description;             // Payment description
    private String returnUrl;               // Success return URL
    private String paymentUrl;              // Payment gateway URL
    private LocalDateTime createdAt;        // Payment creation time
    private LocalDateTime updatedAt;        // Last status update
    private LocalDateTime expiresAt;        // Payment expiration time
    private Map<String, Object> metadata;   // Additional payment data
}
```

**Relationships**:
- **Many-to-One**: Order (`order`)

---

### DeliveryZone Entity

**Package**: `com.baganov.pizzanat.entity`  
**Table**: `delivery_zones`

```java
@Entity
@Table(name = "delivery_zones")
public class DeliveryZone {
    private Integer id;                     // Primary key
    private String name;                    // Zone name
    private BigDecimal deliveryFee;         // Delivery cost
    private BigDecimal freeDeliveryThreshold; // Free delivery minimum
    private Integer estimatedDeliveryTime;  // Delivery time in minutes
    private boolean isActive;               // Zone status
    private List<DeliveryZoneStreet> streets; // Streets in zone
    private List<DeliveryZoneKeyword> keywords; // Address keywords
}
```

**Relationships**:
- **One-to-Many**: Zone Streets (`delivery_zone_streets`)
- **One-to-Many**: Zone Keywords (`delivery_zone_keywords`)

---

## Data Transfer Objects (DTOs)

### ProductDTO

**Package**: `com.baganov.pizzanat.model.dto.product`

```java
public class ProductDTO {
    private Integer id;                // Product ID
    private String name;               // Product name
    private String description;        // Product description
    private BigDecimal price;          // Regular price
    private BigDecimal discountedPrice; // Sale price
    private Integer categoryId;        // Category ID
    private String categoryName;       // Category name
    private String imageUrl;           // Image URL
    private Integer weight;            // Weight in grams
    private boolean isAvailable;       // Availability status
    private boolean isSpecialOffer;    // Special offer flag
    private Integer discountPercent;   // Discount percentage
}
```

**Usage**: API responses for product data

---

### CartDTO

**Package**: `com.baganov.pizzanat.model.dto.cart`

```java
public class CartDTO {
    private Integer id;                    // Cart ID
    private String sessionId;              // Session identifier
    private BigDecimal totalAmount;        // Cart total
    private List<CartItemDTO> items;       // Cart items
}
```

**Usage**: Shopping cart API responses

---

### CartItemDTO

**Package**: `com.baganov.pizzanat.model.dto.cart`

```java
public class CartItemDTO {
    private Integer id;               // Cart item ID
    private Integer productId;        // Product ID
    private String productName;       // Product name
    private BigDecimal price;         // Unit price
    private Integer quantity;         // Item quantity
    private BigDecimal totalPrice;    // Line total (price × quantity)
    private String imageUrl;          // Product image URL
}
```

**Usage**: Individual cart item representation

---

### OrderDTO

**Package**: `com.baganov.pizzanat.model.dto.order`

```java
public class OrderDTO {
    private Integer id;                     // Order ID
    private String orderNumber;             // Order number
    private String status;                  // Order status
    private BigDecimal totalAmount;         // Items total
    private BigDecimal deliveryFee;         // Delivery cost
    private BigDecimal finalAmount;         // Total with delivery
    private String deliveryAddress;         // Delivery address
    private String customerName;            // Customer name
    private String phoneNumber;             // Contact phone
    private String comment;                 // Special instructions
    private String paymentMethod;           // Payment method
    private LocalDateTime deliveryTime;     // Requested delivery time
    private LocalDateTime createdAt;        // Creation time
    private LocalDateTime updatedAt;        // Last update
    private List<OrderItemDTO> items;       // Order items
}
```

**Usage**: Order data in API responses

---

### OrderItemDTO

**Package**: `com.baganov.pizzanat.model.dto.order`

```java
public class OrderItemDTO {
    private Integer id;               // Order item ID
    private Integer productId;        // Product ID
    private String productName;       // Product name
    private BigDecimal price;         // Unit price at order time
    private Integer quantity;         // Ordered quantity
    private BigDecimal totalPrice;    // Line total
}
```

**Usage**: Individual order item representation

---

### CategoryDTO

**Package**: `com.baganov.pizzanat.model.dto.product`

```java
public class CategoryDTO {
    private Integer id;             // Category ID
    private String name;            // Category name
    private String description;     // Category description
    private String imageUrl;        // Category image URL
    private Integer sortOrder;      // Display order
    private boolean isActive;       // Active status
}
```

**Usage**: Category data in API responses

---

## Request DTOs

### AddToCartRequest

**Package**: `com.baganov.pizzanat.model.dto.cart`

```java
public class AddToCartRequest {
    @NotNull
    private Integer productId;      // Product to add
    
    @NotNull
    @Min(1)
    @Max(99)
    private Integer quantity;       // Quantity to add
}
```

**Usage**: Adding items to cart endpoint

---

### UpdateCartItemRequest

**Package**: `com.baganov.pizzanat.model.dto.cart`

```java
public class UpdateCartItemRequest {
    @NotNull
    @Min(1)
    @Max(99)
    private Integer quantity;       // New quantity
}
```

**Usage**: Updating cart item quantity

---

### CreateOrderRequest

**Package**: `com.baganov.pizzanat.model.dto.order`

```java
public class CreateOrderRequest {
    @NotNull
    @Valid
    private DeliveryAddressDTO deliveryAddress;  // Delivery details
    
    private LocalDateTime deliveryTime;          // Requested delivery time
    
    @NotNull
    private PaymentMethod paymentMethod;         // Payment method
    
    @Size(max = 500)
    private String comment;                      // Special instructions
    
    @NotBlank
    private String phoneNumber;                  // Contact phone
    
    @NotBlank
    private String customerName;                 // Customer name
}
```

**Usage**: Order creation endpoint

---

### DeliveryAddressDTO

**Package**: `com.baganov.pizzanat.model.dto.delivery`

```java
public class DeliveryAddressDTO {
    @NotBlank
    private String street;          // Street name
    
    @NotBlank
    private String house;           // House number
    
    private String apartment;       // Apartment number
    private String entrance;        // Entrance number
    private String floor;           // Floor number
    private String comment;         // Delivery instructions
}
```

**Usage**: Delivery address specification

---

### RegisterRequest

**Package**: `com.baganov.pizzanat.model.dto.auth`

```java
public class RegisterRequest {
    @NotBlank
    @Email
    private String username;        // Email address
    
    @NotBlank
    @Size(min = 6, max = 100)
    private String password;        // Password
    
    @NotBlank
    private String fullName;        // Full name
    
    @NotBlank
    @Pattern(regexp = "\\+7\\d{10}")
    private String phoneNumber;     // Phone number
}
```

**Usage**: User registration endpoint

---

### LoginRequest

**Package**: `com.baganov.pizzanat.model.dto.auth`

```java
public class LoginRequest {
    @NotBlank
    private String username;        // Email or username
    
    @NotBlank
    private String password;        // Password
}
```

**Usage**: User authentication endpoint

---

## Response DTOs

### AuthResponse

**Package**: `com.baganov.pizzanat.model.dto.auth`

```java
public class AuthResponse {
    private String token;           // JWT access token
    private String tokenType;       // Token type ("Bearer")
    private UserDTO user;           // User information
    private LocalDateTime expiresAt; // Token expiration
}
```

**Usage**: Authentication success response

---

### UserDTO

**Package**: `com.baganov.pizzanat.model.dto.user`

```java
public class UserDTO {
    private Integer id;             // User ID
    private String username;        // Username/email
    private String fullName;        // Display name
    private String phoneNumber;     // Phone number
    private Long telegramId;        // Telegram ID
    private String role;            // User role
    private LocalDateTime createdAt; // Registration date
}
```

**Usage**: User profile information

---

### PaymentResponse

**Package**: `com.baganov.pizzanat.model.dto.payment`

```java
public class PaymentResponse {
    private String id;              // Payment ID
    private String status;          // Payment status
    private BigDecimal amount;      // Payment amount
    private String currency;        // Currency code
    private String paymentUrl;      // Gateway URL
    private LocalDateTime expiresAt; // Payment expiration
    private PaymentMethodInfo paymentMethod; // Payment method details
}
```

**Usage**: Payment creation and status responses

---

### DeliveryFeeResponse

**Package**: `com.baganov.pizzanat.model.dto.delivery`

```java
public class DeliveryFeeResponse {
    private BigDecimal deliveryFee;           // Calculated fee
    private Integer zoneId;                   // Delivery zone ID
    private String zoneName;                  // Zone name
    private BigDecimal freeDeliveryThreshold; // Free delivery minimum
    private Integer estimatedDeliveryTime;    // Delivery time estimate
    private boolean isDeliveryAvailable;      // Availability flag
}
```

**Usage**: Delivery cost calculation response

---

### AddressSuggestion

**Package**: `com.baganov.pizzanat.model.dto.address`

```java
public class AddressSuggestion {
    private String value;                   // Suggestion text
    private String unrestricted_value;      // Full address
    private AddressData data;               // Structured address data
}

public class AddressData {
    private String street;          // Street name
    private String street_type;     // Street type (ул, пр, etc.)
    private String house;           // House number
    private String city;            // City name
    private String region;          // Region name
    private String postal_code;     // Postal code
}
```

**Usage**: Address autocomplete responses

---

### AdminStatsResponse

**Package**: `com.baganov.pizzanat.model.dto.admin`

```java
public class AdminStatsResponse {
    private Long totalOrders;               // Total order count
    private BigDecimal totalRevenue;        // Total revenue
    private Long ordersToday;               // Today's orders
    private BigDecimal revenueToday;        // Today's revenue
    private List<TopProductStats> topProducts; // Best sellers
    private Map<String, Long> ordersByStatus; // Status breakdown
    private PaymentStatsDTO paymentStats;   // Payment statistics
}
```

**Usage**: Administrative dashboard statistics

---

## Enumerations

### OrderStatus

```java
public enum OrderStatus {
    PENDING,        // Order created, awaiting payment
    PAID,           // Payment confirmed
    IN_PROGRESS,    // Order being prepared
    READY,          // Order ready for delivery
    OUT_FOR_DELIVERY, // Order dispatched
    DELIVERED,      // Order completed
    CANCELLED       // Order cancelled
}
```

### PaymentStatus

```java
public enum PaymentStatus {
    PENDING,        // Payment initiated
    WAITING_FOR_CAPTURE, // Waiting for capture
    SUCCEEDED,      // Payment successful
    CANCELLED,      // Payment cancelled
    FAILED          // Payment failed
}
```

### PaymentMethod

```java
public enum PaymentMethod {
    CASH,           // Cash on delivery
    YOOKASSA,       // YooKassa gateway
    ROBOKASSA,      // Robokassa gateway
    SBP             // Fast Payment System
}
```

### Role

```java
public enum Role {
    USER,           // Regular customer
    ADMIN           // Administrator
}
```

---

## Validation Annotations

Common validation annotations used across DTOs:

- `@NotNull`: Field cannot be null
- `@NotBlank`: String cannot be null, empty, or whitespace
- `@Email`: Valid email format
- `@Pattern`: Regex pattern validation
- `@Size`: String length constraints
- `@Min/@Max`: Numeric range validation
- `@Valid`: Cascade validation to nested objects
- `@DecimalMin/@DecimalMax`: Decimal range validation

---

## JSON Serialization Examples

### Product API Response
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

### Cart API Response
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

### Order API Response
```json
{
  "id": 1001,
  "orderNumber": "ORD-2025-001001",
  "status": "PENDING",
  "totalAmount": 1180.00,
  "deliveryFee": 150.00,
  "finalAmount": 1330.00,
  "deliveryAddress": "ул. Ленина, д. 10, кв. 5",
  "customerName": "John Doe",
  "phoneNumber": "+79161234567",
  "comment": "Extra cheese please",
  "paymentMethod": "YOOKASSA",
  "deliveryTime": "2025-01-07T18:00:00Z",
  "createdAt": "2025-01-07T17:00:00Z",
  "items": [
    {
      "id": 1,
      "productId": 1,
      "productName": "Margherita",
      "price": 590.00,
      "quantity": 2,
      "totalPrice": 1180.00
    }
  ]
}
```

This comprehensive data models documentation provides developers with detailed information about all data structures, their relationships, validation rules, and JSON serialization examples for effective API integration.