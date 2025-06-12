/**
 * @file: TelegramBotIntegrationService.java
 * @description: Сервис для интеграции Long Polling бота с существующим webhook сервисом
 * @dependencies: PizzaNatTelegramBot, TelegramWebhookService
 * @created: 2025-01-11
 */
package com.baganov.pizzanat.service;

import com.baganov.pizzanat.model.dto.telegram.TelegramUpdate;
import com.baganov.pizzanat.model.dto.telegram.TelegramUserData;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * Сервис для интеграции между Long Polling ботом и webhook сервисом.
 * Позволяет использовать оба подхода одновременно.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class TelegramBotIntegrationService {

    private final TelegramWebhookService telegramWebhookService;
    private final TelegramAuthService telegramAuthService;

    /**
     * Инициализация авторизации для пользователя
     *
     * @param userData данные пользователя
     * @return токен авторизации
     */
    public String initializeAuth(TelegramUserData userData) {
        log.debug("Инициализация авторизации для пользователя: {}", userData.getId());
        try {
            return telegramAuthService.initializeAuth(userData);
        } catch (Exception e) {
            log.error("Ошибка инициализации авторизации для пользователя {}: {}", userData.getId(), e.getMessage());
            throw e;
        }
    }

    /**
     * Обновление пользователя с номером телефона
     *
     * @param userData данные пользователя с номером телефона
     */
    public void updateUserWithPhone(TelegramUserData userData) {
        log.debug("Обновление пользователя {} с номером телефона", userData.getId());
        try {
            telegramAuthService.updateUserWithPhoneNumber(userData);
        } catch (Exception e) {
            log.error("Ошибка обновления пользователя {} с номером телефона: {}", userData.getId(), e.getMessage());
            throw e;
        }
    }

    /**
     * Подтверждение авторизации
     *
     * @param authToken токен авторизации
     * @return true если авторизация успешна
     */
    public boolean confirmAuth(String authToken) {
        log.debug("Подтверждение авторизации с токеном: {}", authToken);
        try {
            telegramAuthService.confirmAuth(authToken);
            return true;
        } catch (Exception e) {
            log.error("Ошибка подтверждения авторизации с токеном {}: {}", authToken, e.getMessage());
            return false;
        }
    }

    /**
     * Обработка обновления через webhook сервис
     * Используется для совместимости с существующей логикой
     *
     * @param update обновление от Telegram
     */
    public void processUpdateViaWebhook(TelegramUpdate update) {
        log.debug("Передача обновления {} в webhook сервис", update.getUpdateId());
        try {
            telegramWebhookService.processUpdate(update);
        } catch (Exception e) {
            log.error("Ошибка при обработке обновления через webhook сервис: {}", e.getMessage(), e);
        }
    }

    /**
     * Конвертация Telegram API Update в наш TelegramUpdate DTO
     *
     * @param apiUpdate обновление от Telegram API
     * @return наш DTO
     */
    public TelegramUpdate convertApiUpdateToDto(org.telegram.telegrambots.meta.api.objects.Update apiUpdate) {
        TelegramUpdate.TelegramUpdateBuilder builder = TelegramUpdate.builder()
                .updateId(apiUpdate.getUpdateId().longValue());

        // Конвертация сообщения
        if (apiUpdate.hasMessage()) {
            org.telegram.telegrambots.meta.api.objects.Message apiMessage = apiUpdate.getMessage();

            TelegramUpdate.TelegramMessage message = TelegramUpdate.TelegramMessage.builder()
                    .messageId(apiMessage.getMessageId().longValue())
                    .text(apiMessage.getText())
                    .date(apiMessage.getDate().longValue())
                    .from(convertUser(apiMessage.getFrom()))
                    .chat(convertChat(apiMessage.getChat()))
                    .build();

            // Добавляем контакт если есть
            if (apiMessage.hasContact()) {
                org.telegram.telegrambots.meta.api.objects.Contact apiContact = apiMessage.getContact();
                TelegramUpdate.TelegramContact contact = TelegramUpdate.TelegramContact.builder()
                        .phoneNumber(apiContact.getPhoneNumber())
                        .firstName(apiContact.getFirstName())
                        .lastName(apiContact.getLastName())
                        .userId(apiContact.getUserId())
                        .build();
                message.setContact(contact);
            }

            builder.message(message);
        }

        // Конвертация callback query
        if (apiUpdate.hasCallbackQuery()) {
            org.telegram.telegrambots.meta.api.objects.CallbackQuery apiCallback = apiUpdate.getCallbackQuery();

            TelegramUpdate.TelegramCallbackQuery callbackQuery = TelegramUpdate.TelegramCallbackQuery.builder()
                    .id(apiCallback.getId())
                    .data(apiCallback.getData())
                    .from(convertUser(apiCallback.getFrom()))
                    .build();

            // Добавляем сообщение из callback query
            if (apiCallback.getMessage() != null) {
                org.telegram.telegrambots.meta.api.objects.Message apiMessage = apiCallback.getMessage();
                TelegramUpdate.TelegramMessage message = TelegramUpdate.TelegramMessage.builder()
                        .messageId(apiMessage.getMessageId().longValue())
                        .text(apiMessage.getText())
                        .date(apiMessage.getDate().longValue())
                        .from(convertUser(apiMessage.getFrom()))
                        .chat(convertChat(apiMessage.getChat()))
                        .build();
                callbackQuery.setMessage(message);
            }

            builder.callbackQuery(callbackQuery);
        }

        return builder.build();
    }

    /**
     * Конвертация пользователя
     */
    private com.baganov.pizzanat.model.dto.telegram.TelegramUserData convertUser(
            org.telegram.telegrambots.meta.api.objects.User apiUser) {
        if (apiUser == null) {
            return null;
        }

        return com.baganov.pizzanat.model.dto.telegram.TelegramUserData.builder()
                .id(apiUser.getId())
                .username(apiUser.getUserName())
                .firstName(apiUser.getFirstName())
                .lastName(apiUser.getLastName())
                .build();
    }

    /**
     * Конвертация чата
     */
    private TelegramUpdate.TelegramChat convertChat(org.telegram.telegrambots.meta.api.objects.Chat apiChat) {
        if (apiChat == null) {
            return null;
        }

        return TelegramUpdate.TelegramChat.builder()
                .id(apiChat.getId())
                .type(apiChat.getType())
                .title(apiChat.getTitle())
                .username(apiChat.getUserName())
                .build();
    }
}