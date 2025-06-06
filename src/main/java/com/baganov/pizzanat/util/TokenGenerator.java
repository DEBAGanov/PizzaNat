package com.baganov.pizzanat.util;

import org.springframework.stereotype.Component;

import java.security.SecureRandom;
import java.util.regex.Pattern;

/**
 * Генератор токенов для Telegram аутентификации.
 * Следует принципу Single Responsibility из SOLID.
 */
@Component
public class TokenGenerator {

    private static final String AUTH_TOKEN_PREFIX = "tg_auth_";
    private static final String CHARACTERS = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789";
    private static final int TOKEN_LENGTH = 20; // Длина случайной части
    private static final SecureRandom RANDOM = new SecureRandom();

    // Паттерн для валидации токена
    private static final Pattern TOKEN_PATTERN = Pattern
            .compile("^" + AUTH_TOKEN_PREFIX + "[A-Za-z0-9]{" + TOKEN_LENGTH + "}$");

    /**
     * Генерирует уникальный токен аутентификации
     * Формат: tg_auth_XXXXXXXXXXXXXXXXXX (28 символов)
     * 
     * @return новый токен аутентификации
     */
    public String generateAuthToken() {
        StringBuilder tokenBuilder = new StringBuilder(AUTH_TOKEN_PREFIX);

        for (int i = 0; i < TOKEN_LENGTH; i++) {
            int randomIndex = RANDOM.nextInt(CHARACTERS.length());
            tokenBuilder.append(CHARACTERS.charAt(randomIndex));
        }

        return tokenBuilder.toString();
    }

    /**
     * Проверяет валидность токена аутентификации
     * 
     * @param token токен для проверки
     * @return true если токен имеет правильный формат
     */
    public boolean isValidAuthToken(String token) {
        if (token == null || token.trim().isEmpty()) {
            return false;
        }

        return TOKEN_PATTERN.matcher(token.trim()).matches();
    }

    /**
     * Извлекает токен из команды /start в Telegram
     * Ожидается формат: /start tg_auth_XXXXXXXXXXXXXXXXXX
     * 
     * @param startCommand полная команда от Telegram
     * @return токен аутентификации или null если не найден
     */
    public String extractTokenFromStartCommand(String startCommand) {
        if (startCommand == null || startCommand.trim().isEmpty()) {
            return null;
        }

        // Убираем /start и лишние пробелы
        String cleanCommand = startCommand.trim();
        if (cleanCommand.startsWith("/start ")) {
            String potentialToken = cleanCommand.substring(7).trim(); // 7 = длина "/start "

            if (isValidAuthToken(potentialToken)) {
                return potentialToken;
            }
        }

        return null;
    }

    /**
     * Проверяет, является ли строка токеном аутентификации
     * 
     * @param text текст для проверки
     * @return true если текст является валидным auth токеном
     */
    public boolean isAuthToken(String text) {
        return isValidAuthToken(text);
    }

    /**
     * Получить префикс токенов аутентификации
     * 
     * @return префикс токенов
     */
    public String getAuthTokenPrefix() {
        return AUTH_TOKEN_PREFIX;
    }

    /**
     * Получить длину токена без префикса
     * 
     * @return длина случайной части токена
     */
    public int getTokenLength() {
        return TOKEN_LENGTH;
    }
}