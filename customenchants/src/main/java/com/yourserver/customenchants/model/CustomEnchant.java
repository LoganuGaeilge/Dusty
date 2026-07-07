package com.yourserver.customenchants.model;

import com.yourserver.customenchants.util.RangeValue;
import com.yourserver.customenchants.util.ScaledValue;
import org.bukkit.Material;
import org.bukkit.enchantments.Enchantment;

import java.util.EnumMap;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * A single custom enchant definition, as loaded from config.yml.
 * This is a plain data holder.
 */
public class CustomEnchant implements ActionSource {

    private final String id;
    private final BookTier rarity;
    private final String displayName;
    private final int maxLevel;
    private final Set<Material> supportedItems;
    // Both now resolve through ScaledValue, exactly like values:/ranges: below -
    // flat number, per-level table, formula, or level-aware math expression.
    private final ScaledValue successChance;
    private final Map<Trigger, List<String>> actions;
    private final boolean guiOn;
    // Null means "upgrade-costs was never configured for this enchant at all"
    // (GUI upgrades always show gui-not-configured). Non-null but a TABLE kind
    // still requires an exact level match, same as the original behaviour, so
    // admins can deliberately stop GUI upgrades at a level below max-level.
    private final ScaledValue upgradeCosts;
    private final String currencyName;
    private final String currencyDisplay;

    // Named, level-scalable numbers/ranges defined under this enchant's
    // "values:" and "ranges:" config sections. Referenced by name from
    // "chance:", "setvar:", "randomvar:" and "blockdrop:" action lines,
    // so percentage chances, random amounts, and bonus-drop counts can
    // all scale with level the same way upgrade-costs already does.
    private final Map<String, ScaledValue> values;
    private final Map<String, RangeValue> ranges;

    // Per-enchant override for the "blockdrop:" action. Null enabled means
    // "no override, inherit the plugin-wide settings.block-drops.enabled
    // value". disabledRegions is always additive on top of the plugin-wide
    // settings.block-drops.disabled-regions list, never a replacement.
    private final Boolean blockDropsEnabled;
    private final Set<String> blockDropsDisabledRegions;

    // Optional "vanilla-enchant hijack": when set, this enchant is backed
    // by a real Bukkit Enchantment applied via ItemMeta#addEnchant(...,
    // ignoreLevelRestriction = true), so max-level is never limited to the
    // vanilla cap (e.g. Efficiency 200 really is applied and felt in
    // vanilla mining-speed math). Null means "purely custom enchant",
    // exactly as this class always behaved.
    private final Enchantment vanillaEnchant;
    // When a vanillaEnchant is set, whether to suppress the item's default
    // client-rendered vanilla tooltip line for it (via ItemFlag.HIDE_ENCHANTS)
    // so only this plugin's own lore-format line is shown, avoiding a
    // duplicate-looking display. Irrelevant when vanillaEnchant is null.
    private final boolean hideVanillaTooltip;

    // Optional "vanilla potion effect as an enchant": when set, this
    // enchant keeps a real PotionEffect active on the player for as long
    // as the item carrying it is held/worn (see the on-hold task), with
    // both amplifier and duration scaling per-level like everything else.
    private final PotionEffectSpec potionEffect;

    public CustomEnchant(String id, String displayName, int maxLevel,
                         Set<Material> supportedItems,
                         ScaledValue successChance,
                         Map<Trigger, List<String>> actions,
                         BookTier rarity,
                         boolean guiOn,
                         ScaledValue upgradeCosts,
                         String currencyName,
                         String currencyDisplay,
                         Map<String, ScaledValue> values,
                         Map<String, RangeValue> ranges,
                         Boolean blockDropsEnabled,
                         Set<String> blockDropsDisabledRegions,
                         Enchantment vanillaEnchant,
                         boolean hideVanillaTooltip,
                         PotionEffectSpec potionEffect) {
        this.id = id;
        this.displayName = displayName;
        this.maxLevel = maxLevel;
        this.supportedItems = EnumSet.copyOf(supportedItems.isEmpty()
                ? EnumSet.noneOf(Material.class) : supportedItems);
        this.successChance = successChance;
        this.actions = new EnumMap<>(actions);
        this.rarity = rarity;
        this.guiOn = guiOn;
        this.upgradeCosts = upgradeCosts;
        this.currencyName = currencyName;
        this.currencyDisplay = currencyDisplay;
        this.values = values != null ? new HashMap<>(values) : new HashMap<>();
        this.ranges = ranges != null ? new HashMap<>(ranges) : new HashMap<>();
        this.blockDropsEnabled = blockDropsEnabled;
        this.blockDropsDisabledRegions = blockDropsDisabledRegions != null
                ? new java.util.HashSet<>(blockDropsDisabledRegions) : java.util.Set.of();
        this.vanillaEnchant = vanillaEnchant;
        this.hideVanillaTooltip = hideVanillaTooltip;
        this.potionEffect = potionEffect;
    }

    public String getId() {
        return id;
    }

    public BookTier getRarity() {
        return rarity;
    }

    public String getDisplayName() {
        return displayName;
    }

    public int getMaxLevel() {
        return maxLevel;
    }

    public boolean supports(Material material) {
        return supportedItems.contains(material);
    }

    public Set<Material> getSupportedItems() {
        return supportedItems;
    }

    /**
     * Returns the success chance (0-100) for rolling this enchantment at the
     * given level. success-chance now resolves through the same ScaledValue
     * system as everything else, so a config can leave it flat (identical at
     * every level, matching the old behaviour exactly) or make it a table/
     * formula/expression that scales with the book's level.
     */
    public double getBaseChance(int level) {
        return successChance.resolve(level);
    }

    public List<String> getActions(Trigger trigger) {
        return actions.getOrDefault(trigger, List.of());
    }

    /** Whether this enchant should appear in the pickaxe upgrade GUI. */
    public boolean isGuiOn() {
        return guiOn;
    }

    /**
     * Returns the currency cost to upgrade to the given level via the GUI, or
     * null if not configured (shows gui-not-configured).
     *
     * - If upgrade-costs was never set for this enchant at all: always null.
     * - If it's an explicit per-level table: exact match required, same as
     *   before - a level with no entry is "not configured", which lets an
     *   admin intentionally cap GUI upgrades below max-level while still
     *   allowing higher levels via book rolls.
     * - If it's a flat number or a formula/expression: always resolvable,
     *   for every level up to max-level, with no need to list each one by
     *   hand.
     */
    public Double getUpgradeCost(int level) {
        if (upgradeCosts == null) {
            return null;
        }
        if (upgradeCosts.isTable()) {
            return upgradeCosts.tableValueExact(level);
        }
        return upgradeCosts.resolve(level);
    }

    /** The currency key (e.g. "orbs", "souls", "credits") this enchant's GUI upgrades are paid in, or null to use the server-wide default. Matches the variable prefix in your Skript economy (orbs::<uuid>, souls::<uuid>, etc.) and the <text> arg your /currencyadd command expects. */
    public String getCurrencyName() {
        return currencyName;
    }

    /** Friendly currency name shown in the GUI lore next to the cost (e.g. "Orbs"), or null for none. */
    public String getCurrencyDisplay() {
        return currencyDisplay;
    }

    /** A named, level-scalable numeric value from this enchant's "values:" config section, or null if not defined. */
    @Override
    public ScaledValue getScaledValue(String name) {
        return name == null ? null : values.get(name.toLowerCase());
    }

    /** A named, level-scalable random range from this enchant's "ranges:" config section, or null if not defined. */
    @Override
    public RangeValue getRangeValue(String name) {
        return name == null ? null : ranges.get(name.toLowerCase());
    }

    /**
     * Per-enchant override for whether the "blockdrop:" action is allowed to
     * run at all. Null means "not overridden for this enchant" - the
     * plugin-wide settings.block-drops.enabled value is used instead.
     */
    @Override
    public Boolean getBlockDropsEnabledOverride() {
        return blockDropsEnabled;
    }

    /**
     * WorldGuard region ids (lowercase) in which this enchant's "blockdrop:"
     * action should never run, on top of the plugin-wide
     * settings.block-drops.disabled-regions list.
     */
    @Override
    public Set<String> getBlockDropsDisabledRegions() {
        return blockDropsDisabledRegions;
    }

    /** The real vanilla enchantment this enchant is backed by, from "vanilla-enchant:" in config, or null for a purely custom enchant. */
    public Enchantment getVanillaEnchant() {
        return vanillaEnchant;
    }

    /** Whether the item's native vanilla tooltip line for {@link #getVanillaEnchant()} should be hidden in favour of this plugin's own lore-format line. Meaningless when getVanillaEnchant() is null. */
    public boolean isHideVanillaTooltip() {
        return hideVanillaTooltip;
    }

    /** The vanilla potion effect this enchant keeps active while held/worn, from "potion-effect:" in config, or null if not configured. */
    public PotionEffectSpec getPotionEffect() {
        return potionEffect;
    }
}
