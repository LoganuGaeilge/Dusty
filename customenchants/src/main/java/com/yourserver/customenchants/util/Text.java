package com.yourserver.customenchants.util;

import org.bukkit.ChatColor;

import java.util.Map;

public final class Text {

    private Text() {
    }

    public static String color(String s) {
        if (s == null) return "";
        return ChatColor.translateAlternateColorCodes('&', s);
    }

    public static String placeholders(String input, Map<String, String> values) {
        String result = input;
        for (Map.Entry<String, String> e : values.entrySet()) {
            result = result.replace(e.getKey(), e.getValue());
        }
        return result;
    }
}
