package com.yourserver.customenchants.listeners;

import com.yourserver.customenchants.CustomEnchantsPlugin;
import com.yourserver.customenchants.EnchantManager;
import com.yourserver.customenchants.util.MathExpr;
import org.bukkit.Material;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerItemDamageEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.Random;

/**
 * Overrides vanilla Unbreaking's own "chance a use reduces durability" math
 * with server-configured formulas (settings.unbreaking-override in
 * config.yml), applied separately to armor vs. everything else - the same
 * split vanilla itself makes:
 *
 *   tools/items (vanilla default): 100 / (level + 1)
 *   armor       (vanilla default): 60 + (40 / (level + 1))
 *
 * This applies to ANY item bearing a real vanilla Unbreaking enchantment -
 * whether it got there via this plugin's own "vanilla-enchant: UNBREAKING"
 * hijack, a normal enchanting table, an anvil combine, or another plugin -
 * so the item is still genuinely Unbreaking as far as the game, other
 * plugins, and anvils are concerned. Only the actual durability-roll math
 * is replaced.
 *
 * ACCURACY NOTE: Bukkit's PlayerItemDamageEvent only exposes the durability
 * amount AFTER vanilla's own Unbreaking roll has already run - there's no
 * way on plain Spigot/Paper 1.20 API to see what it would have been before
 * that roll. This listener therefore ignores whatever vanilla just decided
 * and rolls its own independent pass/fail against the configured formula
 * for max(1, event.getDamage()) durability point(s). For the overwhelming
 * majority of real hits (one mining swing, one melee hit, one point of
 * armor damage, etc.) that's exactly one point, so this is fully accurate.
 * The only edge case is a single event that represents more than one point
 * of damage AND vanilla's own roll already zeroed it out entirely (e.g. an
 * explosion or a multi-point fishing-hook hit that vanilla fully negated) -
 * there this listener can only see "0" and assumes that meant one point,
 * which very slightly under-counts how many rolls it gets to make. This
 * does not affect the overall long-run chance per point, just how many
 * points a single rare multi-point event is judged across.
 */
public class UnbreakingOverrideListener implements Listener {

    private final CustomEnchantsPlugin plugin;
    private final Random random = new Random();

    public UnbreakingOverrideListener(CustomEnchantsPlugin plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onItemDamage(PlayerItemDamageEvent event) {
        EnchantManager manager = plugin.getEnchantManager();
        if (!manager.isUnbreakingOverrideEnabled()) return;

        Enchantment unbreaking = manager.getUnbreakingVanillaEnchant();
        if (unbreaking == null) return;

        ItemStack item = event.getItem();
        if (item == null || !item.hasItemMeta()) return;

        ItemMeta meta = item.getItemMeta();
        int level = meta.getEnchantLevel(unbreaking);
        if (level <= 0) return;

        boolean armor = isArmor(item.getType());
        String formula = armor ? manager.getUnbreakingArmorFormula() : manager.getUnbreakingToolsFormula();
        double reduceChance = resolveChance(formula, level, armor);

        int attemptedPoints = Math.max(1, event.getDamage());
        int finalDamage = 0;
        for (int i = 0; i < attemptedPoints; i++) {
            if (random.nextDouble() * 100.0 < reduceChance) {
                finalDamage++;
            }
        }

        event.setDamage(finalDamage);
    }

    /** Evaluates the configured formula, falling back to the real vanilla formula for that category if it's unset or fails to parse. Result is always clamped to 0-100. */
    private double resolveChance(String formula, int level, boolean armor) {
        Double result = MathExpr.tryEvaluate(formula, level);
        if (result == null) {
            result = armor ? (60.0 + (40.0 / (level + 1))) : (100.0 / (level + 1));
        }
        return Math.max(0.0, Math.min(100.0, result));
    }

    private boolean isArmor(Material material) {
        String name = material.name();
        return name.endsWith("_HELMET") || name.endsWith("_CHESTPLATE")
                || name.endsWith("_LEGGINGS") || name.endsWith("_BOOTS");
    }
}
