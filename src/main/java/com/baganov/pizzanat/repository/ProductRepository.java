package com.baganov.pizzanat.repository;

import com.baganov.pizzanat.entity.Product;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface ProductRepository extends JpaRepository<Product, Integer> {

        @Query("SELECT p FROM Product p WHERE p.isAvailable = true")
        Page<Product> findAllByIsAvailableTrue(Pageable pageable);

        @Query("SELECT p FROM Product p WHERE p.category.id = :categoryId AND p.isAvailable = true")
        Page<Product> findByCategoryIdAndIsAvailableTrue(@Param("categoryId") Integer categoryId, Pageable pageable);

        @Query("SELECT p FROM Product p JOIN FETCH p.category WHERE p.isAvailable = true AND p.isSpecialOffer = true ORDER BY p.id DESC LIMIT 8")
        List<Product> findTop8ByIsAvailableTrueAndIsSpecialOfferTrueOrderByIdDesc();

        @Query("SELECT p FROM Product p WHERE (:categoryId IS NULL OR p.category.id = :categoryId) "
                        +
                        "AND p.isAvailable = true " +
                        "AND (:query IS NULL OR :query = '' OR " +
                        "LOWER(p.name) LIKE LOWER(CONCAT('%', :query, '%')) " +
                        "OR LOWER(p.description) LIKE LOWER(CONCAT('%', :query, '%')))")
        Page<Product> searchProducts(@Param("categoryId") Integer categoryId, @Param("query") String query,
                        Pageable pageable);

        @Query("SELECT p FROM Product p JOIN FETCH p.category WHERE p.id = :id")
        Optional<Product> findByIdWithCategory(@Param("id") Integer id);

        boolean existsByName(String name);

        boolean existsByImageUrl(String imageUrl);

        Page<Product> findByCategoryId(Integer categoryId, Pageable pageable);

        List<Product> findByIsSpecialOfferTrue();

        Page<Product> findByCategoryIdAndNameContainingIgnoreCase(Integer categoryId, String name, Pageable pageable);

        Page<Product> findByNameContainingIgnoreCase(String name, Pageable pageable);

        Optional<Product> findByName(String name);
}