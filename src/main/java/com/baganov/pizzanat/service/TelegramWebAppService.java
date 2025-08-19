/**
 * @file: TelegramWebAppService.java
 * @description: Сервис для обработки Telegram Mini App авторизации и валидации
 * @dependencies: AuthService, UserService, JwtService, TelegramConfig
 * @created: 2025-01-23
 */
package com.baganov.pizzanat.service;

import com.baganov.pizzanat.config.TelegramConfig;
import com.baganov.pizzanat.entity.TelegramAuthToken;
import com.baganov.pizzanat.entity.User;
import com.baganov.pizzanat.model.dto.auth.AuthResponse;
import com.baganov.pizzanat.model.dto.telegram.TelegramWebAppInitData;
import com.baganov.pizzanat.model.dto.telegram.TelegramWebAppUser;
import com.baganov.pizzanat.repository.TelegramAuthTokenRepository;
import com.baganov.pizzanat.repository.UserRepository;
import com.baganov.pizzanat.security.JwtService;
import com.baganov.pizzanat.util.TokenGenerator;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.*;

@Slf4j
@Service
@RequiredArgsConstructor
public class TelegramWebAppService {

    private final UserRepository userRepository;
    private final TelegramAuthTokenRepository telegramAuthTokenRepository;
    private final JwtService jwtService;
    private final PasswordEncoder passwordEncoder;
    private final TokenGenerator tokenGenerator;
    private final TelegramConfig.TelegramBotProperties telegramBotProperties;

    /**
     * Авторизация пользователя через Telegram WebApp (из сырой строки)
     */
    @Transactional
    public AuthResponse authenticateUser(String initDataRaw) {
        log.info("Начало авторизации пользователя Telegram WebApp");

        // 1. Парсинг данных от Telegram
        TelegramWebAppInitData initData = parseInitData(initDataRaw);
        
        // 2. Валидация данных от Telegram
        if (!validateInitDataRaw(initDataRaw)) {
            throw new IllegalArgumentException("Некорректные данные от Telegram WebApp");
        }

        // 3. Проверка времени авторизации (не старше 24 часов)
        if (initData.getAuthDate() != null) {
            long currentTime = Instant.now().getEpochSecond();
            long authTime = initData.getAuthDate();
            if (currentTime - authTime > 86400) { // 24 часа
                throw new IllegalArgumentException("Данные авторизации устарели");
            }
        }

        // 4. Поиск или создание пользователя
        TelegramWebAppUser telegramUser = initData.getUser();
        User user = findOrCreateUser(telegramUser);

        // 5. Генерация JWT токена
        String token = jwtService.generateToken(user);

        log.info("Пользователь {} успешно авторизован через Telegram WebApp", user.getId());

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
     * Расширенная авторизация пользователя через Telegram WebApp с номером телефона
     * Создает токен в telegram_auth_tokens для кросс-платформенного доступа
     */
    @Transactional
    public AuthResponse enhancedAuthenticateUser(String initDataRaw, String phoneNumber, String deviceId) {
        log.info("Начало расширенной авторизации пользователя Telegram WebApp с номером телефона");

        // 1. Стандартная валидация и парсинг
        if (!validateInitDataRaw(initDataRaw)) {
            throw new IllegalArgumentException("Некорректные данные от Telegram WebApp");
        }

        TelegramWebAppInitData initData = parseInitData(initDataRaw);
        
        // 2. Проверка времени авторизации (не старше 24 часов)
        if (initData.getAuthDate() != null) {
            long currentTime = Instant.now().getEpochSecond();
            long authTime = initData.getAuthDate();
            if (currentTime - authTime > 86400) {
                throw new IllegalArgumentException("Данные авторизации устарели");
            }
        }

        // 3. Поиск или создание пользователя
        TelegramWebAppUser telegramUser = initData.getUser();
        User user = findOrCreateUser(telegramUser);

        // 4. Обновляем номер телефона если предоставлен
        if (phoneNumber != null && !phoneNumber.trim().isEmpty()) {
            String cleanPhone = phoneNumber.trim();
            log.info("Получен номер телефона для пользователя {}: {}", telegramUser.getId(), cleanPhone);
            
            // Валидация и форматирование номера телефона
            String formattedPhone = formatPhoneNumber(cleanPhone);
            if (formattedPhone != null) {
                user.setPhone(formattedPhone);
                user.setIsTelegramVerified(true);
                user.setIsPhoneVerified(true); // Добавляем флаг верификации телефона
                userRepository.save(user);
                log.info("✅ Номер телефона успешно сохранен для пользователя {}: {}", user.getId(), formattedPhone);
            } else {
                log.warn("⚠️ Некорректный номер телефона для пользователя {}: {}", user.getId(), cleanPhone);
                // Не прерываем авторизацию, но номер не сохраняем
            }
        }

        // 5. Создаем токен в telegram_auth_tokens для кросс-платформенного доступа
        String authToken = generateCrossplatformToken();
        saveTelegramAuthToken(authToken, telegramUser, deviceId);

        // 6. Генерация JWT токена
        String jwtToken = jwtService.generateToken(user);

        log.info("Пользователь {} успешно авторизован через расширенную Telegram WebApp авторизацию", user.getId());

        return AuthResponse.builder()
                .token(jwtToken)
                .userId(user.getId())
                .username(user.getUsername())
                .email(user.getEmail())
                .firstName(user.getFirstName())
                .lastName(user.getLastName())
                .build();
    }

    /**
     * Валидация сырых данных initData от Telegram WebApp
     */
    public boolean validateInitDataRaw(String initDataRaw) {
        try {
            if (initDataRaw == null || initDataRaw.isEmpty()) {
                log.error("initDataRaw пуст");
                return false;
            }

            // Парсим данные
            Map<String, String> params = parseQueryString(initDataRaw);
            String hash = params.get("hash");
            
            if (hash == null || hash.isEmpty()) {
                log.error("Отсутствует hash в initData");
                return false;
            }

            // Получаем bot token
            String botToken = telegramBotProperties.getBotToken();
            if (botToken == null || botToken.isEmpty()) {
                log.error("Bot token не настроен");
                return false;
            }

            // Формируем data-check-string согласно документации Telegram
            String dataCheckString = buildDataCheckStringFromParams(params);
            log.debug("Data check string: {}", dataCheckString);

            // Вычисляем секретный ключ
            byte[] secretKey = computeSecretKey(botToken);

            // Вычисляем HMAC-SHA256
            String computedHash = computeHMAC(dataCheckString, secretKey);

            // Сравниваем хеши
            boolean isValid = computedHash.equals(hash);
            
            if (!isValid) {
                log.warn("Неверный hash. Ожидался: {}, получен: {}", computedHash, hash);
            }

            return isValid;

        } catch (Exception e) {
            log.error("Ошибка валидации initData: {}", e.getMessage(), e);
            return false;
        }
    }

    /**
     * Парсинг initData строки в объект
     */
    public TelegramWebAppInitData parseInitData(String initDataRaw) {
        Map<String, String> params = parseQueryString(initDataRaw);
        
        TelegramWebAppInitData initData = new TelegramWebAppInitData();
        initData.setQueryId(params.get("query_id"));
        initData.setHash(params.get("hash"));
        initData.setStartParam(params.get("start_param"));
        initData.setChatType(params.get("chat_type"));
        initData.setChatInstance(params.get("chat_instance"));
        
        if (params.containsKey("auth_date")) {
            try {
                initData.setAuthDate(Long.valueOf(params.get("auth_date")));
            } catch (NumberFormatException e) {
                log.warn("Некорректная auth_date: {}", params.get("auth_date"));
            }
        }
        
        if (params.containsKey("user")) {
            initData.setUser(parseUser(params.get("user")));
        }
        
        return initData;
    }

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
                    String key = java.net.URLDecoder.decode(keyValue[0], StandardCharsets.UTF_8);
                    String value = java.net.URLDecoder.decode(keyValue[1], StandardCharsets.UTF_8);
                    params.put(key, value);
                } catch (Exception e) {
                    log.warn("Ошибка декодирования параметра: {}", pair);
                }
            }
        }
        
        return params;
    }

    /**
     * Парсинг JSON строки пользователя
     */
    private TelegramWebAppUser parseUser(String userJson) {
        // Простой парсер JSON для пользователя
        TelegramWebAppUser user = new TelegramWebAppUser();
        
        try {
            // Убираем фигурные скобки
            String content = userJson.replaceAll("^\\{|\\}$", "");
            
            // Парсим поля
            String[] fields = content.split(",");
            for (String field : fields) {
                String[] keyValue = field.split(":", 2);
                if (keyValue.length == 2) {
                    String key = keyValue[0].trim().replaceAll("\"", "");
                    String value = keyValue[1].trim().replaceAll("\"", "");
                    
                    switch (key) {
                        case "id":
                            user.setId(Long.valueOf(value));
                            break;
                        case "first_name":
                            user.setFirstName(value);
                            break;
                        case "last_name":
                            user.setLastName(value);
                            break;
                        case "username":
                            user.setUsername(value);
                            break;
                        case "language_code":
                            user.setLanguageCode(value);
                            break;
                        case "is_premium":
                            user.setIsPremium(Boolean.valueOf(value));
                            break;
                        case "photo_url":
                            user.setPhotoUrl(value);
                            break;
                        case "phone_number":
                            user.setPhoneNumber(value);
                            break;
                    }
                }
            }
        } catch (Exception e) {
            log.error("Ошибка парсинга пользователя: {}", e.getMessage());
        }
        
        return user;
    }

    /**
     * Поиск или создание пользователя по данным Telegram
     */
    @Transactional
    public User findOrCreateUser(TelegramWebAppUser telegramUser) {
        log.debug("Поиск пользователя по Telegram ID: {}", telegramUser.getId());

        // Ищем по telegramId
        Optional<User> existingUser = userRepository.findByTelegramId(telegramUser.getId());
        
        if (existingUser.isPresent()) {
            User user = existingUser.get();
            log.info("Найден существующий пользователь: {}", user.getId());
            
            // Обновляем данные пользователя
            updateUserFromTelegram(user, telegramUser);
            return userRepository.save(user);
        }

        // Создаем нового пользователя
        log.info("Создание нового пользователя для Telegram ID: {}", telegramUser.getId());
        
        User newUser = User.builder()
                .username(generateUsername(telegramUser))
                .password(passwordEncoder.encode(UUID.randomUUID().toString())) // Случайный пароль
                .firstName(telegramUser.getFirstName())
                .lastName(telegramUser.getLastName())
                .phoneNumber(telegramUser.getPhoneNumber()) // 🆕 Номер телефона если доступен
                .telegramId(telegramUser.getId())
                .telegramUsername(telegramUser.getUsername())
                .isTelegramVerified(true)
                .isPhoneVerified(telegramUser.getPhoneNumber() != null) // 🆕 Помечаем как верифицированный если есть номер
                .isActive(true)
                .createdAt(LocalDateTime.now())
                .updatedAt(LocalDateTime.now())
                .build();

        User savedUser = userRepository.save(newUser);
        log.info("Создан новый пользователь: {}", savedUser.getId());
        
        return savedUser;
    }

    /**
     * Формирование data-check-string из параметров (исключая hash)
     */
    private String buildDataCheckStringFromParams(Map<String, String> params) {
        return params.entrySet().stream()
                .filter(entry -> !"hash".equals(entry.getKey())) // Исключаем hash
                .sorted(Map.Entry.comparingByKey())
                .map(entry -> entry.getKey() + "=" + entry.getValue())
                .reduce((a, b) -> a + "\n" + b)
                .orElse("");
    }



    /**
     * Вычисление секретного ключа согласно документации Telegram
     */
    private byte[] computeSecretKey(String botToken) throws Exception {
        Mac sha256Hmac = Mac.getInstance("HmacSHA256");
        SecretKeySpec secretKey = new SecretKeySpec("WebAppData".getBytes(StandardCharsets.UTF_8), "HmacSHA256");
        sha256Hmac.init(secretKey);
        return sha256Hmac.doFinal(botToken.getBytes(StandardCharsets.UTF_8));
    }

    /**
     * Вычисление HMAC-SHA256
     */
    private String computeHMAC(String data, byte[] key) throws Exception {
        Mac mac = Mac.getInstance("HmacSHA256");
        SecretKeySpec secretKeySpec = new SecretKeySpec(key, "HmacSHA256");
        mac.init(secretKeySpec);
        
        byte[] hash = mac.doFinal(data.getBytes(StandardCharsets.UTF_8));
        
        // Конвертируем в hex строку
        StringBuilder sb = new StringBuilder();
        for (byte b : hash) {
            sb.append(String.format("%02x", b));
        }
        
        return sb.toString();
    }

    /**
     * Генерация username для нового пользователя
     */
    private String generateUsername(TelegramWebAppUser telegramUser) {
        if (telegramUser.getUsername() != null && !telegramUser.getUsername().isEmpty()) {
            return "tg_" + telegramUser.getUsername();
        }
        
        return "tg_user_" + telegramUser.getId();
    }

    /**
     * Обновление данных пользователя из Telegram
     */
    private void updateUserFromTelegram(User user, TelegramWebAppUser telegramUser) {
        boolean updated = false;
        
        if (telegramUser.getFirstName() != null && 
            !telegramUser.getFirstName().equals(user.getFirstName())) {
            user.setFirstName(telegramUser.getFirstName());
            updated = true;
        }
        
        if (telegramUser.getLastName() != null && 
            !telegramUser.getLastName().equals(user.getLastName())) {
            user.setLastName(telegramUser.getLastName());
            updated = true;
        }
        
        if (telegramUser.getUsername() != null && 
            !telegramUser.getUsername().equals(user.getTelegramUsername())) {
            user.setTelegramUsername(telegramUser.getUsername());
            updated = true;
        }
        
        // 🆕 Обновляем номер телефона если получен
        if (telegramUser.getPhoneNumber() != null && 
            !telegramUser.getPhoneNumber().equals(user.getPhoneNumber())) {
            user.setPhoneNumber(telegramUser.getPhoneNumber());
            user.setIsPhoneVerified(true);
            updated = true;
            log.info("Обновлен номер телефона для пользователя: {}", user.getId());
        }
        
        if (!user.getIsTelegramVerified()) {
            user.setIsTelegramVerified(true);
            updated = true;
        }
        
        if (updated) {
            user.setUpdatedAt(LocalDateTime.now());
            log.debug("Обновлены данные пользователя {} из Telegram", user.getId());
        }
    }

    /**
     * Генерирует токен для кросс-платформенного доступа
     */
    private String generateCrossplatformToken() {
        return tokenGenerator.generateAuthToken();
    }

    /**
     * Сохраняет токен в telegram_auth_tokens для кросс-платформенного доступа
     */
    private void saveTelegramAuthToken(String authToken, TelegramWebAppUser telegramUser, String deviceId) {
        try {
            TelegramAuthToken token = TelegramAuthToken.builder()
                    .authToken(authToken)
                    .telegramId(telegramUser.getId())
                    .telegramUsername(telegramUser.getUsername())
                    .telegramFirstName(telegramUser.getFirstName())
                    .telegramLastName(telegramUser.getLastName())
                    .deviceId(deviceId)
                    .status(TelegramAuthToken.TokenStatus.CONFIRMED)
                    .expiresAt(LocalDateTime.now().plusDays(30)) // Долгосрочный токен для кросс-платформенного доступа
                    .confirmedAt(LocalDateTime.now())
                    .createdAt(LocalDateTime.now())
                    .build();

            telegramAuthTokenRepository.save(token);
            log.info("Сохранен кросс-платформенный токен для пользователя {} (Telegram ID: {})", 
                    authToken, telegramUser.getId());

        } catch (Exception e) {
            log.error("Ошибка при сохранении кросс-платформенного токена: {}", e.getMessage(), e);
            // Не прерываем авторизацию из-за ошибки сохранения токена
        }
    }

    /**
     * Форматирование номера телефона в +7 формат
     */
    private String formatPhoneNumber(String phoneNumber) {
        if (phoneNumber == null || phoneNumber.trim().isEmpty()) {
            return phoneNumber;
        }

        // Убираем все символы кроме цифр
        String cleanPhone = phoneNumber.replaceAll("[^0-9]", "");

        log.info("📱 Форматирование номера: '{}' -> цифры: '{}'", phoneNumber, cleanPhone);

        // Обработка различных форматов
        String result = null;
        
        if (cleanPhone.startsWith("7") && cleanPhone.length() == 11) {
            // Формат: 79161234567 -> +79161234567
            result = "+" + cleanPhone;
            log.info("✅ Формат 7XXXXXXXXXX распознан: {}", result);
        } else if (cleanPhone.startsWith("8") && cleanPhone.length() == 11) {
            // Формат: 89161234567 -> +79161234567
            result = "+7" + cleanPhone.substring(1);
            log.info("✅ Формат 8XXXXXXXXXX распознан: {}", result);
        } else if (cleanPhone.length() == 10) {
            // Формат: 9161234567 -> +79161234567
            result = "+7" + cleanPhone;
            log.info("✅ Формат 10 цифр распознан: {}", result);
        } else if (cleanPhone.startsWith("37") && cleanPhone.length() == 12) {
            // Формат: 379161234567 -> +79161234567 (убираем 3)
            result = "+" + cleanPhone.substring(1);
            log.info("✅ Формат 37XXXXXXXXXX распознан: {}", result);
        }
        
        if (result != null) {
            return result;
        }

        // Если формат не распознан - возвращаем null для валидации
        log.error("❌ Неизвестный формат номера телефона: '{}' (цифр: {})", phoneNumber, cleanPhone.length());
        return null;
    }
}
