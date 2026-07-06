package com.yourserver.customenchants.util;

/**
 * Minimal roman numeral converter, good for enchant levels (1-20ish).
 */
public final class RomanNumeral {

    private static final int[] VALUES = {1000, 900, 500, 400, 100, 90, 50, 40, 10, 9, 5, 4, 1};
    private static final String[] SYMBOLS = {"M", "CM", "D", "CD", "C", "XC", "L", "XL", "X", "IX", "V", "IV", "I"};

    private RomanNumeral() {
    }

    public static String toRoman(int number) {
        if (number <= 0) {
            return String.valueOf(number);
        }
        StringBuilder sb = new StringBuilder();
        int remaining = number;
        for (int i = 0; i < VALUES.length; i++) {
            while (remaining >= VALUES[i]) {
                remaining -= VALUES[i];
                sb.append(SYMBOLS[i]);
            }
        }
        return sb.toString();
    }
}
