package com.baganov.pizzanat.repository;

import com.baganov.pizzanat.model.entity.Product;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface ProductRepository extends JpaRepository<Product, Integer> {

        Page<Product> findAllByIsAvailableTrue(Pageable pageable);

        Page<Product> findByCategoryIdAndIsAvailableTrue(Integer categoryId, Pageable pageable);

        List<Product> findTop8ByIsAvailableTrueAndIsSpecialOfferTrueOrderByIdDesc();

        @Query("SELECT p FROM Product p WHERE (:categoryId IS NULL OR p.category.id = :categoryId) " +
                        "AND p.isAvailable = true " +
                        "AND (LOWER(p.name) LIKE LOWER(CONCAT('%', :query, '%')) " +
                        "OR LOWER(p.description) LIKE LOWER(CONCAT('%', :query, '%')))")
        Page<Product> searchProducts(Integer categoryId, String query, Pageable pageable);

        boolean existsByName(String name);

        boolean existsByImageUrl(String imageUrl);
}