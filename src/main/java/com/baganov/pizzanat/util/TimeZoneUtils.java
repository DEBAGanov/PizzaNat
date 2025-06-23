/**
 * @file: TimeZoneUtils.java
 * @description: Утилитный класс для работы с временными зонами
 * @dependencies: Java Time API
 * @created: 2025-06-23
 */
package com.baganov.pizzanat.util;

import lombok.experimental.UtilityClass;
import lombok.extern.slf4j.Slf4j;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;

/**
 * Утилитный класс для работы с временными зонами.
 * Обеспечивает единообразную работу со временем в московской временной зоне.
 */
@Slf4j
@UtilityClass
public class TimeZoneUtils {

    /**
     * Московская временная зона
     */
    public static final ZoneId MOSCOW_ZONE = ZoneId.of("Europe/Moscow");

    /**
     * Получение текущего времени в московской временной зоне
     *
     * @return LocalDateTime в московской временной зоне
     */
    public static LocalDateTime nowInMoscow() {
        return ZonedDateTime.now(MOSCOW_ZONE).toLocalDateTime();
    }

    /**
     * Конвертация LocalDateTime в московскую временную зону
     *
     * @param dateTime   время для конвертации
     * @param sourceZone исходная временная зона
     * @return LocalDateTime в московской временной зоне
     */
    public static LocalDateTime convertToMoscow(LocalDateTime dateTime, ZoneId sourceZone) {
        if (dateTime == null) {
            return null;
        }

        ZonedDateTime sourceZoned = dateTime.atZone(sourceZone);
        return sourceZoned.withZoneSameInstant(MOSCOW_ZONE).toLocalDateTime();
    }

    /**
     * Конвертация UTC времени в московское
     *
     * @param utcDateTime время в UTC
     * @return LocalDateTime в московской временной зоне
     */
    public static LocalDateTime convertUtcToMoscow(LocalDateTime utcDateTime) {
        return convertToMoscow(utcDateTime, ZoneId.of("UTC"));
    }

    /**
     * Получение текущей временной зоны системы
     *
     * @return строковое представление временной зоны
     */
    public static String getSystemTimeZone() {
        return ZoneId.systemDefault().toString();
    }

    /**
     * Логирование информации о временных зонах (для диагностики)
     */
    public static void logTimeZoneInfo() {
        log.info("Системная временная зона: {}", getSystemTimeZone());
        log.info("Московское время: {}", nowInMoscow());
        log.info("UTC время: {}", LocalDateTime.now(ZoneId.of("UTC")));
        log.info("Системное время: {}", LocalDateTime.now());
    }
}