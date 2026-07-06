package com.yourserver.customenchants.listeners;

import com.yourserver.customenchants.CustomEnchantsPlugin;
import com.yourserver.customenchants.EnchantManager;
import com.yourserver.customenchants.ItemFactory;
import com.yourserver.customenchants.model.CustomEnchant;
import com.yourserver.customenchants.model.GlobalToolSet;
import com.yourserver.customenchants.model.Trigger;
import com.yourserver.customenchants.util.BlockDropContext;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerItemConsumeEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;

import java.util.List;
import java.util.Map;

public class TriggerListener implements Listener {

    private final CustomEnchantsPlugin plugin;
    private final EnchantManager manager;
    private final ItemFactory items;

    public TriggerListener(CustomEnchantsPlugin plugin) {
        this.plugin = plugin;
        this.manager = plugin.getEnchantManager();
        this.items = plugin.getItemFactory();
    }

    @EventHandler
    public void onEntityDamage(EntityDamageByEntityEvent event) {
        if (!(event.getDamager() instanceof Player attacker)) return;

        ItemStack weapon = attacker.getInventory().getItemInMainHand();
        fireAllForItem(weapon, Trigger.ON_HIT, attacker, event.getEntity());

        if (event.getEntity() instanceof Player defender) {
            for (ItemStack armor : defender.getInventory().getArmorContents()) {
                fireAllForItem(armor, Trigger.ON_HIT_TAKEN, defender, attacker);
            }
        }
    }

    @EventHandler
    public void onEntityDeath(EntityDeathEvent event) {
        LivingEntity dead = event.getEntity();
        Player killer = dead.getKiller();
        if (killer == null) return;
        ItemStack weapon = killer.getInventory().getItemInMainHand();
        fireAllForItem(weapon, Trigger.ON_KILL, killer, dead);
    }

    // MONITOR + ignoreCancelled so this only ever fires once the break is
    // truly going to happen. MONITOR runs after every other plugin's
    // listeners (WorldGuard's region protection, Skript scripts doing
    // "cancel event", etc.), and ignoreCancelled means Bukkit skips calling
    // this method at all if the event is already cancelled by then - so a
    // break that WorldGuard or Skript blocked no longer triggers bonus
    // drops, payouts, or any other on-break-block action.
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onBlockBreak(BlockBreakEvent event) {
        Player player = event.getPlayer();
        ItemStack tool = player.getInventory().getItemInMainHand();
        Block block = event.getBlock();

        // Captured now, while the block is still intact, so a "blockdrop:"
        // action always has the real drop(s) to add bonus copies of - even
        // if the action itself ends up running a tick later. The BlockState
        // snapshot is taken before the block is removed so the bonus-drop
        // event we later fire carries the same pre-break state a vanilla
        // block-drop event would.
        BlockDropContext blockDrops = new BlockDropContext(
                block.getLocation(),
                new java.util.ArrayList<>(block.getDrops(tool)),
                block,
                block.getState(),
                player);

        fireAllForItem(tool, Trigger.ON_BREAK_BLOCK, player, null, blockDrops);
    }

    @EventHandler
    public void onInteract(PlayerInteractEvent event) {
        // Skip main-hand book/dust application clicks - ApplyListener already handles those.
        if (event.getHand() != EquipmentSlot.HAND) return;
        Action action = event.getAction();
        if (action != Action.RIGHT_CLICK_AIR && action != Action.RIGHT_CLICK_BLOCK) return;

        Player player = event.getPlayer();
        ItemStack main = player.getInventory().getItemInMainHand();
        if (items.isEnchantBook(main) || items.isWhiteDust(main)) return; // handled elsewhere

        fireAllForItem(main, Trigger.ON_INTERACT, player, null);
    }

    @EventHandler
    public void onConsume(PlayerItemConsumeEvent event) {
        fireAllForItem(event.getItem(), Trigger.ON_CONSUME, event.getPlayer(), null);
    }

    private void fireAllForItem(ItemStack item, Trigger trigger, Player player, org.bukkit.entity.Entity victim) {
        fireAllForItem(item, trigger, player, victim, null);
    }

    private void fireAllForItem(ItemStack item, Trigger trigger, Player player, org.bukkit.entity.Entity victim,
                                 BlockDropContext blockDrops) {
        if (item == null) return;

        Map<String, Integer> applied = items.getAllApplied(item);
        for (Map.Entry<String, Integer> entry : applied.entrySet()) {
            CustomEnchant enchant = manager.getEnchant(entry.getKey());
            if (enchant == null) continue;
            plugin.getActionExecutor().run(enchant, enchant.getActions(trigger), player, entry.getValue(), victim, blockDrops);
        }

        // "global-tools:" - runs on the item's material alone, with no
        // enchant/book/PDC data involved at all, so an admin can make e.g.
        // every diamond pickaxe do something on break without needing a
        // fake always-applied enchant to get there. Fires in addition to
        // (never instead of) any real enchants above, and independently of
        // whether the item has any applied enchants at all.
        Material material = item.getType();
        List<GlobalToolSet> globalTools = manager.getGlobalToolsForMaterial(material);
        for (GlobalToolSet tool : globalTools) {
            List<String> lines = tool.getActions(trigger);
            if (lines.isEmpty()) continue;
            plugin.getActionExecutor().run(tool, lines, player, tool.getLevel(), victim, blockDrops);
        }
    }
}
