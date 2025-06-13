package com.baganov.pizzanat.util;

import com.baganov.pizzanat.entity.User;
import com.baganov.pizzanat.model.dto.telegram.TelegramUserData;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

/**
 * Утилита для извлечения данных пользователя из Telegram API.
 * Следует принципу Single Responsibility из SOLID.
 */
@Component
@RequiredArgsConstructor
public class TelegramUserDataExtractor {

    private final PasswordEncoder passwordEncoder;

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

        String username = generateUsername(telegramData);
        String email = generateEmailForTelegramUser(telegramData);

        return User.builder()
                .telegramId(telegramData.getId())
                .telegramUsername(telegramData.getUsername())
                .firstName(telegramData.getFirstName())
                .lastName(telegramData.getLastName())
                .username(username)
                .email(email) // Генерируем email для совместимости с мобильным приложением
                .password(passwordEncoder.encode("tg_temp_" + telegramData.getId())) // Временный пароль для Telegram
                                                                                     // пользователей
                .isTelegramVerified(true)
                .isActive(true) // Активируем пользователя сразу
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

        // Генерируем email если его нет (для совместимости с мобильным приложением)
        if (existingUser.getEmail() == null || existingUser.getEmail().trim().isEmpty()) {
            existingUser.setEmail(generateEmailForTelegramUser(telegramData));
        }

        // Обновляем имя только если оно не было установлено ранее
        if (existingUser.getFirstName() == null && telegramData.getFirstName() != null) {
            existingUser.setFirstName(telegramData.getFirstName());
        }

        if (existingUser.getLastName() == null && telegramData.getLastName() != null) {
            existingUser.setLastName(telegramData.getLastName());
        }

        // Обновляем номер телефона если предоставлен
        if (telegramData.getPhoneNumber() != null && !telegramData.getPhoneNumber().trim().isEmpty()) {
            String formattedPhone = formatPhoneNumber(telegramData.getPhoneNumber());
            existingUser.setPhone(formattedPhone);
        }

        // Активируем пользователя если он не активен
        if (!existingUser.isActive()) {
            existingUser.setActive(true);
        }

        return existingUser;
    }

    /**
     * Форматирование номера телефона в +7 формат
     *
     * @param phoneNumber исходный номер телефона
     * @return отформатированный номер в формате +7XXXXXXXXXX
     */
    private String formatPhoneNumber(String phoneNumber) {
        if (phoneNumber == null || phoneNumber.trim().isEmpty()) {
            return phoneNumber;
        }

        // Убираем все символы кроме цифр
        String cleanPhone = phoneNumber.replaceAll("[^0-9]", "");

        // Обработка различных форматов
        if (cleanPhone.startsWith("7") && cleanPhone.length() == 11) {
            // Формат: 79161234567 -> +79161234567
            return "+" + cleanPhone;
        } else if (cleanPhone.startsWith("8") && cleanPhone.length() == 11) {
            // Формат: 89161234567 -> +79161234567
            return "+7" + cleanPhone.substring(1);
        } else if (cleanPhone.length() == 10) {
            // Формат: 9161234567 -> +79161234567
            return "+7" + cleanPhone;
        } else if (cleanPhone.startsWith("37") && cleanPhone.length() == 12) {
            // Формат: 379161234567 -> +79161234567 (убираем 3)
            return "+" + cleanPhone.substring(1);
        }

        // Если формат не распознан - возвращаем как есть с префиксом +7
        return "+7" + cleanPhone;
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
     * Генерирует email для пользователя Telegram для совместимости с мобильным
     * приложением
     *
     * @param telegramData данные Telegram
     * @return сгенерированный email
     */
    private String generateEmailForTelegramUser(TelegramUserData telegramData) {
        // Генерируем email на основе Telegram ID для уникальности
        // Используем специальный домен для Telegram пользователей
        return "tg_" + telegramData.getId() + "@telegram.pizzanat.local";
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