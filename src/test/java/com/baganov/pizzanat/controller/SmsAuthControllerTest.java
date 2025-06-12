package com.baganov.pizzanat.controller;

import com.baganov.pizzanat.mapper.SmsAuthMapper;
import com.baganov.pizzanat.model.dto.auth.SendSmsCodeRequest;
import com.baganov.pizzanat.model.dto.auth.VerifySmsCodeRequest;
import com.baganov.pizzanat.service.SmsAuthService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDateTime;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Тест контроллера SMS аутентификации
 */
@WebMvcTest(SmsAuthController.class)
@ActiveProfiles("test")
class SmsAuthControllerTest {

        @Autowired
        private MockMvc mockMvc;

        @Autowired
        private ObjectMapper objectMapper;

        @MockitoBean
        private SmsAuthService smsAuthService;

        @MockitoBean
        private SmsAuthMapper smsAuthMapper;

        @Test
        void testEndpoint_ShouldReturnOk() throws Exception {
                mockMvc.perform(get("/api/v1/auth/sms/test"))
                                .andExpect(status().isOk())
                                .andExpect(content().string("SMS аутентификация доступна"));
        }

        @Test
        void sendCode_WithValidPhoneNumber_ShouldReturnSuccess() throws Exception {
                // Arrange
                SendSmsCodeRequest request = SendSmsCodeRequest.builder()
                                .phoneNumber("+79123456789")
                                .build();

                SmsAuthService.SmsCodeResponse serviceResponse = SmsAuthService.SmsCodeResponse
                                .success(LocalDateTime.now().plusMinutes(10), 4);

                when(smsAuthService.sendCode(anyString())).thenReturn(serviceResponse);

                // Act & Assert
                mockMvc.perform(post("/api/v1/auth/sms/send-code")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(request)))
                                .andExpect(status().isOk());
        }

        @Test
        void sendCode_WithInvalidPhoneNumber_ShouldReturnBadRequest() throws Exception {
                // Arrange
                SendSmsCodeRequest request = SendSmsCodeRequest.builder()
                                .phoneNumber("invalid-phone")
                                .build();

                // Act & Assert
                mockMvc.perform(post("/api/v1/auth/sms/send-code")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(request)))
                                .andExpect(status().isBadRequest());
        }

        @Test
        void verifyCode_WithValidData_ShouldReturnSuccess() throws Exception {
                // Arrange
                VerifySmsCodeRequest request = VerifySmsCodeRequest.builder()
                                .phoneNumber("+79123456789")
                                .code("1234")
                                .build();

                SmsAuthService.AuthResponse serviceResponse = SmsAuthService.AuthResponse
                                .success("test-token", null);

                when(smsAuthService.verifyCode(anyString(), anyString())).thenReturn(serviceResponse);

                // Act & Assert
                mockMvc.perform(post("/api/v1/auth/sms/verify-code")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(request)))
                                .andExpect(status().isOk());
        }

        @Test
        void verifyCode_WithInvalidCode_ShouldReturnBadRequest() throws Exception {
                // Arrange
                VerifySmsCodeRequest request = VerifySmsCodeRequest.builder()
                                .phoneNumber("+79123456789")
                                .code("abc") // invalid code format
                                .build();

                // Act & Assert
                mockMvc.perform(post("/api/v1/auth/sms/verify-code")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(request)))
                                .andExpect(status().isBadRequest());
        }
}