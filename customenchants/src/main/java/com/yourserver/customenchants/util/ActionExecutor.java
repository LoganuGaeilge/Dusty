package com.yourserver.customenchants.util;

import com.yourserver.customenchants.CustomEnchantsPlugin;
import com.yourserver.customenchants.model.ActionSource;
import com.yourserver.customenchants.model.CustomEnchant;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Item;
import org.bukkit.entity.Player;
import org.bukkit.event.block.BlockDropItemEvent;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;

/**
 * Runs the "actions" lists defined per-enchant (and per-{@link
 * com.yourserver.customenchants.model.GlobalToolSet}) per-trigger in
 * config.yml.
 *
 * Supported line prefixes:
 *   cmd: <console command>            - dispatched by console, with placeholders replaced
 *   msg: <message>                    - sent directly to the player, color-coded
 *   skriptvar: <name> OP <value>      - OP is one of '=', '+=', '-=' (raw literal value)
 *
 *   chance: <percent|value-name>: <action>[; <action>...]
 *       Rolls a percentage chance (optionally a named, level-scaled value
 *       from the enchant's "values:" section - see ScaledValue) and, on
 *       success, runs the nested action line(s), separated by " ; ".
 *
 *   disabled-regions: <region1>[,<region2>...]: <action>[; <action>...]
 *       Runs the nested action line(s), separated by " ; ", ONLY if the
 *       current location is not inside any of the listed WorldGuard
 *       regions (comma-separated, case-insensitive). Unlike the
 *       blockdrop-only "settings.block-drops" gating below, this works on
 *       ANY action line - msg:, cmd:, another chance:, blockdrop:,
 *       whatever - so region restrictions aren't limited to bonus drops
 *       and can be placed anywhere in any trigger's action list. With no
 *       WorldGuard installed, or an empty region list, the nested
 *       action(s) always run.
 *
 *   setvar: <name> = <number|value-name>
 *       Resolves a literal number or a named, level-scaled "values:" entry
 *       and stores it both as a Skript variable AND as a local %name%
 *       placeholder usable by later lines in the same action list.
 *
 *   randomvar: <name> = <min-max|range-name>
 *       Picks a random number inside a literal "min-max" range or a named,
 *       level-scaled "ranges:" entry, then stores it the same way as setvar.
 *
 *   blockdrop: <amount|value-name|range-name>
 *       Only valid on the on-break-block trigger. Actually adds that many
 *       extra units of the block's own drop(s) to the world at the broken
 *       block's location - a real bonus-drop effect, not just a message or
 *       a currency payout. The amount can be a flat number, a
 *       "values:"/"ranges:" name, or an inline "min-max" range, so it can
 *       be a random amount that scales with level just like everything
 *       else. Outside of on-break-block (no block context available) this
 *       safely no-ops and logs a warning.
 *
 *       The bonus items are spawned and then announced via a real
 *       BlockDropItemEvent tied to the actual block, its pre-break state,
 *       and the player - so other plugins (auto-sell/shop plugins, drop
 *       counters, region protections, etc.) see them exactly as they would
 *       any other item the block naturally dropped, not as an anonymous
 *       item-spawn. If something cancels that event, the bonus items are
 *       removed again.
 *
 *       Whether this action is allowed to run at all is gated by the
 *       plugin-wide "settings.block-drops" section in config.yml (an
 *       "enabled" kill switch plus a "disabled-regions" WorldGuard region
 *       list) and, optionally, a per-enchant "block-drops" section that
 *       layers on top of it (its own "enabled" override and additional
 *       "disabled-regions"). WorldGuard is an optional soft dependency -
 *       region checks are simply skipped (never block drops) if it isn't
 *       installed.
 *
 * This is intentionally simple text-based "scripting" so the behaviour of
 * every enchant is fully editable from config.yml without touching Java,
 * and can call out to ANY economy plugin (via cmd:) or directly into a
 * Skript-based economy (via skriptvar:/setvar:/randomvar:).
 */
public final class ActionExecutor {

    private final CustomEnchantsPlugin plugin;
    private final Random random = new Random();

    public ActionExecutor(CustomEnchantsPlugin plugin) {
        this.plugin = plugin;
    }

    /**
     * Original entry point, preserved for backward compatibility. Runs the
     * given lines with no enchant context, so "chance:"/"setvar:"/"randomvar:"
     * lines that reference a named value or range (rather than a literal
     * number/range) will not be able to resolve them and will fall back to 0.
     */
    public void run(List<String> lines, Player player, int level, Entity victim) {
        run(null, lines, player, level, victim);
    }

    /**
     * Primary entry point - source is used to resolve named "values:"/"ranges:"
     * entries and blockdrop-region settings. Accepts anything implementing
     * {@link ActionSource} - both a real {@link CustomEnchant} (book-applied)
     * and a {@link com.yourserver.customenchants.model.GlobalToolSet}
     * (material-matched, no enchant involved) work identically here.
     */
    public void run(ActionSource source, List<String> lines, Player player, int level, Entity victim) {
        run(source, lines, player, level, victim, null);
    }

    /**
     * Entry point used for the on-break-block trigger, where {@code blockDrops}
     * carries the broken block's location and item drops so that "blockdrop:"
     * action lines have something real to add to.
     */
    public void run(ActionSource source, List<String> lines, Player player, int level, Entity victim,
                     BlockDropContext blockDrops) {
        if (lines == null || lines.isEmpty()) return;

        Map<String, String> placeholders = basePlaceholders(player, level, victim);
        for (String rawLine : lines) {
            executeLine(source, rawLine, player, level, victim, placeholders, blockDrops);
        }
    }

    private Map<String, String> basePlaceholders(Player player, int level, Entity victim) {
        Map<String, String> placeholders = new HashMap<>();
        placeholders.put("%player%", player.getName());
        placeholders.put("%level%", RomanNumeral.toRoman(level));
        placeholders.put("%level_num%", String.valueOf(level));
        placeholders.put("%victim%", victim != null ? victim.getUniqueId().toString() : "");
        placeholders.put("%world%", player.getWorld().getName());
        placeholders.put("%x%", String.valueOf(player.getLocation().getBlockX()));
        placeholders.put("%y%", String.valueOf(player.getLocation().getBlockY()));
        placeholders.put("%z%", String.valueOf(player.getLocation().getBlockZ()));
        return placeholders;
    }

    /**
     * Executes a single action line, mutating {@code placeholders} in place so that
     * setvar:/randomvar: lines earlier in the same list are visible to later lines
     * (including nested lines run from a successful chance: roll).
     */
    private void executeLine(ActionSource source, String rawLine, Player player, int level,
                              Entity victim, Map<String, String> placeholders, BlockDropContext blockDrops) {
        String line = Text.placeholders(rawLine, placeholders).trim();
        if (line.isEmpty()) return;

        if (line.regionMatches(true, 0, "cmd:", 0, 4)) {
            String command = line.substring(4).trim();
            Bukkit.getScheduler().runTask(plugin, () ->
                    Bukkit.dispatchCommand(Bukkit.getConsoleSender(), command));

        } else if (line.regionMatches(true, 0, "msg:", 0, 4)) {
            String message = line.substring(4).trim();
            player.sendMessage(Text.color(message));

        } else if (line.regionMatches(true, 0, "skriptvar:", 0, 10)) {
            handleSkriptVar(line.substring(10).trim());

        } else if (line.regionMatches(true, 0, "chance:", 0, 7)) {
            handleChance(source, line.substring(7).trim(), player, level, victim, placeholders, blockDrops);

        } else if (line.regionMatches(true, 0, "disabled-regions:", 0, 17)) {
            handleDisabledRegions(source, line.substring(17).trim(), player, level, victim, placeholders, blockDrops);

        } else if (line.regionMatches(true, 0, "setvar:", 0, 7)) {
            handleSetVar(source, line.substring(7).trim(), level, placeholders);

        } else if (line.regionMatches(true, 0, "randomvar:", 0, 10)) {
            handleRandomVar(source, line.substring(10).trim(), level, placeholders);

        } else if (line.regionMatches(true, 0, "blockdrop:", 0, 10)) {
            handleBlockDrop(source, line.substring(10).trim(), level, blockDrops);

        } else {
            // Unknown prefix - treat as a raw console command for convenience.
            String command = line;
            Bukkit.getScheduler().runTask(plugin, () ->
                    Bukkit.dispatchCommand(Bukkit.getConsoleSender(), command));
        }
    }

    // ---------------------------------------------------------------
    // chance: <percent|value-name>: <action>[; <action>...]
    // ---------------------------------------------------------------
    private void handleChance(ActionSource source, String expression, Player player, int level,
                               Entity victim, Map<String, String> placeholders, BlockDropContext blockDrops) {
        int split = expression.indexOf(':');
        if (split == -1) {
            plugin.getLogger().warning("[CustomEnchants] Invalid chance action (missing ':' before the nested action): " + expression);
            return;
        }

        String key = expression.substring(0, split).trim();
        String nested = expression.substring(split + 1).trim();
        if (nested.isEmpty()) return;

        double percent = resolveNumber(source, key, level);
        boolean success = random.nextDouble() * 100.0 < percent;
        if (!success) return;

        for (String nestedLine : nested.split("\\s*;\\s*")) {
            executeLine(source, nestedLine, player, level, victim, placeholders, blockDrops);
        }
    }

    // ---------------------------------------------------------------
    // disabled-regions: <region1>[,<region2>...]: <action>[; <action>...]
    //
    // A general-purpose gate, usable on ANY action line (msg:, cmd:,
    // blockdrop:, another chance:, etc.) - not just blockdrop:. Runs the
    // nested action(s) only if the current location is NOT inside any of
    // the listed WorldGuard regions. With no regions installed, or an
    // empty region list, the nested action(s) always run. This is separate
    // from (and on top of) the dedicated "settings.block-drops"/per-source
    // "block-drops:" sections that blockdrop: also always respects - use
    // this when you want a region check on something other than a bonus
    // drop, or a different region list than the block-drops one.
    //
    // The location checked is the broken block's location on
    // on-break-block (where a BlockDropContext is available), otherwise
    // the player's current location.
    // ---------------------------------------------------------------
    private void handleDisabledRegions(ActionSource source, String expression, Player player, int level,
                                        Entity victim, Map<String, String> placeholders, BlockDropContext blockDrops) {
        int split = expression.indexOf(':');
        if (split == -1) {
            plugin.getLogger().warning("[CustomEnchants] Invalid disabled-regions action (missing ':' before the nested action): " + expression);
            return;
        }

        String regionList = expression.substring(0, split).trim();
        String nested = expression.substring(split + 1).trim();
        if (nested.isEmpty()) return;

        Set<String> regions = new HashSet<>();
        for (String region : regionList.split(",")) {
            String trimmed = region.trim();
            if (!trimmed.isEmpty()) regions.add(trimmed);
        }

        if (!regions.isEmpty()) {
            Location location = (blockDrops != null) ? blockDrops.getLocation() : player.getLocation();
            if (WorldGuardSupport.isInAnyRegion(location, regions, plugin.getLogger())) {
                return; // in a disabled region - skip the nested action(s) entirely
            }
        }

        for (String nestedLine : nested.split("\\s*;\\s*")) {
            executeLine(source, nestedLine, player, level, victim, placeholders, blockDrops);
        }
    }

    // ---------------------------------------------------------------
    // setvar: <name> = <number|value-name>
    // ---------------------------------------------------------------
    private void handleSetVar(ActionSource source, String expression, int level, Map<String, String> placeholders) {
        int eq = expression.indexOf('=');
        if (eq == -1) {
            plugin.getLogger().warning("[CustomEnchants] Invalid setvar action (no '='): " + expression);
            return;
        }
        String name = expression.substring(0, eq).trim();
        String key = expression.substring(eq + 1).trim();

        double value = resolveNumber(source, key, level);
        applyResolvedVariable(name, value, placeholders);
    }

    // ---------------------------------------------------------------
    // randomvar: <name> = <min-max|range-name>
    // ---------------------------------------------------------------
    private void handleRandomVar(ActionSource source, String expression, int level, Map<String, String> placeholders) {
        int eq = expression.indexOf('=');
        if (eq == -1) {
            plugin.getLogger().warning("[CustomEnchants] Invalid randomvar action (no '='): " + expression);
            return;
        }
        String name = expression.substring(0, eq).trim();
        String key = expression.substring(eq + 1).trim();

        RangeValue range = (source != null) ? source.getRangeValue(key) : null;
        double value;
        if (range != null) {
            value = range.resolveRandom(level, random);
        } else {
            value = RangeValue.parseLiteral(key, plugin.getLogger(), "randomvar action '" + name + "'")
                    .resolveRandom(level, random);
        }

        applyResolvedVariable(name, value, placeholders);
    }

    /** Stores a resolved number both as a Skript variable and as a local %name% placeholder. */
    private void applyResolvedVariable(String name, double value, Map<String, String> placeholders) {
        String formatted = formatNumber(value);
        SkriptBridge.setVariable(plugin.getLogger(), name, value);

        String placeholderKey = name.startsWith("%") && name.endsWith("%") ? name : "%" + name + "%";
        placeholders.put(placeholderKey, formatted);
    }

    private String formatNumber(double value) {
        if (value == Math.rint(value) && !Double.isInfinite(value)) {
            return String.valueOf((long) value);
        }
        return String.valueOf(value);
    }

    // ---------------------------------------------------------------
    // blockdrop: <amount|value-name|range-name>
    // ---------------------------------------------------------------
    private void handleBlockDrop(ActionSource source, String key, int level, BlockDropContext blockDrops) {
        if (blockDrops == null) {
            plugin.getLogger().warning("[CustomEnchants] 'blockdrop:' action can only be used on the on-break-block trigger - skipped.");
            return;
        }

        Location location = blockDrops.getLocation();
        if (!isBlockDropAllowed(source, location)) return;

        List<ItemStack> drops = blockDrops.getDrops();
        if (drops == null || drops.isEmpty()) return;

        int amount = resolveAmount(source, key, level);
        if (amount <= 0) return;

        World world = location.getWorld();
        if (world == null) return;

        List<ItemStack> bonuses = new ArrayList<>();
        for (ItemStack drop : drops) {
            if (drop == null || drop.getType().isAir() || drop.getAmount() <= 0) continue;
            ItemStack bonus = drop.clone();
            bonus.setAmount(amount);
            bonuses.add(bonus);
        }
        if (bonuses.isEmpty()) return;

        Bukkit.getScheduler().runTask(plugin, () -> dropAsBlockDrop(world, location, blockDrops, bonuses));
    }

    /**
     * Spawns the bonus items and fires a real BlockDropItemEvent for them,
     * carrying the actual broken block, its pre-break state, and the
     * player - so other plugins (auto-sell/shop plugins, drop trackers,
     * region protections, etc.) see these bonus items exactly as they'd see
     * any other item the block naturally dropped, rather than an anonymous
     * item-spawn with no connection to the block that was mined.
     */
    private void dropAsBlockDrop(World world, Location location, BlockDropContext blockDrops, List<ItemStack> bonuses) {
        List<Item> spawned = new ArrayList<>();
        for (ItemStack bonus : bonuses) {
            spawned.add(world.dropItemNaturally(location, bonus));
        }
        if (spawned.isEmpty()) return;

        if (blockDrops.getBlock() == null || blockDrops.getBlockState() == null || blockDrops.getPlayer() == null) {
            // Missing context (shouldn't happen for on-break-block) - items are
            // still in the world, just can't be tied back to the block/player.
            return;
        }

        BlockDropItemEvent dropEvent = new BlockDropItemEvent(
                blockDrops.getBlock(), blockDrops.getBlockState(), blockDrops.getPlayer(), spawned);
        Bukkit.getPluginManager().callEvent(dropEvent);
        if (dropEvent.isCancelled()) {
            for (Item item : spawned) {
                item.remove();
            }
        }
    }

    /**
     * Whether the "blockdrop:" action is allowed to run at {@code location},
     * given the plugin-wide settings.block-drops section and this enchant's
     * own (optional) block-drops override. Both the global and per-enchant
     * enabled flags must be true, and the location must not be inside any
     * region named in either the global or the per-enchant disabled-regions
     * lists.
     */
    private boolean isBlockDropAllowed(ActionSource source, Location location) {
        com.yourserver.customenchants.EnchantManager manager = plugin.getEnchantManager();

        if (!manager.isBlockDropsEnabledGlobally()) return false;
        if (source != null) {
            Boolean override = source.getBlockDropsEnabledOverride();
            if (override != null && !override) return false;
        }

        Set<String> regions = new HashSet<>(manager.getBlockDropsDisabledRegionsGlobally());
        if (source != null) regions.addAll(source.getBlockDropsDisabledRegions());
        if (regions.isEmpty()) return true;

        return !WorldGuardSupport.isInAnyRegion(location, regions, plugin.getLogger());
    }

    /**
     * Resolves a number that may be a named "values:" entry, a named
     * "ranges:" entry, a literal number, a literal "min-max" range, or (new)
     * an inline "level"-aware math expression - so "chance:", "setvar:", and
     * "blockdrop:" lines can do real math directly without needing a named
     * "values:" entry for one-off cases. e.g. "chance: level*5: msg: ...".
     */
    private double resolveNumber(ActionSource source, String key, int level) {
        ScaledValue value = (source != null) ? source.getScaledValue(key) : null;
        if (value != null) return value.resolve(level);

        RangeValue range = (source != null) ? source.getRangeValue(key) : null;
        if (range != null) return range.resolveRandom(level, random);

        try {
            return Double.parseDouble(key);
        } catch (NumberFormatException ex) {
            // A plain "a-b" shape is treated as a random range first, exactly
            // as before, so existing configs keep behaving identically.
            if (key.matches("-?\\d+(\\.\\d+)?\\s*-\\s*-?\\d+(\\.\\d+)?")) {
                return RangeValue.parseLiteral(key, null, "inline value").resolveRandom(level, random);
            }
            // Otherwise try it as a "level"-aware expression before giving up.
            Double exprResult = MathExpr.tryEvaluate(key, level);
            if (exprResult != null) return exprResult;
            // Last resort - keeps the original warn-and-default-to-0 behaviour for garbage input.
            return RangeValue.parseLiteral(key, plugin.getLogger(), "inline value").resolveRandom(level, random);
        }
    }

    private int resolveAmount(ActionSource source, String key, int level) {
        return (int) Math.round(resolveNumber(source, key, level));
    }

    private void handleSkriptVar(String expression) {
        // expected forms: "name = value" | "name += value" | "name -= value"
        String op;
        int opIndex;
        if ((opIndex = expression.indexOf("+=")) != -1) {
            op = "+=";
        } else if ((opIndex = expression.indexOf("-=")) != -1) {
            op = "-=";
        } else if ((opIndex = expression.indexOf('=')) != -1) {
            op = "=";
        } else {
            plugin.getLogger().warning("[CustomEnchants] Invalid skriptvar action (no operator): " + expression);
            return;
        }

        String name = expression.substring(0, opIndex).trim();
        String rawValue = expression.substring(opIndex + op.length()).trim();

        if ("=".equals(op)) {
            // try numeric first, fall back to raw string
            try {
                double value = Double.parseDouble(rawValue);
                SkriptBridge.setVariable(plugin.getLogger(), name, value);
            } catch (NumberFormatException ex) {
                SkriptBridge.setVariable(plugin.getLogger(), name, rawValue);
            }
            return;
        }

        double delta;
        try {
            delta = Double.parseDouble(rawValue);
        } catch (NumberFormatException ex) {
            plugin.getLogger().warning("[CustomEnchants] Invalid numeric value in skriptvar action: " + expression);
            return;
        }

        double current = SkriptBridge.getNumericVariable(plugin.getLogger(), name);
        double result = "+=".equals(op) ? current + delta : current - delta;
        SkriptBridge.setVariable(plugin.getLogger(), name, result);
    }
}
