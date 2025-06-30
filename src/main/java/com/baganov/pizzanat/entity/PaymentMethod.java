/**
 * @file: PaymentMethod.java
 * @description: Enum для методов оплаты в ЮKassa
 * @dependencies: JPA
 * @created: 2025-01-23
 */
package com.baganov.pizzanat.entity;

/**
 * Методы оплаты, поддерживаемые ЮKassa
 */
public enum PaymentMethod {

    /**
     * Система Быстрых Платежей (основной метод для PizzaNat)
     */
    SBP("sbp", "СБП", "Система Быстрых Платежей", true),

    /**
     * Банковская карта
     */
    BANK_CARD("bank_card", "Банковская карта", "Оплата банковской картой", false),

    /**
     * ЮMoney (бывший Яндекс.Деньги)
     */
    YOOMONEY("yoo_money", "ЮMoney", "Электронный кошелек ЮMoney", false),

    /**
     * QIWI кошелек
     */
    QIWI("qiwi", "QIWI", "Электронный кошелек QIWI", false),

    /**
     * WebMoney
     */
    WEBMONEY("webmoney", "WebMoney", "Электронный кошелек WebMoney", false),

    /**
     * Альфа-Клик
     */
    ALFABANK("alfabank", "Альфа-Клик", "Интернет-банк Альфа-Банка", false),

    /**
     * Сбербанк Онлайн
     */
    SBERBANK("sberbank", "Сбербанк Онлайн", "Интернет-банк Сбербанка", false);

    private final String yookassaMethod;
    private final String displayName;
    private final String description;
    private final boolean isPrimary;

    PaymentMethod(String yookassaMethod, String displayName, String description, boolean isPrimary) {
        this.yookassaMethod = yookassaMethod;
        this.displayName = displayName;
        this.description = description;
        this.isPrimary = isPrimary;
    }

    /**
     * Возвращает метод в формате ЮKassa API
     */
    public String getYookassaMethod() {
        return yookassaMethod;
    }

    /**
     * Возвращает название для отображения пользователю
     */
    public String getDisplayName() {
        return displayName;
    }

    /**
     * Возвращает описание метода оплаты
     */
    public String getDescription() {
        return description;
    }

    /**
     * Проверяет, является ли метод основным для PizzaNat
     */
    public boolean isPrimary() {
        return isPrimary;
    }

    /**
     * Создает PaymentMethod из метода ЮKassa
     */
    public static PaymentMethod fromYookassaMethod(String method) {
        if (method == null) {
            return SBP; // По умолчанию СБП
        }

        for (PaymentMethod paymentMethod : values()) {
            if (paymentMethod.yookassaMethod.equals(method)) {
                return paymentMethod;
            }
        }

        // Если метод неизвестен, возвращаем СБП
        return SBP;
    }

    /**
     * Возвращает все основные методы оплаты
     */
    public static PaymentMethod[] getPrimaryMethods() {
        return new PaymentMethod[] { SBP };
    }

    /**
     * Проверяет, поддерживает ли метод выбор банка
     */
    public boolean supportsBankSelection() {
        return this == SBP;
    }
}