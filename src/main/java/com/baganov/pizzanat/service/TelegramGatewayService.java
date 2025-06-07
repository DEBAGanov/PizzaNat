package com.baganov.pizzanat.service;

import com.baganov.pizzanat.config.TelegramGatewayProperties;
import com.baganov.pizzanat.model.dto.gateway.SendVerificationRequest;
import com.baganov.pizzanat.model.dto.gateway.VerificationStatusResponse;
import com.baganov.pizzanat.model.dto.gateway.RequestStatusResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.util.HashMap;
import java.util.Map;

/**
 * Сервис для работы с Telegram Gateway API
 * Используется для отправки верификационных кодов через Telegram
 * Согласно документации: https://core.telegram.org/gateway/api
 */
@Slf4j
@Service
@RequiredArgsConstructor
@ConditionalOnProperty(value = "telegram.gateway.enabled", havingValue = "true")
public class TelegramGatewayService {

    private final TelegramGatewayProperties gatewayProperties;
    private final RestTemplate gatewayRestTemplate;

    private static final String GATEWAY_BASE_URL = "https://gatewayapi.telegram.org";
    private static final String SEND_VERIFICATION_ENDPOINT = "/sendVerificationMessage";
    private static final String CHECK_STATUS_ENDPOINT = "/checkVerificationStatus";
    private static final String REVOKE_MESSAGE_ENDPOINT = "/revokeVerificationMessage";

    /**
     * Отправляет верификационный код через Telegram Gateway API
     * 
     * @param phoneNumber номер телефона в формате E.164
     * @param codeLength  длина кода (4-8 символов)
     * @param payload     пользовательские данные
     * @return ответ с информацией о запросе
     */
    public RequestStatusResponse sendVerificationCode(String phoneNumber, Integer codeLength, String payload) {
        log.info("Отправка верификационного кода через Telegram Gateway на номер: {}",
                phoneNumber.replaceAll("(\\d{1,3})(\\d{3})(\\d{3})(\\d+)", "$1***$2***$4"));

        try {
            Map<String, Object> requestBody = new HashMap<>();
            requestBody.put("phone_number", phoneNumber);
            requestBody.put("code_length", codeLength != null ? codeLength : 4);
            requestBody.put("ttl", gatewayProperties.getMessageTtl());

            if (payload != null && !payload.isEmpty()) {
                requestBody.put("payload", payload);
            }

            HttpHeaders headers = createHeaders();
            HttpEntity<Map<String, Object>> request = new HttpEntity<>(requestBody, headers);

            ResponseEntity<RequestStatusResponse> response = gatewayRestTemplate.exchange(
                    GATEWAY_BASE_URL + SEND_VERIFICATION_ENDPOINT,
                    HttpMethod.POST,
                    request,
                    RequestStatusResponse.class);

            RequestStatusResponse result = response.getBody();
            if (result != null && result.isOk()) {
                log.info("Верификационный код успешно отправлен. Request ID: {}, Cost: {}",
                        result.getResult().getRequestId(),
                        result.getResult().getRequestCost());
                return result;
            } else {
                log.error("Ошибка отправки через Gateway API: {}",
                        result != null ? result.getError() : "Unknown error");
                throw new RuntimeException("Ошибка отправки верификационного кода");
            }

        } catch (Exception e) {
            log.error("Исключение при отправке через Telegram Gateway: {}", e.getMessage(), e);
            throw new RuntimeException("Ошибка отправки верификационного кода", e);
        }
    }

    /**
     * Проверяет статус верификации и валидность введенного кода
     * 
     * @param requestId ID запроса от sendVerificationCode
     * @param userCode  код, введенный пользователем
     * @return статус верификации
     */
    public VerificationStatusResponse checkVerificationStatus(String requestId, String userCode) {
        log.debug("Проверка статуса верификации для request ID: {}", requestId);

        try {
            Map<String, Object> requestBody = new HashMap<>();
            requestBody.put("request_id", requestId);

            if (userCode != null && !userCode.isEmpty()) {
                requestBody.put("code", userCode);
            }

            HttpHeaders headers = createHeaders();
            HttpEntity<Map<String, Object>> request = new HttpEntity<>(requestBody, headers);

            ResponseEntity<VerificationStatusResponse> response = gatewayRestTemplate.exchange(
                    GATEWAY_BASE_URL + CHECK_STATUS_ENDPOINT,
                    HttpMethod.POST,
                    request,
                    VerificationStatusResponse.class);

            VerificationStatusResponse result = response.getBody();
            if (result != null && result.isOk()) {
                log.debug("Статус верификации получен: {}",
                        result.getResult().getVerificationStatus().getStatus());
                return result;
            } else {
                log.error("Ошибка проверки статуса: {}", result != null ? result.getError() : "Unknown error");
                throw new RuntimeException("Ошибка проверки статуса верификации");
            }

        } catch (Exception e) {
            log.error("Исключение при проверке статуса верификации: {}", e.getMessage(), e);
            throw new RuntimeException("Ошибка проверки статуса верификации", e);
        }
    }

    /**
     * Отзывает отправленное сообщение с кодом
     * 
     * @param requestId ID запроса для отзыва
     * @return true если запрос на отзыв принят
     */
    public boolean revokeVerificationMessage(String requestId) {
        log.info("Отзыв верификационного сообщения для request ID: {}", requestId);

        try {
            Map<String, Object> requestBody = new HashMap<>();
            requestBody.put("request_id", requestId);

            HttpHeaders headers = createHeaders();
            HttpEntity<Map<String, Object>> request = new HttpEntity<>(requestBody, headers);

            ResponseEntity<Map> response = gatewayRestTemplate.exchange(
                    GATEWAY_BASE_URL + REVOKE_MESSAGE_ENDPOINT,
                    HttpMethod.POST,
                    request,
                    Map.class);

            Map<String, Object> result = response.getBody();
            boolean success = result != null && Boolean.TRUE.equals(result.get("ok"));

            if (success) {
                log.info("Запрос на отзыв сообщения принят для request ID: {}", requestId);
            } else {
                log.warn("Не удалось отозвать сообщение для request ID: {}", requestId);
            }

            return success;

        } catch (Exception e) {
            log.error("Исключение при отзыве сообщения: {}", e.getMessage(), e);
            return false;
        }
    }

    /**
     * Проверяет возможность отправки на указанный номер
     * 
     * @param phoneNumber номер телефона
     * @return информация о возможности отправки
     */
    public RequestStatusResponse checkSendAbility(String phoneNumber) {
        log.debug("Проверка возможности отправки на номер: {}",
                phoneNumber.replaceAll("(\\d{1,3})(\\d{3})(\\d{3})(\\d+)", "$1***$2***$4"));

        try {
            Map<String, Object> requestBody = new HashMap<>();
            requestBody.put("phone_number", phoneNumber);

            HttpHeaders headers = createHeaders();
            HttpEntity<Map<String, Object>> request = new HttpEntity<>(requestBody, headers);

            ResponseEntity<RequestStatusResponse> response = gatewayRestTemplate.exchange(
                    GATEWAY_BASE_URL + "/checkSendAbility",
                    HttpMethod.POST,
                    request,
                    RequestStatusResponse.class);

            return response.getBody();

        } catch (Exception e) {
            log.error("Исключение при проверке возможности отправки: {}", e.getMessage(), e);
            throw new RuntimeException("Ошибка проверки возможности отправки", e);
        }
    }

    private HttpHeaders createHeaders() {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.setBearerAuth(gatewayProperties.getAccessToken());
        return headers;
    }
}