package com.baganov.pizzanat.service;

import com.baganov.pizzanat.config.TelegramConfig;
import com.baganov.pizzanat.entity.Role;
import com.baganov.pizzanat.entity.TelegramAuthToken;
import com.baganov.pizzanat.entity.TelegramAuthToken.TokenStatus;
import com.baganov.pizzanat.entity.User;
import com.baganov.pizzanat.model.dto.auth.AuthResponse;
import com.baganov.pizzanat.model.dto.telegram.TelegramAuthResponse;
import com.baganov.pizzanat.model.dto.telegram.TelegramStatusResponse;
import com.baganov.pizzanat.model.dto.telegram.TelegramUserData;
import com.baganov.pizzanat.repository.RoleRepository;
import com.baganov.pizzanat.repository.TelegramAuthTokenRepository;
import com.baganov.pizzanat.repository.UserRepository;
import com.baganov.pizzanat.security.JwtService;
import com.baganov.pizzanat.service.RateLimitService.RateLimitType;
import com.baganov.pizzanat.util.TelegramUserDataExtractor;
import com.baganov.pizzanat.util.TokenGenerator;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Optional;
import java.util.Set;

/**
 * Сервис для Telegram аутентификации.
 * Следует принципам SOLID - Single Responsibility, Dependency Inversion.
 */
@Slf4j
@Service
@RequiredArgsConstructor
@Transactional
public class TelegramAuthService {

    private final TelegramAuthTokenRepository tokenRepository;
    private final UserRepository userRepository;
    private final RoleRepository roleRepository;
    private final JwtService jwtService;
    private final TokenGenerator tokenGenerator;
    private final TelegramUserDataExtractor userDataExtractor;
    private final RateLimitService rateLimitService;
    private final TelegramConfig.TelegramAuthProperties telegramAuthProperties;

    /**
     * Инициализация Telegram аутентификации
     *
     * @param deviceId ID устройства (опционально)
     * @return ответ с токеном и ссылкой на бот
     */
    public TelegramAuthResponse initAuth(String deviceId) {
        try {
            // Проверяем конфигурацию
            if (!telegramAuthProperties.isValid()) {
                log.error("Telegram аутентификация не настроена");
                return TelegramAuthResponse.error("Telegram аутентификация недоступна");
            }

            // Проверяем rate limiting
            String rateLimitKey = deviceId != null ? deviceId : "unknown";
            if (!rateLimitService.isAllowed(rateLimitKey, RateLimitType.TELEGRAM_INIT)) {
                log.warn("Rate limit превышен для устройства: {}", rateLimitKey);
                return TelegramAuthResponse.error("Слишком много попыток. Попробуйте позже");
            }

            // Генерируем токен
            String authToken = tokenGenerator.generateAuthToken();
            LocalDateTime expiresAt = LocalDateTime.now()
                    .plusMinutes(telegramAuthProperties.getTokenTtlMinutes());

            // Создаем запись в БД
            TelegramAuthToken token = TelegramAuthToken.builder()
                    .authToken(authToken)
                    .deviceId(deviceId)
                    .status(TokenStatus.PENDING)
                    .expiresAt(expiresAt)
                    .build();

            tokenRepository.save(token);

            // Записываем попытку для rate limiting
            rateLimitService.recordAttempt(rateLimitKey, RateLimitType.TELEGRAM_INIT);

            // Формируем URL для бота
            String telegramBotUrl = telegramAuthProperties.getStartAuthUrl(authToken);

            log.info("Создан Telegram auth токен: {} для устройства: {}", authToken, deviceId);

            return TelegramAuthResponse.success(authToken, telegramBotUrl, expiresAt);

        } catch (Exception e) {
            log.error("Ошибка при инициализации Telegram аутентификации: {}", e.getMessage(), e);
            return TelegramAuthResponse.error("Внутренняя ошибка сервера");
        }
    }

    /**
     * Проверка статуса Telegram аутентификации
     *
     * @param authToken токен аутентификации
     * @return статус аутентификации
     */
    @Transactional(readOnly = true)
    public TelegramStatusResponse checkAuthStatus(String authToken) {
        try {
            if (!tokenGenerator.isValidAuthToken(authToken)) {
                return TelegramStatusResponse.error("Некорректный токен");
            }

            Optional<TelegramAuthToken> tokenOpt = tokenRepository
                    .findByAuthTokenAndExpiresAtAfter(authToken, LocalDateTime.now());

            if (tokenOpt.isEmpty()) {
                return TelegramStatusResponse.expired();
            }

            TelegramAuthToken token = tokenOpt.get();

            switch (token.getStatus()) {
                case PENDING:
                    return TelegramStatusResponse.pending();

                case CONFIRMED:
                    // Если токен подтвержден, возвращаем данные аутентификации
                    Optional<User> userOpt = userRepository.findByTelegramId(token.getTelegramId());
                    if (userOpt.isPresent()) {
                        User user = userOpt.get();
                        String jwtToken = jwtService.generateToken(user);
                        AuthResponse authResponse = createAuthResponse(jwtToken, user);
                        return TelegramStatusResponse.confirmed(authResponse);
                    } else {
                        log.error("Пользователь не найден для подтвержденного токена: {}", authToken);
                        return TelegramStatusResponse.error("Пользователь не найден");
                    }

                case EXPIRED:
                default:
                    return TelegramStatusResponse.expired();
            }

        } catch (Exception e) {
            log.error("Ошибка при проверке статуса Telegram токена {}: {}", authToken, e.getMessage(), e);
            return TelegramStatusResponse.error("Внутренняя ошибка сервера");
        }
    }

    /**
     * Подтверждение аутентификации от Telegram webhook
     *
     * @param authToken токен аутентификации
     * @param userData  данные пользователя от Telegram
     * @return результат аутентификации
     */
    public AuthResponse confirmAuth(String authToken, TelegramUserData userData) {
        try {
            log.info("Начало подтверждения авторизации для токена: {} и пользователя: {}", authToken, userData.getId());

            // Валидация входных данных
            if (!tokenGenerator.isValidAuthToken(authToken)) {
                log.error("Некорректный формат токена: {}", authToken);
                throw new IllegalArgumentException("Некорректный токен аутентификации");
            }
            log.debug("Токен прошел валидацию: {}", authToken);

            if (!userDataExtractor.isValidUserData(userData)) {
                log.error("Некорректные данные пользователя: {}", userData);
                throw new IllegalArgumentException("Некорректные данные пользователя Telegram");
            }
            log.debug("Данные пользователя прошли валидацию: {}", userData.getId());

            // Поиск токена
            log.debug("Поиск токена в БД: authToken={}, status=PENDING, currentTime={}",
                    authToken, LocalDateTime.now());

            Optional<TelegramAuthToken> tokenOpt = tokenRepository
                    .findByAuthTokenAndStatusAndExpiresAtAfter(authToken, TokenStatus.PENDING, LocalDateTime.now());

            if (tokenOpt.isEmpty()) {
                log.error("Токен не найден в БД: {} (статус PENDING, не истекший)", authToken);
                // Дополнительная диагностика
                Optional<TelegramAuthToken> anyTokenOpt = tokenRepository.findByAuthToken(authToken);
                if (anyTokenOpt.isPresent()) {
                    TelegramAuthToken existingToken = anyTokenOpt.get();
                    log.error("Токен найден, но не подходит: статус={}, истекает={}, текущее время={}",
                            existingToken.getStatus(), existingToken.getExpiresAt(), LocalDateTime.now());
                } else {
                    log.error("Токен вообще не найден в БД: {}", authToken);
                }
                throw new IllegalArgumentException("Токен не найден или истек");
            }

            log.info("Токен найден успешно: {}", authToken);

            TelegramAuthToken token = tokenOpt.get();
            log.debug("Токен получен из БД: id={}, status={}, telegramId={}",
                    token.getId(), token.getStatus(), token.getTelegramId());

            // Поиск или создание пользователя
            log.debug("Поиск или создание пользователя для Telegram ID: {}", userData.getId());
            User user = findOrCreateUser(userData);
            log.info("Пользователь найден/создан: id={}, username={}", user.getId(), user.getUsername());

            // Обновляем токен
            log.debug("Обновление токена данными пользователя");
            token.setTelegramId(userData.getId());
            token.setTelegramUsername(userData.getUsername());
            token.setTelegramFirstName(userData.getFirstName());
            token.setTelegramLastName(userData.getLastName());
            token.confirm();
            log.debug("Токен подтвержден, новый статус: {}, confirmedAt: {}",
                    token.getStatus(), token.getConfirmedAt());

            TelegramAuthToken savedToken = tokenRepository.save(token);
            log.info("Токен сохранен в БД: id={}, status={}", savedToken.getId(), savedToken.getStatus());

            // Генерируем JWT токен
            log.debug("Генерация JWT токена для пользователя: {}", user.getUsername());
            String jwtToken = jwtService.generateToken(user);
            log.debug("JWT токен сгенерирован успешно");

            log.info("Telegram аутентификация подтверждена для пользователя: {} ({})",
                    user.getUsername(), userData.getId());

            return createAuthResponse(jwtToken, user);

        } catch (Exception e) {
            log.error("Ошибка при подтверждении Telegram аутентификации: {}", e.getMessage(), e);
            throw new RuntimeException("Ошибка подтверждения аутентификации: " + e.getMessage());
        }
    }

    /**
     * Поиск или создание пользователя по данным Telegram
     *
     * @param userData данные пользователя от Telegram
     * @return пользователь
     */
    private User findOrCreateUser(TelegramUserData userData) {
        // Сначала ищем по Telegram ID
        Optional<User> existingUser = userRepository.findByTelegramId(userData.getId());

        if (existingUser.isPresent()) {
            // Обновляем существующего пользователя
            User user = existingUser.get();
            userDataExtractor.updateUserWithTelegramData(user, userData);
            return userRepository.save(user);
        }

        // Создаем нового пользователя
        User newUser = userDataExtractor.createUserFromTelegramData(userData);

        // Добавляем роль пользователя
        addDefaultRole(newUser);

        return userRepository.save(newUser);
    }

    /**
     * Добавляет роль пользователя по умолчанию
     *
     * @param user пользователь
     */
    private void addDefaultRole(User user) {
        Role userRole = roleRepository.findByName("ROLE_USER")
                .orElseThrow(() -> new IllegalStateException("Роль ROLE_USER не найдена"));
        user.setRoles(Set.of(userRole));
        user.setActive(true);
    }

    /**
     * Создает AuthResponse из JWT токена и пользователя
     *
     * @param jwtToken JWT токен
     * @param user     пользователь
     * @return AuthResponse
     */
    private AuthResponse createAuthResponse(String jwtToken, User user) {
        return AuthResponse.builder()
                .token(jwtToken)
                .username(user.getUsername())
                .email(user.getEmail())
                .firstName(user.getFirstName())
                .lastName(user.getLastName())
                .build();
    }

    /**
     * Очистка истекших токенов (вызывается по расписанию)
     */
    @Transactional
    public void cleanupExpiredTokens() {
        try {
            LocalDateTime cutoff = LocalDateTime.now();

            // Помечаем истекшие PENDING токены как EXPIRED
            int markedExpired = tokenRepository.markExpiredTokens(cutoff);

            // Удаляем старые токены (старше 24 часов)
            LocalDateTime oldCutoff = cutoff.minusHours(24);
            tokenRepository.deleteByExpiresAtBefore(oldCutoff);

            if (markedExpired > 0) {
                log.info("Помечено как истекшие {} Telegram токенов", markedExpired);
            }
        } catch (Exception e) {
            log.error("Ошибка при очистке истекших Telegram токенов: {}", e.getMessage(), e);
        }
    }

    /**
     * Обновление пользователя номером телефона из Telegram
     *
     * @param userData данные пользователя с номером телефона
     */
    @Transactional
    public void updateUserWithPhoneNumber(TelegramUserData userData) {
        try {
            if (userData == null || userData.getId() == null) {
                throw new IllegalArgumentException("Некорректные данные пользователя");
            }

            // Ищем пользователя по Telegram ID
            Optional<User> userOpt = userRepository.findByTelegramId(userData.getId());

            if (userOpt.isEmpty()) {
                log.warn("Пользователь с Telegram ID {} не найден для обновления номера телефона", userData.getId());
                return;
            }

            User user = userOpt.get();

            // Обновляем номер телефона если он предоставлен
            if (userData.getPhoneNumber() != null && !userData.getPhoneNumber().trim().isEmpty()) {
                user.setPhoneNumber(userData.getPhoneNumber().trim());
                user.setIsPhoneVerified(true);
                log.info("Обновлен номер телефона для пользователя {} (Telegram ID: {})",
                        user.getUsername(), userData.getId());
            }

            // Обновляем другие данные если они предоставлены
            if (userData.getFirstName() != null && user.getFirstName() == null) {
                user.setFirstName(userData.getFirstName().trim());
            }

            if (userData.getLastName() != null && user.getLastName() == null) {
                user.setLastName(userData.getLastName().trim());
            }

            user.setUpdatedAt(LocalDateTime.now());
            userRepository.save(user);

        } catch (Exception e) {
            log.error("Ошибка при обновлении пользователя номером телефона: {}", e.getMessage(), e);
            throw new RuntimeException("Ошибка обновления данных пользователя: " + e.getMessage());
        }
    }
}