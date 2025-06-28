/**
 * @file: DeliveryZoneRepository.java
 * @description: Репозиторий для работы с зонами доставки
 * @dependencies: Spring Data JPA
 * @created: 2025-01-23
 */
package com.baganov.pizzanat.repository;

import com.baganov.pizzanat.entity.DeliveryZone;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface DeliveryZoneRepository extends JpaRepository<DeliveryZone, Integer> {

        /**
         * Находит все активные зоны доставки, отсортированные по приоритету
         */
        List<DeliveryZone> findByIsActiveTrueOrderByPriorityDesc();

        /**
         * Находит все активные зоны доставки с загруженными улицами
         */
        @Query("SELECT DISTINCT z FROM DeliveryZone z " +
                        "LEFT JOIN FETCH z.streets " +
                        "WHERE z.isActive = true " +
                        "ORDER BY z.priority DESC")
        List<DeliveryZone> findByIsActiveTrueWithStreets();

        /**
         * Находит все активные зоны доставки с загруженными ключевыми словами
         */
        @Query("SELECT DISTINCT z FROM DeliveryZone z " +
                        "LEFT JOIN FETCH z.keywords " +
                        "WHERE z.isActive = true " +
                        "ORDER BY z.priority DESC")
        List<DeliveryZone> findByIsActiveTrueWithKeywords();

        /**
         * Находит все активные зоны доставки с загруженными улицами И ключевыми словами
         */
        // @Query("SELECT DISTINCT z FROM DeliveryZone z " +
        // "LEFT JOIN FETCH z.streets " +
        // "LEFT JOIN FETCH z.keywords " +
        // "WHERE z.isActive = true " +
        // "ORDER BY z.priority DESC")
        // List<DeliveryZone> findByIsActiveTrueWithStreetsAndKeywords();

        /**
         * Находит все зоны доставки по активности
         */
        List<DeliveryZone> findByIsActive(Boolean isActive);

        /**
         * Находит зону доставки по названию
         */
        DeliveryZone findByName(String name);

        /**
         * Проверяет существование зоны с указанным названием
         */
        boolean existsByName(String name);

        /**
         * Находит зоны доставки с определенным диапазоном стоимости
         */
        @Query("SELECT z FROM DeliveryZone z WHERE z.isActive = true AND z.baseCost BETWEEN :minCost AND :maxCost ORDER BY z.baseCost")
        List<DeliveryZone> findByBaseCostRange(java.math.BigDecimal minCost, java.math.BigDecimal maxCost);

        /**
         * Находит зоны доставки по приоритету
         */
        List<DeliveryZone> findByIsActiveTrueAndPriorityGreaterThanOrderByPriorityDesc(Integer priority);

        /**
         * Загружает ключевые слова для указанных зон
         * Используется для избежания MultipleBagFetchException
         */
        @Query("SELECT DISTINCT z FROM DeliveryZone z " +
                        "LEFT JOIN FETCH z.keywords " +
                        "WHERE z.id IN :zoneIds")
        List<DeliveryZone> loadKeywordsForZones(List<Integer> zoneIds);
}