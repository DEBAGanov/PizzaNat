package com.baganov.pizzanat.service;

import com.baganov.pizzanat.config.MaxBotConfig;
import com.baganov.pizzanat.entity.User;
import com.baganov.pizzanat.model.dto.auth.AuthResponse;
import com.baganov.pizzanat.model.dto.max.MaxWebAppAuthRequest;
import com.baganov.pizzanat.model.dto.max.MaxWebAppInitData;
import com.baganov.pizzanat.model.dto.max.MaxWebAppUser;
import com.baganov.pizzanat.repository.UserRepository;
import com.baganov.pizzanat.security.JwtService;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Сервис для работы с MAX WebApp
 *
 * Обеспечивает:
 * - Валидацию initData от MAX Mini App
 * - Авторизацию пользователей MAX
 * - Интеграцию с существующей системой пользователей
 *
 * Документация MAX: https://dev.max.ru/docs/webapps/validation
 *
 * ВАЖНО: Использует существующие поля таблицы users:
 * - telegramId -> хранит MAX ID (ID не пересекаются между платформами)
 * - telegramUsername -> хранит username MAX с префиксом "max_"
 * - isTelegramVerified -> true для верифицированных MAX пользователей
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class MaxWebAppService {

    private final MaxBotConfig maxBotConfig;
    private final UserRepository userRepository;
    private final JwtService jwtService;
    private final ObjectMapper objectMapper;

    // Префикс для различения MAX пользователей от Telegram
    private static final String MAX_USERNAME_PREFIX = "max_";
    private static final String MAX_USER_PREFIX = "max_user_";

    /**
     * Авторизация пользователя через MAX WebApp
     *
     * @param request запрос с initData
     * @return AuthResponse с JWT токеном
     */
    @Transactional
    public AuthResponse authenticateUser(MaxWebAppAuthRequest request) {
        log.info("Начало авторизации пользователя MAX WebApp");

        // 1. Парсим initData
        MaxWebAppInitData initData = parseInitData(request.getInitDataRaw());
        if (initData == null || initData.getUser() == null) {
            throw new IllegalArgumentException("Некорректные данные от MAX WebApp");
        }

        // 2. Валидируем initData
        if (!validateInitData(request.getInitDataRaw())) {
            throw new IllegalArgumentException("Ошибка валидации данных от MAX WebApp");
        }

        // 3. Проверяем актуальность данных (не старше 24 часов)
        if (initData.isExpired()) {
            throw new IllegalArgumentException("Данные авторизации устарели");
        }

        // 4. Находим или создаем пользователя
        MaxWebAppUser maxUser = initData.getUser();
        User user = findOrCreateMaxUser(maxUser, request.getPhoneNumber());

        // 5. Генерируем JWT токен
        String token = jwtService.generateToken(user);

        log.info("Пользователь MAX успешно авторизован: userId={}, maxId={}",
                user.getId(), maxUser.getId());

        return AuthResponse.builder()
                .token(token)
                .userId(user.getId())
                .username(user.getUsername())
                .email(user.getEmail())
                .firstName(user.getFirstName())
                .lastName(user.getLastName())
                .build();
    }

    /**
     * Валидация MAX initData
     *
     * Алгоритм согласно документации MAX:
     * 1. Парсим query string, извлекаем hash
     * 2. Строим data-check-string (все параметры кроме hash, по алфавиту)
     * 3. Вычисляем secret_key = HMAC_SHA256("WebAppData", botToken)
     * 4. Вычисляем computed_hash = HMAC_SHA256(data-check-string, secret_key)
     * 5. Сравниваем computed_hash с hash из initData
     *
     * @param initDataRaw сырая строка initData
     * @return true если валидация прошла успешно
     */
    public boolean validateInitData(String initDataRaw) {
        try {
            if (initDataRaw == null || initDataRaw.isEmpty()) {
                log.error("initDataRaw пуст");
                return false;
            }

            // 1. Парсим параметры
            Map<String, String> params = parseQueryString(initDataRaw);
            String hash = params.get("hash");

            if (hash == null || hash.isEmpty()) {
                log.error("Отсутствует hash в MAX initData");
                return false;
            }

            // 2. Получаем bot token
            String botToken = maxBotConfig.getUserBotToken();
            if (botToken == null || botToken.isEmpty()) {
                log.error("MAX Bot token не настроен");
                return false;
            }

            // 3. Формируем data-check-string (без hash, по алфавиту)
            String dataCheckString = buildDataCheckString(params);
            log.debug("MAX data check string: {}", dataCheckString);

            // 4. Вычисляем secret key
            // MAX: HMAC_SHA256("WebAppData", botToken)
            // Отличие от Telegram: нет конкатенации "WebAppData" + botToken
            byte[] secretKey = computeHMAC("WebAppData", botToken.getBytes(StandardCharsets.UTF_8));

            // 5. Вычисляем hash данных
            String computedHash = computeHMACHex(dataCheckString, secretKey);

            // 6. Сравниваем хеши
            boolean isValid = computedHash.equals(hash);

            if (!isValid) {
                log.warn("Неверный hash MAX. Ожидался: {}, получен: {}", computedHash, hash);
            } else {
                log.debug("MAX initData валидация успешна");
            }

            return isValid;

        } catch (Exception e) {
            log.error("Ошибка валидации MAX initData: {}", e.getMessage(), e);
            return false;
        }
    }

    /**
     * Парсинг initData строки в объект
     */
    public MaxWebAppInitData parseInitData(String initDataRaw) {
        try {
            Map<String, String> params = parseQueryString(initDataRaw);

            MaxWebAppInitData initData = new MaxWebAppInitData();
            initData.setQueryId(params.get("query_id"));
            initData.setHash(params.get("hash"));
            initData.setStartParam(params.get("start_param"));
            initData.setChatType(params.get("chat_type"));
            initData.setChatInstance(params.get("chat_instance"));

            // Парсим auth_date
            if (params.containsKey("auth_date")) {
                try {
                    initData.setAuthDate(Long.valueOf(params.get("auth_date")));
                } catch (NumberFormatException e) {
                    log.warn("Некорректная auth_date: {}", params.get("auth_date"));
                }
            }

            // Парсим пользователя из JSON
            if (params.containsKey("user")) {
                String userJson = params.get("user");
                MaxWebAppUser user = parseUserJson(userJson);
                initData.setUser(user);
            }

            return initData;

        } catch (Exception e) {
            log.error("Ошибка парсинга MAX initData: {}", e.getMessage(), e);
            return null;
        }
    }

    /**
     * Поиск или создание пользователя MAX
     *
     * Использует существующие поля:
     * - telegramId -> MAX ID
     * - telegramUsername -> "max_" + username
     * - isTelegramVerified -> true
     */
    @Transactional
    public User findOrCreateMaxUser(MaxWebAppUser maxUser, String phoneNumber) {
        Long maxId = maxUser.getId();
        log.debug("Поиск пользователя по MAX ID: {}", maxId);

        // Ищем по telegramId (используем это поле для MAX ID тоже)
        Optional<User> existingUser = userRepository.findByTelegramId(maxId);

        if (existingUser.isPresent()) {
            User user = existingUser.get();
            log.info("Найден существующий пользователь: {}", user.getId());

            // Обновляем данные пользователя
            updateMaxUserFromWebApp(user, maxUser, phoneNumber);
            return userRepository.save(user);
        }

        // Создаем нового пользователя
        log.info("Создание нового пользователя для MAX ID: {}", maxId);

        User newUser = User.builder()
                .username(generateMaxUsername(maxUser))
                .password(UUID.randomUUID().toString()) // Будет захешировано
                .firstName(maxUser.getFirstName())
                .lastName(maxUser.getLastName())
                // Используем существующие поля для MAX
                .telegramId(maxId)
                .telegramUsername(buildMaxTelegramUsername(maxUser))
                .isTelegramVerified(true) // MAX пользователь верифицирован
                .isActive(true)
                .createdAt(LocalDateTime.now())
                .updatedAt(LocalDateTime.now())
                .build();

        // Добавляем телефон если предоставлен
        if (phoneNumber != null && !phoneNumber.trim().isEmpty()) {
            String formattedPhone = formatPhoneNumber(phoneNumber);
            if (formattedPhone != null) {
                newUser.setPhone(formattedPhone);
                newUser.setIsPhoneVerified(true);
            }
        }

        User savedUser = userRepository.save(newUser);
        log.info("Создан новый MAX пользователь: {}", savedUser.getId());

        return savedUser;
    }

    // ==================== Приватные методы ====================

    /**
     * Парсинг query string в Map
     */
    private Map<String, String> parseQueryString(String queryString) {
        Map<String, String> params = new HashMap<>();
        if (queryString == null || queryString.isEmpty()) {
            return params;
        }

        String[] pairs = queryString.split("&");
        for (String pair : pairs) {
            String[] keyValue = pair.split("=", 2);
            if (keyValue.length == 2) {
                try {
                    String key = URLDecoder.decode(keyValue[0], StandardCharsets.UTF_8);
                    String value = URLDecoder.decode(keyValue[1], StandardCharsets.UTF_8);
                    params.put(key, value);
                } catch (Exception e) {
                    log.warn("Ошибка декодирования параметра: {}", pair);
                }
            }
        }

        return params;
    }

    /**
     * Построение data-check-string (без hash, по алфавиту)
     */
    private String buildDataCheckString(Map<String, String> params) {
        return params.entrySet().stream()
                .filter(entry -> !"hash".equals(entry.getKey()))
                .sorted(Map.Entry.comparingByKey())
                .map(entry -> entry.getKey() + "=" + entry.getValue())
                .collect(Collectors.joining("\n"));
    }

    /**
     * Парсинг JSON пользователя
     */
    private MaxWebAppUser parseUserJson(String userJson) {
        try {
            return objectMapper.readValue(userJson, MaxWebAppUser.class);
        } catch (Exception e) {
            log.error("Ошибка парсинга MAX user JSON: {}", e.getMessage());
            return null;
        }
    }

    /**
     * Вычисление HMAC-SHA256
     */
    private byte[] computeHMAC(String data, byte[] key) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            SecretKeySpec secretKeySpec = new SecretKeySpec(key, "HmacSHA256");
            mac.init(secretKeySpec);
            return mac.doFinal(data.getBytes(StandardCharsets.UTF_8));
        } catch (Exception e) {
            throw new RuntimeException("Ошибка вычисления HMAC", e);
        }
    }

    /**
     * Вычисление HMAC-SHA256 с возвратом hex строки
     */
    private String computeHMACHex(String data, byte[] key) {
        byte[] hash = computeHMAC(data, key);
        StringBuilder sb = new StringBuilder();
        for (byte b : hash) {
            sb.append(String.format("%02x", b));
        }
        return sb.toString();
    }

    /**
     * Генерация username для нового MAX пользователя
     */
    private String generateMaxUsername(MaxWebAppUser maxUser) {
        if (maxUser.getUsername() != null && !maxUser.getUsername().isEmpty()) {
            return MAX_USERNAME_PREFIX + maxUser.getUsername();
        }
        return MAX_USER_PREFIX + maxUser.getId();
    }

    /**
     * Построение telegramUsername для MAX пользователя
     */
    private String buildMaxTelegramUsername(MaxWebAppUser maxUser) {
        if (maxUser.getUsername() != null && !maxUser.getUsername().isEmpty()) {
            return MAX_USERNAME_PREFIX + maxUser.getUsername();
        }
        return null;
    }

    /**
     * Обновление данных пользователя из MAX WebApp
     */
    private void updateMaxUserFromWebApp(User user, MaxWebAppUser maxUser, String phoneNumber) {
        boolean updated = false;

        if (maxUser.getFirstName() != null &&
            !maxUser.getFirstName().equals(user.getFirstName())) {
            user.setFirstName(maxUser.getFirstName());
            updated = true;
        }

        if (maxUser.getLastName() != null &&
            !maxUser.getLastName().equals(user.getLastName())) {
            user.setLastName(maxUser.getLastName());
            updated = true;
        }

        String maxUsername = buildMaxTelegramUsername(maxUser);
        if (maxUsername != null &&
            !maxUsername.equals(user.getTelegramUsername())) {
            user.setTelegramUsername(maxUsername);
            updated = true;
        }

        // Обновляем телефон если предоставлен
        if (phoneNumber != null && !phoneNumber.trim().isEmpty()) {
            String formattedPhone = formatPhoneNumber(phoneNumber);
            if (formattedPhone != null &&
                !formattedPhone.equals(user.getPhone())) {
                user.setPhone(formattedPhone);
                user.setIsPhoneVerified(true);
                updated = true;
            }
        }

        if (!user.getIsTelegramVerified()) {
            user.setIsTelegramVerified(true);
            updated = true;
        }

        if (updated) {
            user.setUpdatedAt(LocalDateTime.now());
            log.debug("Обновлены данные пользователя {} из MAX", user.getId());
        }
    }

    /**
     * Форматирование номера телефона в +7 формат
     */
    private String formatPhoneNumber(String phoneNumber) {
        if (phoneNumber == null || phoneNumber.trim().isEmpty()) {
            return null;
        }

        String cleanPhone = phoneNumber.replaceAll("[^0-9]", "");
        log.debug("Форматирование номера: '{}' -> цифры: '{}'", phoneNumber, cleanPhone);

        if (cleanPhone.startsWith("7") && cleanPhone.length() == 11) {
            return "+" + cleanPhone;
        } else if (cleanPhone.startsWith("8") && cleanPhone.length() == 11) {
            return "+7" + cleanPhone.substring(1);
        } else if (cleanPhone.length() == 10) {
            return "+7" + cleanPhone;
        }

        log.warn("Неизвестный формат номера телефона: '{}' (цифр: {})", phoneNumber, cleanPhone.length());
        return null;
    }
}
