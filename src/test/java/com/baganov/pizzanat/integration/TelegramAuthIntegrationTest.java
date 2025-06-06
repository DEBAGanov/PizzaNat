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

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Интеграционный тест для Telegram аутентификации
 * Проверяет полный цикл аутентификации согласно ТЗ
 * Backend_Requirements_SMS_Telegram_Auth.md
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
public class TelegramAuthIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private TelegramAuthTokenRepository tokenRepository;

    @Autowired
    private UserRepository userRepository;

    @BeforeEach
    void setUp() {
        tokenRepository.deleteAll();
        userRepository.deleteAll();
    }

    @Test
    @DisplayName("Полный цикл Telegram аутентификации - успешный сценарий")
    @Transactional
    void testFullTelegramAuthFlow_Success() throws Exception {
        // 1. Инициализация аутентификации
        InitTelegramAuthRequest initRequest = new InitTelegramAuthRequest();
        initRequest.setDeviceId("test_device_123");

        String initResponse = mockMvc.perform(post("/api/v1/auth/telegram/init")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(initRequest)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.authToken").isNotEmpty())
                .andExpect(jsonPath("$.telegramBotUrl").isNotEmpty())
                .andExpect(jsonPath("$.expiresAt").isNotEmpty())
                .andReturn()
                .getResponse()
                .getContentAsString();

        TelegramAuthResponse authResponse = objectMapper.readValue(initResponse, TelegramAuthResponse.class);
        String authToken = authResponse.getAuthToken();

        // Проверяем, что токен создан в БД
        TelegramAuthToken savedToken = tokenRepository.findByAuthTokenAndExpiresAtAfter(authToken, LocalDateTime.now())
                .orElse(null);
        assertThat(savedToken).isNotNull();
        assertThat(savedToken.getStatus()).isEqualTo(TelegramAuthToken.TokenStatus.PENDING);

        // Проверяем URL бота согласно ТЗ
        assertThat(authResponse.getTelegramBotUrl())
                .contains("https://t.me/pizzanat_test_bot?start=" + authToken);

        // 2. Проверка статуса (PENDING)
        mockMvc.perform(get("/api/v1/auth/telegram/status/{authToken}", authToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.status").value("PENDING"))
                .andExpect(jsonPath("$.message").value("Ожидание подтверждения в Telegram"));

        // 3. Симулируем webhook от Telegram бота (команда /start с токеном)
        TelegramUpdate webhookUpdate = createTelegramUpdate(authToken, 987654321L, "john_doe", "Иван", "Иванов");

        mockMvc.perform(post("/api/v1/telegram/webhook")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(webhookUpdate)))
                .andExpect(status().isOk());

        // 4. Проверка статуса (CONFIRMED)
        String statusResponse = mockMvc.perform(get("/api/v1/auth/telegram/status/{authToken}", authToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.status").value("CONFIRMED"))
                .andExpect(jsonPath("$.authData.token").isNotEmpty())
                .andExpect(jsonPath("$.authData.username").value("john_doe"))
                .andExpect(jsonPath("$.authData.firstName").value("Иван"))
                .andExpect(jsonPath("$.authData.lastName").value("Иванов"))
                .andReturn()
                .getResponse()
                .getContentAsString();

        TelegramStatusResponse finalStatus = objectMapper.readValue(statusResponse, TelegramStatusResponse.class);

        // Проверяем, что пользователь создан
        User createdUser = userRepository.findByTelegramId(987654321L).orElse(null);
        assertThat(createdUser).isNotNull();
        assertThat(createdUser.getUsername()).isEqualTo("john_doe");
        assertThat(createdUser.getTelegramId()).isEqualTo(987654321L);
        assertThat(createdUser.getFirstName()).isEqualTo("Иван");
        assertThat(createdUser.getLastName()).isEqualTo("Иванов");

        // Проверяем JWT токен
        assertThat(finalStatus.getAuthData().getToken()).isNotBlank();
    }

    @Test
    @DisplayName("Проверка rate limiting")
    void testRateLimiting() throws Exception {
        InitTelegramAuthRequest request = new InitTelegramAuthRequest();
        request.setDeviceId("rate_limit_test");

        // Отправляем 6 запросов (лимит 5 в час)
        for (int i = 0; i < 5; i++) {
            mockMvc.perform(post("/api/v1/auth/telegram/init")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isOk());
        }

        // 6-й запрос должен быть отклонен
        mockMvc.perform(post("/api/v1/auth/telegram/init")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isTooManyRequests())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.message").value("Слишком много попыток. Попробуйте позже"));
    }

    @Test
    @DisplayName("Истечение токена аутентификации")
    void testTokenExpiration() throws Exception {
        // Создаем истекший токен
        TelegramAuthToken expiredToken = TelegramAuthToken.builder()
                .authToken("tg_auth_expired123")
                .status(TelegramAuthToken.TokenStatus.PENDING)
                .expiresAt(LocalDateTime.now().minusMinutes(1)) // Истек минуту назад
                .build();

        tokenRepository.save(expiredToken);

        // Проверяем статус истекшего токена
        mockMvc.perform(get("/api/v1/auth/telegram/status/{authToken}", "tg_auth_expired123"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.status").value("EXPIRED"))
                .andExpect(jsonPath("$.message").value("Токен аутентификации истек. Попробуйте снова"));
    }

    @Test
    @DisplayName("Обработка некорректного токена")
    void testInvalidToken() throws Exception {
        mockMvc.perform(get("/api/v1/auth/telegram/status/{authToken}", "invalid_token"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.message").value("Некорректный токен"));
    }

    @Test
    @DisplayName("Webhook info endpoint")
    void testWebhookInfo() throws Exception {
        mockMvc.perform(get("/api/v1/telegram/webhook/info"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.configured").isBoolean());
    }

    @Test
    @DisplayName("Health check endpoint")
    void testHealthCheck() throws Exception {
        mockMvc.perform(get("/api/v1/auth/telegram/test"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("OK"))
                .andExpect(jsonPath("$.service").value("Telegram Authentication"));
    }

    @Test
    @DisplayName("Создание пользователя при первой аутентификации")
    void testUserCreation() throws Exception {
        // Инициализация
        InitTelegramAuthRequest request = new InitTelegramAuthRequest();
        request.setDeviceId("new_user_test");

        String response = mockMvc.perform(post("/api/v1/auth/telegram/init")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();

        TelegramAuthResponse authResponse = objectMapper.readValue(response, TelegramAuthResponse.class);
        String authToken = authResponse.getAuthToken();

        // Симулируем подтверждение нового пользователя
        TelegramUpdate update = createTelegramUpdate(authToken, 123456789L, "new_user", "Новый", "Пользователь");

        mockMvc.perform(post("/api/v1/telegram/webhook")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(update)))
                .andExpect(status().isOk());

        // Проверяем создание пользователя
        User newUser = userRepository.findByTelegramId(123456789L).orElse(null);
        assertThat(newUser).isNotNull();
        assertThat(newUser.getUsername()).isEqualTo("new_user");
        assertThat(newUser.isActive()).isTrue();

        // Проверяем, что токен подтвержден
        TelegramAuthToken confirmedToken = tokenRepository
                .findByAuthTokenAndExpiresAtAfter(authToken, LocalDateTime.now()).orElse(null);
        assertThat(confirmedToken).isNotNull();
        assertThat(confirmedToken.getStatus()).isEqualTo(TelegramAuthToken.TokenStatus.CONFIRMED);
        assertThat(confirmedToken.getTelegramId()).isEqualTo(123456789L);
    }

    /**
     * Создает mock объект TelegramUpdate для тестирования webhook
     */
    private TelegramUpdate createTelegramUpdate(String authToken, Long telegramId, String username, String firstName,
            String lastName) {
        TelegramUpdate update = new TelegramUpdate();

        // Создаем структуру как от реального Telegram API
        TelegramUpdate.TelegramMessage message = new TelegramUpdate.TelegramMessage();
        message.setText("/start " + authToken);
        message.setMessageId(12345L);

        TelegramUserData from = new TelegramUserData();
        from.setId(telegramId);
        from.setUsername(username);
        from.setFirstName(firstName);
        from.setLastName(lastName);

        TelegramUpdate.TelegramChat chat = new TelegramUpdate.TelegramChat();
        chat.setId(telegramId);
        chat.setType("private");

        message.setFrom(from);
        message.setChat(chat);
        update.setMessage(message);

        return update;
    }
}