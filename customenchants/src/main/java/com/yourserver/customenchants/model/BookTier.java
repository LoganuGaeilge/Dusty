package com.yourserver.customenchants.model;

/**
 * The rarity tiers an enchant (and a book) can be classed as. COMMON, RARE
 * and LEGENDARY each have a matching unenchanted "mystery" book that rolls a
 * random same-rarity enchant (see UnenchantedBook / ApplyListener). NA is a
 * fourth rarity with no unenchanted book of its own - enchants classed as NA
 * are simply never rolled by any mystery book.
 */
public enum BookTier {
    COMMON,
    RARE,
    LEGENDARY,
    NA;

    public static BookTier fromString(String s) {
        if (s == null) return null;
        try {
            return BookTier.valueOf(s.trim().toUpperCase());
        } catch (IllegalArgumentException ex) {
            return null;
        }
    }
}
