/**
 * @file: PaymentStatus.java
 * @description: Enum для статусов платежей в системе YooKassa
 * @dependencies: Entity Payment
 * @created: 2025-01-26
 */
package com.baganov.pizzanat.entity;

/**
 * Статусы платежей в соответствии с YooKassa API
 * 
 * @see <a href=
 *      "https://yookassa.ru/developers/api#payment_object-status">YooKassa
 *      Payment Status</a>
 */
public enum PaymentStatus {
    /**
     * Платеж создан, ожидает подтверждения от пользователя
     */
    PENDING("pending", "Ожидает оплаты"),

    /**
     * Платеж ожидает захвата (для двухстадийных платежей)
     */
    WAITING_FOR_CAPTURE("waiting_for_capture", "Ожидает захвата"),

    /**
     * Платеж успешно завершен
     */
    SUCCEEDED("succeeded", "Успешно оплачен"),

    /**
     * Платеж отменен пользователем или системой
     */
    CANCELLED("canceled", "Отменен"),

    /**
     * Платеж завершился ошибкой
     */
    FAILED("failed", "Ошибка оплаты");

    private final String yookassaStatus;
    private final String description;

    PaymentStatus(String yookassaStatus, String description) {
        this.yookassaStatus = yookassaStatus;
        this.description = description;
    }

    /**
     * Получить статус в формате YooKassa API
     * 
     * @return статус для API запросов
     */
    public String getYookassaStatus() {
        return yookassaStatus;
    }

    /**
     * Получить описание статуса на русском языке
     * 
     * @return описание статуса
     */
    public String getDescription() {
        return description;
    }

    /**
     * Преобразовать статус из YooKassa API в enum
     * 
     * @param yookassaStatus статус из YooKassa
     * @return соответствующий enum
     * @throws IllegalArgumentException если статус не найден
     */
    public static PaymentStatus fromYookassaStatus(String yookassaStatus) {
        for (PaymentStatus status : values()) {
            if (status.yookassaStatus.equals(yookassaStatus)) {
                return status;
            }
        }
        throw new IllegalArgumentException("Неизвестный статус платежа: " + yookassaStatus);
    }

    /**
     * Проверить, является ли платеж завершенным (успешно или с ошибкой)
     * 
     * @return true если платеж завершен
     */
    public boolean isCompleted() {
        return this == SUCCEEDED || this == CANCELLED || this == FAILED;
    }

    /**
     * Проверить, является ли платеж успешным
     * 
     * @return true если платеж успешен
     */
    public boolean isSuccessful() {
        return this == SUCCEEDED;
    }

    /**
     * Проверить, можно ли отменить платеж
     * 
     * @return true если платеж можно отменить
     */
    public boolean isCancellable() {
        return this == PENDING || this == WAITING_FOR_CAPTURE;
    }

    @Override
    public String toString() {
        return description + " (" + yookassaStatus + ")";
    }
}