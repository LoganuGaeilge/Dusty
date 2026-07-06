package com.yourserver.customenchants;

import com.yourserver.customenchants.model.BookTier;
import com.yourserver.customenchants.model.CustomEnchant;
import com.yourserver.customenchants.model.GlobalToolSet;
import com.yourserver.customenchants.model.PotionEffectSpec;
import com.yourserver.customenchants.model.Trigger;
import com.yourserver.customenchants.model.UnenchantedBook;
import com.yourserver.customenchants.util.RangeValue;
import com.yourserver.customenchants.util.ScaledValue;
import com.yourserver.customenchants.util.VanillaSupport;
import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.potion.PotionEffectType;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.logging.Level;

public class EnchantManager {

    private final CustomEnchantsPlugin plugin;

    private final Map<String, CustomEnchant> enchants = new HashMap<>();
    private final Map<BookTier, String> tierColors = new EnumMap<>(BookTier.class);
    private final Map<BookTier, String> tierDisplayNames = new EnumMap<>(BookTier.class);

    // "unenchanted-books:" - the per-rarity mystery books that roll a random
    // same-rarity enchant when applied. Keyed by rarity; NA is never present
    // here (there is no NA book).
    private final Map<BookTier, UnenchantedBook> unenchantedBooks = new EnumMap<>(BookTier.class);

    // "global-tools:" - named groups of item types that run their own
    // "actions:" per trigger without ever needing to be enchanted. See
    // GlobalToolSet.
    private final Map<String, GlobalToolSet> globalTools = new HashMap<>();

    private boolean consumeBookOnFail;
    private String loreFormat;
    private String currencyName;
    private String guiTitle;

    // Plugin-wide "blockdrop:" bonus-drop kill switch and WorldGuard region
    // blacklist. Per-enchant settings (see CustomEnchant) layer on top of
    // these rather than replacing them.
    private boolean blockDropsEnabledGlobally;
    private final Set<String> blockDropsDisabledRegionsGlobally = new HashSet<>();

    // Plugin-wide kill switch for the "vanilla-enchant:"/"potion-effect:"
    // hijack features. When false, enchants that configure either one just
    // fall back to behaving as purely custom enchants (their own actions/
    // GUI/book flow are completely unaffected) - nothing is ever removed
    // from config, this only stops the real vanilla side-effects.
    private boolean vanillaEnchantsEnabled;

    // Plugin-wide on/off + tick interval for the on-hold task (see
    // com.yourserver.customenchants.tasks.HoldTickTask), which drives both
    // the "on-hold:" action trigger and potion-effect enchants.
    private boolean onHoldEnabled;
    private int onHoldIntervalTicks;

    // Plugin-wide override of vanilla Unbreaking's own durability-roll math
    // (see settings.unbreaking-override in config.yml). Applies to ANY item
    // bearing a real vanilla Unbreaking enchantment - however it got there -
    // not just items enchanted through this plugin. The formulas are plain
    // MathExpr expressions (same DSL as everything else) with "level" bound
    // to the item's real Unbreaking level; null/unparsable falls back to the
    // real vanilla formula for that category. See UnbreakingOverrideListener.
    private boolean unbreakingOverrideEnabled;
    private String unbreakingToolsFormula;
    private String unbreakingArmorFormula;
    // Resolved once here (not per-event) via the same VanillaSupport lookup
    // used by "vanilla-enchant:" so this keeps working across the legacy
    // DURABILITY name and the modern UNBREAKING key without caring which
    // one the server's Bukkit API version exposes.
    private Enchantment unbreakingVanillaEnchant;

    private Material dustMaterial;
    private String dustName;
    private java.util.List<String> dustLore;
    private double dustBonusPerUse;
    private double dustMaxBonus;

    private final Map<String, String> messages = new HashMap<>();

    public EnchantManager(CustomEnchantsPlugin plugin) {
        this.plugin = plugin;
    }

    public void load() {
        enchants.clear();
        tierColors.clear();
        tierDisplayNames.clear();
        unenchantedBooks.clear();
        messages.clear();
        globalTools.clear();

        plugin.reloadConfig();
        FileConfiguration config = plugin.getConfig();

        ConfigurationSection settings = config.getConfigurationSection("settings");
        blockDropsDisabledRegionsGlobally.clear();
        if (settings != null) {
            consumeBookOnFail = settings.getBoolean("consume-book-on-fail", true);
            loreFormat = settings.getString("lore-format", "%enchant% %level%");
            currencyName = settings.getString("currency-name", "orbs");
            guiTitle = settings.getString("gui-title", "&8Pickaxe Enchant Upgrades");

            ConfigurationSection blockDropsSection = settings.getConfigurationSection("block-drops");
            if (blockDropsSection != null) {
                blockDropsEnabledGlobally = blockDropsSection.getBoolean("enabled", true);
                for (String region : blockDropsSection.getStringList("disabled-regions")) {
                    blockDropsDisabledRegionsGlobally.add(region.toLowerCase());
                }
            } else {
                blockDropsEnabledGlobally = true;
            }

            ConfigurationSection vanillaSection = settings.getConfigurationSection("vanilla-enchants");
            vanillaEnchantsEnabled = vanillaSection == null || vanillaSection.getBoolean("enabled", true);

            ConfigurationSection onHoldSection = settings.getConfigurationSection("on-hold");
            if (onHoldSection != null) {
                onHoldEnabled = onHoldSection.getBoolean("enabled", true);
                onHoldIntervalTicks = Math.max(1, onHoldSection.getInt("interval-ticks", 20));
            } else {
                onHoldEnabled = true;
                onHoldIntervalTicks = 20;
            }

            ConfigurationSection unbreakingSection = settings.getConfigurationSection("unbreaking-override");
            if (unbreakingSection != null) {
                unbreakingOverrideEnabled = unbreakingSection.getBoolean("enabled", false);
                unbreakingToolsFormula = unbreakingSection.getString("tools-formula", null);
                unbreakingArmorFormula = unbreakingSection.getString("armor-formula", null);
            } else {
                unbreakingOverrideEnabled = false;
                unbreakingToolsFormula = null;
                unbreakingArmorFormula = null;
            }
        } else {
            consumeBookOnFail = true;
            loreFormat = "%enchant% %level%";
            currencyName = "orbs";
            guiTitle = "&8Pickaxe Enchant Upgrades";
            blockDropsEnabledGlobally = true;
            vanillaEnchantsEnabled = true;
            onHoldEnabled = true;
            onHoldIntervalTicks = 20;
            unbreakingOverrideEnabled = false;
            unbreakingToolsFormula = null;
            unbreakingArmorFormula = null;
        }

        // Resolved once per /ce reload rather than per durability event -
        // accepts both the modern "UNBREAKING" key and the legacy
        // "DURABILITY" enum name, same as any "vanilla-enchant:" field.
        unbreakingVanillaEnchant = VanillaSupport.resolveEnchantment("UNBREAKING", null, "settings.unbreaking-override");

        ConfigurationSection messagesSection = config.getConfigurationSection("messages");
        if (messagesSection != null) {
            for (String key : messagesSection.getKeys(false)) {
                messages.put(key, messagesSection.getString(key, ""));
            }
        }

        ConfigurationSection tiersSection = config.getConfigurationSection("book-tiers");
        if (tiersSection != null) {
            for (String tierKey : tiersSection.getKeys(false)) {
                BookTier tier = BookTier.fromString(tierKey);
                if (tier == null) {
                    plugin.getLogger().warning("Unknown book tier in config: " + tierKey);
                    continue;
                }
                ConfigurationSection tierSection = tiersSection.getConfigurationSection(tierKey);
                if (tierSection == null) continue;
                tierColors.put(tier, tierSection.getString("color", "&f"));
                tierDisplayNames.put(tier, tierSection.getString("display", tier.name()));
            }
        }

        loadUnenchantedBooks(config.getConfigurationSection("unenchanted-books"));

        ConfigurationSection dustSection = config.getConfigurationSection("white-dust");
        if (dustSection != null) {
            dustMaterial = Material.matchMaterial(dustSection.getString("material", "SUGAR"));
            if (dustMaterial == null) dustMaterial = Material.SUGAR;
            dustName = dustSection.getString("name", "&fWhite Dust");
            dustLore = dustSection.getStringList("lore");
            dustBonusPerUse = dustSection.getDouble("bonus-per-use", 5.0);
            dustMaxBonus = dustSection.getDouble("max-bonus", 30.0);
        } else {
            dustMaterial = Material.SUGAR;
            dustName = "&fWhite Dust";
            dustLore = java.util.List.of();
            dustBonusPerUse = 5.0;
            dustMaxBonus = 30.0;
        }

        ConfigurationSection enchantsSection = config.getConfigurationSection("enchants");
        if (enchantsSection != null) {
            for (String id : enchantsSection.getKeys(false)) {
                try {
                    loadEnchant(id, enchantsSection.getConfigurationSection(id));
                } catch (Exception ex) {
                    plugin.getLogger().log(Level.SEVERE, "Failed to load enchant '" + id + "': " + ex.getMessage(), ex);
                }
            }
        }

        ConfigurationSection globalToolsSection = config.getConfigurationSection("global-tools");
        if (globalToolsSection != null) {
            for (String id : globalToolsSection.getKeys(false)) {
                try {
                    loadGlobalTool(id, globalToolsSection.getConfigurationSection(id));
                } catch (Exception ex) {
                    plugin.getLogger().log(Level.SEVERE, "Failed to load global-tools entry '" + id + "': " + ex.getMessage(), ex);
                }
            }
        }

        plugin.getLogger().info("Loaded " + enchants.size() + " custom enchant(s) and " + globalTools.size() + " global-tools set(s).");
    }

    /**
     * Loads the per-rarity mystery books from "unenchanted-books:". Each key
     * is a rarity (COMMON/RARE/LEGENDARY). NA is explicitly rejected - there is
     * no unenchanted book for NA-classed enchants - as is any unknown rarity.
     */
    private void loadUnenchantedBooks(ConfigurationSection section) {
        if (section == null) return;

        for (String key : section.getKeys(false)) {
            BookTier rarity = BookTier.fromString(key);
            if (rarity == null) {
                plugin.getLogger().warning("Unknown rarity in unenchanted-books: " + key + " - skipping.");
                continue;
            }
            if (rarity == BookTier.NA) {
                plugin.getLogger().warning("unenchanted-books: NA has no unenchanted book (NA enchants are never rolled) - skipping.");
                continue;
            }
            ConfigurationSection bookSection = section.getConfigurationSection(key);
            if (bookSection == null) continue;

            Material material = Material.matchMaterial(bookSection.getString("material", "BOOK"));
            if (material == null) material = Material.BOOK;
            String name = bookSection.getString("name", "&f" + rarity.name() + " Mystery Book");
            List<String> lore = bookSection.getStringList("lore");

            // The level rolled for the chosen enchant. "level" inside this range
            // resolves to the chosen enchant's max-level, so "min: 1, max: level"
            // spans the enchant's full range. Defaults to that when unconfigured.
            RangeValue levelRoll = bookSection.contains("level-roll")
                    ? RangeValue.parse(bookSection.get("level-roll"), plugin.getLogger(),
                        "unenchanted-books '" + key + "' level-roll")
                    : RangeValue.of(ScaledValue.flat(1), ScaledValue.parse("level", plugin.getLogger(),
                        "unenchanted-books '" + key + "' level-roll"));

            unenchantedBooks.put(rarity, new UnenchantedBook(rarity, material, name, lore, levelRoll));
        }
        plugin.getLogger().info("Loaded " + unenchantedBooks.size() + " unenchanted (mystery) book(s).");
    }

    /**
     * Loads one entry of the top-level "global-tools:" section - a named
     * group of item types (e.g. "all pickaxes") that runs "actions:" on its
     * own for the matching item types, with no enchant/book/PDC data
     * involved at all. Structured exactly like an individual enchant's
     * actions/values/ranges/block-drops so the same action-line DSL
     * (including "blockdrop:" and "disabled-regions:") works identically.
     */
    private void loadGlobalTool(String id, ConfigurationSection section) {
        if (section == null) return;

        Set<Material> materials = new HashSet<>();
        for (String matName : section.getStringList("materials")) {
            Material mat = Material.matchMaterial(matName);
            if (mat == null) {
                plugin.getLogger().warning("global-tools '" + id + "': unknown material '" + matName + "'");
                continue;
            }
            materials.add(mat);
        }
        if (materials.isEmpty()) {
            plugin.getLogger().warning("global-tools '" + id + "' has no valid materials configured - skipping.");
            return;
        }

        // Used for %level%/%level_num% placeholders and any level-scaled
        // values:/ranges: entries. There's no book level here, so this is a
        // fixed number for the whole set - defaults to 1 (i.e. "level" in a
        // formula just resolves to 1) unless overridden.
        int level = section.getInt("level", 1);

        Map<Trigger, List<String>> actions = new EnumMap<>(Trigger.class);
        ConfigurationSection actionsSection = section.getConfigurationSection("actions");
        if (actionsSection != null) {
            for (Trigger trigger : Trigger.values()) {
                List<String> lines = actionsSection.getStringList(trigger.configKey());
                if (!lines.isEmpty()) {
                    actions.put(trigger, lines);
                }
            }
        }
        if (actions.isEmpty()) {
            plugin.getLogger().warning("global-tools '" + id + "' has no actions configured - skipping.");
            return;
        }

        Map<String, ScaledValue> values = new HashMap<>();
        ConfigurationSection valuesSection = section.getConfigurationSection("values");
        if (valuesSection != null) {
            for (String key : valuesSection.getKeys(false)) {
                Object raw = valuesSection.isConfigurationSection(key)
                        ? valuesSection.getConfigurationSection(key)
                        : valuesSection.get(key);
                values.put(key.toLowerCase(), ScaledValue.parse(raw, plugin.getLogger(), "global-tools '" + id + "' values." + key));
            }
        }

        Map<String, RangeValue> ranges = new HashMap<>();
        ConfigurationSection rangesSection = section.getConfigurationSection("ranges");
        if (rangesSection != null) {
            for (String key : rangesSection.getKeys(false)) {
                Object raw = rangesSection.isConfigurationSection(key)
                        ? rangesSection.getConfigurationSection(key)
                        : rangesSection.get(key);
                ranges.put(key.toLowerCase(), RangeValue.parse(raw, plugin.getLogger(), "global-tools '" + id + "' ranges." + key));
            }
        }

        // Same "layers on top of settings.block-drops, never replaces it"
        // override style as an enchant's own "block-drops:" section.
        Boolean blockDropsEnabled = null;
        Set<String> blockDropsDisabledRegions = new HashSet<>();
        ConfigurationSection blockDropsSection = section.getConfigurationSection("block-drops");
        if (blockDropsSection != null) {
            if (blockDropsSection.isSet("enabled")) {
                blockDropsEnabled = blockDropsSection.getBoolean("enabled");
            }
            for (String region : blockDropsSection.getStringList("disabled-regions")) {
                blockDropsDisabledRegions.add(region.toLowerCase());
            }
        }

        GlobalToolSet tool = new GlobalToolSet(id, materials, level, actions, values, ranges, blockDropsEnabled, blockDropsDisabledRegions);
        globalTools.put(id.toLowerCase(), tool);
    }

    private void loadEnchant(String id, ConfigurationSection section) {
        if (section == null) return;

        String displayName = section.getString("display-name", id);
        int maxLevel = section.getInt("max-level", 1);
        
        String rarityStr = section.getString("enchant-rarity", "COMMON");
        BookTier rarity = BookTier.fromString(rarityStr);
        if (rarity == null) {
            plugin.getLogger().warning("Enchant '" + id + "' has invalid rarity: " + rarityStr + ". Defaulting to COMMON.");
            rarity = BookTier.COMMON;
        }

        Set<Material> supportedItems = new HashSet<>();
        for (String matName : section.getStringList("supported-items")) {
            Material mat = Material.matchMaterial(matName);
            if (mat == null) {
                plugin.getLogger().warning("Enchant '" + id + "': unknown material '" + matName + "'");
                continue;
            }
            supportedItems.add(mat);
        }

        // success-chance now resolves through ScaledValue like everything else -
        // a flat number behaves exactly as before, but it can also be a
        // per-level table or a "level"-aware formula/expression.
        Object rawChance = section.get("success-chance");
        ScaledValue successChance = rawChance != null
                ? ScaledValue.parse(rawChance, plugin.getLogger(), "enchant '" + id + "' success-chance")
                : ScaledValue.flat(50.0);

        Map<Trigger, java.util.List<String>> actions = new EnumMap<>(Trigger.class);
        ConfigurationSection actionsSection = section.getConfigurationSection("actions");
        if (actionsSection != null) {
            for (Trigger trigger : Trigger.values()) {
                java.util.List<String> lines = actionsSection.getStringList(trigger.configKey());
                if (!lines.isEmpty()) {
                    actions.put(trigger, lines);
                }
            }
        }

        boolean guiOn = section.getBoolean("gui-on", false);

        // upgrade-costs now resolves through the same ScaledValue system as
        // values:/ranges:/success-chance. The per-level table shape (2: 100,
        // 3: 250, ...) still works exactly as before, including "level not
        // listed = not configured" for the GUI. On top of that it can now
        // also be a flat number or a formula/expression, so you don't have
        // to hand-write a cost for every level up to max-level.
        Object rawCosts = section.get("upgrade-costs");
        ScaledValue upgradeCosts = rawCosts != null
                ? ScaledValue.parse(rawCosts, plugin.getLogger(), "enchant '" + id + "' upgrade-costs")
                : null; // not configured at all -> GUI always shows gui-not-configured

        // Per-enchant currency override. If not set, the GUI falls back to the
        // server-wide currency-name defined under settings.
        String currencyName = null;
        String currencyDisplay = null;
        ConfigurationSection currencySection = section.getConfigurationSection("currency");
        if (currencySection != null) {
            currencyName = currencySection.getString("name", null);
            currencyDisplay = currencySection.getString("display-name", null);
        }

        // Named, level-scalable numbers - used by "chance:"/"setvar:" action lines,
        // and by the amount field of "blockdrop:" lines. See ScaledValue for the
        // full set of accepted shapes (flat number, per-level table, or formula).
        Map<String, ScaledValue> values = new HashMap<>();
        ConfigurationSection valuesSection = section.getConfigurationSection("values");
        if (valuesSection != null) {
            for (String key : valuesSection.getKeys(false)) {
                Object raw = valuesSection.isConfigurationSection(key)
                        ? valuesSection.getConfigurationSection(key)
                        : valuesSection.get(key);
                values.put(key.toLowerCase(), ScaledValue.parse(raw, plugin.getLogger(), "enchant '" + id + "' values." + key));
            }
        }

        // Named, level-scalable random ranges - used by "randomvar:" action lines
        // and by the amount field of "blockdrop:" lines. See RangeValue.
        Map<String, RangeValue> ranges = new HashMap<>();
        ConfigurationSection rangesSection = section.getConfigurationSection("ranges");
        if (rangesSection != null) {
            for (String key : rangesSection.getKeys(false)) {
                Object raw = rangesSection.isConfigurationSection(key)
                        ? rangesSection.getConfigurationSection(key)
                        : rangesSection.get(key);
                ranges.put(key.toLowerCase(), RangeValue.parse(raw, plugin.getLogger(), "enchant '" + id + "' ranges." + key));
            }
        }

        // Per-enchant override for the "blockdrop:" action - layers on top of
        // (never replaces) the plugin-wide settings.block-drops section.
        Boolean blockDropsEnabled = null;
        Set<String> blockDropsDisabledRegions = new HashSet<>();
        ConfigurationSection blockDropsSection = section.getConfigurationSection("block-drops");
        if (blockDropsSection != null) {
            if (blockDropsSection.isSet("enabled")) {
                blockDropsEnabled = blockDropsSection.getBoolean("enabled");
            }
            for (String region : blockDropsSection.getStringList("disabled-regions")) {
                blockDropsDisabledRegions.add(region.toLowerCase());
            }
        }

        // Optional vanilla-enchant hijack: makes this enchant a real Bukkit
        // Enchantment under the hood (Efficiency, Sharpness, Protection,
        // ...), applied via ItemMeta#addEnchant(..., ignoreLevelRestriction
        // = true) - so "max-level: 200" on a vanilla-enchant: EFFICIENCY
        // enchant genuinely applies Efficiency 200 and its real vanilla
        // mining-speed math, not just a cosmetic number. Every other field
        // (success-chance, upgrade-costs, gui-on, actions, values/ranges)
        // works completely unchanged whether or not this is set - leave it
        // out entirely for a purely custom enchant, exactly as before.
        String vanillaEnchantName = section.getString("vanilla-enchant", null);
        Enchantment vanillaEnchant = vanillaEnchantName != null
                ? VanillaSupport.resolveEnchantment(vanillaEnchantName, plugin.getLogger(), "enchant '" + id + "' vanilla-enchant")
                : null;
        // Only meaningful when vanilla-enchant is set - hides the item's
        // own client-rendered tooltip line for it so only this plugin's
        // lore-format line is shown (avoids a duplicate-looking display).
        // Defaults to true; set to false to keep vanilla's native display too.
        boolean hideVanillaTooltip = section.getBoolean("hide-vanilla-tooltip", true);

        // Optional "vanilla potion effect as an enchant": kept active on
        // the player for as long as they hold/wear the item (see the
        // on-hold task), with amplifier and duration each resolving
        // through the same level-scaling DSL as values:/ranges: above.
        PotionEffectSpec potionEffect = null;
        ConfigurationSection potionSection = section.getConfigurationSection("potion-effect");
        if (potionSection != null) {
            String potionTypeName = potionSection.getString("type", null);
            PotionEffectType potionType = VanillaSupport.resolvePotionEffect(potionTypeName, plugin.getLogger(), "enchant '" + id + "' potion-effect.type");
            if (potionType != null) {
                ScaledValue amplifier = potionSection.contains("amplifier")
                        ? ScaledValue.parse(potionSection.get("amplifier"), plugin.getLogger(), "enchant '" + id + "' potion-effect.amplifier")
                        : ScaledValue.flat(0);
                ScaledValue durationTicks = potionSection.contains("duration-ticks")
                        ? ScaledValue.parse(potionSection.get("duration-ticks"), plugin.getLogger(), "enchant '" + id + "' potion-effect.duration-ticks")
                        : ScaledValue.flat(100);
                boolean ambient = potionSection.getBoolean("ambient", false);
                boolean particles = potionSection.getBoolean("particles", true);
                boolean icon = potionSection.getBoolean("icon", true);
                potionEffect = new PotionEffectSpec(potionType, amplifier, durationTicks, ambient, particles, icon);
            }
        }

        CustomEnchant enchant = new CustomEnchant(id, displayName, maxLevel, supportedItems, successChance, actions, rarity, guiOn, upgradeCosts, currencyName, currencyDisplay, values, ranges, blockDropsEnabled, blockDropsDisabledRegions, vanillaEnchant, hideVanillaTooltip, potionEffect);
        enchants.put(id.toLowerCase(), enchant);
    }

    public CustomEnchant getEnchant(String id) {
        return enchants.get(id.toLowerCase());
    }

    public Map<String, CustomEnchant> getEnchants() {
        return enchants;
    }

    public String getTierColor(BookTier tier) {
        return tierColors.getOrDefault(tier, "&f");
    }

    public String getTierDisplay(BookTier tier) {
        return tierDisplayNames.getOrDefault(tier, tier.name());
    }

    /** The unenchanted (mystery) book configured for this rarity, or null if none (always null for NA). */
    public UnenchantedBook getUnenchantedBook(BookTier rarity) {
        return rarity == null ? null : unenchantedBooks.get(rarity);
    }

    /** All configured unenchanted (mystery) books, keyed by rarity. */
    public Map<BookTier, UnenchantedBook> getUnenchantedBooks() {
        return unenchantedBooks;
    }

    /** Every enchant classed as the given rarity that can be applied to {@code material}, in no particular order. */
    public List<CustomEnchant> getEnchantsByRarityFor(BookTier rarity, Material material) {
        List<CustomEnchant> result = new ArrayList<>();
        if (rarity == null || material == null) return result;
        for (CustomEnchant enchant : enchants.values()) {
            if (enchant.getRarity() == rarity && enchant.supports(material)) {
                result.add(enchant);
            }
        }
        return result;
    }

    public boolean isConsumeBookOnFail() {
        return consumeBookOnFail;
    }

    public String getLoreFormat() {
        return loreFormat;
    }

    public String getCurrencyName() {
        return currencyName;
    }

    public String getGuiTitle() {
        return guiTitle;
    }

    public Material getDustMaterial() {
        return dustMaterial;
    }

    public String getDustName() {
        return dustName;
    }

    public java.util.List<String> getDustLore() {
        return dustLore;
    }

    public double getDustBonusPerUse() {
        return dustBonusPerUse;
    }

    public double getDustMaxBonus() {
        return dustMaxBonus;
    }

    public String getMessage(String key) {
        return messages.getOrDefault(key, "");
    }

    /** Plugin-wide kill switch for the "blockdrop:" bonus-drop action, from settings.block-drops.enabled. */
    public boolean isBlockDropsEnabledGlobally() {
        return blockDropsEnabledGlobally;
    }

    /** Plugin-wide WorldGuard region ids (lowercase) where "blockdrop:" never runs, from settings.block-drops.disabled-regions. */
    public Set<String> getBlockDropsDisabledRegionsGlobally() {
        return blockDropsDisabledRegionsGlobally;
    }

    /** Plugin-wide kill switch for "vanilla-enchant:"/"potion-effect:" hijack behaviour, from settings.vanilla-enchants.enabled. */
    public boolean isVanillaEnchantsEnabled() {
        return vanillaEnchantsEnabled;
    }

    /** Whether the on-hold task (on-hold action trigger + potion-effect enchants) is running at all, from settings.on-hold.enabled. */
    public boolean isOnHoldEnabled() {
        return onHoldEnabled;
    }

    /** How often (in ticks) the on-hold task runs, from settings.on-hold.interval-ticks. */
    public int getOnHoldIntervalTicks() {
        return onHoldIntervalTicks;
    }

    /** Plugin-wide kill switch for settings.unbreaking-override, from settings.unbreaking-override.enabled. Defaults to false (pure vanilla Unbreaking math) when unset. */
    public boolean isUnbreakingOverrideEnabled() {
        return unbreakingOverrideEnabled;
    }

    /** The configured formula (MathExpr DSL, "level"-aware) for non-armor items' chance-to-reduce-durability, or null to fall back to vanilla's 100/(level+1). */
    public String getUnbreakingToolsFormula() {
        return unbreakingToolsFormula;
    }

    /** The configured formula (MathExpr DSL, "level"-aware) for armor's chance-to-reduce-durability, or null to fall back to vanilla's 60+40/(level+1). */
    public String getUnbreakingArmorFormula() {
        return unbreakingArmorFormula;
    }

    /** The real vanilla Unbreaking enchantment (resolved once per load, accepting both the modern and legacy name), or null if this server's Bukkit API can't resolve it at all. */
    public Enchantment getUnbreakingVanillaEnchant() {
        return unbreakingVanillaEnchant;
    }

    /** All configured "global-tools:" sets, keyed by their lowercase config id. */
    public Map<String, GlobalToolSet> getGlobalTools() {
        return globalTools;
    }

    /** Every "global-tools:" set whose material list contains {@code material}, in config order. */
    public List<GlobalToolSet> getGlobalToolsForMaterial(Material material) {
        if (material == null) return List.of();
        List<GlobalToolSet> matches = new ArrayList<>();
        for (GlobalToolSet tool : globalTools.values()) {
            if (tool.matches(material)) matches.add(tool);
        }
        return matches;
    }
}
