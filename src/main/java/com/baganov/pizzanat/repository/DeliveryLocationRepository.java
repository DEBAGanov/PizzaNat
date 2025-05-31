package com.baganov.pizzanat.repository;

import com.baganov.pizzanat.entity.DeliveryLocation;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface DeliveryLocationRepository extends JpaRepository<DeliveryLocation, Integer> {

    List<DeliveryLocation> findAllByIsActiveTrue();

    Optional<DeliveryLocation> findByAddress(String address);

    boolean existsByName(String name);
}