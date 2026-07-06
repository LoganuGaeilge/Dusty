package com.yourserver.customenchants.util;

import org.bukkit.NamespacedKey;
import org.bukkit.plugin.java.JavaPlugin;

/**
 * All PersistentDataContainer keys used to tag books, dust, and enchanted items.
 */
public final class Keys {

    public NamespacedKey bookEnchantId;
    public NamespacedKey bookLevel;
    public NamespacedKey bookTier;
    public NamespacedKey bookDustBonus;

    // Marks an unenchanted (mystery) book and stores which rarity it rolls.
    public NamespacedKey unenchantedBookTier;

    public NamespacedKey dustItem;

    // Stored on the actual enchanted item (comma separated list of enchant ids)
    public NamespacedKey appliedEnchantIds;

    public Keys(JavaPlugin plugin) {
        bookEnchantId = new NamespacedKey(plugin, "ce_book_enchant_id");
        bookLevel = new NamespacedKey(plugin, "ce_book_level");
        bookTier = new NamespacedKey(plugin, "ce_book_tier");
        bookDustBonus = new NamespacedKey(plugin, "ce_book_dust_bonus");
        unenchantedBookTier = new NamespacedKey(plugin, "ce_unenchanted_tier");

        dustItem = new NamespacedKey(plugin, "ce_white_dust");

        appliedEnchantIds = new NamespacedKey(plugin, "ce_applied_ids");
    }

    /** Per-enchant level key on the item itself, e.g. "ce_lvl_venom_strike" */
    public NamespacedKey enchantLevelKey(JavaPlugin plugin, String enchantId) {
        return new NamespacedKey(plugin, "ce_lvl_" + enchantId);
    }
}
