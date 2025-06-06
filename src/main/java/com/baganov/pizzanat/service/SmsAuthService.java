package com.baganov.pizzanat.service;

import com.baganov.pizzanat.entity.Role;
import com.baganov.pizzanat.entity.SmsCode;
import com.baganov.pizzanat.entity.User;
import com.baganov.pizzanat.repository.RoleRepository;
import com.baganov.pizzanat.repository.SmsCodeRepository;
import com.baganov.pizzanat.repository.UserRepository;
import com.baganov.pizzanat.security.JwtService;
import com.baganov.pizzanat.util.PhoneNumberValidator;
import com.baganov.pizzanat.util.SmsCodeGenerator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Optional;
import java.util.Set;

/**
 * Сервис для SMS аутентификации.
 * Следует принципам SOLID - Single Responsibility, Dependency Inversion.
 */
@Service
@Transactional
public class SmsAuthService {

    private static final Logger logger = LoggerFactory.getLogger(SmsAuthService.class);

    private final SmsCodeRepository smsCodeRepository;
    private final UserRepository userRepository;
    private final RoleRepository roleRepository;
    private final ExolveService exolveService;
    private final PhoneNumberValidator phoneValidator;
    private final SmsCodeGenerator smsCodeGenerator;
    private final RateLimitService rateLimitService;
    private final JwtService jwtService;
    private final PasswordEncoder passwordEncoder;

    @Value("${sms.code.ttl.minutes:10}")
    private Integer smsCodeTtlMinutes;

    @Value("${sms.max.attempts:3}")
    private Integer maxAttempts;

    public SmsAuthService(SmsCodeRepository smsCodeRepository,
            UserRepository userRepository,
            RoleRepository roleRepository,
            ExolveService exolveService,
            PhoneNumberValidator phoneValidator,
            SmsCodeGenerator smsCodeGenerator,
            RateLimitService rateLimitService,
            JwtService jwtService,
            PasswordEncoder passwordEncoder) {
        this.smsCodeRepository = smsCodeRepository;
        this.userRepository = userRepository;
        this.roleRepository = roleRepository;
        this.exolveService = exolveService;
        this.phoneValidator = phoneValidator;
        this.smsCodeGenerator = smsCodeGenerator;
        this.rateLimitService = rateLimitService;
        this.jwtService = jwtService;
        this.passwordEncoder = passwordEncoder;
    }

    /**
     * Отправляет SMS код на указанный номер телефона
     * 
     * @param phoneNumber номер телефона в любом формате
     * @return результат отправки кода
     */
    public SmsCodeResponse sendCode(String phoneNumber) {
        try {
            // Валидация и нормализация номера
            String normalizedPhone = phoneValidator.normalizePhoneNumber(phoneNumber);
            if (!phoneValidator.isValidRussianNumber(normalizedPhone)) {
                logger.warn("Попытка отправки SMS на некорректный номер: {}",
                        phoneValidator.maskForLogging(phoneNumber));
                return SmsCodeResponse.error("Некорректный формат номера телефона");
            }

            // Проверка rate limiting
            if (!rateLimitService.isAllowed(normalizedPhone, RateLimitService.RateLimitType.SMS_SEND)) {
                logger.warn("Rate limit превышен для номера: {}", phoneValidator.maskForLogging(normalizedPhone));
                return SmsCodeResponse.rateLimitExceeded(
                        rateLimitService.getRetryAfter(normalizedPhone, RateLimitService.RateLimitType.SMS_SEND));
            }

            // Деактивация предыдущих кодов
            markPreviousCodesAsUsed(normalizedPhone);

            // Генерация нового кода
            String code = smsCodeGenerator.generateSecureCode();
            LocalDateTime expiresAt = LocalDateTime.now().plusMinutes(smsCodeTtlMinutes);

            // Сохранение кода в БД
            SmsCode smsCode = SmsCode.builder()
                    .phoneNumber(normalizedPhone)
                    .code(code)
                    .expiresAt(expiresAt)
                    .build();

            smsCodeRepository.save(smsCode);

            // Отправка SMS
            String message = exolveService.generateSmsMessage(code);
            boolean smsSent = exolveService.sendSms(normalizedPhone, message);

            // Запись попытки rate limiting
            rateLimitService.recordAttempt(normalizedPhone, RateLimitService.RateLimitType.SMS_SEND);

            if (smsSent) {
                logger.info("SMS код успешно отправлен на номер: {}", phoneValidator.maskForLogging(normalizedPhone));
                return SmsCodeResponse.success(expiresAt, smsCodeGenerator.getCodeLength());
            } else {
                logger.error("Ошибка отправки SMS на номер: {}", phoneValidator.maskForLogging(normalizedPhone));
                return SmsCodeResponse.error("Ошибка отправки SMS. Попробуйте позже");
            }

        } catch (Exception e) {
            logger.error("Неожиданная ошибка при отправке SMS кода на номер {}: {}",
                    phoneValidator.maskForLogging(phoneNumber), e.getMessage(), e);
            return SmsCodeResponse.error("Внутренняя ошибка сервера");
        }
    }

    /**
     * Проверяет SMS код и выполняет аутентификацию
     * 
     * @param phoneNumber номер телефона
     * @param code        SMS код
     * @return результат аутентификации
     */
    public AuthResponse verifyCode(String phoneNumber, String code) {
        try {
            // Валидация входных данных
            String normalizedPhone = phoneValidator.normalizePhoneNumber(phoneNumber);
            if (!phoneValidator.isValidRussianNumber(normalizedPhone)) {
                logger.warn("Попытка верификации SMS кода для некорректного номера: {}",
                        phoneValidator.maskForLogging(phoneNumber));
                return AuthResponse.error("Некорректный формат номера телефона");
            }

            String normalizedCode = smsCodeGenerator.normalizeCode(code);
            if (!smsCodeGenerator.isValidCode(normalizedCode)) {
                logger.warn("Попытка верификации некорректного SMS кода для номера: {}",
                        phoneValidator.maskForLogging(normalizedPhone));
                return AuthResponse.error("Некорректный формат кода");
            }

            // Проверка rate limiting
            if (!rateLimitService.isAllowed(normalizedPhone, RateLimitService.RateLimitType.SMS_VERIFY)) {
                logger.warn("Rate limit превышен для верификации SMS кода для номера: {}",
                        phoneValidator.maskForLogging(normalizedPhone));
                return AuthResponse.rateLimitExceeded();
            }

            // Поиск действующего кода
            Optional<SmsCode> smsCodeOpt = smsCodeRepository.findByPhoneNumberAndCodeAndUsedFalseAndExpiresAtAfter(
                    normalizedPhone, normalizedCode, LocalDateTime.now());

            if (smsCodeOpt.isEmpty()) {
                // Запись неудачной попытки
                rateLimitService.recordAttempt(normalizedPhone, RateLimitService.RateLimitType.SMS_VERIFY);

                logger.warn("Неверный или истекший SMS код для номера: {} код: {}",
                        phoneValidator.maskForLogging(normalizedPhone),
                        smsCodeGenerator.maskCodeForLogging(normalizedCode));
                return AuthResponse.error("Неверный или истекший код");
            }

            SmsCode smsCodeEntity = smsCodeOpt.get();

            // Увеличение счетчика попыток
            smsCodeEntity.incrementAttempts();

            // Проверка максимального количества попыток
            if (smsCodeEntity.getAttempts() > maxAttempts) {
                smsCodeEntity.markAsUsed();
                smsCodeRepository.save(smsCodeEntity);

                logger.warn("Превышено максимальное количество попыток ввода SMS кода для номера: {}",
                        phoneValidator.maskForLogging(normalizedPhone));
                return AuthResponse.error("Превышено максимальное количество попыток. Запросите новый код");
            }

            // Помечаем код как использованный
            smsCodeEntity.markAsUsed();
            smsCodeRepository.save(smsCodeEntity);

            // Поиск или создание пользователя
            User user = findOrCreateUserByPhone(normalizedPhone);

            // Генерация JWT токена
            String token = jwtService.generateToken(user);

            // Очистка rate limiting для успешной аутентификации
            rateLimitService.clearAttempts(normalizedPhone);

            logger.info("Успешная SMS аутентификация для номера: {}", phoneValidator.maskForLogging(normalizedPhone));
            return AuthResponse.success(token, user);

        } catch (Exception e) {
            logger.error("Неожиданная ошибка при верификации SMS кода для номера {}: {}",
                    phoneValidator.maskForLogging(phoneNumber), e.getMessage(), e);
            return AuthResponse.error("Внутренняя ошибка сервера");
        }
    }

    /**
     * Помечает все предыдущие коды для номера как использованные
     */
    private void markPreviousCodesAsUsed(String phoneNumber) {
        var unusedCodes = smsCodeRepository.findByPhoneNumberAndUsedFalseOrderByCreatedAtDesc(phoneNumber);
        for (SmsCode code : unusedCodes) {
            code.markAsUsed();
        }
        if (!unusedCodes.isEmpty()) {
            smsCodeRepository.saveAll(unusedCodes);
            logger.debug("Деактивировано {} предыдущих кодов для номера: {}",
                    unusedCodes.size(), phoneValidator.maskForLogging(phoneNumber));
        }
    }

    /**
     * Находит существующего пользователя или создает нового
     */
    private User findOrCreateUserByPhone(String phoneNumber) {
        Optional<User> existingUser = userRepository.findByPhoneNumber(phoneNumber);

        if (existingUser.isPresent()) {
            User user = existingUser.get();

            // Обновляем статус верификации телефона
            if (!Boolean.TRUE.equals(user.getIsPhoneVerified())) {
                user.setIsPhoneVerified(true);
                userRepository.save(user);
                logger.info("Обновлен статус верификации телефона для пользователя: {}", user.getId());
            }

            return user;
        }

        // Создание нового пользователя
        String username = generateUsernameFromPhone(phoneNumber);

        // Получение роли USER
        Role userRole = roleRepository.findByName("ROLE_USER")
                .orElseThrow(() -> new RuntimeException("Роль ROLE_USER не найдена"));

        User newUser = User.builder()
                .username(username)
                .phoneNumber(phoneNumber)
                .password(passwordEncoder.encode(phoneNumber)) // Временный пароль
                .isPhoneVerified(true)
                .isActive(true)
                .roles(Set.of(userRole))
                .build();

        User savedUser = userRepository.save(newUser);
        logger.info("Создан новый пользователь через SMS аутентификацию: {} для номера: {}",
                savedUser.getId(), phoneValidator.maskForLogging(phoneNumber));

        return savedUser;
    }

    /**
     * Генерирует уникальный username из номера телефона
     */
    private String generateUsernameFromPhone(String phoneNumber) {
        // Базовый username из номера
        String baseUsername = "user" + phoneValidator.extractDigitsOnly(phoneNumber);

        // Проверка уникальности
        String username = baseUsername;
        int counter = 1;

        while (userRepository.existsByUsername(username)) {
            username = baseUsername + "_" + counter;
            counter++;
        }

        return username;
    }

    // === Response DTO классы ===

    /**
     * Ответ при отправке SMS кода
     */
    public static class SmsCodeResponse {
        private final boolean success;
        private final String message;
        private final LocalDateTime expiresAt;
        private final Integer codeLength;
        private final java.time.Duration retryAfter;

        private SmsCodeResponse(boolean success, String message, LocalDateTime expiresAt, Integer codeLength,
                java.time.Duration retryAfter) {
            this.success = success;
            this.message = message;
            this.expiresAt = expiresAt;
            this.codeLength = codeLength;
            this.retryAfter = retryAfter;
        }

        public static SmsCodeResponse success(LocalDateTime expiresAt, Integer codeLength) {
            return new SmsCodeResponse(true, "SMS код отправлен", expiresAt, codeLength, null);
        }

        public static SmsCodeResponse error(String message) {
            return new SmsCodeResponse(false, message, null, null, null);
        }

        public static SmsCodeResponse rateLimitExceeded(java.time.Duration retryAfter) {
            return new SmsCodeResponse(false, "Слишком много запросов. Повторите через некоторое время", null, null,
                    retryAfter);
        }

        // Геттеры
        public boolean isSuccess() {
            return success;
        }

        public String getMessage() {
            return message;
        }

        public LocalDateTime getExpiresAt() {
            return expiresAt;
        }

        public Integer getCodeLength() {
            return codeLength;
        }

        public java.time.Duration getRetryAfter() {
            return retryAfter;
        }
    }

    /**
     * Ответ при аутентификации
     */
    public static class AuthResponse {
        private final boolean success;
        private final String message;
        private final String token;
        private final UserInfo user;

        private AuthResponse(boolean success, String message, String token, UserInfo user) {
            this.success = success;
            this.message = message;
            this.token = token;
            this.user = user;
        }

        public static AuthResponse success(String token, User user) {
            return new AuthResponse(true, "Аутентификация успешна", token, new UserInfo(user));
        }

        public static AuthResponse error(String message) {
            return new AuthResponse(false, message, null, null);
        }

        public static AuthResponse rateLimitExceeded() {
            return new AuthResponse(false, "Слишком много попыток. Повторите позже", null, null);
        }

        // Геттеры
        public boolean isSuccess() {
            return success;
        }

        public String getMessage() {
            return message;
        }

        public String getToken() {
            return token;
        }

        public UserInfo getUser() {
            return user;
        }
    }

    /**
     * Информация о пользователе для ответа
     */
    public static class UserInfo {
        private final Integer id;
        private final String phoneNumber;
        private final String firstName;
        private final String lastName;
        private final String email;
        private final Boolean isPhoneVerified;
        private final LocalDateTime createdAt;

        public UserInfo(User user) {
            this.id = user.getId();
            this.phoneNumber = user.getPhoneNumber();
            this.firstName = user.getFirstName();
            this.lastName = user.getLastName();
            this.email = user.getEmail();
            this.isPhoneVerified = user.getIsPhoneVerified();
            this.createdAt = user.getCreatedAt();
        }

        // Геттеры
        public Integer getId() {
            return id;
        }

        public String getPhoneNumber() {
            return phoneNumber;
        }

        public String getFirstName() {
            return firstName;
        }

        public String getLastName() {
            return lastName;
        }

        public String getEmail() {
            return email;
        }

        public Boolean getIsPhoneVerified() {
            return isPhoneVerified;
        }

        public LocalDateTime getCreatedAt() {
            return createdAt;
        }
    }
}