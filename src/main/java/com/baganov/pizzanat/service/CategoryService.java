package com.baganov.pizzanat.service;

import com.baganov.pizzanat.model.dto.product.CategoryDTO;
import com.baganov.pizzanat.entity.Category;
import com.baganov.pizzanat.repository.CategoryRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class CategoryService {

    private final CategoryRepository categoryRepository;
    private final StorageService storageService;

    @Cacheable(value = "categories", key = "'category:' + #id")
    public CategoryDTO getCategoryById(Integer id) {
        Category category = categoryRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Категория не найдена с ID: " + id));
        return mapToDTO(category);
    }

    @Cacheable(value = "categories", key = "'categories:all'")
    public List<CategoryDTO> getAllActiveCategories() {
        return categoryRepository.findAllByIsActiveTrueOrderByDisplayOrderAsc()
                .stream()
                .map(this::mapToDTO)
                .collect(Collectors.toList());
    }

    private CategoryDTO mapToDTO(Category category) {
        String imageUrlWithPresignedUrl = null;
        if (category.getImageUrl() != null && !category.getImageUrl().isEmpty()) {
            try {
                imageUrlWithPresignedUrl = storageService.getPresignedUrl(category.getImageUrl(), 3600);
            } catch (Exception e) {
                log.error("Failed to generate presigned URL for category image: {}", category.getImageUrl(), e);
                imageUrlWithPresignedUrl = category.getImageUrl();
            }
        }

        return CategoryDTO.builder()
                .id(category.getId())
                .name(category.getName())
                .description(category.getDescription())
                .imageUrl(imageUrlWithPresignedUrl)
                .displayOrder(category.getDisplayOrder())
                .build();
    }
}