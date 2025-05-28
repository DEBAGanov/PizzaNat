package com.baganov.pizzanat.repository;

import com.baganov.pizzanat.entity.Cart;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface CartRepository extends JpaRepository<Cart, Integer> {

    Optional<Cart> findByUserId(Integer userId);

    Optional<Cart> findBySessionId(String sessionId);

    boolean existsByUserId(Integer userId);
}