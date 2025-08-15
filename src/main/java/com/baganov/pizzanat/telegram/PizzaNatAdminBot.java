/**
 * @file: PizzaNatAdminBot.java
 * @description: Telegram бот для администраторов пиццерии с поддержкой условного включения
 * @dependencies: TelegramBots API, TelegramAdminBotConfig
 * @created: 2025-06-13
 * @updated: 2025-01-15 - добавлена поддержка условного включения через переменные окружения
 */
package com.baganov.pizzanat.telegram;

import com.baganov.pizzanat.config.TelegramAdminBotConfig;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import org.telegram.telegrambots.bots.TelegramLongPollingBot;
import org.telegram.telegrambots.meta.api.methods.AnswerCallbackQuery;
import org.telegram.telegrambots.meta.api.methods.send.SendMessage;
import org.telegram.telegrambots.meta.api.objects.CallbackQuery;
import org.telegram.telegrambots.meta.api.objects.Message;
import org.telegram.telegrambots.meta.api.objects.Update;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.InlineKeyboardMarkup;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.InlineKeyboardButton;
import org.telegram.telegrambots.meta.exceptions.TelegramApiException;

import java.util.ArrayList;
import java.util.List;

@Slf4j
@Component
@ConditionalOnProperty(name = "telegram.admin-bot.enabled", havingValue = "true", matchIfMissing = false)
public class PizzaNatAdminBot extends TelegramLongPollingBot {

    private final TelegramAdminBotConfig botConfig;

    @Autowired(required = false)
    private AdminBotCallbackHandler callbackHandler;

    public PizzaNatAdminBot(TelegramAdminBotConfig botConfig) {
        super(botConfig.getToken());
        this.botConfig = botConfig;

        // ДИАГНОСТИКА: Логируем токен админского бота
        String token = botConfig.getCleanToken();
        log.info("🔍 ДИАГНОСТИКА: Админский бот (PizzaNatAdminBot) использует токен: {}...",
                token != null && token.length() > 10 ? token.substring(0, 10) : "NULL");
        log.info("🤖 PizzaNat Admin Bot инициализирован для Long Polling");
    }

    @Override
    public String getBotToken() {
        return botConfig.getCleanToken();
    }

    @Override
    public String getBotUsername() {
        return botConfig.getCleanUsername();
    }

    @Override
    public void onUpdateReceived(Update update) {
        try {
            if (update.hasMessage()) {
                handleMessage(update.getMessage());
            } else if (update.hasCallbackQuery()) {
                handleCallbackQuery(update.getCallbackQuery());
            }
        } catch (Exception e) {
            log.error("Ошибка при обработке обновления в админском боте: {}", e.getMessage(), e);
        }
    }

    /**
     * Обработка текстовых сообщений
     */
    private void handleMessage(Message message) {
        String messageText = message.getText();
        Long chatId = message.getChatId();
        String username = message.getFrom().getUserName();
        String firstName = message.getFrom().getFirstName();

        log.info("Получено сообщение в админском боте от {}: {}", username, messageText);

        if (messageText.startsWith("/start")) {
            handleStartCommand(chatId, username, firstName);
        } else if (messageText.startsWith("/help")) {
            handleHelpCommand(chatId);
        } else if (messageText.startsWith("/register")) {
            handleRegisterCommand(chatId, username, firstName);
        } else if (messageText.startsWith("/stats")) {
            handleStatsCommand(chatId);
        } else if (messageText.startsWith("/orders")) {
            handleOrdersCommand(chatId);
        } else if (messageText.startsWith("/message ")) {
            handleBroadcastCommand(chatId, messageText);
        } else {
            handleUnknownCommand(chatId);
        }
    }

    /**
     * Обработка callback запросов (нажатия на кнопки)
     */
    private void handleCallbackQuery(CallbackQuery callbackQuery) {
        Long chatId = callbackQuery.getMessage().getChatId();
        Integer messageId = callbackQuery.getMessage().getMessageId();
        String callbackData = callbackQuery.getData();
        String callbackQueryId = callbackQuery.getId();

        log.debug("Получен callback: chatId={}, data={}", chatId, callbackData);

        try {
            // Отвечаем на callback query для предотвращения дублированных вызовов
            AnswerCallbackQuery answerCallbackQuery = new AnswerCallbackQuery();
            answerCallbackQuery.setCallbackQueryId(callbackQueryId);
            answerCallbackQuery.setText("Обрабатываем...");
            execute(answerCallbackQuery);

            if (callbackHandler != null) {
                callbackHandler.handleCallback(chatId, messageId, callbackData);
            } else {
                log.warn("AdminBotCallbackHandler не инициализирован");
            }
        } catch (TelegramApiException e) {
            log.error("Ошибка обработки callback query: {}", e.getMessage());
        }
    }

    /**
     * Команда /start
     */
    private void handleStartCommand(Long chatId, String username, String firstName) {
        String welcomeMessage = String.format(
                "🍕 *Добро пожаловать в PizzaNat Admin Bot!*\n\n" +
                        "Привет, %s! 👋\n\n" +
                        "Этот бот предназначен для сотрудников PizzaNat.\n" +
                        "Здесь вы будете получать уведомления о новых заказах и сможете управлять их статусами.\n\n" +
                        "*Доступные команды:*\n" +
                        "/register - Зарегистрироваться как администратор\n" +
                        "/help - Показать справку\n" +
                        "/stats - Показать статистику заказов\n" +
                        "/orders - Показать активные заказы\n\n" +
                        "Для начала работы выполните команду /register",
                firstName != null ? firstName : "Администратор");

        sendMessage(chatId, welcomeMessage, true);
    }

    /**
     * Команда /help
     */
    private void handleHelpCommand(Long chatId) {
        String helpMessage = "📋 *Справка по командам PizzaNat Admin Bot*\n\n" +
                "*Основные команды:*\n" +
                "/start - Начать работу с ботом\n" +
                "/register - Зарегистрироваться как администратор\n" +
                "/help - Показать эту справку\n" +
                "/stats - Показать статистику заказов за сегодня\n" +
                "/orders - Показать список активных заказов\n" +
                "/message <текст> - Массовая рассылка от @DIMBOpizzaBot\n\n" +
                "*Функции бота:*\n" +
                "• 🔔 Автоматические уведомления о новых заказах\n" +
                "• ⚡ Быстрое изменение статуса заказа через кнопки\n" +
                "• 📊 Просмотр статистики и активных заказов\n" +
                "• 👥 Управление доступом администраторов\n" +
                "• 📢 Массовая рассылка от @DIMBOpizzaBot (с соблюдением лимитов)\n\n" +
                "*Статусы заказов:*\n" +
                "🆕 PENDING - Новый заказ\n" +
                "✅ CONFIRMED - Подтвержден\n" +
                "👨‍🍳 PREPARING - Готовится\n" +
                "🍕 READY - Готов к выдаче\n" +
                "🚗 DELIVERING - Доставляется\n" +
                "✅ DELIVERED - Доставлен\n" +
                "❌ CANCELLED - Отменен";

        sendMessage(chatId, helpMessage, true);
    }

    /**
     * Обработка команды /register
     */
    private void handleRegisterCommand(Long chatId, String username, String firstName) {
        if (callbackHandler != null) {
            callbackHandler.handleCommand("/register", chatId, username, firstName);
        } else {
            sendMessage(chatId, "❌ Сервис временно недоступен", false);
        }
    }

    /**
     * Обработка команды /stats
     */
    private void handleStatsCommand(Long chatId) {
        if (callbackHandler != null) {
            callbackHandler.handleCommand("/stats", chatId, null, null);
        } else {
            sendMessage(chatId, "❌ Сервис временно недоступен", false);
        }
    }

    /**
     * Обработка команды /orders
     */
    private void handleOrdersCommand(Long chatId) {
        if (callbackHandler != null) {
            callbackHandler.handleCommand("/orders", chatId, null, null);
        } else {
            sendMessage(chatId, "❌ Сервис временно недоступен", false);
        }
    }

    /**
     * Команда /message для массовой рассылки
     */
    private void handleBroadcastCommand(Long chatId, String messageText) {
        // Проверяем, что администратор зарегистрирован
        if (callbackHandler == null) {
            sendMessage(chatId, "❌ Сервис недоступен", false);
            return;
        }

        // Извлекаем текст сообщения после команды
        String broadcastText = messageText.substring("/message ".length()).trim();
        
        if (broadcastText.isEmpty()) {
            sendMessage(chatId, "❌ Укажите текст сообщения\n\nИспользование: /message Ваш текст", false);
            return;
        }

        try {
            // Отправляем запрос в AdminBotService для рассылки
            if (callbackHandler != null) {
                callbackHandler.handleBroadcastMessage(chatId, broadcastText);
            }
        } catch (Exception e) {
            log.error("Ошибка обработки команды /message: {}", e.getMessage(), e);
            sendMessage(chatId, "❌ Ошибка при отправке сообщения", false);
        }
    }

    /**
     * Неизвестная команда
     */
    private void handleUnknownCommand(Long chatId) {
        String message = "❓ *Неизвестная команда*\n\n" +
                "Используйте /help для просмотра доступных команд.";
        sendMessage(chatId, message, true);
    }

    /**
     * Отправка сообщения
     */
    public void sendMessage(Long chatId, String text, boolean parseMarkdown) {
        try {
            SendMessage message = new SendMessage();
            message.setChatId(chatId.toString());
            message.setText(text);

            if (parseMarkdown) {
                message.setParseMode("Markdown");
            }

            execute(message);
            log.debug("Сообщение отправлено в админский бот: chatId={}", chatId);
        } catch (TelegramApiException e) {
            log.error("Ошибка отправки сообщения в админский бот: chatId={}, error={}", chatId, e.getMessage());
        }
    }

    /**
     * Отправка сообщения с inline кнопками
     */
    public void sendMessageWithButtons(Long chatId, String text, InlineKeyboardMarkup keyboard) {
        try {
            SendMessage message = new SendMessage();
            message.setChatId(chatId.toString());
            message.setText(text);
            message.setParseMode("Markdown");
            message.setReplyMarkup(keyboard);

            execute(message);
            log.debug("Сообщение с кнопками отправлено в админский бот: chatId={}", chatId);
        } catch (TelegramApiException e) {
            log.error("Ошибка отправки сообщения с кнопками в админский бот: chatId={}, error={}", chatId,
                    e.getMessage());
        }
    }

    /**
     * Создание inline клавиатуры для управления заказом
     */
    public InlineKeyboardMarkup createOrderManagementKeyboard(Long orderId) {
        InlineKeyboardMarkup keyboard = new InlineKeyboardMarkup();
        List<List<InlineKeyboardButton>> rows = new ArrayList<>();

        // Первая строка - статусы заказа
        List<InlineKeyboardButton> row1 = new ArrayList<>();
        row1.add(createButton("✅ Подтвердить", "order_status_" + orderId + "_CONFIRMED"));
        row1.add(createButton("👨‍🍳 Готовится", "order_status_" + orderId + "_PREPARING"));
        rows.add(row1);

        // Вторая строка - статусы заказа
        List<InlineKeyboardButton> row2 = new ArrayList<>();
        row2.add(createButton("🍕 Готов", "order_status_" + orderId + "_READY"));
        row2.add(createButton("🚗 Доставляется", "order_status_" + orderId + "_DELIVERING"));
        rows.add(row2);

        // Третья строка - финальные статусы
        List<InlineKeyboardButton> row3 = new ArrayList<>();
        row3.add(createButton("✅ Доставлен", "order_status_" + orderId + "_DELIVERED"));
        row3.add(createButton("❌ Отменить", "order_status_" + orderId + "_CANCELLED"));
        rows.add(row3);

        // Четвертая строка - дополнительные действия
        List<InlineKeyboardButton> row4 = new ArrayList<>();
        row4.add(createButton("📋 Детали", "order_details_" + orderId));
        rows.add(row4);

        keyboard.setKeyboard(rows);
        return keyboard;
    }

    /**
     * Создание inline кнопки
     */
    private InlineKeyboardButton createButton(String text, String callbackData) {
        InlineKeyboardButton button = new InlineKeyboardButton();
        button.setText(text);
        button.setCallbackData(callbackData);
        return button;
    }
}