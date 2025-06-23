package com.baganov.pizzanat.service;

import com.baganov.pizzanat.model.dto.address.AddressSuggestion;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Сервис автоподсказок адресов через DaData API
 * Специализируется на российских адресах
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class DaDataAddressSuggestionService {

    private final RestTemplate restTemplate = new RestTemplate();

    @Value("${dadata.api.key:}")
    private String dadataApiKey;

    @Value("${dadata.api.enabled:false}")
    private boolean dadataApiEnabled;

    /**
     * Получить автоподсказки адресов через DaData API
     */
    public List<AddressSuggestion> getDaDataSuggestions(String query) {
        if (!dadataApiEnabled || dadataApiKey.isEmpty()) {
            log.warn("DaData API не настроен");
            return new ArrayList<>();
        }

        if (query == null || query.trim().length() < 3) {
            return new ArrayList<>();
        }

        try {
            // Добавляем фильтр по городу Волжск
            String searchQuery = query.toLowerCase().contains("волжск")
                    ? query
                    : "Волжск " + query;

            DaDataRequest request = DaDataRequest.builder()
                    .query(searchQuery)
                    .count(10)
                    .locations(List.of(
                            DaDataLocation.builder()
                                    .city("Волжск")
                                    .region("Марий Эл")
                                    .build()))
                    .build();

            HttpHeaders headers = new HttpHeaders();
            headers.set("Authorization", "Token " + dadataApiKey);
            headers.set("Content-Type", "application/json");
            headers.set("Accept", "application/json");

            HttpEntity<DaDataRequest> entity = new HttpEntity<>(request, headers);

            ResponseEntity<DaDataResponse> response = restTemplate.exchange(
                    "https://suggestions.dadata.ru/suggestions/api/4_1/rs/suggest/address",
                    HttpMethod.POST,
                    entity,
                    DaDataResponse.class);

            if (response.getBody() != null && response.getBody().getSuggestions() != null) {
                return response.getBody().getSuggestions().stream()
                        .map(this::convertToSuggestion)
                        .filter(suggestion -> suggestion.getAddress().toLowerCase().contains("волжск"))
                        .collect(Collectors.toList());
            }

        } catch (Exception e) {
            log.error("Ошибка при получении автоподсказок от DaData: {}", e.getMessage());
        }

        return new ArrayList<>();
    }

    /**
     * Преобразование ответа DaData в нашу модель
     */
    private AddressSuggestion convertToSuggestion(DaDataSuggestion suggestion) {
        String fullAddress = suggestion.getValue();
        DaDataData data = suggestion.getData();

        return AddressSuggestion.builder()
                .address(fullAddress)
                .shortAddress(extractShortAddress(fullAddress))
                .latitude(data != null && data.getGeoLat() != null ? Double.valueOf(data.getGeoLat()) : null)
                .longitude(data != null && data.getGeoLon() != null ? Double.valueOf(data.getGeoLon()) : null)
                .source("dadata")
                .metadata(data != null ? data.getFiasLevel() : null)
                .build();
    }

    /**
     * Извлечение короткого адреса
     */
    private String extractShortAddress(String fullAddress) {
        return fullAddress
                .replaceFirst("^Респ Марий Эл,\\s*", "")
                .replaceFirst("^Республика Марий Эл,\\s*", "")
                .trim();
    }

    // DTO классы для DaData API
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class DaDataRequest {
        private String query;
        private Integer count;
        private List<DaDataLocation> locations;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class DaDataLocation {
        private String city;
        private String region;
    }

    @Data
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class DaDataResponse {
        private List<DaDataSuggestion> suggestions;
    }

    @Data
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class DaDataSuggestion {
        private String value;
        private String unrestricted_value;
        private DaDataData data;
    }

    @Data
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class DaDataData {
        @JsonProperty("postal_code")
        private String postalCode;

        @JsonProperty("country")
        private String country;

        @JsonProperty("region")
        private String region;

        @JsonProperty("city")
        private String city;

        @JsonProperty("street")
        private String street;

        @JsonProperty("house")
        private String house;

        @JsonProperty("geo_lat")
        private String geoLat;

        @JsonProperty("geo_lon")
        private String geoLon;

        @JsonProperty("fias_level")
        private String fiasLevel;
    }
}