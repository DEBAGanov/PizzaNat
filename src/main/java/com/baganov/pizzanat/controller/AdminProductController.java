/**
 * @file: AdminProductController.java
 * @description: Административный API для управления продуктами
 * @dependencies: Spring Web, Spring Security, Jackson
 * @created: 2025-05-31
 */
package com.baganov.pizzanat.controller;

import com.baganov.pizzanat.model.dto.product.CreateProductRequest;
import com.baganov.pizzanat.model.dto.product.ProductDTO;
import com.baganov.pizzanat.model.dto.product.UpdateProductRequest;
import com.baganov.pizzanat.service.AdminProductService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@Slf4j
@RestController
@RequestMapping("/api/v1/admin/products")
@RequiredArgsConstructor
@PreAuthorize("hasRole('ADMIN')")
@Tag(name = "Admin Products", description = "API для администрирования продуктов")
public class AdminProductController {

    private final AdminProductService adminProductService;

    @PostMapping
    @Operation(summary = "Создание нового продукта", security = @SecurityRequirement(name = "bearerAuth"))
    public ResponseEntity<ProductDTO> createProduct(@Valid @RequestBody CreateProductRequest request) {
        log.info("Создание нового продукта: {}", request.getName());
        ProductDTO product = adminProductService.createProduct(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(product);
    }

    @PutMapping("/{productId}")
    @Operation(summary = "Обновление продукта", security = @SecurityRequirement(name = "bearerAuth"))
    public ResponseEntity<ProductDTO> updateProduct(
            @Parameter(description = "ID продукта", required = true) @PathVariable Integer productId,
            @Valid @RequestBody UpdateProductRequest request) {
        log.info("Обновление продукта с ID: {}", productId);
        ProductDTO product = adminProductService.updateProduct(productId, request);
        return ResponseEntity.ok(product);
    }

    @DeleteMapping("/{productId}")
    @Operation(summary = "Удаление продукта", security = @SecurityRequirement(name = "bearerAuth"))
    public ResponseEntity<Void> deleteProduct(
            @Parameter(description = "ID продукта", required = true) @PathVariable Integer productId) {
        log.info("Удаление продукта с ID: {}", productId);
        adminProductService.deleteProduct(productId);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/{productId}")
    @Operation(summary = "Получение продукта по ID (админ)", security = @SecurityRequirement(name = "bearerAuth"))
    public ResponseEntity<ProductDTO> getProductById(
            @Parameter(description = "ID продукта", required = true) @PathVariable Integer productId) {
        log.info("Получение продукта с ID: {}", productId);
        ProductDTO product = adminProductService.getProductById(productId);
        return ResponseEntity.ok(product);
    }
}