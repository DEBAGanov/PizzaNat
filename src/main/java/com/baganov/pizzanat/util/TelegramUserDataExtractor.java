package com.baganov.pizzanat.util;

import com.baganov.pizzanat.entity.User;
import com.baganov.pizzanat.model.dto.telegram.TelegramUserData;
import org.springframework.stereotype.Component;

/**
 * Утилита для извлечения данных пользователя из Telegram API.
 * Следует принципу Single Responsibility из SOLID.
 */
@Component
public class TelegramUserDataExtractor {

    /**
     * Извлекает данные пользователя из Telegram API update
     * 
     * @param telegramUserData данные от Telegram API
     * @return валидированные данные пользователя
     */
    public TelegramUserData extractFromTelegramData(TelegramUserData telegramUserData) {
        if (telegramUserData == null) {
            return null;
        }

        // Проверяем обязательные поля
        if (telegramUserData.getId() == null || telegramUserData.getId() <= 0) {
            return null;
        }

        // Создаем очищенную копию данных
        return TelegramUserData.builder()
                .id(telegramUserData.getId())
                .username(sanitizeUsername(telegramUserData.getUsername()))
                .firstName(sanitizeText(telegramUserData.getFirstName()))
                .lastName(sanitizeText(telegramUserData.getLastName()))
                .build();
    }

    /**
     * Проверяет валидность данных пользователя Telegram
     * 
     * @param userData данные пользователя
     * @return true если данные валидны
     */
    public boolean isValidUserData(TelegramUserData userData) {
        if (userData == null) {
            return false;
        }

        // Обязательное поле - ID пользователя
        if (userData.getId() == null || userData.getId() <= 0) {
            return false;
        }

        // Должно быть хотя бы одно поле для идентификации
        return hasIdentification(userData);
    }

    /**
     * Создает нового пользователя на основе данных Telegram
     * 
     * @param telegramData данные от Telegram
     * @return новый пользователь
     */
    public User createUserFromTelegramData(TelegramUserData telegramData) {
        if (!isValidUserData(telegramData)) {
            throw new IllegalArgumentException("Некорректные данные пользователя Telegram");
        }

        return User.builder()
                .telegramId(telegramData.getId())
                .telegramUsername(telegramData.getUsername())
                .firstName(telegramData.getFirstName())
                .lastName(telegramData.getLastName())
                .username(generateUsername(telegramData))
                .isTelegramVerified(true)
                .build();
    }

    /**
     * Обновляет существующего пользователя данными Telegram
     * 
     * @param existingUser существующий пользователь
     * @param telegramData новые данные от Telegram
     * @return обновленный пользователь
     */
    public User updateUserWithTelegramData(User existingUser, TelegramUserData telegramData) {
        if (existingUser == null || !isValidUserData(telegramData)) {
            throw new IllegalArgumentException("Некорректные данные для обновления пользователя");
        }

        existingUser.setTelegramId(telegramData.getId());
        existingUser.setTelegramUsername(telegramData.getUsername());
        existingUser.setIsTelegramVerified(true);

        // Обновляем имя только если оно не было установлено ранее
        if (existingUser.getFirstName() == null && telegramData.getFirstName() != null) {
            existingUser.setFirstName(telegramData.getFirstName());
        }

        if (existingUser.getLastName() == null && telegramData.getLastName() != null) {
            existingUser.setLastName(telegramData.getLastName());
        }

        return existingUser;
    }

    /**
     * Генерирует username для пользователя на основе Telegram данных
     * 
     * @param telegramData данные Telegram
     * @return сгенерированный username
     */
    private String generateUsername(TelegramUserData telegramData) {
        // Приоритет: telegram username > первое имя > fallback
        if (telegramData.getUsername() != null && !telegramData.getUsername().trim().isEmpty()) {
            return telegramData.getUsername().trim();
        }

        if (telegramData.getFirstName() != null && !telegramData.getFirstName().trim().isEmpty()) {
            return "tg_" + telegramData.getFirstName().trim().replaceAll("[^a-zA-Z0-9]", "");
        }

        return "tg_user_" + telegramData.getId();
    }

    /**
     * Проверяет наличие данных для идентификации пользователя
     * 
     * @param userData данные пользователя
     * @return true если есть данные для идентификации
     */
    private boolean hasIdentification(TelegramUserData userData) {
        return (userData.getUsername() != null && !userData.getUsername().trim().isEmpty()) ||
                (userData.getFirstName() != null && !userData.getFirstName().trim().isEmpty()) ||
                (userData.getLastName() != null && !userData.getLastName().trim().isEmpty());
    }

    /**
     * Очищает username от лишних символов
     * 
     * @param username исходный username
     * @return очищенный username
     */
    private String sanitizeUsername(String username) {
        if (username == null || username.trim().isEmpty()) {
            return null;
        }

        // Убираем @ если есть, оставляем только буквы, цифры и подчеркивания
        String cleaned = username.trim();
        if (cleaned.startsWith("@")) {
            cleaned = cleaned.substring(1);
        }

        return cleaned.replaceAll("[^a-zA-Z0-9_]", "");
    }

    /**
     * Очищает текстовые поля от потенциально опасного содержимого
     * 
     * @param text исходный текст
     * @return очищенный текст
     */
    private String sanitizeText(String text) {
        if (text == null || text.trim().isEmpty()) {
            return null;
        }

        // Убираем HTML-теги и ограничиваем длину
        String cleaned = text.trim()
                .replaceAll("<[^>]*>", "")
                .replaceAll("[\\r\\n\\t]", " ")
                .replaceAll("\\s+", " ");

        // Ограничиваем длину
        if (cleaned.length() > 100) {
            cleaned = cleaned.substring(0, 100);
        }

        return cleaned;
    }
}