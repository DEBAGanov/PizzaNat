package com.baganov.pizzanat.service;

import com.baganov.pizzanat.model.dto.address.AddressSuggestion;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Локальный сервис автоподсказок адресов для города Волжск
 * Использует предзаполненную базу улиц
 */
@Service
@Slf4j
public class LocalAddressSuggestionService {

    // Предзаполненный список улиц Волжска
    private static final List<String> VOLZHSK_STREETS = Arrays.asList(
            "улица Ленина", "улица 107-й Бригады", "улица Шестакова", "улица Свердлова",
            "улица Первомайская", "улица Мира", "улица Советская", "улица Кирова",
            "улица Гагарина", "улица Пушкина", "улица Чапаева", "улица Комсомольская",
            "улица Победы", "улица Строителей", "улица Молодежная", "улица Садовая",
            "улица Лесная", "улица Школьная", "улица Центральная", "улица Новая",
            "проспект Ленина", "переулок Школьный", "переулок Садовый", "переулок Мирный",
            "улица Заводская", "улица Рабочая", "улица Промышленная", "улица Энергетиков",
            "улица Химиков", "улица Металлургов", "улица Машиностроителей",
            "микрорайон Дубрава", "микрорайон Сосновка", "микрорайон Березки");

    /**
     * Получить локальные автоподсказки адресов
     */
    public List<AddressSuggestion> getLocalSuggestions(String query) {
        if (query == null || query.trim().length() < 2) {
            return new ArrayList<>();
        }

        String normalizedQuery = query.toLowerCase().trim();

        return VOLZHSK_STREETS.stream()
                .filter(street -> street.toLowerCase().contains(normalizedQuery))
                .map(this::createLocalSuggestion)
                .collect(Collectors.toList());
    }

    /**
     * Получить автоподсказки домов для конкретной улицы
     */
    public List<AddressSuggestion> getHouseSuggestions(String street, String houseQuery) {
        List<AddressSuggestion> suggestions = new ArrayList<>();

        if (houseQuery == null || houseQuery.trim().isEmpty()) {
            // Возвращаем примеры домов для улицы
            for (int i = 1; i <= 20; i += 2) { // Нечетные дома
                suggestions.add(createHouseSuggestion(street, String.valueOf(i)));
            }
            for (int i = 2; i <= 20; i += 2) { // Четные дома
                suggestions.add(createHouseSuggestion(street, String.valueOf(i)));
            }
        } else {
            // Фильтруем по запросу
            String normalizedQuery = houseQuery.toLowerCase().trim();

            // Генерируем варианты домов (1-200)
            for (int i = 1; i <= 200; i++) {
                String houseNumber = String.valueOf(i);
                if (houseNumber.startsWith(normalizedQuery)) {
                    suggestions.add(createHouseSuggestion(street, houseNumber));

                    // Добавляем варианты с буквами
                    if (suggestions.size() < 10) {
                        suggestions.add(createHouseSuggestion(street, houseNumber + "А"));
                        suggestions.add(createHouseSuggestion(street, houseNumber + "Б"));
                    }
                }

                if (suggestions.size() >= 15)
                    break;
            }
        }

        return suggestions.stream().limit(10).collect(Collectors.toList());
    }

    /**
     * Создание локального предложения адреса
     * ИСПРАВЛЕНО: Показываем только название улицы для мобильного приложения
     */
    private AddressSuggestion createLocalSuggestion(String street) {
        String fullAddress = "Республика Марий Эл, Волжск, " + street;

        // Извлекаем только название улицы без "улица", "переулок" и т.д.
        String shortStreetName = extractStreetName(street);

        return AddressSuggestion.builder()
                .address(fullAddress)
                .shortAddress(shortStreetName) // ИСПРАВЛЕНО: только название улицы
                .latitude(55.866 + (Math.random() - 0.5) * 0.01) // Небольшая вариация координат
                .longitude(48.359 + (Math.random() - 0.5) * 0.01)
                .source("local")
                .metadata("street")
                .build();
    }

    /**
     * Создание предложения с номером дома
     * ИСПРАВЛЕНО: Показываем только название улицы с номером дома
     */
    private AddressSuggestion createHouseSuggestion(String street, String houseNumber) {
        String fullAddress = "Республика Марий Эл, Волжск, " + street + ", " + houseNumber;

        // Извлекаем только название улицы без "улица", "переулок" и т.д.
        String shortStreetName = extractStreetName(street);

        return AddressSuggestion.builder()
                .address(fullAddress)
                .shortAddress(shortStreetName + ", " + houseNumber) // ИСПРАВЛЕНО: только название улицы с домом
                .latitude(55.866 + (Math.random() - 0.5) * 0.01)
                .longitude(48.359 + (Math.random() - 0.5) * 0.01)
                .source("local")
                .metadata("house")
                .build();
    }

    /**
     * Извлечение названия улицы без типа (улица, переулок и т.д.)
     * ДОБАВЛЕНО: Для унификации с Yandex API
     */
    private String extractStreetName(String street) {
        String streetName = street.trim();

        if (streetName.toLowerCase().startsWith("улица ")) {
            streetName = streetName.substring(6); // Убираем "улица "
        } else if (streetName.toLowerCase().startsWith("ул. ")) {
            streetName = streetName.substring(4); // Убираем "ул. "
        } else if (streetName.toLowerCase().startsWith("переулок ")) {
            streetName = streetName.substring(9); // Убираем "переулок "
        } else if (streetName.toLowerCase().startsWith("пер. ")) {
            streetName = streetName.substring(5); // Убираем "пер. "
        } else if (streetName.toLowerCase().startsWith("проспект ")) {
            streetName = streetName.substring(9); // Убираем "проспект "
        } else if (streetName.toLowerCase().startsWith("микрорайон ")) {
            streetName = streetName.substring(11); // Убираем "микрорайон "
        }

        return streetName.trim();
    }

    /**
     * Проверить, является ли адрес валидным для Волжска
     */
    public boolean isValidVolzhskAddress(String address) {
        if (address == null || address.trim().isEmpty()) {
            return false;
        }

        String normalizedAddress = address.toLowerCase();

        // Проверяем, содержит ли адрес "волжск"
        if (!normalizedAddress.contains("волжск")) {
            return false;
        }

        // Проверяем, содержит ли одну из известных улиц
        return VOLZHSK_STREETS.stream()
                .anyMatch(street -> normalizedAddress.contains(street.toLowerCase()));
    }
}