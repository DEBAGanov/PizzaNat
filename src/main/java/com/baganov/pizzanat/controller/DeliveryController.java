/**
 * @file: DeliveryController.java
 * @description: REST контроллер для управления доставкой и валидации адресов
 * @dependencies: Spring Web, AddressSuggestionService
 * @created: 2025-01-23
 */
package com.baganov.pizzanat.controller;

import com.baganov.pizzanat.model.dto.address.AddressSuggestion;
import com.baganov.pizzanat.service.AddressSuggestionService;
import com.baganov.pizzanat.service.DeliveryLocationService;
import com.baganov.pizzanat.service.DeliveryZoneService;
import com.baganov.pizzanat.model.dto.delivery.DeliveryLocationDTO;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * REST контроллер для доставки
 * Обеспечивает интеграцию с мобильным приложением для автоподсказок адресов,
 * валидации адресов и расчета стоимости доставки
 */
@RestController
@RequestMapping("/api/v1/delivery")
@RequiredArgsConstructor
@Slf4j
@Tag(name = "Доставка", description = "API для работы с доставкой и адресами")
public class DeliveryController {

    private final AddressSuggestionService addressSuggestionService;
    private final DeliveryLocationService deliveryLocationService;
    private final DeliveryZoneService deliveryZoneService;

    /**
     * Получить автоподсказки адресов для города Волжск
     *
     * @param query поисковый запрос (минимум 2 символа)
     * @param limit максимальное количество результатов (по умолчанию 10)
     * @return список подходящих адресов
     */
    @GetMapping("/address-suggestions")
    @Operation(summary = "Получить автоподсказки адресов", description = "Возвращает список подходящих адресов для города Волжск на основе поискового запроса")
    @ApiResponse(responseCode = "200", description = "Список автоподсказок успешно получен")
    @ApiResponse(responseCode = "400", description = "Некорректный запрос (слишком короткий поисковый запрос)")
    public ResponseEntity<List<AddressSuggestion>> getAddressSuggestions(
            @Parameter(description = "Поисковый запрос (минимум 2 символа)", example = "Ленина") @RequestParam(required = false) String query,
            @Parameter(description = "Максимальное количество результатов", example = "10") @RequestParam(defaultValue = "10") int limit) {

        try {
            log.info("Запрос автоподсказок адресов для: {}, лимит: {}", query, limit);

            if (query == null || query.trim().length() < 2) {
                log.warn("Слишком короткий поисковый запрос: {}", query);
                return ResponseEntity.badRequest().build();
            }

            List<AddressSuggestion> suggestions = addressSuggestionService.getSuggestions(query.trim());

            // Ограничиваем количество результатов
            if (suggestions.size() > limit) {
                suggestions = suggestions.subList(0, limit);
            }

            log.info("Найдено {} автоподсказок для запроса: {}", suggestions.size(), query);
            return ResponseEntity.ok(suggestions);
        } catch (Exception e) {
            log.error("Ошибка при получении автоподсказок адресов", e);
            return ResponseEntity.status(500).build();
        }
    }

    /**
     * Валидация адреса для города Волжск
     *
     * @param address адрес для проверки
     * @return информация о валидности адреса
     */
    @GetMapping("/validate-address")
    @Operation(summary = "Валидация адреса", description = "Проверяет валидность адреса для города Волжск")
    @ApiResponse(responseCode = "200", description = "Результат валидации получен")
    @ApiResponse(responseCode = "400", description = "Адрес не указан")
    public ResponseEntity<Map<String, Object>> validateAddress(
            @Parameter(description = "Адрес для валидации", example = "Волжск, улица Ленина, 1") @RequestParam(required = false) String address) {

        try {
            log.info("Валидация адреса: {}", address);

            if (address == null || address.trim().isEmpty()) {
                return ResponseEntity.badRequest().build();
            }

            boolean isValid = addressSuggestionService.isValidAddress(address.trim());
            List<AddressSuggestion> suggestions = isValid ? addressSuggestionService.getSuggestions(address.trim())
                    : List.of();

            Map<String, Object> response = new HashMap<>();
            response.put("valid", isValid);
            response.put("address", address.trim());
            response.put("message", isValid ? "Адрес найден в городе Волжск" : "Адрес не найден в городе Волжск");
            response.put("suggestions", suggestions);

            log.info("Результат валидации адреса '{}': {}", address, isValid);
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            log.error("Ошибка при валидации адреса", e);
            return ResponseEntity.status(500).build();
        }
    }

    /**
     * Расчет стоимости доставки с поддержкой зональной системы
     *
     * @param address     адрес доставки
     * @param orderAmount сумма заказа (необязательно)
     * @return информация о стоимости и времени доставки
     */
    @GetMapping("/estimate")
    @Operation(summary = "Расчет стоимости доставки", description = "Рассчитывает стоимость и время доставки для указанного адреса с учетом зональной системы")
    @ApiResponse(responseCode = "200", description = "Расчет доставки выполнен")
    @ApiResponse(responseCode = "400", description = "Адрес не указан или некорректен")
    public ResponseEntity<Map<String, Object>> estimateDelivery(
            @Parameter(description = "Адрес доставки", example = "Волжск, улица Ленина, 1") @RequestParam(required = false) String address,
            @Parameter(description = "Сумма заказа для расчета скидок", example = "1200.00") @RequestParam(required = false) BigDecimal orderAmount) {

        try {
            log.info("Расчет доставки для адреса: {}, сумма заказа: {}", address, orderAmount);

            if (address == null || address.trim().isEmpty()) {
                return ResponseEntity.badRequest().build();
            }

            String cleanAddress = address.trim();

            // АКТИВНАЯ ЗОНАЛЬНАЯ СИСТЕМА: полная логика с fallback для критических ошибок
            log.info("🚀 НАЧИНАЕМ РАСЧЕТ ДОСТАВКИ для адреса: {}", cleanAddress);
            DeliveryZoneService.DeliveryCalculationResult result;
            try {
                log.info("📍 Вызываем зональную систему...");
                result = deliveryZoneService.calculateDelivery(cleanAddress,
                        orderAmount != null ? orderAmount : BigDecimal.ZERO);
                log.info("✅ Зональная система успешно обработала адрес: {} -> зона: {}, стоимость: {}",
                        cleanAddress, result.getZoneName(), result.getDeliveryCost());
            } catch (Exception e) {
                log.error("🚨 КРИТИЧЕСКАЯ ОШИБКА зональной системы, используем fallback: {}", e.getMessage(), e);
                log.error("🚨 Полная трассировка ошибки:", e);
                result = createFallbackDeliveryResult(cleanAddress,
                        orderAmount != null ? orderAmount : BigDecimal.ZERO);
            }

            Map<String, Object> response = new HashMap<>();
            response.put("address", result.getAddress());
            response.put("deliveryAvailable", result.isDeliveryAvailable());

            if (result.isDeliveryAvailable()) {
                response.put("deliveryCost", result.getDeliveryCost());
                response.put("baseCost", result.getBaseCost());
                response.put("currency", result.getCurrency());
                response.put("estimatedTime", result.getEstimatedTime());
                response.put("estimatedTimeMin", result.getEstimatedTimeMin());
                response.put("estimatedTimeMax", result.getEstimatedTimeMax());
                response.put("freeDeliveryThreshold", result.getFreeDeliveryThreshold());
                response.put("isDeliveryFree", result.isDeliveryFree());
                response.put("message", result.getMessage());
                response.put("zoneName", result.getZoneName());
                response.put("zoneDescription", result.getZoneDescription());
                response.put("workingHours", result.getWorkingHours());
                response.put("city", result.getCity());
                response.put("region", result.getRegion());
            } else {
                response.put("deliveryCost", null);
                response.put("estimatedTime", null);
                response.put("message", result.getMessage());
                response.put("reason", result.getReason());
                response.put("zoneName", result.getZoneName());
            }

            log.info("Результат расчета доставки для '{}': доступна={}, зона={}, стоимость={}",
                    cleanAddress, result.isDeliveryAvailable(), result.getZoneName(),
                    result.isDeliveryAvailable() ? result.getDeliveryCost() + " RUB" : "недоступна");

            return ResponseEntity.ok(response);
        } catch (Exception e) {
            log.error("Ошибка при расчете доставки", e);
            return ResponseEntity.status(500).build();
        }
    }

    /**
     * Получить список активных пунктов доставки
     *
     * @return список активных пунктов доставки
     */
    @GetMapping("/locations")
    @Operation(summary = "Получить пункты доставки", description = "Возвращает список всех активных пунктов доставки")
    @ApiResponse(responseCode = "200", description = "Список пунктов доставки получен")
    public ResponseEntity<List<DeliveryLocationDTO>> getDeliveryLocations() {
        try {
            log.info("Запрос всех активных пунктов доставки");
            List<DeliveryLocationDTO> locations = deliveryLocationService.getAllActiveLocations();
            return ResponseEntity.ok(locations);
        } catch (Exception e) {
            log.error("Ошибка при получении пунктов доставки", e);
            return ResponseEntity.status(500).build();
        }
    }

    /**
     * Получить информацию о пункте доставки по ID
     *
     * @param id идентификатор пункта доставки
     * @return информация о пункте доставки
     */
    @GetMapping("/locations/{id}")
    @Operation(summary = "Получить пункт доставки по ID", description = "Возвращает детальную информацию о пункте доставки")
    @ApiResponse(responseCode = "200", description = "Пункт доставки найден")
    @ApiResponse(responseCode = "404", description = "Пункт доставки не найден")
    public ResponseEntity<DeliveryLocationDTO> getDeliveryLocationById(
            @Parameter(description = "ID пункта доставки", example = "1") @PathVariable Integer id) {
        try {
            log.info("Запрос пункта доставки с ID: {}", id);
            DeliveryLocationDTO location = deliveryLocationService.getLocationById(id);
            return ResponseEntity.ok(location);
        } catch (Exception e) {
            log.error("Ошибка при получении пункта доставки по ID: {}", id, e);
            return ResponseEntity.status(500).build();
        }
    }

    /**
     * Fallback метод для расчета доставки при критических ошибках зональной системы
     * Используется только в случае неработоспособности основной системы
     */
    private DeliveryZoneService.DeliveryCalculationResult createFallbackDeliveryResult(String address,
            BigDecimal orderAmount) {
        // ИСПРАВЛЕНО: Новая логика доставки - 250₽ для неизвестных адресов
        BigDecimal baseCost = new BigDecimal("250"); // 250₽ по умолчанию (было 200₽)
        BigDecimal freeThreshold = new BigDecimal("1200"); // Бесплатно от 1200₽ (было 1000₽)
        boolean isDeliveryFree = orderAmount.compareTo(freeThreshold) >= 0;
        BigDecimal finalCost = isDeliveryFree ? BigDecimal.ZERO : baseCost;

        return DeliveryZoneService.DeliveryCalculationResult.builder()
                .address(address)
                .deliveryAvailable(true)
                .zoneName("Стандартная зона")
                .zoneDescription("Доставка по городу Волжск (fallback тариф)")
                .deliveryCost(finalCost)
                .baseCost(baseCost)
                .freeDeliveryThreshold(freeThreshold)
                .isDeliveryFree(isDeliveryFree)
                .estimatedTimeMin(30)
                .estimatedTimeMax(50) // Увеличено время доставки
                .estimatedTime("30-50 минут")
                .currency("RUB")
                .message(isDeliveryFree ? "Бесплатная доставка" : "Доставка - " + finalCost + " ₽")
                .workingHours("09:00-22:00")
                .city("Волжск")
                .region("Республика Марий Эл")
                .build();
    }
}