/**
 * @file: DeliveryLocationController.java
 * @description: REST контроллер для управления пунктами доставки
 * @dependencies: Spring Web, JPA, Security
 * @created: 2025-05-31
 */
package com.baganov.pizzanat.controller;

import com.baganov.pizzanat.model.dto.delivery.DeliveryLocationDTO;
import com.baganov.pizzanat.service.DeliveryLocationService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/delivery-locations")
@RequiredArgsConstructor
@Slf4j
@Tag(name = "Пункты доставки", description = "API для работы с пунктами доставки")
public class DeliveryLocationController {

    private final DeliveryLocationService deliveryLocationService;

    @GetMapping
    @Operation(summary = "Получить все активные пункты доставки")
    @ApiResponse(responseCode = "200", description = "Список пунктов доставки получен")
    public ResponseEntity<List<DeliveryLocationDTO>> getAllActiveLocations() {
        log.info("Запрос всех активных пунктов доставки");
        List<DeliveryLocationDTO> locations = deliveryLocationService.getAllActiveLocations();
        return ResponseEntity.ok(locations);
    }

    @GetMapping("/{id}")
    @Operation(summary = "Получить пункт доставки по ID")
    @ApiResponse(responseCode = "200", description = "Пункт доставки найден")
    @ApiResponse(responseCode = "404", description = "Пункт доставки не найден")
    public ResponseEntity<DeliveryLocationDTO> getLocationById(@PathVariable Integer id) {
        log.info("Запрос пункта доставки с ID: {}", id);
        DeliveryLocationDTO location = deliveryLocationService.getLocationById(id);
        return ResponseEntity.ok(location);
    }
}