# PizzaNat Services Documentation

## Overview

This document provides comprehensive documentation for all business service classes and their public methods in the PizzaNat application. These services encapsulate the core business logic and provide reusable functionality across the application.

## Core Business Services

### ProductService

**Package**: `com.baganov.pizzanat.service`  
**Description**: Manages product operations including CRUD operations, caching, and search functionality.

#### Public Methods

##### `createProduct(ProductDto productDto, MultipartFile image)`
**Description**: Creates a new product with image upload.  
**Parameters**:
- `productDto`: Product data transfer object
- `image`: Product image file

**Returns**: `ProductDto` - Created product with generated ID and image URL  
**Throws**: `IllegalArgumentException` if validation fails

**Example Usage**:
```java
@Autowired
private ProductService productService;

ProductDto newProduct = ProductDto.builder()
    .name("Supreme Pizza")
    .description("Loaded with pepperoni, sausage, and vegetables")
    .price(new BigDecimal("750.00"))
    .categoryId(1)
    .weight(450)
    .isAvailable(true)
    .build();

MultipartFile imageFile = // ... image file
ProductDto created = productService.createProduct(newProduct, imageFile);
```

##### `getProductById(Integer id)`
**Description**: Retrieves a product by ID with caching support.  
**Parameters**:
- `id`: Product ID

**Returns**: `ProductDto` - Product details  
**Throws**: `IllegalArgumentException` if product not found  
**Cache**: Results cached with key `product:{id}`

##### `getAllProducts(Pageable pageable)`
**Description**: Retrieves all products with pagination.  
**Parameters**:
- `pageable`: Pagination and sorting parameters

**Returns**: `Page<ProductDto>` - Paginated product list

##### `getProductsByCategory(Integer categoryId, Pageable pageable)`
**Description**: Retrieves products filtered by category.  
**Parameters**:
- `categoryId`: Category filter
- `pageable`: Pagination parameters

**Returns**: `Page<ProductDto>` - Filtered and paginated products

##### `getSpecialOffers()`
**Description**: Retrieves all special offer products.  
**Returns**: `List<ProductDto>` - Special offer products  
**Cache**: Results cached with key `products:special`

##### `searchProducts(String query, Integer categoryId, Pageable pageable)`
**Description**: Searches products by name with optional category filter.  
**Parameters**:
- `query`: Search term
- `categoryId`: Optional category filter
- `pageable`: Pagination parameters

**Returns**: `Page<ProductDto>` - Search results

---

### CartService

**Package**: `com.baganov.pizzanat.service`  
**Description**: Manages shopping cart operations for both authenticated and anonymous users.

#### Public Methods

##### `getCart(String sessionId, Integer userId)`
**Description**: Retrieves cart for user or session.  
**Parameters**:
- `sessionId`: Anonymous session ID (nullable)
- `userId`: Authenticated user ID (nullable)

**Returns**: `CartDTO` - Current cart state

##### `addToCart(String sessionId, Integer userId, Integer productId, Integer quantity)`
**Description**: Adds or updates product quantity in cart.  
**Parameters**:
- `sessionId`: Session ID
- `userId`: User ID (nullable)
- `productId`: Product to add
- `quantity`: Quantity to add

**Returns**: `CartDTO` - Updated cart

##### `updateCartItem(String sessionId, Integer userId, Integer productId, Integer quantity)`
**Description**: Updates specific product quantity in cart.  
**Parameters**:
- `sessionId`: Session ID
- `userId`: User ID (nullable)
- `productId`: Product to update
- `quantity`: New quantity

**Returns**: `CartDTO` - Updated cart

##### `removeFromCart(String sessionId, Integer userId, Integer productId)`
**Description**: Removes product from cart.  
**Parameters**:
- `sessionId`: Session ID
- `userId`: User ID (nullable)
- `productId`: Product to remove

**Returns**: `CartDTO` - Updated cart

##### `clearCart(String sessionId, Integer userId)`
**Description**: Removes all items from cart.  
**Parameters**:
- `sessionId`: Session ID
- `userId`: User ID (nullable)

**Returns**: `void`

##### `mergeAnonymousCartWithUserCart(String sessionId, Integer userId)`
**Description**: Merges anonymous session cart with user's cart during login.  
**Parameters**:
- `sessionId`: Anonymous session ID
- `userId`: User ID

**Returns**: `void`

---

### OrderService

**Package**: `com.baganov.pizzanat.service`  
**Description**: Manages order lifecycle including creation, status updates, and payment processing.

#### Public Methods

##### `createOrder(CreateOrderRequest request, Integer userId)`
**Description**: Creates a new order from user's cart.  
**Parameters**:
- `request`: Order creation request with delivery details
- `userId`: User ID

**Returns**: `OrderDTO` - Created order  
**Throws**: `IllegalArgumentException` if cart is empty or validation fails

**Example Usage**:
```java
CreateOrderRequest request = CreateOrderRequest.builder()
    .deliveryAddress(DeliveryAddress.builder()
        .street("Ленина")
        .house("10")
        .apartment("5")
        .build())
    .paymentMethod(PaymentMethod.YOOKASSA)
    .comment("Extra cheese please")
    .phoneNumber("+79161234567")
    .customerName("John Doe")
    .build();

OrderDTO order = orderService.createOrder(request, userId);
```

##### `getOrderById(Integer orderId, Integer userId)`
**Description**: Retrieves order by ID with user access control.  
**Parameters**:
- `orderId`: Order ID
- `userId`: User ID (null for admin access)

**Returns**: `OrderDTO` - Order details

##### `getUserOrders(Integer userId, Pageable pageable)`
**Description**: Retrieves user's orders with pagination.  
**Parameters**:
- `userId`: User ID
- `pageable`: Pagination parameters

**Returns**: `Page<OrderDTO>` - User's orders

##### `updateOrderStatus(Integer orderId, OrderStatus newStatus, String comment)`
**Description**: Updates order status (admin operation).  
**Parameters**:
- `orderId`: Order ID
- `newStatus`: New order status
- `comment`: Optional status change comment

**Returns**: `OrderDTO` - Updated order

##### `calculateDeliveryFee(String address)`
**Description**: Calculates delivery fee for given address.  
**Parameters**:
- `address`: Delivery address

**Returns**: `BigDecimal` - Delivery fee amount

---

### AuthService

**Package**: `com.baganov.pizzanat.service`  
**Description**: Handles user authentication and JWT token management.

#### Public Methods

##### `register(RegisterRequest request)`
**Description**: Registers a new user account.  
**Parameters**:
- `request`: Registration request with user details

**Returns**: `AuthResponse` - Authentication response with JWT token  
**Throws**: `UserAlreadyExistsException` if username/phone already exists

##### `login(LoginRequest request)`
**Description**: Authenticates user with username/password.  
**Parameters**:
- `request`: Login credentials

**Returns**: `AuthResponse` - Authentication response with JWT token  
**Throws**: `AuthenticationException` if credentials are invalid

##### `generateJwtToken(User user)`
**Description**: Generates JWT token for authenticated user.  
**Parameters**:
- `user`: Authenticated user entity

**Returns**: `String` - JWT token

##### `validateToken(String token)`
**Description**: Validates JWT token and extracts user information.  
**Parameters**:
- `token`: JWT token to validate

**Returns**: `User` - User entity if token is valid  
**Throws**: `SecurityException` if token is invalid or expired

---

### SmsAuthService

**Package**: `com.baganov.pizzanat.service`  
**Description**: Manages SMS-based authentication workflow.

#### Public Methods

##### `sendVerificationCode(String phoneNumber)`
**Description**: Sends SMS verification code to phone number.  
**Parameters**:
- `phoneNumber`: Phone number in international format

**Returns**: `SmsCodeResponse` - Response with request ID and expiration

##### `verifyCode(String phoneNumber, String code, String requestId)`
**Description**: Verifies SMS code and authenticates user.  
**Parameters**:
- `phoneNumber`: Phone number
- `code`: Verification code
- `requestId`: SMS request ID

**Returns**: `AuthResponse` - Authentication response with JWT token  
**Throws**: `InvalidCodeException` if code is incorrect or expired

##### `resendCode(String requestId)`
**Description**: Resends verification code for existing request.  
**Parameters**:
- `requestId`: Original SMS request ID

**Returns**: `SmsCodeResponse` - New code response

---

### YooKassaPaymentService

**Package**: `com.baganov.pizzanat.service`  
**Description**: Integrates with YooKassa payment gateway for order payments.

#### Public Methods

##### `createPayment(CreatePaymentRequest request)`
**Description**: Creates payment in YooKassa system.  
**Parameters**:
- `request`: Payment creation request

**Returns**: `PaymentResponse` - Payment details with payment URL  
**Throws**: `PaymentException` if payment creation fails

**Example Usage**:
```java
CreatePaymentRequest request = CreatePaymentRequest.builder()
    .orderId(1001)
    .amount(new BigDecimal("1330.00"))
    .paymentMethod("bank_card")
    .returnUrl("https://yourapp.com/payment/success")
    .build();

PaymentResponse payment = yooKassaService.createPayment(request);
```

##### `getPaymentStatus(String paymentId)`
**Description**: Retrieves payment status from YooKassa.  
**Parameters**:
- `paymentId`: YooKassa payment ID

**Returns**: `PaymentStatusResponse` - Current payment status

##### `cancelPayment(String paymentId)`
**Description**: Cancels pending payment.  
**Parameters**:
- `paymentId`: Payment ID to cancel

**Returns**: `PaymentResponse` - Updated payment details

##### `processWebhook(WebhookRequest webhook)`
**Description**: Processes payment status webhook from YooKassa.  
**Parameters**:
- `webhook`: Webhook payload from YooKassa

**Returns**: `void`  
**Side Effects**: Updates order and payment status in database

##### `getSbpBanks()`
**Description**: Retrieves list of available SBP banks.  
**Returns**: `List<SbpBankResponse>` - Available banks for SBP payments

---

### DeliveryZoneService

**Package**: `com.baganov.pizzanat.service`  
**Description**: Manages delivery zones, cost calculation, and address validation.

#### Public Methods

##### `getActiveDeliveryZones()`
**Description**: Retrieves all active delivery zones.  
**Returns**: `List<DeliveryZoneDTO>` - Active delivery zones

##### `calculateDeliveryFee(String address)`
**Description**: Calculates delivery fee for given address.  
**Parameters**:
- `address`: Delivery address string

**Returns**: `DeliveryFeeResponse` - Fee calculation result

##### `validateAddress(AddressValidationRequest request)`
**Description**: Validates delivery address and determines zone.  
**Parameters**:
- `request`: Address validation request

**Returns**: `AddressValidationResponse` - Validation result with suggestions

##### `findZoneByAddress(String address)`
**Description**: Determines delivery zone for given address.  
**Parameters**:
- `address`: Address to analyze

**Returns**: `DeliveryZone` - Matching delivery zone or null

---

### TelegramAuthService

**Package**: `com.baganov.pizzanat.service`  
**Description**: Manages Telegram-based authentication workflow.

#### Public Methods

##### `initializeAuth(String phoneNumber)`
**Description**: Initializes Telegram authentication process.  
**Parameters**:
- `phoneNumber`: User's phone number

**Returns**: `TelegramAuthResponse` - Auth token and QR code URL

##### `getAuthStatus(String authToken)`
**Description**: Checks authentication status for token.  
**Parameters**:
- `authToken`: Authentication token

**Returns**: `AuthStatusResponse` - Current authentication status

##### `completeAuth(String authToken, Long telegramId)`
**Description**: Completes authentication after Telegram verification.  
**Parameters**:
- `authToken`: Authentication token
- `telegramId`: User's Telegram ID

**Returns**: `AuthResponse` - JWT token and user details

---

### AddressSuggestionService

**Package**: `com.baganov.pizzanat.service`  
**Description**: Provides address autocomplete and validation using multiple providers.

#### Public Methods

##### `getAddressSuggestions(String query, int limit)`
**Description**: Gets address suggestions for search query.  
**Parameters**:
- `query`: Address search query
- `limit`: Maximum number of suggestions

**Returns**: `List<AddressSuggestion>` - Address suggestions

##### `getHouseSuggestions(String street, int limit)`
**Description**: Gets house number suggestions for street.  
**Parameters**:
- `street`: Street name
- `limit`: Maximum number of suggestions

**Returns**: `List<HouseSuggestion>` - House number suggestions

##### `validateAddress(String address)`
**Description**: Validates address format and existence.  
**Parameters**:
- `address`: Address to validate

**Returns**: `AddressValidationResult` - Validation result

---

### NotificationService

**Package**: `com.baganov.pizzanat.service`  
**Description**: Manages various notification channels (Telegram, SMS, email).

#### Public Methods

##### `sendOrderNotification(Order order, NotificationType type)`
**Description**: Sends order-related notification.  
**Parameters**:
- `order`: Order entity
- `type`: Type of notification (CREATED, STATUS_CHANGED, etc.)

**Returns**: `void`

##### `sendPaymentNotification(Payment payment, PaymentStatus status)`
**Description**: Sends payment status notification.  
**Parameters**:
- `payment`: Payment entity
- `status`: New payment status

**Returns**: `void`

##### `scheduleNotification(ScheduledNotificationRequest request)`
**Description**: Schedules notification for future delivery.  
**Parameters**:
- `request`: Notification scheduling request

**Returns**: `String` - Notification ID

---

### AdminStatsService

**Package**: `com.baganov.pizzanat.service`  
**Description**: Provides administrative statistics and analytics.

#### Public Methods

##### `getOverallStats()`
**Description**: Retrieves overall business statistics.  
**Returns**: `AdminStatsResponse` - Comprehensive statistics

##### `getOrderStats(LocalDate from, LocalDate to)`
**Description**: Gets order statistics for date range.  
**Parameters**:
- `from`: Start date
- `to`: End date

**Returns**: `OrderStatsResponse` - Order analytics

##### `getRevenueStats(LocalDate from, LocalDate to)`
**Description**: Gets revenue statistics for date range.  
**Parameters**:
- `from`: Start date
- `to`: End date

**Returns**: `RevenueStatsResponse` - Revenue analytics

##### `getTopProducts(int limit)`
**Description**: Gets top-selling products.  
**Parameters**:
- `limit`: Number of products to return

**Returns**: `List<TopProductResponse>` - Top products by sales

---

## Utility Services

### S3Service

**Package**: `com.baganov.pizzanat.service`  
**Description**: Manages file uploads to S3-compatible storage.

#### Public Methods

##### `uploadImage(MultipartFile file, String folder)`
**Description**: Uploads image file to storage.  
**Parameters**:
- `file`: Image file to upload
- `folder`: Storage folder path

**Returns**: `String` - Public URL of uploaded image  
**Throws**: `StorageException` if upload fails

##### `deleteImage(String imageUrl)`
**Description**: Deletes image from storage.  
**Parameters**:
- `imageUrl`: URL of image to delete

**Returns**: `void`

##### `generatePresignedUrl(String key, Duration expiration)`
**Description**: Generates temporary access URL for private files.  
**Parameters**:
- `key`: File key in storage
- `expiration`: URL expiration duration

**Returns**: `String` - Presigned URL

---

### RateLimitService

**Package**: `com.baganov.pizzanat.service`  
**Description**: Implements rate limiting for API endpoints.

#### Public Methods

##### `isAllowed(String identifier, int maxRequests, Duration timeWindow)`
**Description**: Checks if request is within rate limit.  
**Parameters**:
- `identifier`: Client identifier (IP, user ID, etc.)
- `maxRequests`: Maximum requests allowed
- `timeWindow`: Time window for rate limiting

**Returns**: `boolean` - True if request is allowed

##### `getRemainingRequests(String identifier, Duration timeWindow)`
**Description**: Gets remaining requests in current window.  
**Parameters**:
- `identifier`: Client identifier
- `timeWindow`: Time window

**Returns**: `int` - Remaining request count

---

## Integration Services

### TelegramBotService

**Package**: `com.baganov.pizzanat.service`  
**Description**: Manages Telegram bot integration and message handling.

#### Public Methods

##### `sendMessage(Long chatId, String message)`
**Description**: Sends text message to Telegram chat.  
**Parameters**:
- `chatId`: Telegram chat ID
- `message`: Message text

**Returns**: `void`

##### `sendOrderNotification(Long chatId, Order order)`
**Description**: Sends formatted order notification.  
**Parameters**:
- `chatId`: Telegram chat ID
- `order`: Order to notify about

**Returns**: `void`

##### `processIncomingMessage(Update update)`
**Description**: Processes incoming Telegram message.  
**Parameters**:
- `update`: Telegram update object

**Returns**: `void`

---

### ExolveService

**Package**: `com.baganov.pizzanat.service`  
**Description**: Integrates with Exolve SMS service provider.

#### Public Methods

##### `sendSms(String phoneNumber, String message)`
**Description**: Sends SMS message via Exolve.  
**Parameters**:
- `phoneNumber`: Phone number
- `message`: SMS text

**Returns**: `SmsResponse` - Delivery status

##### `getDeliveryStatus(String messageId)`
**Description**: Checks SMS delivery status.  
**Parameters**:
- `messageId`: SMS message ID

**Returns**: `DeliveryStatusResponse` - Current delivery status

---

## Service Configuration

### Caching

Most services use Spring Cache annotations for performance optimization:

- `@Cacheable`: Caches method results
- `@CacheEvict`: Removes items from cache
- `@CachePut`: Updates cache with new value

**Cache Names Used**:
- `products`: Product-related cache
- `categories`: Category cache
- `delivery-zones`: Delivery zone cache
- `address-suggestions`: Address suggestion cache

### Transaction Management

Services use `@Transactional` annotations:
- **Read-only**: `@Transactional(readOnly = true)` for query methods
- **Read-write**: `@Transactional` for modification methods
- **Propagation**: Custom propagation settings for complex operations

### Error Handling

Services throw specific business exceptions:
- `BusinessException`: Base business logic exception
- `NotFoundException`: Entity not found
- `ValidationException`: Data validation errors
- `PaymentException`: Payment processing errors
- `AuthenticationException`: Authentication failures

### Example Service Usage

```java
@RestController
@RequiredArgsConstructor
public class ExampleController {
    
    private final ProductService productService;
    private final CartService cartService;
    private final OrderService orderService;
    
    @PostMapping("/complete-order")
    public ResponseEntity<OrderDTO> completeOrder(
            @RequestBody CreateOrderRequest request,
            Authentication auth) {
        
        // Get user from authentication
        Integer userId = ((User) auth.getPrincipal()).getId();
        
        // Create order from cart
        OrderDTO order = orderService.createOrder(request, userId);
        
        // Clear cart after successful order
        cartService.clearCart(null, userId);
        
        return ResponseEntity.ok(order);
    }
}
```

This comprehensive service documentation provides developers with detailed information about all public methods, their parameters, return values, exceptions, and usage examples for effective integration with the PizzaNat business logic layer.