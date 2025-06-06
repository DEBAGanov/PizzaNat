package com.baganov.pizzanat.util;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.security.SecureRandom;
import java.util.regex.Pattern;

/**
 * Генератор SMS кодов для аутентификации.
 * Следует принципу Single Responsibility из SOLID.
 */
@Component
public class SmsCodeGenerator {

    private static final SecureRandom SECURE_RANDOM = new SecureRandom();

    @Value("${sms.code.length:4}")
    private Integer codeLength;

    // Паттерн для валидации кода (только цифры)
    private Pattern codePattern;

    /**
     * Инициализация паттерна на основе настроек
     */
    private Pattern getCodePattern() {
        if (codePattern == null) {
            codePattern = Pattern.compile("^\\d{" + codeLength + "}$");
        }
        return codePattern;
    }

    /**
     * Генерирует случайный числовой код заданной длины
     * 
     * @return сгенерированный код
     */
    public String generateCode() {
        StringBuilder code = new StringBuilder();

        for (int i = 0; i < codeLength; i++) {
            // Генерируем цифру от 0 до 9
            int digit = SECURE_RANDOM.nextInt(10);
            code.append(digit);
        }

        return code.toString();
    }

    /**
     * Генерирует код, исключающий простые последовательности
     * (например, 1111, 1234, 0000)
     * 
     * @return безопасный сгенерированный код
     */
    public String generateSecureCode() {
        String code;
        int attempts = 0;
        int maxAttempts = 50; // Предотвращение бесконечного цикла

        do {
            code = generateCode();
            attempts++;
        } while (isWeakCode(code) && attempts < maxAttempts);

        // Если не удалось сгенерировать безопасный код за 50 попыток,
        // возвращаем обычный код
        return code;
    }

    /**
     * Проверяет, является ли код валидным
     * 
     * @param code код для проверки
     * @return true если код валидный
     */
    public boolean isValidCode(String code) {
        if (code == null || code.trim().isEmpty()) {
            return false;
        }

        return getCodePattern().matcher(code.trim()).matches();
    }

    /**
     * Проверяет, является ли код "слабым" (легко угадываемым)
     * 
     * @param code код для проверки
     * @return true если код слабый
     */
    public boolean isWeakCode(String code) {
        if (!isValidCode(code)) {
            return true;
        }

        // Проверка на одинаковые цифры (1111, 0000, etc.)
        if (code.chars().allMatch(c -> c == code.charAt(0))) {
            return true;
        }

        // Проверка на последовательность (1234, 4321, etc.)
        if (isSequential(code)) {
            return true;
        }

        // Проверка на популярные комбинации
        String[] weakCodes = { "1234", "4321", "0123", "9876", "1111", "0000", "2222", "3333", "4444", "5555", "6666",
                "7777", "8888", "9999" };
        for (String weakCode : weakCodes) {
            if (code.equals(weakCode)) {
                return true;
            }
        }

        return false;
    }

    /**
     * Проверяет, является ли код последовательностью
     * 
     * @param code код для проверки
     * @return true если код является последовательностью
     */
    private boolean isSequential(String code) {
        if (code.length() < 2) {
            return false;
        }

        // Проверка возрастающей последовательности
        boolean ascending = true;
        boolean descending = true;

        for (int i = 1; i < code.length(); i++) {
            int current = Character.getNumericValue(code.charAt(i));
            int previous = Character.getNumericValue(code.charAt(i - 1));

            if (current != previous + 1) {
                ascending = false;
            }
            if (current != previous - 1) {
                descending = false;
            }
        }

        return ascending || descending;
    }

    /**
     * Нормализует код (убирает пробелы, приводит к верхнему регистру если нужно)
     * 
     * @param code исходный код
     * @return нормализованный код
     */
    public String normalizeCode(String code) {
        if (code == null) {
            return null;
        }

        return code.trim().replaceAll("\\s+", "");
    }

    /**
     * Маскирует код для логирования (безопасность)
     * 
     * @param code код для маскировки
     * @return замаскированный код (например, 12** для кода 1234)
     */
    public String maskCodeForLogging(String code) {
        if (!isValidCode(code)) {
            return "****";
        }

        if (code.length() <= 2) {
            return "*".repeat(code.length());
        }

        // Показываем первые 2 символа, остальные заменяем на *
        return code.substring(0, 2) + "*".repeat(code.length() - 2);
    }

    /**
     * Возвращает настроенную длину кода
     * 
     * @return длина кода
     */
    public Integer getCodeLength() {
        return codeLength;
    }

    /**
     * Проверяет, содержит ли строка только цифры
     * 
     * @param str строка для проверки
     * @return true если строка содержит только цифры
     */
    public boolean isNumeric(String str) {
        if (str == null || str.isEmpty()) {
            return false;
        }

        return str.chars().allMatch(Character::isDigit);
    }
}