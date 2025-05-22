package com.baganov.pizzanat.repository;

import com.baganov.pizzanat.model.entity.Order;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface OrderRepository extends JpaRepository<Order, Integer> {

    Page<Order> findByUserIdOrderByCreatedAtDesc(Integer userId, Pageable pageable);

    List<Order> findTop5ByUserIdOrderByCreatedAtDesc(Integer userId);

    Optional<Order> findByIdAndUserId(Integer id, Integer userId);
}