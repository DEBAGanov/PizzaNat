package com.baganov.pizzanat.service;

import com.baganov.pizzanat.dto.ProductDto;
import com.baganov.pizzanat.entity.Product;
import com.baganov.pizzanat.mapper.ProductMapper;
import com.baganov.pizzanat.repository.ProductRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ProductService {

    private final ProductRepository productRepository;
    private final ProductMapper productMapper;
    private final S3Service s3Service;

    private static final String PRODUCTS_FOLDER = "products";

    @Transactional
    public ProductDto createProduct(ProductDto productDto, MultipartFile image) {
        String imageUrl = s3Service.uploadImage(image, PRODUCTS_FOLDER);
        productDto.setImageUrl(imageUrl);

        Product product = productMapper.toEntity(productDto);
        Product savedProduct = productRepository.save(product);
        return productMapper.toDto(savedProduct);
    }

    @Cacheable(value = "products", key = "'product:' + #id")
    public ProductDto getProductById(Integer id) {
        Product product = productRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Продукт не найден с ID: " + id));
        return productMapper.toDto(product);
    }

    public Page<ProductDto> getAllProducts(Pageable pageable) {
        return productRepository.findAllByIsAvailableTrue(pageable)
                .map(productMapper::toDto);
    }

    public Page<ProductDto> getProductsByCategory(Integer categoryId, Pageable pageable) {
        return productRepository.findByCategoryId(categoryId, pageable)
                .map(productMapper::toDto);
    }

    @Cacheable(value = "products", key = "'products:special'")
    public List<ProductDto> getSpecialOffers() {
        return productRepository.findByIsSpecialOfferTrue()
                .stream()
                .map(productMapper::toDto)
                .toList();
    }

    public Page<ProductDto> searchProducts(String query, Integer categoryId, Pageable pageable) {
        if (categoryId != null) {
            return productRepository.findByCategoryIdAndNameContainingIgnoreCase(categoryId, query, pageable)
                    .map(productMapper::toDto);
        }
        return productRepository.findByNameContainingIgnoreCase(query, pageable)
                .map(productMapper::toDto);
    }
}