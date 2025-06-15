package com.baganov.pizzanat.service;

import com.baganov.pizzanat.config.TelegramConfig;
import com.baganov.pizzanat.entity.Role;
import com.baganov.pizzanat.entity.TelegramAuthToken;
import com.baganov.pizzanat.entity.User;
import com.baganov.pizzanat.model.dto.auth.AuthResponse;
import com.baganov.pizzanat.model.dto.telegram.TelegramUserData;
import com.baganov.pizzanat.repository.RoleRepository;
import com.baganov.pizzanat.repository.TelegramAuthTokenRepository;
import com.baganov.pizzanat.repository.UserRepository;
import com.baganov.pizzanat.security.JwtService;
import com.baganov.pizzanat.util.TelegramUserDataExtractor;
import com.baganov.pizzanat.util.TokenGenerator;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Unit тесты для проверки исправлений в TelegramAuthService
 * Проверяет корректность исправленной логики авторизации
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("TelegramAuthService - Тесты исправлений")
class TelegramAuthServiceFixesTest {

        @Mock
        private TelegramAuthTokenRepository tokenRepository;

        @Mock
        private UserRepository userRepository;

        @Mock
        private RoleRepository roleRepository;

        @Mock
        private JwtService jwtService;

        @Mock
        private TokenGenerator tokenGenerator;

        @Mock
        private TelegramUserDataExtractor userDataExtractor;

        @Mock
        private RateLimitService rateLimitService;

        @Mock
        private TelegramConfig.TelegramAuthProperties telegramAuthProperties;

        @InjectMocks
        private TelegramAuthService telegramAuthService;

        private TelegramUserData testUserData;
        private TelegramAuthToken testToken;
        private User testUser;
        private Role userRole;

        @BeforeEach
        void setUp() {
                // Тестовые данные пользователя
                testUserData = TelegramUserData.builder()
                                .id(7819187384L)
                                .username("vladimir_baganov")
                                .firstName("Владимир")
                                .lastName("Баганов")
                                .phoneNumber("+79199969633")
                                .build();

                // Тестовый токен
                testToken = TelegramAuthToken.builder()
                                .id(1L)
                                .authToken("tg_auth_test123456789")
                                .status(TelegramAuthToken.TokenStatus.PENDING)
                                .expiresAt(LocalDateTime.now().plusMinutes(10))
                                .createdAt(LocalDateTime.now())
                                .build();

                // Тестовый пользователь
                testUser = User.builder()
                                .id(1)
                                .username("telegram_user_7819187384")
                                .firstName("Владимир")
                                .lastName("Баганов")
                                .phone("+79199969633")
                                .telegramId(7819187384L)
                                .isTelegramVerified(true)
                                .isActive(true)
                                .build();

                // Роль пользователя
                userRole = Role.builder()
                                .id(1)
                                .name("ROLE_USER")
                                .build();

                // Настройка моков
                when(telegramAuthProperties.getTokenTtlMinutes()).thenReturn(10);
                when(tokenGenerator.isValidAuthToken(anyString())).thenReturn(true);
                when(userDataExtractor.isValidUserData(any())).thenReturn(true);
                when(roleRepository.findByName("ROLE_USER")).thenReturn(Optional.of(userRole));
                when(jwtService.generateToken(any(User.class))).thenReturn("jwt_token_test");
        }

        @Test
        @DisplayName("ИСПРАВЛЕНИЕ #1: confirmAuth работает без telegramId в токене")
        void testConfirmAuthWithoutTelegramIdInToken() {
                // Given: Токен без telegramId (как создается изначально)
                TelegramAuthToken tokenWithoutTelegramId = TelegramAuthToken.builder()
                                .id(1L)
                                .authToken("tg_auth_test123456789")
                                .status(TelegramAuthToken.TokenStatus.PENDING)
                                .expiresAt(LocalDateTime.now().plusMinutes(10))
                                .telegramId(null) // Ключевое отличие - нет telegramId
                                .build();

                when(tokenRepository.findByAuthTokenAndStatusAndExpiresAtAfter(
                                eq("tg_auth_test123456789"),
                                eq(TelegramAuthToken.TokenStatus.PENDING),
                                any(LocalDateTime.class)))
                                .thenReturn(Optional.of(tokenWithoutTelegramId));

                // When & Then: Должно выбросить исключение с понятным сообщением
                assertThatThrownBy(() -> telegramAuthService.confirmAuth("tg_auth_test123456789"))
                                .isInstanceOf(RuntimeException.class)
                                .hasMessageContaining(
                                                "Авторизация не завершена. Пожалуйста, отправьте свой номер телефона в боте");
        }

        @Test
        @DisplayName("ИСПРАВЛЕНИЕ #2: confirmAuth работает с telegramId в токене")
        void testConfirmAuthWithTelegramIdInToken() {
                // Given: Токен с telegramId (после отправки контакта)
                testToken.setTelegramId(7819187384L);

                when(tokenRepository.findByAuthTokenAndStatusAndExpiresAtAfter(
                                eq("tg_auth_test123456789"),
                                eq(TelegramAuthToken.TokenStatus.PENDING),
                                any(LocalDateTime.class)))
                                .thenReturn(Optional.of(testToken));

                when(userRepository.findByTelegramId(7819187384L))
                                .thenReturn(Optional.of(testUser));

                when(tokenRepository.save(any(TelegramAuthToken.class)))
                                .thenReturn(testToken);

                // When
                AuthResponse response = telegramAuthService.confirmAuth("tg_auth_test123456789");

                // Then
                assertThat(response).isNotNull();
                assertThat(response.getToken()).isEqualTo("jwt_token_test");
                assertThat(response.getUsername()).isEqualTo("telegram_user_7819187384");

                verify(tokenRepository)
                                .save(argThat(token -> token.getStatus() == TelegramAuthToken.TokenStatus.CONFIRMED));
        }

        @Test
        @DisplayName("ИСПРАВЛЕНИЕ #3: updateTokenWithUserData связывает токен с пользователем")
        void testUpdateTokenWithUserData() {
                // Given
                when(tokenRepository.findByAuthToken("tg_auth_test123456789"))
                                .thenReturn(Optional.of(testToken));

                when(tokenRepository.save(any(TelegramAuthToken.class)))
                                .thenReturn(testToken);

                // When
                telegramAuthService.updateTokenWithUserData("tg_auth_test123456789", testUserData);

                // Then
                verify(tokenRepository).save(argThat(token -> {
                        assertThat(token.getTelegramId()).isEqualTo(7819187384L);
                        assertThat(token.getTelegramUsername()).isEqualTo("vladimir_baganov");
                        assertThat(token.getTelegramFirstName()).isEqualTo("Владимир");
                        assertThat(token.getTelegramLastName()).isEqualTo("Баганов");
                        return true;
                }));
        }

        @Test
        @DisplayName("ИСПРАВЛЕНИЕ #4: findPendingTokensWithoutTelegramId находит токены без telegramId")
        void testFindPendingTokensWithoutTelegramId() {
                // Given
                List<TelegramAuthToken> pendingTokens = List.of(testToken);

                when(tokenRepository.findByStatusAndTelegramIdIsNullAndCreatedAtAfterOrderByCreatedAtAsc(
                                eq(TelegramAuthToken.TokenStatus.PENDING),
                                any(LocalDateTime.class)))
                                .thenReturn(pendingTokens);

                // When
                List<TelegramAuthToken> result = telegramAuthService.findPendingTokensWithoutTelegramId();

                // Then
                assertThat(result).hasSize(1);
                assertThat(result.get(0)).isEqualTo(testToken);

                verify(tokenRepository).findByStatusAndTelegramIdIsNullAndCreatedAtAfterOrderByCreatedAtAsc(
                                eq(TelegramAuthToken.TokenStatus.PENDING),
                                any(LocalDateTime.class));
        }

        @Test
        @DisplayName("ИСПРАВЛЕНИЕ #5: updateUserWithPhoneNumber создает пользователя если не существует")
        void testUpdateUserWithPhoneNumberCreatesUserIfNotExists() {
                // Given
                when(userRepository.findByTelegramId(7819187384L))
                                .thenReturn(Optional.empty()); // Пользователь не найден

                when(userDataExtractor.createUserFromTelegramData(testUserData))
                                .thenReturn(testUser);

                when(userRepository.save(any(User.class)))
                                .thenReturn(testUser);

                // When
                telegramAuthService.updateUserWithPhoneNumber(testUserData);

                // Then
                verify(userDataExtractor).createUserFromTelegramData(testUserData);
                verify(userRepository).save(argThat(user -> {
                        assertThat(user.getPhone()).isEqualTo("+79199969633");
                        assertThat(user.getIsTelegramVerified()).isTrue();
                        return true;
                }));
        }

        @Test
        @DisplayName("ИСПРАВЛЕНИЕ #6: updateUserWithPhoneNumber обновляет существующего пользователя")
        void testUpdateUserWithPhoneNumberUpdatesExistingUser() {
                // Given
                User existingUser = User.builder()
                                .id(1)
                                .username("existing_user")
                                .telegramId(7819187384L)
                                .phone(null) // Номер телефона не установлен
                                .build();

                when(userRepository.findByTelegramId(7819187384L))
                                .thenReturn(Optional.of(existingUser));

                when(userRepository.save(any(User.class)))
                                .thenReturn(existingUser);

                // When
                telegramAuthService.updateUserWithPhoneNumber(testUserData);

                // Then
                verify(userRepository).save(argThat(user -> {
                        assertThat(user.getPhone()).isEqualTo("+79199969633");
                        assertThat(user.getIsTelegramVerified()).isTrue();
                        assertThat(user.getFirstName()).isEqualTo("Владимир");
                        assertThat(user.getLastName()).isEqualTo("Баганов");
                        return true;
                }));
        }

        @Test
        @DisplayName("ИСПРАВЛЕНИЕ #7: Детальная диагностика при ошибке поиска токена")
        void testDetailedDiagnosticsWhenTokenNotFound() {
                // Given: Токен не найден по основному запросу
                when(tokenRepository.findByAuthTokenAndStatusAndExpiresAtAfter(
                                eq("tg_auth_test123456789"),
                                eq(TelegramAuthToken.TokenStatus.PENDING),
                                any(LocalDateTime.class)))
                                .thenReturn(Optional.empty());

                // Но токен существует с другим статусом
                TelegramAuthToken expiredToken = TelegramAuthToken.builder()
                                .authToken("tg_auth_test123456789")
                                .status(TelegramAuthToken.TokenStatus.EXPIRED)
                                .expiresAt(LocalDateTime.now().minusMinutes(5))
                                .build();

                when(tokenRepository.findByAuthToken("tg_auth_test123456789"))
                                .thenReturn(Optional.of(expiredToken));

                // When & Then
                assertThatThrownBy(() -> telegramAuthService.confirmAuth("tg_auth_test123456789"))
                                .isInstanceOf(RuntimeException.class)
                                .hasMessageContaining("Токен не найден или истек");

                // Проверяем, что была вызвана диагностика
                verify(tokenRepository).findByAuthToken("tg_auth_test123456789");
        }

        @Test
        @DisplayName("ИСПРАВЛЕНИЕ #8: Полный цикл авторизации с исправлениями")
        void testFullAuthenticationCycleWithFixes() {
                // Given: Симулируем полный цикл

                // 1. Создание токена без telegramId
                TelegramAuthToken initialToken = TelegramAuthToken.builder()
                                .authToken("tg_auth_test123456789")
                                .status(TelegramAuthToken.TokenStatus.PENDING)
                                .expiresAt(LocalDateTime.now().plusMinutes(10))
                                .telegramId(null)
                                .build();

                // 2. Токен после обновления с telegramId
                TelegramAuthToken updatedToken = TelegramAuthToken.builder()
                                .authToken("tg_auth_test123456789")
                                .status(TelegramAuthToken.TokenStatus.PENDING)
                                .expiresAt(LocalDateTime.now().plusMinutes(10))
                                .telegramId(7819187384L)
                                .telegramUsername("vladimir_baganov")
                                .telegramFirstName("Владимир")
                                .telegramLastName("Баганов")
                                .build();

                when(userRepository.findByTelegramId(7819187384L))
                                .thenReturn(Optional.of(testUser));

                // Этап 1: Обновление токена данными пользователя
                when(tokenRepository.findByAuthToken("tg_auth_test123456789"))
                                .thenReturn(Optional.of(initialToken));
                when(tokenRepository.save(any(TelegramAuthToken.class)))
                                .thenReturn(updatedToken);

                telegramAuthService.updateTokenWithUserData("tg_auth_test123456789", testUserData);

                // Этап 2: Подтверждение авторизации
                when(tokenRepository.findByAuthTokenAndStatusAndExpiresAtAfter(
                                eq("tg_auth_test123456789"),
                                eq(TelegramAuthToken.TokenStatus.PENDING),
                                any(LocalDateTime.class)))
                                .thenReturn(Optional.of(updatedToken));

                AuthResponse response = telegramAuthService.confirmAuth("tg_auth_test123456789");

                // Then
                assertThat(response).isNotNull();
                assertThat(response.getToken()).isEqualTo("jwt_token_test");

                // Проверяем, что токен был обновлен и подтвержден
                verify(tokenRepository, times(2)).save(any(TelegramAuthToken.class));
        }
}