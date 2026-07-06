package com.dustyrpg.economy;

/**
 * The four currencies tracked by the plugin. Mirrors the {@code money},
 * {@code orbs}, {@code souls} and {@code credits} variable lists from the
 * original Skript.
 */
public enum Currency {

    MONEY("money", "&a$", "dollars"),
    ORBS("orbs", "&b", "Orbs"),
    SOULS("souls", "&d", "Souls"),
    CREDITS("credits", "&e", "Credits");

    private final String key;
    private final String colorPrefix;
    private final String displayName;

    Currency(String key, String colorPrefix, String displayName) {
        this.key = key;
        this.colorPrefix = colorPrefix;
        this.displayName = displayName;
    }

    public String getKey() {
        return key;
    }

    public String getColorPrefix() {
        return colorPrefix;
    }

    public String getDisplayName() {
        return displayName;
    }

    public static Currency fromKey(String key) {
        for (Currency currency : values()) {
            if (currency.key.equalsIgnoreCase(key)) {
                return currency;
            }
        }
        return null;
    }
}
