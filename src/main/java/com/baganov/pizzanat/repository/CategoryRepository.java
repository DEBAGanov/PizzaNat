package com.baganov.pizzanat.repository;

import com.baganov.pizzanat.model.entity.Category;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface CategoryRepository extends JpaRepository<Category, Integer> {

    List<Category> findAllByIsActiveTrueOrderByDisplayOrderAsc();

    Optional<Category> findByName(String name);
}