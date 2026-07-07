package com.yourserver.customenchants.util;

import org.bukkit.NamespacedKey;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.potion.PotionEffectType;

import java.util.logging.Logger;

/**
 * Resolves vanilla {@link Enchantment}s and {@link PotionEffectType}s from
 * plain config strings, accepting both the modern namespaced-key style
 * names ("efficiency", "sharpness", "speed") and the legacy Bukkit enum
 * names ("DIG_SPEED", "DAMAGE_ALL", "SPEED") so a config author can write
 * whatever name they're used to seeing.
 *
 * Used by the "vanilla-enchant:" and "potion-effect: type:" config fields
 * that let a {@link com.yourserver.customenchants.model.CustomEnchant}
 * hijack a real vanilla enchantment or potion effect instead of being
 * purely custom. Both are entirely optional - an enchant that never sets
 * either of these behaves exactly as it always has.
 */
public final class VanillaSupport {

    private VanillaSupport() {
    }

    /** Resolves a vanilla enchantment by name, or null (with a warning) if it doesn't match anything. */
    @SuppressWarnings("deprecation")
    public static Enchantment resolveEnchantment(String name, Logger logger, String context) {
        if (name == null || name.trim().isEmpty()) return null;
        String trimmed = name.trim();

        Enchantment byKey = Enchantment.getByKey(NamespacedKey.minecraft(normalizeKey(trimmed)));
        if (byKey != null) return byKey;

        Enchantment byLegacyName = Enchantment.getByName(normalizeEnumName(trimmed));
        if (byLegacyName != null) return byLegacyName;

        if (logger != null) {
            logger.warning("[CustomEnchants] Unknown vanilla enchantment '" + name + "' for " + context + " - this enchant will behave as a purely custom enchant with no real vanilla effect.");
        }
        return null;
    }

    /** Resolves a vanilla potion effect type by name, or null (with a warning) if it doesn't match anything. */
    @SuppressWarnings("deprecation")
    public static PotionEffectType resolvePotionEffect(String name, Logger logger, String context) {
        if (name == null || name.trim().isEmpty()) return null;
        String trimmed = name.trim();

        PotionEffectType type = PotionEffectType.getByName(normalizeEnumName(trimmed));
        if (type == null) {
            try {
                NamespacedKey key = NamespacedKey.minecraft(normalizeKey(trimmed));
                type = PotionEffectType.getByKey(key);
            } catch (Throwable ignored) {
                // Older API surface without getByKey(NamespacedKey) - the
                // getByName() attempt above already covers the common case.
            }
        }

        if (type == null && logger != null) {
            logger.warning("[CustomEnchants] Unknown vanilla potion effect '" + name + "' for " + context + " - this enchant's potion-effect section will be ignored.");
        }
        return type;
    }

    private static String normalizeKey(String name) {
        return name.trim().toLowerCase().replace(' ', '_');
    }

    private static String normalizeEnumName(String name) {
        return name.trim().toUpperCase().replace(' ', '_');
    }
}
