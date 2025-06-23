package com.baganov.pizzanat.service;

import com.baganov.pizzanat.model.dto.address.AddressSuggestion;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Сервис автоподсказок адресов через Nominatim API (OpenStreetMap)
 * Бесплатная альтернатива коммерческим API
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class NominatimAddressSuggestionService {

    private final RestTemplate restTemplate = new RestTemplate();

    /**
     * Получить автоподсказки адресов через Nominatim API
     */
    public List<AddressSuggestion> getNominatimSuggestions(String query) {
        if (query == null || query.trim().length() < 3) {
            return new ArrayList<>();
        }

        try {
            // Добавляем "Волжск, Марий Эл" к запросу для более точного поиска
            String searchQuery = query.toLowerCase().contains("волжск")
                    ? query + ", Марий Эл, Россия"
                    : "Волжск, " + query + ", Марий Эл, Россия";

            String url = UriComponentsBuilder
                    .fromHttpUrl("https://nominatim.openstreetmap.org/search")
                    .queryParam("q", searchQuery)
                    .queryParam("format", "json")
                    .queryParam("limit", "10")
                    .queryParam("addressdetails", "1")
                    .queryParam("countrycodes", "ru")
                    .queryParam("bounded", "1")
                    .queryParam("viewbox", "48.2,55.8,48.5,55.9") // Ограничиваем область поиска Волжском
                    .build()
                    .toUriString();

            log.info("Запрос к Nominatim: {}", url);

            HttpHeaders headers = new HttpHeaders();
            headers.set("Accept", "application/json");
            headers.set("User-Agent", "PizzaNat/1.0 (contact@pizzanat.ru)"); // Nominatim требует User-Agent

            HttpEntity<String> entity = new HttpEntity<>(headers);

            ResponseEntity<NominatimResponse[]> response = restTemplate.exchange(
                    url,
                    HttpMethod.GET,
                    entity,
                    NominatimResponse[].class);

            if (response.getBody() != null) {
                List<AddressSuggestion> suggestions = new ArrayList<>();

                for (NominatimResponse item : response.getBody()) {
                    // Фильтруем только результаты из Волжска
                    if (item.getDisplayName() != null &&
                            item.getDisplayName().toLowerCase().contains("волжск")) {
                        suggestions.add(convertToSuggestion(item));
                    }
                }

                log.info("Получено {} результатов от Nominatim для запроса: {}", suggestions.size(), query);
                return suggestions;
            }

        } catch (Exception e) {
            log.error("Ошибка при получении автоподсказок от Nominatim: {}", e.getMessage(), e);
        }

        return new ArrayList<>();
    }

    /**
     * Преобразование ответа Nominatim в нашу модель
     */
    private AddressSuggestion convertToSuggestion(NominatimResponse item) {
        String fullAddress = item.getDisplayName();

        return AddressSuggestion.builder()
                .address(fullAddress)
                .shortAddress(extractShortAddress(fullAddress))
                .latitude(Double.parseDouble(item.getLat()))
                .longitude(Double.parseDouble(item.getLon()))
                .source("nominatim")
                .metadata(item.getType() + "/" + item.getOsmType())
                .build();
    }

    /**
     * Извлечение короткого адреса (без региона и страны)
     */
    private String extractShortAddress(String fullAddress) {
        // Убираем лишние части адреса
        String shortAddress = fullAddress
                .replaceFirst(",\\s*Волжский городской округ.*$", "")
                .replaceFirst(",\\s*Республика Марий Эл.*$", "")
                .replaceFirst(",\\s*Россия.*$", "")
                .trim();

        return shortAddress;
    }

    // DTO класс для Nominatim API
    @Data
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class NominatimResponse {
        @JsonProperty("place_id")
        private Long placeId;

        @JsonProperty("licence")
        private String licence;

        @JsonProperty("osm_type")
        private String osmType;

        @JsonProperty("osm_id")
        private Long osmId;

        @JsonProperty("lat")
        private String lat;

        @JsonProperty("lon")
        private String lon;

        @JsonProperty("class")
        private String clazz;

        @JsonProperty("type")
        private String type;

        @JsonProperty("place_rank")
        private Integer placeRank;

        @JsonProperty("importance")
        private Double importance;

        @JsonProperty("addresstype")
        private String addressType;

        @JsonProperty("name")
        private String name;

        @JsonProperty("display_name")
        private String displayName;

        @JsonProperty("address")
        private NominatimAddress address;

        @JsonProperty("boundingbox")
        private String[] boundingBox;
    }

    @Data
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class NominatimAddress {
        @JsonProperty("house_number")
        private String houseNumber;

        @JsonProperty("road")
        private String road;

        @JsonProperty("suburb")
        private String suburb;

        @JsonProperty("city")
        private String city;

        @JsonProperty("town")
        private String town;

        @JsonProperty("village")
        private String village;

        @JsonProperty("state")
        private String state;

        @JsonProperty("postcode")
        private String postcode;

        @JsonProperty("country")
        private String country;

        @JsonProperty("country_code")
        private String countryCode;
    }
}