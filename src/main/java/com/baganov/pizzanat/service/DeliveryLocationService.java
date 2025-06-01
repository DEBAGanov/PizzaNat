/**
 * @file: DeliveryLocationService.java
 * @description: Сервис для работы с пунктами доставки
 * @dependencies: Spring JPA, MapStruct
 * @created: 2025-05-31
 */
package com.baganov.pizzanat.service;

import com.baganov.pizzanat.entity.DeliveryLocation;
import com.baganov.pizzanat.model.dto.delivery.DeliveryLocationDTO;
import com.baganov.pizzanat.repository.DeliveryLocationRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
@Slf4j
@Transactional(readOnly = true)
public class DeliveryLocationService {

    private final DeliveryLocationRepository deliveryLocationRepository;

    /**
     * Получить все активные пункты доставки
     */
    public List<DeliveryLocationDTO> getAllActiveLocations() {
        log.info("Получение всех активных пунктов доставки");
        List<DeliveryLocation> locations = deliveryLocationRepository.findAllByIsActiveTrue();
        return locations.stream()
                .map(this::mapToDTO)
                .toList();
    }

    /**
     * Получить пункт доставки по ID
     */
    public DeliveryLocationDTO getLocationById(Integer id) {
        log.info("Получение пункта доставки с ID: {}", id);
        DeliveryLocation location = deliveryLocationRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Пункт доставки не найден с ID: " + id));

        return mapToDTO(location);
    }

    /**
     * Маппинг Entity в DTO
     */
    private DeliveryLocationDTO mapToDTO(DeliveryLocation location) {
        return DeliveryLocationDTO.builder()
                .id(location.getId())
                .name(location.getName())
                .address(location.getAddress())
                .latitude(location.getLatitude())
                .longitude(location.getLongitude())
                .workingHours(location.getWorkingHours())
                .phone(location.getPhone())
                .isActive(location.isActive())
                .build();
    }
}