/**
 * @file: GoogleSheetsAdminController.java
 * @description: Административный контроллер для управления Google Sheets интеграцией
 * @dependencies: GoogleCredentialsDownloadService, GoogleSheetsService
 * @created: 2025-01-28
 */
package com.baganov.pizzanat.controller;

import com.baganov.pizzanat.service.GoogleCredentialsDownloadService;
import com.baganov.pizzanat.service.GoogleSheetsService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@Slf4j
@RestController
@RequestMapping("/api/v1/admin/google-sheets")
@RequiredArgsConstructor
@Tag(name = "Google Sheets Admin", description = "Административное управление Google Sheets интеграцией")
@ConditionalOnProperty(name = "google.sheets.enabled", havingValue = "true")
public class GoogleSheetsAdminController {

    private final GoogleCredentialsDownloadService credentialsDownloadService;
    private final GoogleSheetsService googleSheetsService;

    /**
     * Ручная загрузка credentials из S3
     */
    @PostMapping("/credentials/download")
    @Operation(summary = "Загрузить Google Sheets credentials из S3", 
               security = @SecurityRequirement(name = "bearerAuth"))
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Map<String, Object>> downloadCredentials() {
        log.info("🔄 Запрос ручной загрузки Google Sheets credentials из S3");
        
        try {
            boolean success = credentialsDownloadService.downloadCredentials();
            
            if (success) {
                return ResponseEntity.ok(Map.of(
                    "status", "success",
                    "message", "Google Sheets credentials успешно загружены из S3",
                    "credentialsExist", credentialsDownloadService.credentialsExist()
                ));
            } else {
                return ResponseEntity.badRequest().body(Map.of(
                    "status", "error",
                    "message", "Ошибка загрузки Google Sheets credentials из S3"
                ));
            }
            
        } catch (Exception e) {
            log.error("❌ Ошибка ручной загрузки credentials: {}", e.getMessage(), e);
            return ResponseEntity.internalServerError().body(Map.of(
                "status", "error",
                "message", "Внутренняя ошибка: " + e.getMessage()
            ));
        }
    }

    /**
     * Проверка статуса credentials
     */
    @GetMapping("/credentials/status")
    @Operation(summary = "Проверить статус Google Sheets credentials", 
               security = @SecurityRequirement(name = "bearerAuth"))
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Map<String, Object>> getCredentialsStatus() {
        try {
            boolean credentialsExist = credentialsDownloadService.credentialsExist();
            String downloadInfo = credentialsDownloadService.getDownloadInfo();
            
            return ResponseEntity.ok(Map.of(
                "status", "success",
                "credentialsExist", credentialsExist,
                "downloadInfo", downloadInfo
            ));
            
        } catch (Exception e) {
            log.error("❌ Ошибка проверки статуса credentials: {}", e.getMessage(), e);
            return ResponseEntity.internalServerError().body(Map.of(
                "status", "error",
                "message", "Ошибка проверки статуса: " + e.getMessage()
            ));
        }
    }

    /**
     * Инициализация Google Sheets таблицы
     */
    @PostMapping("/sheet/initialize")
    @Operation(summary = "Инициализировать Google Sheets таблицу с заголовками", 
               security = @SecurityRequirement(name = "bearerAuth"))
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Map<String, Object>> initializeSheet() {
        log.info("🔄 Запрос инициализации Google Sheets таблицы");
        
        try {
            // Проверяем наличие credentials
            if (!credentialsDownloadService.credentialsExist()) {
                return ResponseEntity.badRequest().body(Map.of(
                    "status", "error",
                    "message", "Google Sheets credentials не найдены. Сначала загрузите credentials из S3."
                ));
            }
            
            googleSheetsService.initializeSheet();
            
            return ResponseEntity.ok(Map.of(
                "status", "success",
                "message", "Google Sheets таблица инициализирована с заголовками"
            ));
            
        } catch (Exception e) {
            log.error("❌ Ошибка инициализации Google Sheets: {}", e.getMessage(), e);
            return ResponseEntity.internalServerError().body(Map.of(
                "status", "error",
                "message", "Ошибка инициализации: " + e.getMessage()
            ));
        }
    }

    /**
     * Получение информации о конфигурации Google Sheets
     */
    @GetMapping("/config")
    @Operation(summary = "Получить информацию о конфигурации Google Sheets", 
               security = @SecurityRequirement(name = "bearerAuth"))
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Map<String, Object>> getConfig() {
        try {
            String downloadInfo = credentialsDownloadService.getDownloadInfo();
            
            return ResponseEntity.ok(Map.of(
                "status", "success",
                "configuration", downloadInfo,
                "credentialsExist", credentialsDownloadService.credentialsExist()
            ));
            
        } catch (Exception e) {
            log.error("❌ Ошибка получения конфигурации: {}", e.getMessage(), e);
            return ResponseEntity.internalServerError().body(Map.of(
                "status", "error",
                "message", "Ошибка получения конфигурации: " + e.getMessage()
            ));
        }
    }
}