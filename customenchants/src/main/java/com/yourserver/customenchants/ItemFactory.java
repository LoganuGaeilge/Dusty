package com.yourserver.customenchants;

import com.yourserver.customenchants.model.BookTier;
import com.yourserver.customenchants.model.CustomEnchant;
import com.yourserver.customenchants.util.Keys;
import com.yourserver.customenchants.util.RomanNumeral;
import com.yourserver.customenchants.util.Text;
import org.bukkit.Material;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

public class ItemFactory {

    private final CustomEnchantsPlugin plugin;
    private final EnchantManager manager;
    private final Keys keys;

    public ItemFactory(CustomEnchantsPlugin plugin) {
        this.plugin = plugin;
        this.manager = plugin.getEnchantManager();
        this.keys = plugin.getKeys();
    }

    // ---------------------------------------------------------------
    // Enchant Book
    // ---------------------------------------------------------------

    public ItemStack createEnchantBook(CustomEnchant enchant, int level, BookTier tier) {
        ItemStack book = new ItemStack(Material.ENCHANTED_BOOK);
        ItemMeta meta = book.getItemMeta();

        String tierColor = manager.getTierColor(tier);
        String tierName = manager.getTierDisplay(tier);

        meta.setDisplayName(Text.color(tierColor + tierName + " Enchant Book"));

        List<String> lore = new ArrayList<>();
        lore.add(Text.color("&7" + enchant.getDisplayName() + " &7" + RomanNumeral.toRoman(level)));
        lore.add("");
        lore.add(Text.color("&8Success Chance: &f" + trimDecimal(enchant.getBaseChance(level)) + "%"));
        lore.add("");
        lore.add(Text.color("&eDrag and click over"));
        lore.add(Text.color("&ea valid item to use."));
        meta.setLore(lore);

        meta.getPersistentDataContainer().set(keys.bookEnchantId, PersistentDataType.STRING, enchant.getId());
        meta.getPersistentDataContainer().set(keys.bookLevel, PersistentDataType.INTEGER, level);
        meta.getPersistentDataContainer().set(keys.bookTier, PersistentDataType.STRING, tier.name());
        meta.getPersistentDataContainer().set(keys.bookDustBonus, PersistentDataType.DOUBLE, 0.0);

        book.setItemMeta(meta);
        return book;
    }

    public boolean isEnchantBook(ItemStack item) {
        if (item == null || item.getType() != Material.ENCHANTED_BOOK || !item.hasItemMeta()) return false;
        return item.getItemMeta().getPersistentDataContainer().has(keys.bookEnchantId, PersistentDataType.STRING);
    }

    public String getBookEnchantId(ItemStack book) {
        return book.getItemMeta().getPersistentDataContainer().get(keys.bookEnchantId, PersistentDataType.STRING);
    }

    public int getBookLevel(ItemStack book) {
        Integer level = book.getItemMeta().getPersistentDataContainer().get(keys.bookLevel, PersistentDataType.INTEGER);
        return level != null ? level : 1;
    }

    public BookTier getBookTier(ItemStack book) {
        String tier = book.getItemMeta().getPersistentDataContainer().get(keys.bookTier, PersistentDataType.STRING);
        return BookTier.fromString(tier);
    }

    public double getBookDustBonus(ItemStack book) {
        Double bonus = book.getItemMeta().getPersistentDataContainer().get(keys.bookDustBonus, PersistentDataType.DOUBLE);
        return bonus != null ? bonus : 0.0;
    }

    /** Returns true if dust was applied, false if the book was already at max bonus. */
    public boolean applyDust(ItemStack book) {
        double current = getBookDustBonus(book);
        double max = manager.getDustMaxBonus();
        if (current >= max) {
            return false;
        }
        double updated = Math.min(max, current + manager.getDustBonusPerUse());

        ItemMeta meta = book.getItemMeta();
        meta.getPersistentDataContainer().set(keys.bookDustBonus, PersistentDataType.DOUBLE, updated);

        // refresh the chance line + add a bonus line in the lore
        CustomEnchant enchant = manager.getEnchant(getBookEnchantId(book));
        BookTier tier = getBookTier(book);
        if (enchant != null && tier != null) {
            double baseChance = enchant.getBaseChance(getBookLevel(book));
            double finalChance = Math.min(100, baseChance + updated);

            List<String> lore = meta.getLore();
            if (lore != null) {
                for (int i = 0; i < lore.size(); i++) {
                    String stripped = org.bukkit.ChatColor.stripColor(lore.get(i));
                    if (stripped != null && stripped.startsWith("Success Chance:")) {
                        lore.set(i, Text.color("&8Success Chance: &f" + trimDecimal(finalChance) + "% &a(+" + trimDecimal(updated) + "% dust)"));
                    }
                }
                meta.setLore(lore);
            }
        }

        book.setItemMeta(meta);
        return true;
    }

    // ---------------------------------------------------------------
    // Unenchanted (mystery) Book
    // ---------------------------------------------------------------

    /** Builds the physical item for a rarity's unenchanted (mystery) book. */
    public ItemStack createUnenchantedBook(com.yourserver.customenchants.model.UnenchantedBook book) {
        ItemStack item = new ItemStack(book.getMaterial());
        ItemMeta meta = item.getItemMeta();

        meta.setDisplayName(Text.color(book.getName()));

        List<String> lore = new ArrayList<>();
        for (String line : book.getLore()) {
            lore.add(Text.color(line));
        }
        meta.setLore(lore);

        meta.getPersistentDataContainer().set(keys.unenchantedBookTier, PersistentDataType.STRING, book.getRarity().name());

        item.setItemMeta(meta);
        return item;
    }

    public boolean isUnenchantedBook(ItemStack item) {
        if (item == null || !item.hasItemMeta()) return false;
        return item.getItemMeta().getPersistentDataContainer().has(keys.unenchantedBookTier, PersistentDataType.STRING);
    }

    /** The rarity an unenchanted book rolls, or null if the item isn't one / has an unknown rarity. */
    public BookTier getUnenchantedBookTier(ItemStack item) {
        if (!isUnenchantedBook(item)) return null;
        String tier = item.getItemMeta().getPersistentDataContainer().get(keys.unenchantedBookTier, PersistentDataType.STRING);
        return BookTier.fromString(tier);
    }

    // ---------------------------------------------------------------
    // White Dust
    // ---------------------------------------------------------------

    public ItemStack createWhiteDust() {
        ItemStack dust = new ItemStack(manager.getDustMaterial());
        ItemMeta meta = dust.getItemMeta();
        meta.setDisplayName(Text.color(manager.getDustName()));
        List<String> lore = new ArrayList<>();
        for (String line : manager.getDustLore()) {
            lore.add(Text.color(line));
        }
        meta.setLore(lore);
        meta.getPersistentDataContainer().set(keys.dustItem, PersistentDataType.BYTE, (byte) 1);
        dust.setItemMeta(meta);
        return dust;
    }

    public boolean isWhiteDust(ItemStack item) {
        if (item == null || !item.hasItemMeta()) return false;
        return item.getItemMeta().getPersistentDataContainer().has(keys.dustItem, PersistentDataType.BYTE);
    }

    // ---------------------------------------------------------------
    // Applying an enchant to a real item
    // ---------------------------------------------------------------

    public int getAppliedLevel(ItemStack item, String enchantId) {
        if (item == null || !item.hasItemMeta()) return 0;
        ItemMeta meta = item.getItemMeta();
        Integer level = meta.getPersistentDataContainer()
                .get(keys.enchantLevelKey(plugin, enchantId), PersistentDataType.INTEGER);
        int pdcLevel = level != null ? level : 0;

        // A vanilla-backed enchant (see "vanilla-enchant:" in config.yml) can end up
        // on an item without ever going through setAppliedLevel - e.g. it was already
        // enchanted before this plugin got involved, traded/looted, combined in an
        // anvil, or given by another plugin/command. In that case our own PDC tag is
        // never written, so without this check we'd think the enchant isn't applied
        // at all and let a book "apply" it again from scratch instead of picking up
        // from the real vanilla level that's already on the item.
        if (manager.isVanillaEnchantsEnabled()) {
            CustomEnchant enchant = manager.getEnchant(enchantId);
            Enchantment vanilla = enchant != null ? enchant.getVanillaEnchant() : null;
            if (vanilla != null) {
                int vanillaLevel = meta.getEnchantLevel(vanilla);
                return Math.max(pdcLevel, vanillaLevel);
            }
        }

        return pdcLevel;
    }

    /** Sets the enchant level on the item and rebuilds its lore. Returns the updated item. */
    public ItemStack setAppliedLevel(ItemStack item, String enchantId, int level) {
        ItemMeta meta = item.getItemMeta();
        meta.getPersistentDataContainer().set(keys.enchantLevelKey(plugin, enchantId), PersistentDataType.INTEGER, level);

        // track which enchant ids are present on this item so we can rebuild lore fully
        String existingIds = meta.getPersistentDataContainer().get(keys.appliedEnchantIds, PersistentDataType.STRING);
        Set<String> idSet = new LinkedHashSet<>();
        if (existingIds != null && !existingIds.isEmpty()) {
            idSet.addAll(Arrays.asList(existingIds.split(",")));
        }
        idSet.add(enchantId);
        meta.getPersistentDataContainer().set(keys.appliedEnchantIds, PersistentDataType.STRING, String.join(",", idSet));

        item.setItemMeta(meta);
        rebuildLore(item);
        syncVanillaEnchant(item, enchantId, level);
        return item;
    }

    /**
     * Removes exactly one level from an already-applied enchant (the inverse
     * of setAppliedLevel bumping it up by one) - used by the GUI's downgrade
     * click to let a player refund a level. No-ops (returns the item
     * unchanged) if the enchant isn't currently applied at all. Dropping
     * from level 1 to 0 fully clears the enchant (see clearEnchant) rather
     * than leaving a dangling "level 0" PDC entry.
     */
    public ItemStack removeLevel(ItemStack item, String enchantId) {
        int current = getAppliedLevel(item, enchantId);
        if (current <= 0) return item;

        int newLevel = current - 1;
        if (newLevel <= 0) {
            return clearEnchant(item, enchantId);
        }
        return setAppliedLevel(item, enchantId, newLevel);
    }

    /**
     * Fully strips one enchant off an item: the PDC level entry, its id from
     * the appliedEnchantIds tracking list, the real vanilla enchantment (if
     * this enchant hijacks one via "vanilla-enchant:"), and its lore line.
     */
    private ItemStack clearEnchant(ItemStack item, String enchantId) {
        ItemMeta meta = item.getItemMeta();
        meta.getPersistentDataContainer().remove(keys.enchantLevelKey(plugin, enchantId));

        String existingIds = meta.getPersistentDataContainer().get(keys.appliedEnchantIds, PersistentDataType.STRING);
        if (existingIds != null && !existingIds.isEmpty()) {
            Set<String> idSet = new LinkedHashSet<>(Arrays.asList(existingIds.split(",")));
            idSet.remove(enchantId);
            if (idSet.isEmpty()) {
                meta.getPersistentDataContainer().remove(keys.appliedEnchantIds);
            } else {
                meta.getPersistentDataContainer().set(keys.appliedEnchantIds, PersistentDataType.STRING, String.join(",", idSet));
            }
        }

        CustomEnchant enchant = manager.getEnchant(enchantId);
        Enchantment vanilla = enchant != null ? enchant.getVanillaEnchant() : null;
        if (vanilla != null) {
            meta.removeEnchant(vanilla);
        }

        item.setItemMeta(meta);
        rebuildLore(item);
        return item;
    }

    /**
     * Keeps a real vanilla Bukkit enchantment in sync with a vanilla-backed
     * CustomEnchant's stored level (see "vanilla-enchant:" in config.yml),
     * via ItemMeta#addEnchant(..., ignoreLevelRestriction = true) - this is
     * the actual "hijack" that lets an item genuinely carry Efficiency 200
     * (or any other vanilla enchant) instead of just displaying the number.
     * No-ops entirely for a plain custom enchant (no "vanilla-enchant:"
     * configured) or while settings.vanilla-enchants.enabled is false, so
     * this never affects any enchant that doesn't opt into it.
     */
    private void syncVanillaEnchant(ItemStack item, String enchantId, int level) {
        if (!manager.isVanillaEnchantsEnabled()) return;

        com.yourserver.customenchants.model.CustomEnchant enchant = manager.getEnchant(enchantId);
        if (enchant == null) return;
        Enchantment vanilla = enchant.getVanillaEnchant();
        if (vanilla == null) return;

        ItemMeta meta = item.getItemMeta();
        meta.addEnchant(vanilla, level, true);
        if (enchant.isHideVanillaTooltip()) {
            meta.addItemFlags(ItemFlag.HIDE_ENCHANTS);
        }
        item.setItemMeta(meta);
    }

    /** Returns a map of enchantId -> level for every custom enchant currently applied to this item. */
    public java.util.Map<String, Integer> getAllApplied(ItemStack item) {
        java.util.Map<String, Integer> result = new java.util.LinkedHashMap<>();
        if (item == null || !item.hasItemMeta()) return result;

        String idsRaw = item.getItemMeta().getPersistentDataContainer().get(keys.appliedEnchantIds, PersistentDataType.STRING);
        Set<String> ids = new LinkedHashSet<>();
        if (idsRaw != null && !idsRaw.isEmpty()) {
            ids.addAll(Arrays.asList(idsRaw.split(",")));
        }

        // A vanilla-backed enchant can genuinely be on the item (looted, traded,
        // anvil-combined, admin-given, or just enchanted through vanilla means)
        // without its id ever having been recorded in appliedEnchantIds - that
        // list is only written by setAppliedLevel. Without this, the GUI, the
        // on-hold task, and triggers would all treat it as not applied at all
        // (level 0) instead of picking up the real vanilla level. getAppliedLevel
        // already knows how to read that real level, so just make sure every
        // vanilla-backed enchant id gets checked here too.
        for (CustomEnchant enchant : manager.getEnchants().values()) {
            if (enchant.getVanillaEnchant() != null) {
                ids.add(enchant.getId());
            }
        }

        for (String id : ids) {
            int level = getAppliedLevel(item, id);
            if (level > 0) {
                result.put(id, level);
            }
        }
        return result;
    }

    /** Rewrites the enchant lines of an item's lore based on stored PDC levels, preserving other lore lines. */
    public void rebuildLore(ItemStack item) {
        ItemMeta meta = item.getItemMeta();
        String idsRaw = meta.getPersistentDataContainer().get(keys.appliedEnchantIds, PersistentDataType.STRING);
        Set<String> ids = new LinkedHashSet<>();
        if (idsRaw != null && !idsRaw.isEmpty()) {
            ids.addAll(Arrays.asList(idsRaw.split(",")));
        }

        List<String> newEnchantLines = new ArrayList<>();
        for (String id : ids) {
            CustomEnchant enchant = manager.getEnchant(id);
            int level = getAppliedLevel(item, id);
            if (enchant == null || level <= 0) continue;
            String line = manager.getLoreFormat()
                    .replace("%enchant%", enchant.getDisplayName())
                    .replace("%level%", RomanNumeral.toRoman(level));
            newEnchantLines.add(Text.color(line));
        }

        List<String> lore = meta.getLore();
        if (lore == null) lore = new ArrayList<>();

        // Strip previously-written enchant lines (anything matching a known enchant display name prefix)
        List<String> keptLore = new ArrayList<>();
        for (String line : lore) {
            if (!isKnownEnchantLine(line)) {
                keptLore.add(line);
            }
        }

        List<String> finalLore = new ArrayList<>(newEnchantLines);
        if (!newEnchantLines.isEmpty() && !keptLore.isEmpty()) {
            finalLore.add("");
        }
        finalLore.addAll(keptLore);

        meta.setLore(finalLore);
        item.setItemMeta(meta);
    }

    private boolean isKnownEnchantLine(String loreLine) {
        String stripped = org.bukkit.ChatColor.stripColor(loreLine);
        if (stripped == null) return false;
        for (CustomEnchant enchant : manager.getEnchants().values()) {
            String plainName = org.bukkit.ChatColor.stripColor(Text.color(enchant.getDisplayName()));
            if (plainName != null && stripped.startsWith(plainName)) {
                return true;
            }
        }
        return false;
    }

    private String trimDecimal(double value) {
        if (value == Math.floor(value)) {
            return String.valueOf((long) value);
        }
        return String.valueOf(value);
    }
}
