package com.baganov.pizzanat.controller;

import com.baganov.pizzanat.dto.ProductDto;
import com.baganov.pizzanat.service.ProductService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

@RestController
@RequestMapping("/api/v1/products")
@RequiredArgsConstructor
@Tag(name = "Продукты", description = "API для работы с продуктами")
public class ProductController {

    private final ProductService productService;

    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @Operation(summary = "Создать новый продукт")
    public ProductDto createProduct(@RequestPart("product") ProductDto productDto,
            @RequestPart("image") MultipartFile image) {
        return productService.createProduct(productDto, image);
    }

    @GetMapping
    @Operation(summary = "Получить все продукты")
    public Page<ProductDto> getAllProducts(@PageableDefault Pageable pageable) {
        return productService.getAllProducts(pageable);
    }

    @GetMapping("/{id}")
    @Operation(summary = "Получить продукт по ID")
    public ProductDto getProductById(@PathVariable Integer id) {
        return productService.getProductById(id);
    }

    @GetMapping("/category/{categoryId}")
    @Operation(summary = "Получить продукты по категории")
    public Page<ProductDto> getProductsByCategory(@PathVariable Integer categoryId,
            @PageableDefault Pageable pageable) {
        return productService.getProductsByCategory(categoryId, pageable);
    }

    @GetMapping("/special-offers")
    @Operation(summary = "Получить специальные предложения")
    public List<ProductDto> getSpecialOffers() {
        return productService.getSpecialOffers();
    }

    @GetMapping("/search")
    @Operation(summary = "Поиск продуктов")
    public Page<ProductDto> searchProducts(@RequestParam String query,
            @RequestParam(required = false) Integer categoryId,
            @PageableDefault Pageable pageable) {
        return productService.searchProducts(query, categoryId, pageable);
    }
}