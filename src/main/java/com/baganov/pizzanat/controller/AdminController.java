package com.baganov.pizzanat.controller;

import com.baganov.pizzanat.model.dto.AdminStatsResponse;
import com.baganov.pizzanat.service.AdminStatsService;
import com.baganov.pizzanat.service.StorageService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.HashMap;
import java.util.Map;

@Slf4j
@RestController
@RequestMapping("/api/v1/admin")
@RequiredArgsConstructor
@Tag(name = "Admin", description = "Административный API")
@SecurityRequirement(name = "bearerAuth")
public class AdminController {

    private final StorageService storageService;
    private final AdminStatsService adminStatsService;

    @GetMapping("/stats")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Получение статистики для админ панели", description = "Возвращает общую статистику: заказы, выручка, популярные товары, статусы заказов")
    public ResponseEntity<AdminStatsResponse> getAdminStats() {
        log.info("Запрос статистики админ панели");
        AdminStatsResponse stats = adminStatsService.getAdminStats();
        return ResponseEntity.ok(stats);
    }

    @PostMapping("/upload")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Загрузка изображения")
    public ResponseEntity<Map<String, String>> uploadImage(
            @Parameter(description = "Файл для загрузки", required = true) @RequestParam("file") MultipartFile file,
            @Parameter(description = "Тип изображения (products, categories)") @RequestParam(defaultValue = "products") String type) {

        log.info("Uploading image for {}, original filename: {}", type, file.getOriginalFilename());
        String objectName = storageService.uploadFile(file, type);
        String url = storageService.getPresignedUrl(objectName, 3600);

        Map<String, String> response = new HashMap<>();
        response.put("objectName", objectName);
        response.put("url", url);

        return ResponseEntity.ok(response);
    }
}
