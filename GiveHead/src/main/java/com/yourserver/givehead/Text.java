package com.yourserver.givehead;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.bukkit.ChatColor;

public final class Text {

    // Matches a Minecraft color/format code using either the raw '&'
    // trigger or the already-translated '\u00A7' (section) char, e.g.
    // "&6", "&l", "\u00A76". These are what break the ALL_CAPS check when
    // someone writes something like "&6PIG" in config.yml.
    private static final Pattern COLOR_CODE = Pattern.compile("[&\u00A7][0-9A-FK-ORa-fk-or]");

    private Text() {
    }

    public static String color(String input) {
        return ChatColor.translateAlternateColorCodes('&', input);
    }

    /**
     * Turns any ALL_CAPS_WITH_UNDERSCORES word(s) in the input into Title
     * Case - e.g. "ZOMBIE_PIGLIN" -> "Zombie Piglin". This is what fixes
     * head names showing as the raw Java enum text when they come straight
     * from CustomEnchants' %victim_type% (which is just
     * Entity#getType().name(), e.g. "ZOMBIE").
     *
     * Only words that look like an enum constant (letters/digits/underscore,
     * no lowercase) are touched - ordinary hand-typed names like "Legendary
     * Zombie" or "Bob's" pass through completely unchanged.
     */
    public static String prettifyMobToken(String input) {
        if (input == null || input.isEmpty()) {
            return input;
        }
        String[] words = input.split(" ");
        StringBuilder result = new StringBuilder();
        for (int i = 0; i < words.length; i++) {
            if (i > 0) {
                result.append(' ');
            }
            result.append(prettifyWord(words[i]));
        }
        return result.toString();
    }

    private static String prettifyWord(String word) {
        if (word.isEmpty()) {
            return word;
        }

        // Pull out any color codes first, remembering where (in the
        // stripped-down word) each one belongs, so a stray "&6" in front of
        // "PIG" doesn't make the [A-Z0-9_]+ check bail out and leave the
        // whole thing untouched.
        Matcher matcher = COLOR_CODE.matcher(word);
        List<String> codes = new ArrayList<>();
        List<Integer> codePositions = new ArrayList<>();
        StringBuilder stripped = new StringBuilder();
        int lastEnd = 0;
        while (matcher.find()) {
            stripped.append(word, lastEnd, matcher.start());
            codes.add(matcher.group());
            codePositions.add(stripped.length());
            lastEnd = matcher.end();
        }
        stripped.append(word, lastEnd, word.length());
        String plain = stripped.toString();

        if (plain.isEmpty() || !plain.matches("[A-Z0-9_]+")) {
            return word;
        }

        StringBuilder sb = new StringBuilder();
        for (String part : plain.split("_")) {
            if (part.isEmpty()) {
                continue;
            }
            if (sb.length() > 0) {
                sb.append(' ');
            }
            sb.append(Character.toUpperCase(part.charAt(0)));
            if (part.length() > 1) {
                sb.append(part.substring(1).toLowerCase());
            }
        }
        String prettifiedPlain = sb.length() > 0 ? sb.toString() : plain;

        // plain -> prettifiedPlain is always a 1-for-1 length swap (case
        // changes don't change length, and each '_' becomes exactly one
        // ' '), so the recorded positions still line up and codes can be
        // spliced straight back in.
        if (codes.isEmpty()) {
            return prettifiedPlain;
        }
        StringBuilder result = new StringBuilder();
        int codeIndex = 0;
        for (int i = 0; i < prettifiedPlain.length(); i++) {
            while (codeIndex < codes.size() && codePositions.get(codeIndex) == i) {
                result.append(codes.get(codeIndex));
                codeIndex++;
            }
            result.append(prettifiedPlain.charAt(i));
        }
        while (codeIndex < codes.size()) {
            result.append(codes.get(codeIndex));
            codeIndex++;
        }
        return result.toString();
    }
}
