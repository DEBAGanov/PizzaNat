/**
 * @file: DeliveryZoneStreet.java
 * @description: Entity для улиц, входящих в зоны доставки
 * @dependencies: JPA, Lombok
 * @created: 2025-01-23
 */
package com.baganov.pizzanat.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "delivery_zone_streets")
public class DeliveryZoneStreet {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "zone_id", nullable = false)
    private DeliveryZone zone;

    @Column(name = "street_name", nullable = false)
    private String streetName;

    @Column(name = "house_number_from")
    private Integer houseNumberFrom;

    @Column(name = "house_number_to")
    private Integer houseNumberTo;

    @Column(name = "is_even_only")
    @Builder.Default
    private Boolean isEvenOnly = false;

    @Column(name = "is_odd_only")
    @Builder.Default
    private Boolean isOddOnly = false;

    @Column(name = "created_at")
    private LocalDateTime createdAt;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
    }

    /**
     * Проверяет, подходит ли адрес под эту улицу
     */
    public boolean matchesAddress(String address) {
        if (address == null || address.trim().isEmpty()) {
            return false;
        }

        String normalizedAddress = address.toLowerCase().trim();
        String normalizedStreet = streetName.toLowerCase().trim();

        // Логирование для отладки
        System.out
                .println("DEBUG: Проверка адреса '" + normalizedAddress + "' против улицы '" + normalizedStreet + "'");

        // Улучшенная проверка названия улицы
        boolean streetMatches = false;

        // 1. Прямое включение названия улицы
        if (normalizedAddress.contains(normalizedStreet)) {
            streetMatches = true;
        }

        // 2. Проверка с различными префиксами
        String[] prefixes = { "улица ", "ул. ", "ул ", "проспект ", "пр-т ", "пр ", "переулок ", "пер. ", "пер " };
        for (String prefix : prefixes) {
            if (normalizedAddress.contains(prefix + normalizedStreet)) {
                streetMatches = true;
                break;
            }
        }

        // 3. Проверка без префиксов (например, "дружбы" в "улица дружбы, 5")
        String addressWithoutPrefixes = normalizedAddress;
        for (String prefix : prefixes) {
            addressWithoutPrefixes = addressWithoutPrefixes.replace(prefix, " ");
        }
        addressWithoutPrefixes = addressWithoutPrefixes.trim().replaceAll("\\s+", " ");

        if (addressWithoutPrefixes.contains(normalizedStreet)) {
            streetMatches = true;
        }

        System.out.println("DEBUG: Улица совпадает: " + streetMatches);

        if (!streetMatches) {
            return false;
        }

        // Если диапазон домов не указан - вся улица подходит
        if (houseNumberFrom == null && houseNumberTo == null) {
            System.out.println("DEBUG: Диапазон домов не указан, улица подходит полностью");
            return true;
        }

        // Извлечение номера дома из адреса (простая реализация)
        Integer houseNumber = extractHouseNumber(normalizedAddress);
        System.out.println("DEBUG: Извлеченный номер дома: " + houseNumber);

        if (houseNumber == null) {
            System.out.println("DEBUG: Номер дома не определен, считаем что подходит");
            return true; // Если не можем определить номер дома, считаем что подходит
        }

        // Проверка диапазона домов
        boolean inRange = true;
        if (houseNumberFrom != null) {
            inRange = houseNumber >= houseNumberFrom;
            System.out.println("DEBUG: Проверка минимального номера " + houseNumberFrom + ": " + inRange);
        }
        if (houseNumberTo != null && inRange) {
            inRange = houseNumber <= houseNumberTo;
            System.out.println("DEBUG: Проверка максимального номера " + houseNumberTo + ": " + inRange);
        }

        // Проверка четности/нечетности
        if (inRange && (isEvenOnly || isOddOnly)) {
            if (isEvenOnly && houseNumber % 2 != 0) {
                System.out.println("DEBUG: Требуются четные дома, но номер нечетный");
                return false;
            }
            if (isOddOnly && houseNumber % 2 == 0) {
                System.out.println("DEBUG: Требуются нечетные дома, но номер четный");
                return false;
            }
        }

        System.out.println("DEBUG: Итоговый результат: " + inRange);
        return inRange;
    }

    /**
     * Извлекает номер дома из адреса (простая реализация)
     */
    private Integer extractHouseNumber(String address) {
        try {
            // Ищем паттерн "д. 123", "дом 123", "123"
            String[] patterns = { "д\\.", "дом", "д", "house" };

            for (String pattern : patterns) {
                String regex = pattern + "\\s*(\\d+)";
                java.util.regex.Pattern p = java.util.regex.Pattern.compile(regex);
                java.util.regex.Matcher m = p.matcher(address);
                if (m.find()) {
                    return Integer.parseInt(m.group(1));
                }
            }

            // Если не нашли по паттернам, ищем любое число в конце
            java.util.regex.Pattern p = java.util.regex.Pattern.compile("(\\d+)(?!.*\\d)");
            java.util.regex.Matcher m = p.matcher(address);
            if (m.find()) {
                return Integer.parseInt(m.group(1));
            }
        } catch (NumberFormatException e) {
            // Игнорируем ошибки парсинга
        }

        return null;
    }
}