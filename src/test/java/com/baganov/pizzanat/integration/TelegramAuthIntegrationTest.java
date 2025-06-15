package com.baganov.pizzanat.integration;

import com.baganov.pizzanat.entity.TelegramAuthToken;
import com.baganov.pizzanat.entity.User;
import com.baganov.pizzanat.model.dto.telegram.InitTelegramAuthRequest;
import com.baganov.pizzanat.model.dto.telegram.TelegramAuthResponse;
import com.baganov.pizzanat.model.dto.telegram.TelegramStatusResponse;
import com.baganov.pizzanat.model.dto.telegram.TelegramUpdate;
import com.baganov.pizzanat.model.dto.telegram.TelegramUserData;
import com.baganov.pizzanat.repository.TelegramAuthTokenRepository;
import com.baganov.pizzanat.repository.UserRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureWebMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Интеграционный тест для проверки исправлений Telegram авторизации
 * Тестирует полный цикл авторизации через реальные HTTP запросы
 */
@SpringBootTest
@AutoConfigureWebMvc
@TestPropertySource(properties = {
                "spring.datasource.url=jdbc:h2:mem:testdb",
                "spring.jpa.hibernate.ddl-auto=create-drop",
                "spring.flyway.enabled=false",
                "telegram.auth.enabled=true",
                "telegram.auth.bot.token=123456789:ABCdefGHijklMNopQRstUVwxyz_TEST",
                "telegram.auth.bot.username=pizzanat_test_bot",
                "telegram.auth.webhook.enabled=false"
})
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_EACH_TEST_METHOD)
@DisplayName("Telegram Auth Integration - Тесты исправлений")
class TelegramAuthIntegrationTest {

        @Autowired
        private MockMvc mockMvc;

        @Autowired
        private ObjectMapper objectMapper;

        @Autowired
        private TelegramAuthTokenRepository tokenRepository;

        @Autowired
        private UserRepository userRepository;

        private static final Long TEST_TELEGRAM_USER_ID = 7819187384L;
        private static final Long TEST_CHAT_ID = -4919444764L;
        private static final String TEST_PHONE_NUMBER = "+79199969633";
        private static final String TEST_FIRST_NAME = "Владимир";
        private static final String TEST_LAST_NAME = "Баганов";
        private static final String TEST_USERNAME = "vladimir_baganov";

        @BeforeEach
        void setUp() {
                // Очищаем данные перед каждым тестом
                tokenRepository.deleteAll();
                userRepository.findByTelegramId(TEST_TELEGRAM_USER_ID)
                                .ifPresent(user -> userRepository.delete(user));
        }

        @Test
        @DisplayName("ПОЛНЫЙ ЦИКЛ: Исправленная авторизация работает от начала до конца")
        void testFullFixedAuthenticationCycle() throws Exception {
                // Этап 1: Инициализация токена
                String initRequest = """
                                {
                                    "deviceId": "integration_test_device"
                                }
                                """;

                String initResponse = mockMvc.perform(post("/api/v1/auth/telegram/init")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(initRequest))
                                .andExpect(status().isOk())
                                .andExpect(jsonPath("$.success").value(true))
                                .andExpect(jsonPath("$.authToken").exists())
                                .andExpect(jsonPath("$.telegramBotUrl").exists())
                                .andReturn()
                                .getResponse()
                                .getContentAsString();

                Map<String, Object> initData = objectMapper.readValue(initResponse, Map.class);
                String authToken = (String) initData.get("authToken");

                assertThat(authToken).isNotNull();

                // Проверяем, что токен создан в БД без telegramId
                Optional<TelegramAuthToken> tokenOpt = tokenRepository.findByAuthToken(authToken);
                assertThat(tokenOpt).isPresent();
                assertThat(tokenOpt.get().getTelegramId()).isNull();
                assertThat(tokenOpt.get().getStatus()).isEqualTo(TelegramAuthToken.TokenStatus.PENDING);

                // Этап 2: Команда /start через webhook
                String startWebhook = String.format("""
                                {
                                    "update_id": 1001,
                                    "message": {
                                        "message_id": 2001,
                                        "from": {
                                            "id": %d,
                                            "first_name": "%s",
                                            "last_name": "%s",
                                            "username": "%s"
                                        },
                                        "chat": {
                                            "id": %d,
                                            "type": "private"
                                        },
                                        "date": %d,
                                        "text": "/start %s"
                                    }
                                }
                                """, TEST_TELEGRAM_USER_ID, TEST_FIRST_NAME, TEST_LAST_NAME,
                                TEST_USERNAME, TEST_CHAT_ID, System.currentTimeMillis() / 1000, authToken);

                mockMvc.perform(post("/api/v1/telegram/webhook")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(startWebhook))
                                .andExpect(status().isOk());

                // Этап 3: Отправка контакта через webhook
                String contactWebhook = String.format("""
                                {
                                    "update_id": 1002,
                                    "message": {
                                        "message_id": 2002,
                                        "from": {
                                            "id": %d,
                                            "first_name": "%s",
                                            "last_name": "%s",
                                            "username": "%s"
                                        },
                                        "chat": {
                                            "id": %d,
                                            "type": "private"
                                        },
                                        "date": %d,
                                        "contact": {
                                            "phone_number": "%s",
                                            "first_name": "%s",
                                            "last_name": "%s",
                                            "user_id": %d
                                        }
                                    }
                                }
                                """, TEST_TELEGRAM_USER_ID, TEST_FIRST_NAME, TEST_LAST_NAME, TEST_USERNAME,
                                TEST_CHAT_ID, System.currentTimeMillis() / 1000, TEST_PHONE_NUMBER,
                                TEST_FIRST_NAME, TEST_LAST_NAME, TEST_TELEGRAM_USER_ID);

                mockMvc.perform(post("/api/v1/telegram/webhook")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(contactWebhook))
                                .andExpect(status().isOk());

                // Проверяем, что токен обновлен с telegramId
                tokenOpt = tokenRepository.findByAuthToken(authToken);
                assertThat(tokenOpt).isPresent();
                TelegramAuthToken updatedToken = tokenOpt.get();
                assertThat(updatedToken.getTelegramId()).isEqualTo(TEST_TELEGRAM_USER_ID);
                assertThat(updatedToken.getTelegramFirstName()).isEqualTo(TEST_FIRST_NAME);
                assertThat(updatedToken.getTelegramLastName()).isEqualTo(TEST_LAST_NAME);

                // Проверяем, что пользователь создан
                Optional<User> userOpt = userRepository.findByTelegramId(TEST_TELEGRAM_USER_ID);
                assertThat(userOpt).isPresent();
                User user = userOpt.get();
                assertThat(user.getPhone()).isEqualTo(TEST_PHONE_NUMBER);
                assertThat(user.getIsTelegramVerified()).isTrue();

                // Этап 4: Подтверждение авторизации через callback
                String confirmWebhook = String.format("""
                                {
                                    "update_id": 1003,
                                    "callback_query": {
                                        "id": "callback_test_123",
                                        "from": {
                                            "id": %d,
                                            "first_name": "%s",
                                            "last_name": "%s",
                                            "username": "%s"
                                        },
                                        "message": {
                                            "message_id": 2003,
                                            "chat": {
                                                "id": %d,
                                                "type": "private"
                                            }
                                        },
                                        "data": "confirm_auth_%s"
                                    }
                                }
                                """, TEST_TELEGRAM_USER_ID, TEST_FIRST_NAME, TEST_LAST_NAME,
                                TEST_USERNAME, TEST_CHAT_ID, authToken);

                mockMvc.perform(post("/api/v1/telegram/webhook")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(confirmWebhook))
                                .andExpect(status().isOk());

                // Этап 5: Проверка статуса токена
                String statusResponse = mockMvc.perform(get("/api/v1/auth/telegram/status/" + authToken))
                                .andExpect(status().isOk())
                                .andExpect(jsonPath("$.status").value("CONFIRMED"))
                                .andExpect(jsonPath("$.token").exists())
                                .andExpect(jsonPath("$.user").exists())
                                .andReturn()
                                .getResponse()
                                .getContentAsString();

                Map<String, Object> statusData = objectMapper.readValue(statusResponse, Map.class);
                String jwtToken = (String) statusData.get("token");

                assertThat(jwtToken).isNotNull();
                assertThat(jwtToken.length()).isGreaterThan(50);

                // Проверяем финальное состояние токена в БД
                tokenOpt = tokenRepository.findByAuthToken(authToken);
                assertThat(tokenOpt).isPresent();
                assertThat(tokenOpt.get().getStatus()).isEqualTo(TelegramAuthToken.TokenStatus.CONFIRMED);
        }

        @Test
        @DisplayName("ИСПРАВЛЕНИЕ: confirmAuth работает без telegramId")
        void testConfirmAuthWithoutTelegramIdFails() throws Exception {
                // Создаем токен без telegramId
                TelegramAuthToken token = TelegramAuthToken.builder()
                                .authToken("test_token_without_telegram_id")
                                .status(TelegramAuthToken.TokenStatus.PENDING)
                                .expiresAt(LocalDateTime.now().plusMinutes(10))
                                .telegramId(null) // Ключевое условие
                                .build();

                tokenRepository.save(token);

                // Пытаемся подтвердить авторизацию
                String confirmRequest = """
                                {
                                    "authToken": "test_token_without_telegram_id"
                                }
                                """;

                mockMvc.perform(post("/api/v1/auth/telegram/confirm")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(confirmRequest))
                                .andExpect(status().isBadRequest())
                                .andExpect(jsonPath("$.message").value(containsString("Авторизация не завершена")));
        }

        @Test
        @DisplayName("ИСПРАВЛЕНИЕ: Повторная авторизация работает корректно")
        void testRepeatedAuthenticationWorks() throws Exception {
                // Первая авторизация
                testFullFixedAuthenticationCycle();

                // Создаем новый токен для повторной авторизации
                String secondInitRequest = """
                                {
                                    "deviceId": "integration_test_device_2"
                                }
                                """;

                String secondInitResponse = mockMvc.perform(post("/api/v1/auth/telegram/init")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(secondInitRequest))
                                .andExpect(status().isOk())
                                .andExpect(jsonPath("$.success").value(true))
                                .andExpect(jsonPath("$.authToken").exists())
                                .andReturn()
                                .getResponse()
                                .getContentAsString();

                Map<String, Object> secondInitData = objectMapper.readValue(secondInitResponse, Map.class);
                String secondAuthToken = (String) secondInitData.get("authToken");

                // Повторная команда /start должна работать
                String secondStartWebhook = String.format("""
                                {
                                    "update_id": 2001,
                                    "message": {
                                        "message_id": 3001,
                                        "from": {
                                            "id": %d,
                                            "first_name": "%s",
                                            "last_name": "%s",
                                            "username": "%s"
                                        },
                                        "chat": {
                                            "id": %d,
                                            "type": "private"
                                        },
                                        "date": %d,
                                        "text": "/start %s"
                                    }
                                }
                                """, TEST_TELEGRAM_USER_ID, TEST_FIRST_NAME, TEST_LAST_NAME,
                                TEST_USERNAME, TEST_CHAT_ID, System.currentTimeMillis() / 1000, secondAuthToken);

                mockMvc.perform(post("/api/v1/telegram/webhook")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(secondStartWebhook))
                                .andExpect(status().isOk());

                // Проверяем, что второй токен создан корректно
                Optional<TelegramAuthToken> secondTokenOpt = tokenRepository.findByAuthToken(secondAuthToken);
                assertThat(secondTokenOpt).isPresent();
                assertThat(secondTokenOpt.get().getStatus()).isEqualTo(TelegramAuthToken.TokenStatus.PENDING);
        }

        @Test
        @DisplayName("ИСПРАВЛЕНИЕ: Детальная диагностика при ошибке токена")
        void testDetailedDiagnosticsForTokenErrors() throws Exception {
                // Создаем истекший токен
                TelegramAuthToken expiredToken = TelegramAuthToken.builder()
                                .authToken("expired_token_test")
                                .status(TelegramAuthToken.TokenStatus.EXPIRED)
                                .expiresAt(LocalDateTime.now().minusMinutes(5))
                                .build();

                tokenRepository.save(expiredToken);

                // Пытаемся подтвердить истекший токен
                String confirmRequest = """
                                {
                                    "authToken": "expired_token_test"
                                }
                                """;

                mockMvc.perform(post("/api/v1/auth/telegram/confirm")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(confirmRequest))
                                .andExpect(status().isBadRequest())
                                .andExpect(jsonPath("$.message").value(containsString("Токен не найден или истек")));
        }

        @Test
        @DisplayName("ИСПРАВЛЕНИЕ: Проверка статуса токена на всех этапах")
        void testTokenStatusAtAllStages() throws Exception {
                // Этап 1: Создание токена
                String initRequest = """
                                {
                                    "deviceId": "status_test_device"
                                }
                                """;

                String initResponse = mockMvc.perform(post("/api/v1/auth/telegram/init")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(initRequest))
                                .andExpect(status().isOk())
                                .andReturn()
                                .getResponse()
                                .getContentAsString();

                Map<String, Object> initData = objectMapper.readValue(initResponse, Map.class);
                String authToken = (String) initData.get("authToken");

                // Проверка статуса: PENDING без telegramId
                mockMvc.perform(get("/api/v1/auth/telegram/status/" + authToken))
                                .andExpect(status().isOk())
                                .andExpect(jsonPath("$.status").value("PENDING"))
                                .andExpect(jsonPath("$.token").doesNotExist())
                                .andExpect(jsonPath("$.user").doesNotExist());

                // Этап 2: Обновление токена через контакт
                String contactWebhook = String.format("""
                                {
                                    "update_id": 3001,
                                    "message": {
                                        "message_id": 4001,
                                        "from": {
                                            "id": %d,
                                            "first_name": "%s",
                                            "last_name": "%s",
                                            "username": "%s"
                                        },
                                        "chat": {
                                            "id": %d,
                                            "type": "private"
                                        },
                                        "date": %d,
                                        "text": "/start %s"
                                    }
                                }
                                """, TEST_TELEGRAM_USER_ID, TEST_FIRST_NAME, TEST_LAST_NAME,
                                TEST_USERNAME, TEST_CHAT_ID, System.currentTimeMillis() / 1000, authToken);

                mockMvc.perform(post("/api/v1/telegram/webhook")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(contactWebhook))
                                .andExpect(status().isOk());

                // Отправляем контакт
                String contactMessage = String.format("""
                                {
                                    "update_id": 3002,
                                    "message": {
                                        "message_id": 4002,
                                        "from": {
                                            "id": %d,
                                            "first_name": "%s",
                                            "last_name": "%s",
                                            "username": "%s"
                                        },
                                        "chat": {
                                            "id": %d,
                                            "type": "private"
                                        },
                                        "date": %d,
                                        "contact": {
                                            "phone_number": "%s",
                                            "first_name": "%s",
                                            "last_name": "%s",
                                            "user_id": %d
                                        }
                                    }
                                }
                                """, TEST_TELEGRAM_USER_ID, TEST_FIRST_NAME, TEST_LAST_NAME, TEST_USERNAME,
                                TEST_CHAT_ID, System.currentTimeMillis() / 1000, TEST_PHONE_NUMBER,
                                TEST_FIRST_NAME, TEST_LAST_NAME, TEST_TELEGRAM_USER_ID);

                mockMvc.perform(post("/api/v1/telegram/webhook")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(contactMessage))
                                .andExpect(status().isOk());

                // Проверка статуса: PENDING с telegramId
                mockMvc.perform(get("/api/v1/auth/telegram/status/" + authToken))
                                .andExpect(status().isOk())
                                .andExpect(jsonPath("$.status").value("PENDING"))
                                .andExpect(jsonPath("$.token").doesNotExist());

                // Этап 3: Подтверждение
                String confirmWebhook = String.format("""
                                {
                                    "update_id": 3003,
                                    "callback_query": {
                                        "id": "status_test_callback",
                                        "from": {
                                            "id": %d,
                                            "first_name": "%s",
                                            "last_name": "%s",
                                            "username": "%s"
                                        },
                                        "message": {
                                            "message_id": 4003,
                                            "chat": {
                                                "id": %d,
                                                "type": "private"
                                            }
                                        },
                                        "data": "confirm_auth_%s"
                                    }
                                }
                                """, TEST_TELEGRAM_USER_ID, TEST_FIRST_NAME, TEST_LAST_NAME,
                                TEST_USERNAME, TEST_CHAT_ID, authToken);

                mockMvc.perform(post("/api/v1/telegram/webhook")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(confirmWebhook))
                                .andExpect(status().isOk());

                // Проверка финального статуса: CONFIRMED с JWT токеном
                mockMvc.perform(get("/api/v1/auth/telegram/status/" + authToken))
                                .andExpect(status().isOk())
                                .andExpect(jsonPath("$.status").value("CONFIRMED"))
                                .andExpect(jsonPath("$.token").exists())
                                .andExpect(jsonPath("$.user").exists())
                                .andExpect(jsonPath("$.user.phone").value(TEST_PHONE_NUMBER))
                                .andExpect(jsonPath("$.user.telegramId").value(TEST_TELEGRAM_USER_ID));
        }
}