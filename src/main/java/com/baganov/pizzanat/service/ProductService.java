package com.baganov.pizzanat.service;

import com.baganov.pizzanat.model.dto.product.ProductDTO;
import com.baganov.pizzanat.model.entity.Product;
import com.baganov.pizzanat.repository.CategoryRepository;
import com.baganov.pizzanat.repository.ProductRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class ProductService {

    private final ProductRepository productRepository;
    private final CategoryRepository categoryRepository;
    private final StorageService storageService;

    @Cacheable(value = "products", key = "'product:' + #id")
    public ProductDTO getProductById(Integer id) {
        Product product = productRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Продукт не найден с ID: " + id));
        return mapToDTO(product);
    }

    @Cacheable(value = "products", key = "'products:page:' + #pageable.pageNumber + ':' + #pageable.pageSize")
    public Page<ProductDTO> getAllProducts(Pageable pageable) {
        return productRepository.findAllByIsAvailableTrue(pageable)
                .map(this::mapToDTO);
    }

    @Cacheable(value = "products", key = "'products:category:' + #categoryId + ':page:' + #pageable.pageNumber + ':' + #pageable.pageSize")
    public Page<ProductDTO> getProductsByCategory(Integer categoryId, Pageable pageable) {
        return productRepository.findByCategoryIdAndIsAvailableTrue(categoryId, pageable)
                .map(this::mapToDTO);
    }

    @Cacheable(value = "products", key = "'products:special'")
    public List<ProductDTO> getSpecialOffers() {
        return productRepository.findTop8ByIsAvailableTrueAndIsSpecialOfferTrueOrderByIdDesc()
                .stream()
                .map(this::mapToDTO)
                .collect(Collectors.toList());
    }

    @Cacheable(value = "products", key = "'products:search:' + #query + ':category:' + #categoryId + ':page:' + #pageable.pageNumber + ':' + #pageable.pageSize")
    public Page<ProductDTO> searchProducts(String query, Integer categoryId, Pageable pageable) {
        return productRepository.searchProducts(categoryId, query, pageable)
                .map(this::mapToDTO);
    }

    private ProductDTO mapToDTO(Product product) {
        String imageUrlWithPresignedUrl = null;
        if (product.getImageUrl() != null && !product.getImageUrl().isEmpty()) {
            try {
                imageUrlWithPresignedUrl = storageService.getPresignedUrl(product.getImageUrl(), 3600);
            } catch (Exception e) {
                log.error("Failed to generate presigned URL for product image: {}", product.getImageUrl(), e);
                imageUrlWithPresignedUrl = product.getImageUrl();
            }
        }

        return ProductDTO.builder()
                .id(product.getId())
                .name(product.getName())
                .description(product.getDescription())
                .price(product.getPrice())
                .discountedPrice(product.getDiscountedPrice())
                .categoryId(product.getCategory() != null ? product.getCategory().getId() : null)
                .categoryName(product.getCategory() != null ? product.getCategory().getName() : null)
                .imageUrl(imageUrlWithPresignedUrl)
                .weight(product.getWeight())
                .isAvailable(product.isAvailable())
                .isSpecialOffer(product.isSpecialOffer())
                .discountPercent(product.getDiscountPercent())
                .build();
    }
}