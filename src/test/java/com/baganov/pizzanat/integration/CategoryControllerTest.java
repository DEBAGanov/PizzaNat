/**
 * @file: CategoryControllerTest.java
 * @description: Тесты для CategoryController
 * @dependencies: Spring Boot Test
 * @created: 2025-05-22
 */
package com.baganov.pizzanat.integration;

import com.baganov.pizzanat.entity.Category;
import com.baganov.pizzanat.repository.CategoryRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.transaction.annotation.Transactional;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultHandlers.print;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@Transactional
public class CategoryControllerTest extends BaseIntegrationTest {

    @Autowired
    private CategoryRepository categoryRepository;

    private Integer categoryId;

    @BeforeEach
    void setupTestData() {
        // Создаем тестовую категорию
        Category category = Category.builder()
                .name("Тестовая категория")
                .isActive(true)
                .build();

        category = categoryRepository.save(category);
        categoryId = category.getId();
    }

    @Test
    @DisplayName("Получение списка всех активных категорий")
    public void testGetAllCategories() throws Exception {
        mockMvc.perform(get("/api/v1/categories")
                .contentType(MediaType.APPLICATION_JSON))
                .andDo(print())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray())
                .andExpect(jsonPath("$[0].id").exists())
                .andExpect(jsonPath("$[0].name").exists());
    }

    @Test
    @DisplayName("Получение категории по ID")
    public void testGetCategoryById() throws Exception {
        mockMvc.perform(get("/api/v1/categories/" + categoryId)
                .contentType(MediaType.APPLICATION_JSON))
                .andDo(print())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(categoryId))
                .andExpect(jsonPath("$.name").value("Тестовая категория"));
    }

    @Test
    @DisplayName("Ошибка при запросе несуществующей категории")
    public void testGetNonExistentCategory() throws Exception {
        // Используем заведомо несуществующий ID
        // Сервис выбрасывает IllegalArgumentException при отсутствии категории,
        // что обрабатывается GlobalExceptionHandler и возвращает статус 404 Not Found
        mockMvc.perform(get("/api/v1/categories/9999")
                .contentType(MediaType.APPLICATION_JSON))
                .andDo(print())
                .andExpect(status().isNotFound());
    }
}