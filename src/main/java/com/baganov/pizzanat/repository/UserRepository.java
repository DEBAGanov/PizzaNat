package com.baganov.pizzanat.repository;

import com.baganov.pizzanat.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface UserRepository extends JpaRepository<User, Integer> {

    Optional<User> findByUsername(String username);

    Optional<User> findByEmail(String email);

    boolean existsByUsername(String username);

    boolean existsByEmail(String email);

    // === Методы для SMS аутентификации ===

    /**
     * Поиск пользователя по номеру телефона
     * 
     * @param phoneNumber номер телефона в формате +7XXXXXXXXXX
     * @return пользователь если найден
     */
    Optional<User> findByPhoneNumber(String phoneNumber);

    /**
     * Проверка существования пользователя с номером телефона
     * 
     * @param phoneNumber номер телефона
     * @return true если пользователь существует
     */
    boolean existsByPhoneNumber(String phoneNumber);

    // === Методы для Telegram аутентификации ===

    /**
     * Поиск пользователя по Telegram ID
     * 
     * @param telegramId ID пользователя в Telegram
     * @return пользователь если найден
     */
    Optional<User> findByTelegramId(Long telegramId);

    /**
     * Проверка существования пользователя с Telegram ID
     * 
     * @param telegramId ID пользователя в Telegram
     * @return true если пользователь существует
     */
    boolean existsByTelegramId(Long telegramId);

    /**
     * Поиск пользователя по Telegram username
     * 
     * @param telegramUsername username в Telegram (без @)
     * @return пользователь если найден
     */
    Optional<User> findByTelegramUsername(String telegramUsername);

    /**
     * Получение всех пользователей с подтвержденным Telegram ID
     * 
     * @return список пользователей с Telegram ID
     */
    java.util.List<User> findByTelegramIdIsNotNullAndIsTelegramVerifiedTrue();
}