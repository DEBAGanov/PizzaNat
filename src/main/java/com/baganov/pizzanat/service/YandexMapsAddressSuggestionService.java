package com.baganov.pizzanat.service;

import com.baganov.pizzanat.model.dto.address.AddressSuggestion;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
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
 * Сервис автоподсказок адресов через Яндекс.Карты API
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class YandexMapsAddressSuggestionService {

    private final RestTemplate restTemplate = new RestTemplate();

    @Value("${yandex.maps.api.key:}")
    private String yandexApiKey;

    @Value("${yandex.maps.api.enabled:false}")
    private boolean yandexApiEnabled;

    /**
     * Получить автоподсказки адресов через Яндекс.Карты API
     * ИСПРАВЛЕНО: Автоматически добавляем "Волжск" и показываем только улицы города
     */
    public List<AddressSuggestion> getYandexSuggestions(String query) {
        if (!yandexApiEnabled || yandexApiKey.isEmpty()) {
            log.debug("Яндекс.Карты API не настроен, используем локальную базу");
            return new ArrayList<>();
        }

        if (query == null || query.trim().length() < 1) { // Уменьшено с 2 до 1 символа
            return new ArrayList<>();
        }

        try {
            List<AddressSuggestion> allResults = new ArrayList<>();

            // ИСПРАВЛЕНИЕ: Всегда добавляем "Волжск" к запросу для поиска только в городе
            String searchQuery;
            if (query.toLowerCase().contains("волжск")) {
                searchQuery = query;
            } else {
                // Формируем запрос специально для поиска улиц в Волжске
                searchQuery = "Волжск, " + query;
            }

            log.info("Поиск улиц в Волжске по запросу: '{}' -> '{}'", query, searchQuery);

            // Используем правильный endpoint для HTTP API Геокодера
            String url = UriComponentsBuilder
                    .fromHttpUrl("https://geocode-maps.yandex.ru/1.x/")
                    .queryParam("apikey", yandexApiKey)
                    .queryParam("geocode", searchQuery)
                    .queryParam("format", "json")
                    .queryParam("results", "15") // Увеличено количество результатов
                    .queryParam("ll", "48.359,55.866") // Координаты Волжска
                    .queryParam("spn", "0.3,0.3") // Уменьшено для более точного поиска в городе
                    .queryParam("rspn", "1") // Учитывать регион
                    .queryParam("kind", "street") // ДОБАВЛЕНО: Ищем только улицы
                    .build()
                    .toUriString();

            log.info("Запрос к Яндекс.Карты для улиц: {}", url);

            HttpHeaders headers = new HttpHeaders();
            headers.set("Accept", "application/json");
            headers.set("User-Agent", "PizzaNat/1.0");

            HttpEntity<String> entity = new HttpEntity<>(headers);

            ResponseEntity<YandexGeocoderResponse> response = restTemplate.exchange(
                    url,
                    HttpMethod.GET,
                    entity,
                    YandexGeocoderResponse.class);

            if (response.getBody() != null &&
                    response.getBody().getResponse() != null &&
                    response.getBody().getResponse().getGeoObjectCollection() != null &&
                    response.getBody().getResponse().getGeoObjectCollection().getFeatureMember() != null) {

                List<AddressSuggestion> suggestions = response.getBody()
                        .getResponse()
                        .getGeoObjectCollection()
                        .getFeatureMember()
                        .stream()
                        .filter(featureMember -> featureMember != null && featureMember.getGeoObject() != null)
                        .map(this::convertToSuggestion)
                        .filter(suggestion -> suggestion != null &&
                                suggestion.getAddress() != null &&
                                suggestion.getAddress().toLowerCase().contains("волжск") &&
                                // ДОБАВЛЕНО: Фильтруем только улицы (содержат "улица", "переулок", "проспект" и
                                // т.д.)
                                (suggestion.getAddress().toLowerCase().contains("улица") ||
                                        suggestion.getAddress().toLowerCase().contains("переулок") ||
                                        suggestion.getAddress().toLowerCase().contains("проспект") ||
                                        suggestion.getAddress().toLowerCase().contains("бульвар") ||
                                        suggestion.getAddress().toLowerCase().contains("проезд")))
                        .collect(Collectors.toList());

                allResults.addAll(suggestions);
            }

            log.info("Найдено {} улиц в Волжске для запроса: '{}'", allResults.size(), query);
            return allResults;

        } catch (Exception e) {
            log.error("Ошибка при получении автоподсказок улиц от Яндекс.Карт: {}", e.getMessage(), e);
        }

        return new ArrayList<>();
    }

    /**
     * Получить все улицы в Волжске через обратное геокодирование
     */
    private List<AddressSuggestion> getAllStreetsInVolzhsk() {
        try {
            String url = UriComponentsBuilder
                    .fromHttpUrl("https://geocode-maps.yandex.ru/1.x/")
                    .queryParam("apikey", yandexApiKey)
                    .queryParam("geocode", "48.359,55.866") // Координаты Волжска
                    .queryParam("format", "json")
                    .queryParam("kind", "street")
                    .queryParam("results", "50")
                    .build()
                    .toUriString();

            log.info("Запрос всех улиц Волжска: {}", url);

            HttpHeaders headers = new HttpHeaders();
            headers.set("Accept", "application/json");
            headers.set("User-Agent", "PizzaNat/1.0");

            HttpEntity<String> entity = new HttpEntity<>(headers);

            ResponseEntity<YandexGeocoderResponse> response = restTemplate.exchange(
                    url,
                    HttpMethod.GET,
                    entity,
                    YandexGeocoderResponse.class);

            if (response.getBody() != null &&
                    response.getBody().getResponse() != null &&
                    response.getBody().getResponse().getGeoObjectCollection() != null &&
                    response.getBody().getResponse().getGeoObjectCollection().getFeatureMember() != null) {

                List<AddressSuggestion> streets = response.getBody()
                        .getResponse()
                        .getGeoObjectCollection()
                        .getFeatureMember()
                        .stream()
                        .filter(featureMember -> featureMember != null && featureMember.getGeoObject() != null)
                        .map(this::convertToSuggestion)
                        .filter(suggestion -> suggestion != null &&
                                suggestion.getAddress() != null &&
                                suggestion.getAddress().toLowerCase().contains("волжск"))
                        .collect(Collectors.toList());

                log.info("Найдено {} улиц в Волжске", streets.size());
                return streets;
            }
        } catch (Exception e) {
            log.error("Ошибка при получении улиц Волжска: {}", e.getMessage(), e);
        }

        return new ArrayList<>();
    }

    /**
     * Преобразование ответа Яндекс.Карт в нашу модель
     */
    private AddressSuggestion convertToSuggestion(YandexGeocoderResponse.FeatureMember featureMember) {
        try {
            if (featureMember == null || featureMember.getGeoObject() == null) {
                return null;
            }

            YandexGeocoderResponse.GeoObject geoObject = featureMember.getGeoObject();

            if (geoObject.getMetaDataProperty() == null ||
                    geoObject.getMetaDataProperty().getGeocoderMetaData() == null ||
                    geoObject.getPoint() == null ||
                    geoObject.getPoint().getPos() == null) {
                return null;
            }

            String fullAddress = geoObject.getMetaDataProperty().getGeocoderMetaData().getText();
            if (fullAddress == null || fullAddress.trim().isEmpty()) {
                return null;
            }

            // Извлекаем координаты
            String[] coords = geoObject.getPoint().getPos().split(" ");
            if (coords.length < 2) {
                log.warn("Некорректные координаты для адреса: {}", fullAddress);
                return null;
            }

            double longitude = Double.parseDouble(coords[0]); // долгота
            double latitude = Double.parseDouble(coords[1]); // широта

            String kind = geoObject.getMetaDataProperty().getGeocoderMetaData().getKind();

            return AddressSuggestion.builder()
                    .address(fullAddress)
                    .shortAddress(extractShortAddress(fullAddress))
                    .latitude(latitude)
                    .longitude(longitude)
                    .source("yandex")
                    .metadata(kind != null ? kind : "unknown")
                    .build();

        } catch (Exception e) {
            log.error("Ошибка преобразования ответа Яндекс.Карт в AddressSuggestion: {}", e.getMessage());
            return null;
        }
    }

    /**
     * Извлечение короткого адреса (только название улицы для мобильного приложения)
     * ИСПРАВЛЕНО: Показываем только название улицы, убираем город и регион
     */
    private String extractShortAddress(String fullAddress) {
        // Убираем "Россия, Республика Марий Эл, Волжск, " из начала
        String shortAddress = fullAddress
                .replaceFirst("^Россия,\\s*", "")
                .replaceFirst("^Республика Марий Эл,\\s*", "")
                .replaceFirst("^Волжск,\\s*", "")
                .replaceFirst("^г\\.\\s*Волжск,\\s*", "")
                .replaceFirst("^город\\s*Волжск,\\s*", "")
                .trim();

        // Дополнительная очистка для мобильного приложения
        // Оставляем только название улицы
        if (shortAddress.toLowerCase().startsWith("улица ")) {
            shortAddress = shortAddress.substring(6); // Убираем "улица "
        } else if (shortAddress.toLowerCase().startsWith("ул. ")) {
            shortAddress = shortAddress.substring(4); // Убираем "ул. "
        } else if (shortAddress.toLowerCase().startsWith("переулок ")) {
            shortAddress = shortAddress.substring(9); // Убираем "переулок "
        } else if (shortAddress.toLowerCase().startsWith("пер. ")) {
            shortAddress = shortAddress.substring(5); // Убираем "пер. "
        }

        return shortAddress.trim();
    }

    // DTO классы для Яндекс.Карт API
    @Data
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class YandexGeocoderResponse {
        private Response response;

        @Data
        @JsonIgnoreProperties(ignoreUnknown = true)
        public static class Response {
            @JsonProperty("GeoObjectCollection")
            private GeoObjectCollection geoObjectCollection;
        }

        @Data
        @JsonIgnoreProperties(ignoreUnknown = true)
        public static class GeoObjectCollection {
            @JsonProperty("featureMember")
            private List<FeatureMember> featureMember = new ArrayList<>();
        }

        @Data
        @JsonIgnoreProperties(ignoreUnknown = true)
        public static class FeatureMember {
            @JsonProperty("GeoObject")
            private GeoObject geoObject;
        }

        @Data
        @JsonIgnoreProperties(ignoreUnknown = true)
        public static class GeoObject {
            @JsonProperty("metaDataProperty")
            private MetaDataProperty metaDataProperty;

            @JsonProperty("Point")
            private Point point;
        }

        @Data
        @JsonIgnoreProperties(ignoreUnknown = true)
        public static class MetaDataProperty {
            @JsonProperty("GeocoderMetaData")
            private GeocoderMetaData geocoderMetaData;
        }

        @Data
        @JsonIgnoreProperties(ignoreUnknown = true)
        public static class GeocoderMetaData {
            private String text;
            private String kind;
        }

        @Data
        @JsonIgnoreProperties(ignoreUnknown = true)
        public static class Point {
            private String pos;
        }
    }
}