package com.baganov.pizzanat.service;

import com.baganov.pizzanat.model.dto.cart.CartDTO;
import com.baganov.pizzanat.model.dto.cart.CartItemDTO;
import com.baganov.pizzanat.model.entity.Cart;
import com.baganov.pizzanat.model.entity.CartItem;
import com.baganov.pizzanat.model.entity.Product;
import com.baganov.pizzanat.model.entity.User;
import com.baganov.pizzanat.repository.CartRepository;
import com.baganov.pizzanat.repository.ProductRepository;
import com.baganov.pizzanat.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class CartService {

    private final CartRepository cartRepository;
    private final UserRepository userRepository;
    private final ProductRepository productRepository;
    private final StorageService storageService;

    @Transactional(readOnly = true)
    public CartDTO getCart(String sessionId, Integer userId) {
        Cart cart = findOrCreateCart(sessionId, userId);
        return mapToDTO(cart);
    }

    @Transactional
    public CartDTO addToCart(String sessionId, Integer userId, Integer productId, Integer quantity) {
        Cart cart = findOrCreateCart(sessionId, userId);
        Product product = productRepository.findById(productId)
                .orElseThrow(() -> new IllegalArgumentException("Продукт не найден с ID: " + productId));

        if (!product.isAvailable()) {
            throw new IllegalArgumentException("Продукт недоступен");
        }

        Optional<CartItem> existingItem = cart.getItems().stream()
                .filter(item -> item.getProduct().getId().equals(productId))
                .findFirst();

        if (existingItem.isPresent()) {
            CartItem item = existingItem.get();
            item.setQuantity(item.getQuantity() + quantity);
        } else {
            CartItem newItem = CartItem.builder()
                    .cart(cart)
                    .product(product)
                    .quantity(quantity)
                    .build();
            cart.addItem(newItem);
        }

        cartRepository.save(cart);
        return mapToDTO(cart);
    }

    @Transactional
    public CartDTO updateCartItem(String sessionId, Integer userId, Integer productId, Integer quantity) {
        Cart cart = findOrCreateCart(sessionId, userId);

        CartItem item = cart.getItems().stream()
                .filter(i -> i.getProduct().getId().equals(productId))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Товар не найден в корзине"));

        if (quantity <= 0) {
            cart.removeItem(item);
        } else {
            item.setQuantity(quantity);
        }

        cartRepository.save(cart);
        return mapToDTO(cart);
    }

    @Transactional
    public CartDTO removeFromCart(String sessionId, Integer userId, Integer productId) {
        Cart cart = findOrCreateCart(sessionId, userId);

        CartItem item = cart.getItems().stream()
                .filter(i -> i.getProduct().getId().equals(productId))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Товар не найден в корзине"));

        cart.removeItem(item);
        cartRepository.save(cart);
        return mapToDTO(cart);
    }

    @Transactional
    public void clearCart(String sessionId, Integer userId) {
        Cart cart = findOrCreateCart(sessionId, userId);
        cart.getItems().clear();
        cartRepository.save(cart);
    }

    @Transactional
    public void mergeAnonymousCartWithUserCart(String sessionId, Integer userId) {
        if (sessionId == null || userId == null) {
            return;
        }

        Optional<Cart> anonymousCartOpt = cartRepository.findBySessionId(sessionId);
        if (anonymousCartOpt.isEmpty() || anonymousCartOpt.get().getItems().isEmpty()) {
            return;
        }

        Cart anonymousCart = anonymousCartOpt.get();
        Cart userCart = findOrCreateCart(null, userId);

        for (CartItem anonymousItem : anonymousCart.getItems()) {
            Optional<CartItem> existingItem = userCart.getItems().stream()
                    .filter(item -> item.getProduct().getId().equals(anonymousItem.getProduct().getId()))
                    .findFirst();

            if (existingItem.isPresent()) {
                CartItem item = existingItem.get();
                item.setQuantity(item.getQuantity() + anonymousItem.getQuantity());
            } else {
                CartItem newItem = CartItem.builder()
                        .cart(userCart)
                        .product(anonymousItem.getProduct())
                        .quantity(anonymousItem.getQuantity())
                        .build();
                userCart.addItem(newItem);
            }
        }

        cartRepository.save(userCart);
        cartRepository.delete(anonymousCart);
    }

    private Cart findOrCreateCart(String sessionId, Integer userId) {
        if (userId != null) {
            return cartRepository.findByUserId(userId)
                    .orElseGet(() -> createCartForUser(userId));
        } else if (sessionId != null) {
            return cartRepository.findBySessionId(sessionId)
                    .orElseGet(() -> createAnonymousCart(sessionId));
        } else {
            throw new IllegalArgumentException("Должен быть указан либо sessionId, либо userId");
        }
    }

    private Cart createCartForUser(Integer userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new IllegalArgumentException("Пользователь не найден с ID: " + userId));

        Cart cart = Cart.builder()
                .user(user)
                .build();

        return cartRepository.save(cart);
    }

    private Cart createAnonymousCart(String sessionId) {
        Cart cart = Cart.builder()
                .sessionId(sessionId != null ? sessionId : UUID.randomUUID().toString())
                .build();

        return cartRepository.save(cart);
    }

    private CartDTO mapToDTO(Cart cart) {
        List<CartItemDTO> itemDTOs = cart.getItems().stream()
                .map(this::mapToDTO)
                .collect(Collectors.toList());

        return CartDTO.builder()
                .id(cart.getId())
                .sessionId(cart.getSessionId())
                .totalAmount(cart.getTotalAmount())
                .items(itemDTOs)
                .build();
    }

    private CartItemDTO mapToDTO(CartItem item) {
        String imageUrl = null;
        if (item.getProduct().getImageUrl() != null && !item.getProduct().getImageUrl().isEmpty()) {
            try {
                imageUrl = storageService.getPresignedUrl(item.getProduct().getImageUrl(), 3600);
            } catch (Exception e) {
                log.error("Error generating presigned URL for product image", e);
            }
        }

        BigDecimal price = item.getProduct().getPrice();
        BigDecimal discountedPrice = item.getProduct().getDiscountedPrice() != null
                ? item.getProduct().getDiscountedPrice()
                : price;

        return CartItemDTO.builder()
                .id(item.getId())
                .productId(item.getProduct().getId())
                .productName(item.getProduct().getName())
                .productImageUrl(imageUrl)
                .price(price)
                .discountedPrice(discountedPrice)
                .quantity(item.getQuantity())
                .subtotal(discountedPrice.multiply(BigDecimal.valueOf(item.getQuantity())))
                .build();
    }
}