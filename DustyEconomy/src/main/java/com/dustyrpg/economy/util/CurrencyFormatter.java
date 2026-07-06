package com.dustyrpg.economy.util;

import java.util.Locale;

/**
 * Reimplements the {@code formatCurrency()} and {@code formatCredits()}
 * functions from the original Skript.
 */
public final class CurrencyFormatter {

    private CurrencyFormatter() {
    }

    /**
     * Floors each tier (k / M / B / T) and drops the decimal point when the
     * result is a whole number, e.g. 1000 -> "1k", 1500 -> "1.5k".
     */
    public static String formatCurrency(double value) {
        if (value < 1_000) {
            return trim(value);
        }
        if (value < 1_000_000) {
            return floorTier(value, 100) + "k";
        }
        if (value < 1_000_000_000) {
            return floorTier(value, 100_000) + "M";
        }
        if (value < 1_000_000_000_000L) {
            return floorTier(value, 100_000_000) + "B";
        }
        return floorTier(value, 100_000_000_000L) + "T";
    }

    /**
     * Rounds each tier instead of flooring, and always keeps one decimal
     * place (matches the original function, which never trims ".0").
     */
    public static String formatCredits(double value) {
        if (value < 1_000) {
            return trim(value);
        }
        if (value < 1_000_000) {
            return roundTier(value, 100) + "k";
        }
        if (value < 1_000_000_000) {
            return roundTier(value, 100_000) + "M";
        }
        if (value < 1_000_000_000_000L) {
            return roundTier(value, 100_000_000) + "B";
        }
        return roundTier(value, 100_000_000_000L) + "T";
    }

    private static String floorTier(double value, long divisor) {
        double n = Math.floor(value / divisor) / 10.0;
        if (n == Math.floor(n)) {
            return String.valueOf((long) n);
        }
        return trim(n);
    }

    private static double roundTier(double value, long divisor) {
        return Math.round(value / divisor) / 10.0;
    }

    private static String trim(double value) {
        if (value == Math.floor(value) && !Double.isInfinite(value)) {
            return String.format(Locale.US, "%d", (long) value);
        }
        return String.valueOf(value);
    }
}
