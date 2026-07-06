package com.yourserver.customenchants.model;

import com.yourserver.customenchants.util.RangeValue;
import org.bukkit.Material;

import java.util.List;
import java.util.Random;

/**
 * An "unenchanted" (mystery) book, defined per rarity under the
 * "unenchanted-books:" config section. Applying one to a valid item rolls a
 * random enchant classed as this book's rarity (see {@link BookTier}) and a
 * random level for it, then applies that enchant - the book itself carries no
 * pre-chosen enchant.
 *
 * There is deliberately no unenchanted book for {@link BookTier#NA}: only
 * COMMON, RARE and LEGENDARY have one.
 *
 * The rolled level comes from {@link #levelRoll}, an admin-editable
 * {@link RangeValue}. Its "level" variable is bound to the chosen enchant's
 * own max-level, so a range like {@code min: 1, max: "level"} rolls uniformly
 * across that enchant's full range, while formulas like {@code max: "level*0.5"}
 * let each rarity bias the levels it hands out. The result is always clamped to
 * {@code [1, enchant max-level]}.
 */
public final class UnenchantedBook {

    private final BookTier rarity;
    private final Material material;
    private final String name;
    private final List<String> lore;
    private final RangeValue levelRoll;

    public UnenchantedBook(BookTier rarity, Material material, String name,
                           List<String> lore, RangeValue levelRoll) {
        this.rarity = rarity;
        this.material = material;
        this.name = name;
        this.lore = lore;
        this.levelRoll = levelRoll;
    }

    public BookTier getRarity() {
        return rarity;
    }

    public Material getMaterial() {
        return material;
    }

    public String getName() {
        return name;
    }

    public List<String> getLore() {
        return lore;
    }

    /**
     * Rolls the level to apply for an enchant with the given max-level. The
     * "level" variable inside the configured {@code level-roll} range resolves
     * to {@code enchantMaxLevel}, and the result is clamped to
     * {@code [1, enchantMaxLevel]}.
     */
    public int rollLevel(int enchantMaxLevel, Random random) {
        int rolled = levelRoll.resolveRandomInt(enchantMaxLevel, random);
        if (rolled < 1) rolled = 1;
        if (rolled > enchantMaxLevel) rolled = enchantMaxLevel;
        return rolled;
    }
}
