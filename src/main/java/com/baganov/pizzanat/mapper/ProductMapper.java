package com.baganov.pizzanat.mapper;

import com.baganov.pizzanat.dto.ProductDto;
import com.baganov.pizzanat.entity.Product;
import org.springframework.stereotype.Component;

@Component
public class ProductMapper {

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
        return ProductDto.builder()
                .id(entity.getId())
                .name(entity.getName())
                .description(entity.getDescription())
                .price(entity.getPrice())
                .discountedPrice(entity.getDiscountedPrice())
                .categoryId(entity.getCategory() != null ? entity.getCategory().getId() : null)
                .categoryName(entity.getCategory() != null ? entity.getCategory().getName() : null)
                .imageUrl(entity.getImageUrl())
                .weight(entity.getWeight())
                .isAvailable(entity.isAvailable())
                .isSpecialOffer(entity.isSpecialOffer())
                .discountPercent(entity.getDiscountPercent())
                .build();
    }
}