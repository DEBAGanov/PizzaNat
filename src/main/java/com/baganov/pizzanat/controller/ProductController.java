package com.baganov.pizzanat.controller;

import com.baganov.pizzanat.model.dto.product.ProductDTO;
import com.baganov.pizzanat.service.ProductService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@Slf4j
@RestController
@RequestMapping("/api/v1/products")
@RequiredArgsConstructor
@Tag(name = "Products", description = "API для работы с продуктами")
public class ProductController {

    private final ProductService productService;

    @GetMapping
    @Operation(summary = "Получение списка продуктов")
    public ResponseEntity<Page<ProductDTO>> getAllProducts(
            @Parameter(description = "Номер страницы (начиная с 0)") @RequestParam(defaultValue = "0") int page,
            @Parameter(description = "Размер страницы") @RequestParam(defaultValue = "10") int size,
            @Parameter(description = "Поле для сортировки") @RequestParam(defaultValue = "id") String sortBy,
            @Parameter(description = "Направление сортировки (ASC, DESC)") @RequestParam(defaultValue = "ASC") String sortDir) {
        log.info("Getting all products, page: {}, size: {}", page, size);
        Sort sort = Sort.by(Sort.Direction.fromString(sortDir), sortBy);
        Pageable pageable = PageRequest.of(page, size, sort);
        Page<ProductDTO> products = productService.getAllProducts(pageable);
        return ResponseEntity.ok(products);
    }

    @GetMapping("/{id}")
    @Operation(summary = "Получение продукта по ID")
    public ResponseEntity<ProductDTO> getProductById(
            @Parameter(description = "ID продукта", required = true) @PathVariable Integer id) {
        log.info("Getting product by id: {}", id);
        ProductDTO product = productService.getProductById(id);
        return ResponseEntity.ok(product);
    }

    @GetMapping("/category/{categoryId}")
    @Operation(summary = "Получение продуктов по категории")
    public ResponseEntity<Page<ProductDTO>> getProductsByCategory(
            @Parameter(description = "ID категории", required = true) @PathVariable Integer categoryId,
            @Parameter(description = "Номер страницы (начиная с 0)") @RequestParam(defaultValue = "0") int page,
            @Parameter(description = "Размер страницы") @RequestParam(defaultValue = "10") int size,
            @Parameter(description = "Поле для сортировки") @RequestParam(defaultValue = "id") String sortBy,
            @Parameter(description = "Направление сортировки (ASC, DESC)") @RequestParam(defaultValue = "ASC") String sortDir) {
        log.info("Getting products by category id: {}, page: {}, size: {}", categoryId, page, size);
        Sort sort = Sort.by(Sort.Direction.fromString(sortDir), sortBy);
        Pageable pageable = PageRequest.of(page, size, sort);
        Page<ProductDTO> products = productService.getProductsByCategory(categoryId, pageable);
        return ResponseEntity.ok(products);
    }

    @GetMapping("/special-offers")
    @Operation(summary = "Получение специальных предложений")
    public ResponseEntity<List<ProductDTO>> getSpecialOffers() {
        log.info("Getting special offers");
        List<ProductDTO> products = productService.getSpecialOffers();
        return ResponseEntity.ok(products);
    }

    @GetMapping("/search")
    @Operation(summary = "Поиск продуктов по названию или описанию")
    public ResponseEntity<Page<ProductDTO>> searchProducts(
            @Parameter(description = "Поисковый запрос") @RequestParam(required = false) String query,
            @Parameter(description = "ID категории (опционально)") @RequestParam(required = false) Integer categoryId,
            @Parameter(description = "Номер страницы (начиная с 0)") @RequestParam(defaultValue = "0") int page,
            @Parameter(description = "Размер страницы") @RequestParam(defaultValue = "10") int size,
            @Parameter(description = "Поле для сортировки") @RequestParam(defaultValue = "id") String sortBy,
            @Parameter(description = "Направление сортировки (ASC, DESC)") @RequestParam(defaultValue = "ASC") String sortDir) {
        log.info("Searching products with query: {}, categoryId: {}, page: {}, size: {}", query, categoryId, page,
                size);
        Sort sort = Sort.by(Sort.Direction.fromString(sortDir), sortBy);
        Pageable pageable = PageRequest.of(page, size, sort);
        Page<ProductDTO> products = productService.searchProducts(query, categoryId, pageable);
        return ResponseEntity.ok(products);
    }
}