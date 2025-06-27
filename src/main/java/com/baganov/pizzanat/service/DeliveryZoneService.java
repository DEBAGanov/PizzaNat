/**
 * @file: DeliveryZoneService.java
 * @description: Сервис для определения зон доставки и расчета стоимости по адресу
 * @dependencies: Spring Data JPA, DeliveryZone entities
 * @created: 2025-01-23
 */
package com.baganov.pizzanat.service;

import com.baganov.pizzanat.entity.DeliveryZone;
import com.baganov.pizzanat.entity.DeliveryZoneKeyword;
import com.baganov.pizzanat.entity.DeliveryZoneStreet;
import com.baganov.pizzanat.repository.DeliveryZoneRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

@Service
@RequiredArgsConstructor
@Slf4j
public class DeliveryZoneService {

    private final DeliveryZoneRepository deliveryZoneRepository;

    /**
     * Определяет зону доставки по адресу
     *
     * @param address адрес для проверки
     * @return зона доставки или пустой Optional если зона не найдена
     */
    public Optional<DeliveryZone> determineZoneByAddress(String address) {
        if (address == null || address.trim().isEmpty()) {
            log.warn("Пустой адрес для определения зоны доставки");
            return Optional.empty();
        }

        log.info("=== НАЧАЛО ОПРЕДЕЛЕНИЯ ЗОНЫ ДОСТАВКИ ===");
        log.info("Определение зоны доставки для адреса: {}", address);

        List<DeliveryZone> activeZones = deliveryZoneRepository.findByIsActiveTrueOrderByPriorityDesc();
        log.info("Найдено активных зон в БД: {}", activeZones.size());

        for (DeliveryZone zone : activeZones) {
            log.info("Проверка зоны: {} (ID: {}, приоритет: {}, улиц: {}, ключевых слов: {})",
                    zone.getName(), zone.getId(), zone.getPriority(),
                    zone.getStreets().size(), zone.getKeywords().size());

            if (addressMatchesZone(address, zone)) {
                log.info("✅ НАЙДЕНА ЗОНА: Адрес '{}' соответствует зоне: {} (стоимость: {}₽)",
                        address, zone.getName(), zone.getBaseCost());
                return Optional.of(zone);
            } else {
                log.info("❌ Адрес '{}' НЕ соответствует зоне: {}", address, zone.getName());
            }
        }

        log.warn("❌ НЕ НАЙДЕНА зона доставки для адреса: {}", address);
        log.info("=== КОНЕЦ ОПРЕДЕЛЕНИЯ ЗОНЫ ДОСТАВКИ ===");
        return Optional.empty();
    }

    /**
     * Проверяет, соответствует ли адрес указанной зоне доставки
     */
    private boolean addressMatchesZone(String address, DeliveryZone zone) {
        // Проверка по улицам
        for (DeliveryZoneStreet street : zone.getStreets()) {
            if (street.matchesAddress(address)) {
                log.debug("Адрес соответствует улице '{}' в зоне '{}'",
                        street.getStreetName(), zone.getName());
                return true;
            }
        }

        // Проверка по ключевым словам
        for (DeliveryZoneKeyword keyword : zone.getKeywords()) {
            if (keyword.matchesAddress(address)) {
                log.debug("Адрес соответствует ключевому слову '{}' в зоне '{}'",
                        keyword.getKeyword(), zone.getName());
                return true;
            }
        }

        return false;
    }

    /**
     * Рассчитывает стоимость доставки для указанного адреса и суммы заказа
     *
     * @param address     адрес доставки
     * @param orderAmount сумма заказа
     * @return результат расчета доставки
     */
    public DeliveryCalculationResult calculateDelivery(String address, BigDecimal orderAmount) {
        Optional<DeliveryZone> zoneOpt = determineZoneByAddress(address);

        if (zoneOpt.isEmpty()) {
            return DeliveryCalculationResult.builder()
                    .address(address)
                    .deliveryAvailable(false)
                    .reason("Адрес находится за пределами зоны доставки")
                    .zoneName("Вне зоны")
                    .build();
        }

        DeliveryZone zone = zoneOpt.get();
        BigDecimal finalCost = zone.getFinalDeliveryCost(orderAmount);
        boolean isFree = zone.isDeliveryFree(orderAmount);

        return DeliveryCalculationResult.builder()
                .address(address)
                .deliveryAvailable(true)
                .zoneName(zone.getName())
                .zoneDescription(zone.getDescription())
                .deliveryCost(finalCost)
                .baseCost(zone.getBaseCost())
                .freeDeliveryThreshold(zone.getFreeDeliveryThreshold())
                .isDeliveryFree(isFree)
                .estimatedTimeMin(zone.getDeliveryTimeMin())
                .estimatedTimeMax(zone.getDeliveryTimeMax())
                .estimatedTime(zone.getFormattedDeliveryTime())
                .currency("RUB")
                .message(isFree ? "Бесплатная доставка" : "Доставка - " + finalCost + " ₽")
                .workingHours("09:00-22:00") // TODO: сделать настраиваемым
                .city("Волжск")
                .region("Республика Марий Эл")
                .build();
    }

    /**
     * Получает все активные зоны доставки
     */
    public List<DeliveryZone> getAllActiveZones() {
        return deliveryZoneRepository.findByIsActiveTrueOrderByPriorityDesc();
    }

    /**
     * Получает зону по ID
     */
    public Optional<DeliveryZone> getZoneById(Integer id) {
        return deliveryZoneRepository.findById(id);
    }

    /**
     * Результат расчета доставки
     */
    @lombok.Data
    @lombok.Builder
    @lombok.NoArgsConstructor
    @lombok.AllArgsConstructor
    public static class DeliveryCalculationResult {
        private String address;
        private boolean deliveryAvailable;
        private String reason;

        // Информация о зоне
        private String zoneName;
        private String zoneDescription;

        // Стоимость
        private BigDecimal deliveryCost;
        private BigDecimal baseCost;
        private BigDecimal freeDeliveryThreshold;
        private boolean isDeliveryFree;
        private String currency;

        // Время доставки
        private Integer estimatedTimeMin;
        private Integer estimatedTimeMax;
        private String estimatedTime;

        // Дополнительная информация
        private String message;
        private String workingHours;
        private String city;
        private String region;
    }
}