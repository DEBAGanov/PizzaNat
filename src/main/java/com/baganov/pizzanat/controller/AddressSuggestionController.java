package com.baganov.pizzanat.controller;

import com.baganov.pizzanat.model.dto.address.AddressSuggestion;
import com.baganov.pizzanat.service.AddressSuggestionService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.ArrayList;
import java.util.List;

/**
 * REST контроллер для автоподсказок адресов
 */
@RestController
@RequestMapping("/api/v1/address")
@RequiredArgsConstructor
@Slf4j
public class AddressSuggestionController {

    private final AddressSuggestionService addressSuggestionService;

    /**
     * Получить автоподсказки адресов для города Волжск
     * 
     * @param query поисковый запрос (минимум 2 символа)
     * @return список подходящих адресов
     */
    @GetMapping("/suggestions")
    public ResponseEntity<List<AddressSuggestion>> getSuggestions(
            @RequestParam String query) {

        log.info("Запрос автоподсказок адресов для: {}", query);

        if (query == null || query.trim().length() < 2) {
            return ResponseEntity.badRequest().build();
        }

        List<AddressSuggestion> suggestions = addressSuggestionService.getSuggestions(query.trim());

        log.info("Найдено {} автоподсказок для запроса: {}", suggestions.size(), query);

        return ResponseEntity.ok(suggestions);
    }

    /**
     * Получить автоподсказки домов для конкретной улицы
     * 
     * @param street     название улицы
     * @param houseQuery номер дома (опционально)
     * @return список подходящих домов
     */
    @GetMapping("/houses")
    public ResponseEntity<List<AddressSuggestion>> getHouseSuggestions(
            @RequestParam String street,
            @RequestParam(required = false) String houseQuery) {

        log.info("Запрос автоподсказок домов для улицы: {}, дом: {}", street, houseQuery);

        if (street == null || street.trim().isEmpty()) {
            return ResponseEntity.badRequest().build();
        }

        List<AddressSuggestion> suggestions = addressSuggestionService.getHouseSuggestions(street.trim(), houseQuery);

        log.info("Найдено {} автоподсказок домов", suggestions.size());

        return ResponseEntity.ok(suggestions);
    }

    /**
     * Проверить валидность адреса для города Волжск
     * 
     * @param address адрес для проверки
     * @return информация о валидности адреса
     */
    @PostMapping("/validate")
    public ResponseEntity<AddressValidationResponse> validateAddress(
            @RequestBody AddressValidationRequest request) {

        log.info("Валидация адреса: {}", request.getAddress());

        boolean isValid = addressSuggestionService.isValidAddress(request.getAddress());
        List<AddressSuggestion> suggestions = isValid ? addressSuggestionService.getSuggestions(request.getAddress())
                : new ArrayList<>();

        AddressValidationResponse response = AddressValidationResponse.builder()
                .valid(isValid)
                .suggestions(suggestions)
                .message(isValid ? "Адрес найден" : "Адрес не найден в городе Волжск")
                .build();

        return ResponseEntity.ok(response);
    }

    /**
     * DTO для запроса валидации адреса
     */
    @lombok.Data
    @lombok.Builder
    @lombok.NoArgsConstructor
    @lombok.AllArgsConstructor
    public static class AddressValidationRequest {
        private String address;
    }

    /**
     * DTO для ответа валидации адреса
     */
    @lombok.Data
    @lombok.Builder
    @lombok.NoArgsConstructor
    @lombok.AllArgsConstructor
    public static class AddressValidationResponse {
        private boolean valid;
        private String message;
        private List<AddressSuggestion> suggestions;
    }
}