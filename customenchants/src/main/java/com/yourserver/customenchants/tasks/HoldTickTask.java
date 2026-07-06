package com.yourserver.customenchants.tasks;

import com.yourserver.customenchants.CustomEnchantsPlugin;
import com.yourserver.customenchants.EnchantManager;
import com.yourserver.customenchants.ItemFactory;
import com.yourserver.customenchants.model.CustomEnchant;
import com.yourserver.customenchants.model.GlobalToolSet;
import com.yourserver.customenchants.model.PotionEffectSpec;
import com.yourserver.customenchants.model.Trigger;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitRunnable;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Periodic "while holding/wearing" tick - purely additive on top of the
 * existing event-driven triggers in {@link com.yourserver.customenchants.listeners.TriggerListener}.
 * Drives two features, both fully config-toggleable via settings.on-hold:
 *
 * <ul>
 *   <li>The {@code on-hold} action trigger: any enchant's (or global-tools
 *       set's) {@code actions: on-hold:} list runs once per tick interval
 *       for every currently held (main/off hand) or worn (armor) item that
 *       carries it.</li>
 *   <li>Potion-effect enchants ({@code potion-effect:} in config): keeps
 *       the configured {@link PotionEffect} refreshed on the player for as
 *       long as the item granting it stays held/worn, and cleanly removes
 *       it the instant it no longer is - without ever touching a potion
 *       effect this plugin didn't grant itself.</li>
 * </ul>
 *
 * Nothing here changes the behaviour of any existing trigger, item, or
 * enchant that doesn't opt into either feature.
 */
public class HoldTickTask extends BukkitRunnable {

    private final CustomEnchantsPlugin plugin;
    private final EnchantManager manager;
    private final ItemFactory items;
    private final int intervalTicks;

    // Potion effect types currently active on a player because THIS plugin
    // granted them via a potion-effect enchant - the only ones we're ever
    // allowed to remove again, so a real potion the player drank (or an
    // effect from some other plugin) is never touched.
    private final Map<UUID, Set<PotionEffectType>> managedEffects = new HashMap<>();

    public HoldTickTask(CustomEnchantsPlugin plugin, int intervalTicks) {
        this.plugin = plugin;
        this.manager = plugin.getEnchantManager();
        this.items = plugin.getItemFactory();
        this.intervalTicks = Math.max(1, intervalTicks);
    }

    @Override
    public void run() {
        for (Player player : Bukkit.getOnlinePlayers()) {
            tickPlayer(player);
        }
    }

    private void tickPlayer(Player player) {
        PlayerInventory inv = player.getInventory();
        ItemStack[] slots = {
                inv.getItemInMainHand(),
                inv.getItemInOffHand(),
                inv.getHelmet(),
                inv.getChestplate(),
                inv.getLeggings(),
                inv.getBoots()
        };

        Set<PotionEffectType> desired = new HashSet<>();
        for (ItemStack item : slots) {
            if (item == null || item.getType().isAir()) continue;
            tickItem(player, item, desired);
        }

        reconcilePotionEffects(player, desired);
    }

    private void tickItem(Player player, ItemStack item, Set<PotionEffectType> desired) {
        Map<String, Integer> applied = items.getAllApplied(item);
        for (Map.Entry<String, Integer> entry : applied.entrySet()) {
            CustomEnchant enchant = manager.getEnchant(entry.getKey());
            if (enchant == null) continue;
            int level = entry.getValue();

            List<String> lines = enchant.getActions(Trigger.ON_HOLD);
            if (!lines.isEmpty()) {
                plugin.getActionExecutor().run(enchant, lines, player, level, null);
            }

            if (manager.isVanillaEnchantsEnabled()) {
                applyPotionEffect(player, enchant.getPotionEffect(), level, desired);
            }
        }

        // global-tools: on-hold works here too, exactly like every other trigger.
        for (GlobalToolSet tool : manager.getGlobalToolsForMaterial(item.getType())) {
            List<String> lines = tool.getActions(Trigger.ON_HOLD);
            if (!lines.isEmpty()) {
                plugin.getActionExecutor().run(tool, lines, player, tool.getLevel(), null);
            }
        }
    }

    private void applyPotionEffect(Player player, PotionEffectSpec spec, int level, Set<PotionEffectType> desired) {
        if (spec == null || spec.getType() == null) return;

        int amplifier = Math.max(0, (int) Math.round(spec.getAmplifier().resolve(level)));
        int configuredDuration = Math.max(1, (int) Math.round(spec.getDurationTicks().resolve(level)));
        // Always outlast the tick interval by a small buffer so the effect
        // never visibly blinks off between two consecutive on-hold ticks.
        int appliedDuration = Math.max(configuredDuration, intervalTicks + 10);

        player.addPotionEffect(new PotionEffect(spec.getType(), appliedDuration, amplifier,
                spec.isAmbient(), spec.isParticles(), spec.isIcon()), true);
        desired.add(spec.getType());
    }

    private void reconcilePotionEffects(Player player, Set<PotionEffectType> desired) {
        UUID uuid = player.getUniqueId();
        Set<PotionEffectType> previous = managedEffects.getOrDefault(uuid, Set.of());
        for (PotionEffectType type : previous) {
            if (!desired.contains(type)) {
                player.removePotionEffect(type);
            }
        }
        if (desired.isEmpty()) {
            managedEffects.remove(uuid);
        } else {
            managedEffects.put(uuid, desired);
        }
    }

    /** Drops bookkeeping for a player who disconnected. Never touches their actual potion effects - they're leaving anyway. */
    public void forgetPlayer(UUID uuid) {
        managedEffects.remove(uuid);
    }
}
