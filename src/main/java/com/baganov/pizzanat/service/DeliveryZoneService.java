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
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

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
    @org.springframework.transaction.annotation.Transactional(readOnly = true)
    public Optional<DeliveryZone> determineZoneByAddress(String address) {
        if (address == null || address.trim().isEmpty()) {
            log.warn("Пустой адрес для определения зоны доставки");
            return Optional.empty();
        }

        log.info("=== НАЧАЛО ОПРЕДЕЛЕНИЯ ЗОНЫ ДОСТАВКИ ===");
        log.info("Определение зоны доставки для адреса: {}", address);

        try {
            // ИСПРАВЛЕНИЕ: Загружаем коллекции отдельными запросами для избежания
            // MultipleBagFetchException
            // 1. Сначала загружаем зоны с улицами
            List<DeliveryZone> activeZones = deliveryZoneRepository.findByIsActiveTrueWithStreets();
            log.info("Найдено активных зон в БД: {}", activeZones.size());

            if (activeZones.isEmpty()) {
                log.error("❌ В БД НЕТ АКТИВНЫХ ЗОН ДОСТАВКИ!");
                return Optional.empty();
            }

            // 2. Затем загружаем ключевые слова для всех найденных зон одним запросом
            List<Integer> zoneIds = activeZones.stream()
                    .map(DeliveryZone::getId)
                    .toList();

            // Загружаем ключевые слова для всех зон
            List<DeliveryZone> zonesWithKeywords = deliveryZoneRepository.loadKeywordsForZones(zoneIds);
            log.info("Загружены ключевые слова для {} зон", zonesWithKeywords.size());

            // 3. Объединяем данные: у нас есть zones с streets и zonesWithKeywords с
            // keywords
            // Поскольку Hibernate кэширует сущности, keywords теперь доступны в исходных
            // объектах
            // Принудительная инициализация коллекций keywords внутри транзакции
            for (DeliveryZone zone : zonesWithKeywords) {
                // Инициализируем коллекцию keywords в рамках активной сессии
                int keywordsCount = zone.getKeywords().size();
                log.debug("Зона {}: ключевых слов={}", zone.getName(), keywordsCount);
            }

            // Создаем Map для быстрого поиска зон с инициализированными keywords
            Map<Integer, DeliveryZone> zonesMap = zonesWithKeywords.stream()
                    .collect(Collectors.toMap(DeliveryZone::getId, zone -> zone));

            // Используем зоны с полностью инициализированными коллекциями
            for (DeliveryZone zone : activeZones) {
                DeliveryZone zoneWithKeywords = zonesMap.get(zone.getId());
                log.info("Проверка зоны: {} (ID: {}, приоритет: {}, улиц: {})",
                        zone.getName(), zone.getId(), zone.getPriority(),
                        zone.getStreets().size());

                if (addressMatchesZone(address, zone, zoneWithKeywords)) {
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
        } catch (Exception e) {
            log.error("🚨 КРИТИЧЕСКАЯ ОШИБКА в determineZoneByAddress: {}", e.getMessage(), e);
            throw e;
        }
    }

    /**
     * Проверяет, соответствует ли адрес указанной зоне доставки
     *
     * @param address          адрес для проверки
     * @param zoneWithStreets  зона с инициализированными улицами
     * @param zoneWithKeywords зона с инициализированными ключевыми словами (может
     *                         быть null)
     */
    private boolean addressMatchesZone(String address, DeliveryZone zoneWithStreets, DeliveryZone zoneWithKeywords) {
        // Проверка по улицам (используем зону с инициализированными улицами)
        for (DeliveryZoneStreet street : zoneWithStreets.getStreets()) {
            if (street.matchesAddress(address)) {
                log.debug("Адрес соответствует улице '{}' в зоне '{}'",
                        street.getStreetName(), zoneWithStreets.getName());
                return true;
            }
        }

        // Проверка по ключевым словам (используем зону с инициализированными ключевыми
        // словами)
        if (zoneWithKeywords != null) {
            for (DeliveryZoneKeyword keyword : zoneWithKeywords.getKeywords()) {
                if (keyword.matchesAddress(address)) {
                    log.debug("Адрес соответствует ключевому слову '{}' в зоне '{}'",
                            keyword.getKeyword(), zoneWithStreets.getName());
                    return true;
                }
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
            // ИСПРАВЛЕНИЕ: Вместо отказа в доставке, возвращаем стандартную зону с 250₽
            log.info("Адрес '{}' не найден в зональной системе, применяем стандартный тариф 250₽", address);

            BigDecimal standardCost = new BigDecimal("250"); // 250₽ для неизвестных адресов
            BigDecimal freeThreshold = new BigDecimal("1200"); // Бесплатно от 1200₽
            boolean isDeliveryFree = orderAmount.compareTo(freeThreshold) >= 0;
            BigDecimal finalCost = isDeliveryFree ? BigDecimal.ZERO : standardCost;

            return DeliveryCalculationResult.builder()
                    .address(address)
                    .deliveryAvailable(true) // ИЗМЕНЕНО: доставка доступна
                    .zoneName("Стандартная зона")
                    .zoneDescription("Доставка по городу Волжск (стандартный тариф)")
                    .deliveryCost(finalCost)
                    .baseCost(standardCost)
                    .freeDeliveryThreshold(freeThreshold)
                    .isDeliveryFree(isDeliveryFree)
                    .estimatedTimeMin(30)
                    .estimatedTimeMax(50)
                    .estimatedTime("30-50 минут")
                    .currency("RUB")
                    .message(isDeliveryFree ? "Бесплатная доставка" : "Доставка - " + finalCost + " ₽")
                    .workingHours("09:00-22:00")
                    .city("Волжск")
                    .region("Республика Марий Эл")
                    .reason(null) // Убираем причину отказа
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