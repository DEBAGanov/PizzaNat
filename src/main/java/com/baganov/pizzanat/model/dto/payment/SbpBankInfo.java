/**
 * @file: SbpBankInfo.java
 * @description: DTO для информации о банках, поддерживающих СБП
 * @dependencies: Jackson annotations
 * @created: 2025-01-26
 */
package com.baganov.pizzanat.model.dto.payment;

import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * DTO для информации о банке, поддерживающем СБП
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public class SbpBankInfo {

    /**
     * ID банка в ЮKassa (например: sberbank, tinkoff, vtb)
     */
    private String bankId;

    /**
     * Название банка для отображения
     */
    private String name;

    /**
     * Краткое название банка
     */
    private String shortName;

    /**
     * URL логотипа банка
     */
    private String logoUrl;

    /**
     * Цвет банка в hex формате
     */
    private String brandColor;

    /**
     * Популярность банка (для сортировки)
     */
    private Integer priority;

    /**
     * Доступность банка
     */
    private Boolean available;

    // Конструкторы
    public SbpBankInfo() {
    }

    public SbpBankInfo(String bankId, String name, String shortName) {
        this.bankId = bankId;
        this.name = name;
        this.shortName = shortName;
        this.available = true;
    }

    public SbpBankInfo(String bankId, String name, String shortName,
            String logoUrl, String brandColor, Integer priority) {
        this.bankId = bankId;
        this.name = name;
        this.shortName = shortName;
        this.logoUrl = logoUrl;
        this.brandColor = brandColor;
        this.priority = priority;
        this.available = true;
    }

    // Геттеры и сеттеры
    public String getBankId() {
        return bankId;
    }

    public void setBankId(String bankId) {
        this.bankId = bankId;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getShortName() {
        return shortName;
    }

    public void setShortName(String shortName) {
        this.shortName = shortName;
    }

    public String getLogoUrl() {
        return logoUrl;
    }

    public void setLogoUrl(String logoUrl) {
        this.logoUrl = logoUrl;
    }

    public String getBrandColor() {
        return brandColor;
    }

    public void setBrandColor(String brandColor) {
        this.brandColor = brandColor;
    }

    public Integer getPriority() {
        return priority;
    }

    public void setPriority(Integer priority) {
        this.priority = priority;
    }

    public Boolean getAvailable() {
        return available;
    }

    public void setAvailable(Boolean available) {
        this.available = available;
    }

    // Utility методы
    public boolean isAvailable() {
        return available != null && available;
    }

    public boolean hasLogo() {
        return logoUrl != null && !logoUrl.trim().isEmpty();
    }

    public boolean hasBrandColor() {
        return brandColor != null && !brandColor.trim().isEmpty();
    }

    /**
     * Создает объект для популярных банков
     */
    public static SbpBankInfo createPopularBank(String bankId, String name,
            String shortName, Integer priority) {
        SbpBankInfo bank = new SbpBankInfo(bankId, name, shortName);
        bank.setPriority(priority);
        return bank;
    }

    /**
     * Список популярных банков для СБП
     */
    public static SbpBankInfo[] getPopularBanks() {
        return new SbpBankInfo[] {
                createPopularBank("sberbank", "Сбербанк", "Сбербанк", 1),
                createPopularBank("tinkoff", "Тинькофф Банк", "Тинькофф", 2),
                createPopularBank("vtb", "ВТБ", "ВТБ", 3),
                createPopularBank("alfabank", "Альфа-Банк", "Альфа-Банк", 4),
                createPopularBank("raiffeisen", "Райффайзенбанк", "Райффайзен", 5),
                createPopularBank("gazprombank", "Газпромбанк", "Газпромбанк", 6),
                createPopularBank("rosbank", "Росбанк", "Росбанк", 7),
                createPopularBank("otkritie", "Банк Открытие", "Открытие", 8)
        };
    }

    @Override
    public String toString() {
        return "SbpBankInfo{" +
                "bankId='" + bankId + '\'' +
                ", name='" + name + '\'' +
                ", shortName='" + shortName + '\'' +
                ", priority=" + priority +
                ", available=" + available +
                '}';
    }
}