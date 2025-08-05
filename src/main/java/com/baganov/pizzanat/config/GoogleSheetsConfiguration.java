/**
 * @file: GoogleSheetsConfiguration.java
 * @description: Конфигурация Google Sheets API интеграции
 * @dependencies: Spring Configuration, Google Sheets API
 * @created: 2025-01-28
 */
package com.baganov.pizzanat.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Data
@Configuration
@ConfigurationProperties(prefix = "google.sheets")
public class GoogleSheetsConfiguration {
    
    /**
     * Включена ли интеграция с Google Sheets
     */
    private boolean enabled = false;
    
    /**
     * ID Google таблицы (из URL)
     */
    private String spreadsheetId;
    
    /**
     * Название листа в таблице
     */
    private String sheetName = "Заказы";
    
    /**
     * Путь к файлу с credentials
     */
    private String credentialsPath = "/app/config/google-credentials.json";
    
    /**
     * Название приложения для Google API
     */
    private String applicationName = "PizzaNat Order Tracker";
    
    /**
     * Timeout для HTTP запросов (мс)
     */
    private int connectTimeout = 10000;
    
    /**
     * Timeout для чтения ответа (мс)
     */
    private int readTimeout = 30000;
    
    /**
     * Максимальное количество попыток при ошибках
     */
    private int maxRetryAttempts = 3;
    
    /**
     * Задержка между попытками (мс)
     */
    private int retryDelay = 1000;
}