package com.baganov.pizzanat.repository;

import com.baganov.pizzanat.model.entity.DeliveryLocation;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface DeliveryLocationRepository extends JpaRepository<DeliveryLocation, Integer> {

    List<DeliveryLocation> findAllByIsActiveTrue();
}