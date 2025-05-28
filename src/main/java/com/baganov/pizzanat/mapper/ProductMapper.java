package com.baganov.pizzanat.mapper;

import com.baganov.pizzanat.dto.ProductDto;
import com.baganov.pizzanat.entity.Product;
import com.baganov.pizzanat.service.StorageService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class ProductMapper {

    private final StorageService storageService;

    public Product toEntity(ProductDto dto) {
        return Product.builder()
                .id(dto.getId())
                .name(dto.getName())
                .description(dto.getDescription())
                .price(dto.getPrice())
                .discountedPrice(dto.getDiscountedPrice())
                .imageUrl(dto.getImageUrl())
                .weight(dto.getWeight())
                .isAvailable(dto.isAvailable())
                .isSpecialOffer(dto.isSpecialOffer())
                .discountPercent(dto.getDiscountPercent())
                .build();
    }

    public ProductDto toDto(Product entity) {
        String imageUrl = null;
        if (entity.getImageUrl() != null && !entity.getImageUrl().isEmpty()) {
            try {
                // Для изображений продуктов используем простые публичные URL
                if (entity.getImageUrl().startsWith("products/")) {
                    imageUrl = storageService.getPublicUrl(entity.getImageUrl());
                } else {
                    // Если URL уже полный, используем как есть
                    imageUrl = entity.getImageUrl();
                }
            } catch (Exception e) {
                log.error("Error generating public URL for product image", e);
                imageUrl = entity.getImageUrl();
            }
        }

        return ProductDto.builder()
                .id(entity.getId())
                .name(entity.getName())
                .description(entity.getDescription())
                .price(entity.getPrice())
                .discountedPrice(entity.getDiscountedPrice())
                .categoryId(entity.getCategory() != null ? entity.getCategory().getId() : null)
                .categoryName(entity.getCategory() != null ? entity.getCategory().getName() : null)
                .imageUrl(imageUrl)
                .weight(entity.getWeight())
                .isAvailable(entity.isAvailable())
                .isSpecialOffer(entity.isSpecialOffer())
                .discountPercent(entity.getDiscountPercent())
                .build();
    }
}