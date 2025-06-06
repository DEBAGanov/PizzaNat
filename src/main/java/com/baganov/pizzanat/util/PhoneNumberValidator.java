package com.baganov.pizzanat.util;

import org.springframework.stereotype.Component;

import java.util.regex.Pattern;

/**
 * Валидатор номеров телефонов для SMS аутентификации.
 * Следует принципу Single Responsibility из SOLID.
 */
@Component
public class PhoneNumberValidator {

    // Паттерн для российских номеров: +7XXXXXXXXXX
    private static final Pattern RUSSIAN_PHONE_PATTERN = Pattern.compile("^\\+7\\d{10}$");

    // Паттерн для номеров без кода страны: 9XXXXXXXXX или 8XXXXXXXXX
    private static final Pattern RUSSIAN_PHONE_WITHOUT_CODE = Pattern.compile("^[89]\\d{9}$");

    // Паттерн для номеров с 8: 8XXXXXXXXXX
    private static final Pattern RUSSIAN_PHONE_WITH_8 = Pattern.compile("^8\\d{10}$");

    /**
     * Проверяет, является ли номер валидным российским номером
     * 
     * @param phoneNumber номер телефона для проверки
     * @return true если номер валидный
     */
    public boolean isValidRussianNumber(String phoneNumber) {
        if (phoneNumber == null || phoneNumber.trim().isEmpty()) {
            return false;
        }

        String normalized = normalizePhoneNumber(phoneNumber);
        return RUSSIAN_PHONE_PATTERN.matcher(normalized).matches();
    }

    /**
     * Нормализует номер телефона к формату +7XXXXXXXXXX
     * 
     * @param phoneNumber исходный номер телефона
     * @return нормализованный номер или исходный если нормализация невозможна
     */
    public String normalizePhoneNumber(String phoneNumber) {
        if (phoneNumber == null) {
            return null;
        }

        // Убираем все символы кроме цифр и +
        String cleaned = phoneNumber.replaceAll("[^+\\d]", "");

        // Если номер уже в формате +7XXXXXXXXXX
        if (RUSSIAN_PHONE_PATTERN.matcher(cleaned).matches()) {
            return cleaned;
        }

        // Если номер начинается с 8 и содержит 11 цифр: 8XXXXXXXXXX -> +7XXXXXXXXXX
        if (RUSSIAN_PHONE_WITH_8.matcher(cleaned).matches()) {
            return "+7" + cleaned.substring(1);
        }

        // Если номер начинается с 9 и содержит 10 цифр: 9XXXXXXXXX -> +79XXXXXXXXX
        if (RUSSIAN_PHONE_WITHOUT_CODE.matcher(cleaned).matches() && cleaned.startsWith("9")) {
            return "+7" + cleaned;
        }

        // Если номер содержит 10 цифр и начинается не с 9, добавляем +7
        if (cleaned.matches("^\\d{10}$") && !cleaned.startsWith("9")) {
            return "+7" + cleaned;
        }

        // Если номер начинается с 7 без + и содержит 11 цифр: 7XXXXXXXXXX ->
        // +7XXXXXXXXXX
        if (cleaned.matches("^7\\d{10}$")) {
            return "+" + cleaned;
        }

        // Возвращаем исходный номер, если нормализация невозможна
        return phoneNumber;
    }

    /**
     * Форматирует номер для отображения пользователю
     * 
     * @param phoneNumber номер телефона в формате +7XXXXXXXXXX
     * @return отформатированный номер +7 (XXX) XXX-XX-XX
     */
    public String formatForDisplay(String phoneNumber) {
        if (!isValidRussianNumber(phoneNumber)) {
            return phoneNumber;
        }

        String normalized = normalizePhoneNumber(phoneNumber);
        if (normalized.length() == 12 && normalized.startsWith("+7")) {
            String digits = normalized.substring(2); // Убираем +7
            return String.format("+7 (%s) %s-%s-%s",
                    digits.substring(0, 3), // XXX
                    digits.substring(3, 6), // XXX
                    digits.substring(6, 8), // XX
                    digits.substring(8, 10) // XX
            );
        }

        return phoneNumber;
    }

    /**
     * Извлекает "чистый" номер для работы с API (только цифры)
     * 
     * @param phoneNumber номер телефона
     * @return номер без символов форматирования
     */
    public String extractDigitsOnly(String phoneNumber) {
        if (phoneNumber == null) {
            return null;
        }
        return phoneNumber.replaceAll("[^\\d]", "");
    }

    /**
     * Маскирует номер телефона для логирования (безопасность)
     * 
     * @param phoneNumber номер телефона
     * @return замаскированный номер +7 (XXX) ***-**-XX
     */
    public String maskForLogging(String phoneNumber) {
        if (!isValidRussianNumber(phoneNumber)) {
            return "***";
        }

        String normalized = normalizePhoneNumber(phoneNumber);
        if (normalized.length() == 12 && normalized.startsWith("+7")) {
            String digits = normalized.substring(2); // Убираем +7
            return String.format("+7 (%s) ***-**-%s",
                    digits.substring(0, 3), // Показываем код региона
                    digits.substring(8, 10) // Показываем последние 2 цифры
            );
        }

        return "***";
    }

    /**
     * Проверяет, относится ли номер к московскому региону
     * 
     * @param phoneNumber номер телефона
     * @return true если номер московский
     */
    public boolean isMoscowNumber(String phoneNumber) {
        if (!isValidRussianNumber(phoneNumber)) {
            return false;
        }

        String normalized = normalizePhoneNumber(phoneNumber);
        if (normalized.length() == 12 && normalized.startsWith("+7")) {
            String code = normalized.substring(2, 5); // Первые 3 цифры после +7
            // Московские коды: 495, 499, 903, 905, 906, 909, 915, 916, 917, 925, 926, 929,
            // 965, 977, 985, 991, 993, 994, 996, 997, 999
            return code.equals("495") || code.equals("499") ||
                    code.equals("903") || code.equals("905") || code.equals("906") ||
                    code.equals("909") || code.equals("915") || code.equals("916") ||
                    code.equals("917") || code.equals("925") || code.equals("926") ||
                    code.equals("929") || code.equals("965") || code.equals("977") ||
                    code.equals("985") || code.equals("991") || code.equals("993") ||
                    code.equals("994") || code.equals("996") || code.equals("997") ||
                    code.equals("999");
        }

        return false;
    }
}