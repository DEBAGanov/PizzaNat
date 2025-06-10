package com.baganov.pizzanat.service;

import com.baganov.pizzanat.model.dto.order.CreateOrderRequest;
import com.baganov.pizzanat.model.dto.order.OrderDTO;
import com.baganov.pizzanat.model.dto.order.OrderItemDTO;
import com.baganov.pizzanat.model.dto.payment.PaymentUrlResponse;
import com.baganov.pizzanat.entity.*;
import com.baganov.pizzanat.repository.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.cache.annotation.Caching;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class OrderService {

    private final OrderRepository orderRepository;
    private final OrderStatusRepository orderStatusRepository;
    private final CartRepository cartRepository;
    private final UserRepository userRepository;
    private final DeliveryLocationRepository deliveryLocationRepository;
    private final StorageService storageService;
    private final NotificationService notificationService;
    private final PaymentService paymentService;
    private final TelegramBotService telegramBotService;

    @Transactional
    @CacheEvict(value = { "userOrders", "orderDetails", "allOrders" }, allEntries = true)
    public OrderDTO createOrder(Integer userId, String sessionId, CreateOrderRequest request) {
        // Валидация входных данных
        if (!request.hasValidDeliveryInfo()) {
            throw new IllegalArgumentException("Необходимо указать либо ID пункта выдачи, либо адрес доставки");
        }

        User user = null;
        if (userId != null) {
            user = userRepository.findById(userId)
                    .orElseThrow(() -> new IllegalArgumentException("Пользователь не найден"));
        }

        Cart cart = findCart(userId, sessionId);
        if (cart == null || cart.getItems().isEmpty()) {
            throw new IllegalArgumentException("Корзина пуста");
        }

        // Определяем пункт доставки
        DeliveryLocation deliveryLocation;
        if (request.getDeliveryLocationId() != null) {
            // Используем существующий пункт доставки
            deliveryLocation = deliveryLocationRepository.findById(request.getDeliveryLocationId())
                    .orElseThrow(() -> new IllegalArgumentException("Пункт выдачи не найден"));

            if (!deliveryLocation.isActive()) {
                throw new IllegalArgumentException("Пункт выдачи недоступен");
            }
        } else {
            // Создаем новый пункт доставки для Android приложения
            deliveryLocation = createDeliveryLocationFromAddress(request.getDeliveryAddress());
        }

        OrderStatus createdStatus = orderStatusRepository.findByName("CREATED")
                .orElseThrow(() -> new IllegalArgumentException("Статус заказа 'CREATED' не найден"));

        // Получаем финальный комментарий (приоритет: comment > notes)
        String finalComment = request.getFinalComment();

        Order order = Order.builder()
                .user(user)
                .status(createdStatus)
                .deliveryLocation(deliveryLocation)
                .deliveryAddress(request.getDeliveryAddress()) // Сохраняем Android адрес
                .totalAmount(cart.getTotalAmount())
                .comment(finalComment)
                .contactName(request.getContactName())
                .contactPhone(request.getContactPhone())
                .build();

        // Копирование товаров из корзины в заказ
        for (CartItem cartItem : cart.getItems()) {
            // Используем скидочную цену если есть, иначе обычную
            BigDecimal itemPrice = cartItem.getProduct().getDiscountedPrice() != null
                    ? cartItem.getProduct().getDiscountedPrice()
                    : cartItem.getProduct().getPrice();

            OrderItem orderItem = OrderItem.builder()
                    .order(order)
                    .product(cartItem.getProduct())
                    .quantity(cartItem.getQuantity())
                    .price(itemPrice)
                    .build();
            order.addItem(orderItem);
        }

        order = orderRepository.save(order);

        // Очистка корзины после создания заказа
        cart.getItems().clear();
        cartRepository.save(cart);

        // Отправка Telegram уведомления о новом заказе
        try {
            telegramBotService.sendNewOrderNotification(order);
        } catch (Exception e) {
            log.error("Ошибка отправки Telegram уведомления о новом заказе #{}: {}", order.getId(), e.getMessage());
        }

        log.info("Создан новый заказ #{} на сумму {} (адрес: {})",
                order.getId(), order.getTotalAmount(),
                request.getDeliveryAddress() != null ? request.getDeliveryAddress() : deliveryLocation.getAddress());

        return mapToDTO(order);
    }

    /**
     * Создает новый пункт доставки из адреса (для Android приложения)
     * Если пункт с таким адресом уже существует, возвращает существующий
     */
    private DeliveryLocation createDeliveryLocationFromAddress(String address) {
        if (address == null || address.trim().isEmpty()) {
            throw new IllegalArgumentException("Адрес доставки не может быть пустым");
        }

        String cleanAddress = address.trim();

        // Проверяем, существует ли уже пункт доставки с таким адресом
        Optional<DeliveryLocation> existingLocation = deliveryLocationRepository.findByAddress(cleanAddress);
        if (existingLocation.isPresent()) {
            log.info("Используется существующий пункт доставки с адресом: {}", cleanAddress);
            return existingLocation.get();
        }

        // Генерируем уникальное имя для пункта доставки
        String locationName = "Доставка по адресу: " + cleanAddress;

        // На всякий случай проверяем существование по имени (хотя это маловероятно)
        int counter = 1;
        String finalName = locationName;
        while (deliveryLocationRepository.existsByName(finalName)) {
            finalName = locationName + " (" + counter + ")";
            counter++;
        }

        // Создаем новый пункт доставки
        DeliveryLocation newLocation = DeliveryLocation.builder()
                .name(finalName)
                .address(cleanAddress)
                .phone("Указать при доставке")
                .workingHours("Круглосуточно")
                .isActive(true)
                .build();

        try {
            DeliveryLocation savedLocation = deliveryLocationRepository.save(newLocation);
            log.info("Создан новый пункт доставки: {} по адресу: {}", savedLocation.getName(), cleanAddress);
            return savedLocation;
        } catch (Exception e) {
            log.error("Ошибка при создании пункта доставки для адреса: {}", cleanAddress, e);
            // Если произошла ошибка уникальности, попробуем найти существующий
            return deliveryLocationRepository.findByAddress(cleanAddress)
                    .orElseThrow(() -> new RuntimeException("Не удалось создать или найти пункт доставки"));
        }
    }

    /**
     * Создает URL для оплаты заказа через платежную систему
     *
     * @param orderId идентификатор заказа
     * @return объект с URL для оплаты
     */
    @Transactional(readOnly = true)
    public PaymentUrlResponse createPaymentUrl(Integer orderId, Integer userId) {
        Order order = findOrder(orderId, userId);

        // Проверяем статус заказа - он должен быть в статусе "Создан"
        if (!"CREATED".equals(order.getStatus().getName())) {
            throw new IllegalStateException("Невозможно создать ссылку для оплаты заказа в текущем статусе");
        }

        String description = String.format("Оплата заказа №%d в PizzaNat", order.getId());

        // Создаем URL для оплаты через платежный сервис
        String paymentUrl = paymentService.createPaymentUrl(order.getId(), order.getTotalAmount(), description);

        log.info("Создан URL для оплаты заказа #{}: {}", order.getId(), paymentUrl);

        return new PaymentUrlResponse(paymentUrl);
    }

    @Transactional(readOnly = true)
    @Cacheable(value = "orderDetails", key = "#orderId + '-' + #userId")
    public OrderDTO getOrderById(Integer orderId, Integer userId) {
        Order order = findOrder(orderId, userId);
        return mapToDTO(order);
    }

    @Transactional(readOnly = true)
    @Cacheable(value = "userOrders", key = "#userId + '-' + #pageable.pageNumber + '-' + #pageable.pageSize")
    public Page<OrderDTO> getUserOrders(Integer userId, Pageable pageable) {
        return orderRepository.findByUserIdOrderByCreatedAtDesc(userId, pageable)
                .map(this::mapToDTO);
    }

    @Transactional(readOnly = true)
    @Cacheable(value = "allOrders", key = "#pageable.pageNumber + '-' + #pageable.pageSize + '-' + #pageable.sort")
    public Page<OrderDTO> getAllOrders(Pageable pageable) {
        return orderRepository.findAll(pageable)
                .map(this::mapToDTO);
    }

    @Transactional
    @Caching(evict = {
            @CacheEvict(value = "userOrders", allEntries = true),
            @CacheEvict(value = "orderDetails", allEntries = true),
            @CacheEvict(value = "allOrders", allEntries = true)
    })
    public OrderDTO updateOrderStatus(Integer orderId, String statusName) {
        log.info("Начало обновления статуса заказа {} на '{}'", orderId, statusName);

        // Валидация входных параметров
        if (orderId == null) {
            throw new IllegalArgumentException("ID заказа не может быть null");
        }
        if (statusName == null || statusName.trim().isEmpty()) {
            throw new IllegalArgumentException("Название статуса не может быть пустым");
        }

        String normalizedStatusName = statusName.trim().toUpperCase();
        log.debug("Нормализованное название статуса: '{}'", normalizedStatusName);

        try {
            // Поиск заказа
            Order order = orderRepository.findById(orderId)
                    .orElseThrow(() -> new IllegalArgumentException("Заказ с ID " + orderId + " не найден"));

            log.debug("Найден заказ с ID: {}, текущий статус: '{}'", orderId, order.getStatus().getName());

            // Сохраняем старый статус для логирования и уведомлений
            OrderStatus oldStatus = order.getStatus();

            // Поиск нового статуса
            OrderStatus newStatus = orderStatusRepository.findByName(normalizedStatusName)
                    .orElseThrow(() -> new IllegalArgumentException(
                            String.format(
                                    "Статус заказа '%s' не найден. Доступные статусы: PENDING, CONFIRMED, PREPARING, READY, DELIVERING, DELIVERED, CANCELLED, CREATED, PAID",
                                    normalizedStatusName)));

            log.debug("Найден новый статус: '{}' с ID: {}", newStatus.getName(), newStatus.getId());

            // Проверяем, не пытаемся ли мы установить тот же статус
            if (oldStatus.getId().equals(newStatus.getId())) {
                log.info("Статус заказа {} уже установлен на '{}', изменений не требуется", orderId,
                        normalizedStatusName);
                return mapToDTO(order);
            }

            // Обновляем статус
            order.setStatus(newStatus);
            order = orderRepository.save(order);

            log.info("Статус заказа #{} успешно изменен с '{}' на '{}'",
                    order.getId(), oldStatus.getName(), newStatus.getName());

            // Отправка Telegram уведомления об изменении статуса (безопасно)
            sendTelegramNotificationSafely(order, oldStatus.getName(), newStatus.getName());

            return mapToDTO(order);

        } catch (IllegalArgumentException e) {
            log.error("Ошибка валидации при обновлении статуса заказа {}: {}", orderId, e.getMessage());
            throw e;
        } catch (Exception e) {
            log.error("Неожиданная ошибка при обновлении статуса заказа {} на '{}': {}", orderId, statusName,
                    e.getMessage(), e);
            throw new RuntimeException("Не удалось обновить статус заказа: " + e.getMessage(), e);
        }
    }

    /**
     * Безопасная отправка Telegram уведомления
     */
    private void sendTelegramNotificationSafely(Order order, String oldStatusName, String newStatusName) {
        try {
            if (telegramBotService != null) {
                telegramBotService.sendOrderStatusUpdateNotification(order, oldStatusName, newStatusName);
                log.debug("Telegram уведомление об изменении статуса заказа #{} успешно отправлено", order.getId());
            } else {
                log.warn("TelegramBotService недоступен, уведомление не отправлено для заказа #{}", order.getId());
            }
        } catch (Exception e) {
            log.error("Ошибка отправки Telegram уведомления об изменении статуса заказа #{}: {}",
                    order.getId(), e.getMessage());
            // Не пробрасываем исключение, чтобы не нарушать основную логику обновления
            // статуса
        }
    }

    /**
     * Обновляет статус заказа на "Оплачен"
     *
     * @param orderId идентификатор заказа
     * @return обновленный заказ
     */
    @Transactional
    @Caching(evict = {
            @CacheEvict(value = "userOrders", allEntries = true),
            @CacheEvict(value = "orderDetails", allEntries = true),
            @CacheEvict(value = "allOrders", allEntries = true)
    })
    public OrderDTO markOrderAsPaid(Integer orderId) {
        // Обновляем статус заказа на "Оплачен"
        return updateOrderStatus(orderId, "PAID");
    }

    private Order findOrder(Integer orderId, Integer userId) {
        if (userId != null) {
            return orderRepository.findByIdAndUserId(orderId, userId)
                    .orElseThrow(() -> new IllegalArgumentException("Заказ не найден"));
        } else {
            return orderRepository.findById(orderId)
                    .orElseThrow(() -> new IllegalArgumentException("Заказ не найден"));
        }
    }

    private Cart findCart(Integer userId, String sessionId) {
        if (userId != null) {
            return cartRepository.findByUserId(userId).orElse(null);
        } else if (sessionId != null) {
            return cartRepository.findBySessionId(sessionId).orElse(null);
        }
        return null;
    }

    private OrderDTO mapToDTO(Order order) {
        List<OrderItemDTO> itemDTOs = order.getItems().stream()
                .map(this::mapToDTO)
                .collect(Collectors.toList());

        // Форматируем даты в строки для избежания проблем сериализации
        String createdAtStr = order.getCreatedAt() != null ? order.getCreatedAt().toString() : null;
        String updatedAtStr = order.getUpdatedAt() != null ? order.getUpdatedAt().toString() : null;

        return OrderDTO.builder()
                .id(order.getId())
                .status(order.getStatus().getName())
                .statusDescription(order.getStatus().getDescription())
                .deliveryLocationId(order.getDeliveryLocation().getId())
                .deliveryLocationName(order.getDeliveryLocation().getName())
                .deliveryLocationAddress(order.getDeliveryLocation().getAddress())
                .deliveryAddress(order.getDeliveryAddress())
                .totalAmount(order.getTotalAmount())
                .comment(order.getComment())
                .contactName(order.getContactName())
                .contactPhone(order.getContactPhone())
                .createdAt(createdAtStr)
                .updatedAt(updatedAtStr)
                .items(itemDTOs)
                .build();
    }

    private OrderItemDTO mapToDTO(OrderItem item) {
        String imageUrl = null;
        if (item.getProduct().getImageUrl() != null && !item.getProduct().getImageUrl().isEmpty()) {
            try {
                // Для изображений продуктов используем простые публичные URL
                if (item.getProduct().getImageUrl().startsWith("products/")) {
                    imageUrl = storageService.getPublicUrl(item.getProduct().getImageUrl());
                } else {
                    // Если URL уже полный, используем как есть
                    imageUrl = item.getProduct().getImageUrl();
                }
            } catch (Exception e) {
                log.error("Error getting public URL for product image", e);
            }
        }

        BigDecimal subtotal = item.getPrice().multiply(BigDecimal.valueOf(item.getQuantity()));

        return OrderItemDTO.builder()
                .id(item.getId())
                .productId(item.getProduct().getId())
                .productName(item.getProduct().getName())
                .productImageUrl(imageUrl)
                .quantity(item.getQuantity())
                .price(item.getPrice())
                .subtotal(subtotal)
                .build();
    }
}