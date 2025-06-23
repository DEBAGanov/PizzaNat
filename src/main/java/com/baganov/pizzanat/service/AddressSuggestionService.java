package com.baganov.pizzanat.service;

import com.baganov.pizzanat.model.dto.address.AddressSuggestion;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

/**
 * Сервис для получения автоподсказок адресов для города Волжск
 * Использует локальную базу данных улиц и может интегрироваться с внешними API
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class AddressSuggestionService {

    private final LocalAddressSuggestionService localAddressSuggestionService;
    private final YandexMapsAddressSuggestionService yandexMapsAddressSuggestionService;
    private final NominatimAddressSuggestionService nominatimAddressSuggestionService;

    @Value("${yandex.maps.api.enabled:false}")
    private boolean yandexApiEnabled;

    @Value("${nominatim.api.enabled:true}")
    private boolean nominatimApiEnabled;

    /**
     * Получить автоподсказки адресов для города Волжск
     * Приоритет: Яндекс.Карты API -> локальная база
     */
    public List<AddressSuggestion> getSuggestions(String query) {
        if (query == null || query.trim().length() < 2) {
            return new ArrayList<>();
        }

        log.info("Получение автоподсказок адресов для запроса: {}", query);

        // Приоритет: Яндекс.Карты -> Nominatim -> локальная база

        // Сначала пытаемся получить данные от Яндекс.Карт
        if (yandexApiEnabled) {
            List<AddressSuggestion> yandexSuggestions = yandexMapsAddressSuggestionService.getYandexSuggestions(query);
            if (!yandexSuggestions.isEmpty()) {
                log.info("Найдено {} автоподсказок от Яндекс.Карт для запроса: {}", yandexSuggestions.size(), query);
                return yandexSuggestions;
            }
        }

        // Если Яндекс.Карты недоступны, пробуем Nominatim
        if (nominatimApiEnabled) {
            List<AddressSuggestion> nominatimSuggestions = nominatimAddressSuggestionService
                    .getNominatimSuggestions(query);
            if (!nominatimSuggestions.isEmpty()) {
                log.info("Найдено {} автоподсказок от Nominatim для запроса: {}", nominatimSuggestions.size(), query);
                return nominatimSuggestions;
            }
        }

        // Если внешние API недоступны или не дали результатов, используем локальную
        // базу
        log.info("Используем локальную базу адресов для запроса: {}", query);
        List<AddressSuggestion> localSuggestions = localAddressSuggestionService.getLocalSuggestions(query);

        log.info("Найдено {} автоподсказок из локальной базы для запроса: {}", localSuggestions.size(), query);

        return localSuggestions;
    }

    /**
     * Получить автоподсказки домов для конкретной улицы
     */
    public List<AddressSuggestion> getHouseSuggestions(String street, String houseQuery) {
        log.info("Получение автоподсказок домов для улицы: {}, дом: {}", street, houseQuery);

        return localAddressSuggestionService.getHouseSuggestions(street, houseQuery);
    }

    /**
     * Проверить валидность адреса для города Волжск
     */
    public boolean isValidAddress(String address) {
        return localAddressSuggestionService.isValidVolzhskAddress(address);
    }
}