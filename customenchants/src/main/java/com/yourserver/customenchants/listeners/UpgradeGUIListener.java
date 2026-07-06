package com.yourserver.customenchants.gui;

import com.yourserver.customenchants.CustomEnchantsPlugin;
import com.yourserver.customenchants.EnchantManager;
import com.yourserver.customenchants.ItemFactory;
import com.yourserver.customenchants.model.CustomEnchant;
import com.yourserver.customenchants.model.Trigger;
import com.yourserver.customenchants.util.RomanNumeral;
import com.yourserver.customenchants.util.DustyEconomyBridge;
import com.yourserver.customenchants.util.SkriptBridge;
import com.yourserver.customenchants.util.Text;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Right-clicking a pickaxe opens a GUI listing every enchant that has
 * gui-on: true and supports pickaxes. Clicking an entry spends currency
 * to instantly bump that enchant up one level on the pickaxe in hand -
 * no book, no roll, just a straight purchase.
 *
 * Balance is read live from DustyEconomy's own EconomyManager (via
 * DustyEconomyBridge), falling back to the legacy Skript variable only if
 * DustyEconomy isn't installed. The actual charge is still applied by
 * dispatching DustyEconomy's /currencyadd command with a negative amount,
 * so all balance changes go through the same code path DustyEconomy
 * already uses elsewhere (and that command's own clamping keeps the
 * balance from ever going negative even if it were ever called for more
 * than a player has).
 */
public class UpgradeGUIListener implements Listener {

    private static final Set<Material> PICKAXE_MATERIALS = Set.of(
            Material.WOODEN_PICKAXE, Material.STONE_PICKAXE, Material.IRON_PICKAXE,
            Material.GOLDEN_PICKAXE, Material.DIAMOND_PICKAXE, Material.NETHERITE_PICKAXE
    );

    private final CustomEnchantsPlugin plugin;
    private final EnchantManager manager;
    private final ItemFactory items;

    public UpgradeGUIListener(CustomEnchantsPlugin plugin) {
        this.plugin = plugin;
        this.manager = plugin.getEnchantManager();
        this.items = plugin.getItemFactory();
    }

    @EventHandler
    public void onInteract(PlayerInteractEvent event) {
        if (event.getHand() != EquipmentSlot.HAND) return;
        Action action = event.getAction();
        if (action != Action.RIGHT_CLICK_AIR && action != Action.RIGHT_CLICK_BLOCK) return;

        Player player = event.getPlayer();
        ItemStack main = player.getInventory().getItemInMainHand();
        if (!PICKAXE_MATERIALS.contains(main.getType())) return;
        if (items.isEnchantBook(main) || items.isWhiteDust(main)) return;

        event.setCancelled(true);
        openUpgradeGui(player, main);
    }

    private void openUpgradeGui(Player player, ItemStack pickaxe) {
        Map<String, Integer> applied = items.getAllApplied(pickaxe);

        List<CustomEnchant> eligible = new ArrayList<>();
        for (CustomEnchant enchant : manager.getEnchants().values()) {
            if (!enchant.isGuiOn()) continue;
            if (!enchant.supports(pickaxe.getType())) continue;
            eligible.add(enchant);
        }

        int size = Math.max(9, ((eligible.size() / 9) + 1) * 9);
        Map<Integer, String> slotMap = new HashMap<>();
        UpgradeGUIHolder holder = new UpgradeGUIHolder(player, slotMap);
        Inventory inv = Bukkit.createInventory(holder, size, Text.color(manager.getGuiTitle()));
        holder.setInventory(inv);

        int slot = 0;
        for (CustomEnchant enchant : eligible) {
            int currentLevel = applied.getOrDefault(enchant.getId(), 0);
            inv.setItem(slot, buildIcon(enchant, currentLevel));
            slotMap.put(slot, enchant.getId());
            slot++;
        }

        player.openInventory(inv);
    }

    private ItemStack buildIcon(CustomEnchant enchant, int currentLevel) {
        int nextLevel = currentLevel + 1;

        ItemStack icon = new ItemStack(Material.ENCHANTED_BOOK);
        ItemMeta meta = icon.getItemMeta();
        meta.setDisplayName(Text.color("&b" + enchant.getDisplayName()));

        List<String> lore = new ArrayList<>();
        lore.add(Text.color("&7Current Level: &f" + (currentLevel > 0 ? RomanNumeral.toRoman(currentLevel) : "None")));
        if (nextLevel > enchant.getMaxLevel()) {
            lore.add(Text.color("&eThis enchant is at max level."));
        } else {
            Double cost = enchant.getUpgradeCost(nextLevel);
            if (cost == null) {
                lore.add(Text.color("&cUpgrade not configured."));
            } else {
                String currencyLabel = enchant.getCurrencyDisplay() != null
                        ? enchant.getCurrencyDisplay()
                        : (enchant.getCurrencyName() != null ? enchant.getCurrencyName() : manager.getCurrencyName());
                String costLine = "&7Cost: &a" + trimDecimal(cost) + " " + currencyLabel;
                lore.add(Text.color("&7Next Level: &f" + RomanNumeral.toRoman(nextLevel)));
                lore.add(Text.color(costLine));
                lore.add("");
                lore.add(Text.color("&eLeft-Click to upgrade!"));
            }
        }

        // Refund line: the value shown is exactly the same upgrade-costs
        // formula's output at the current level - i.e. whatever was actually
        // paid to reach this level - so refunding always gives that back.
        if (currentLevel > 0) {
            Double refund = enchant.getUpgradeCost(currentLevel);
            String currencyLabel = enchant.getCurrencyDisplay() != null
                    ? enchant.getCurrencyDisplay()
                    : (enchant.getCurrencyName() != null ? enchant.getCurrencyName() : manager.getCurrencyName());
            lore.add("");
            if (refund == null) {
                lore.add(Text.color("&7Refund: &cnot configured"));
            } else {
                lore.add(Text.color("&7Refund: &a" + trimDecimal(refund) + " " + currencyLabel));
                lore.add(Text.color("&eRight-Click to remove a level!"));
            }
        }
        meta.setLore(lore);
        icon.setItemMeta(meta);
        return icon;
    }

    @EventHandler
    public void onClick(InventoryClickEvent event) {
        if (!(event.getInventory().getHolder() instanceof UpgradeGUIHolder holder)) return;
        event.setCancelled(true);

        if (event.getClickedInventory() == null || !event.getClickedInventory().equals(event.getInventory())) return;

        String enchantId = holder.getEnchantIdForSlot(event.getSlot());
        if (enchantId == null) return;

        Player player = (Player) event.getWhoClicked();
        CustomEnchant enchant = manager.getEnchant(enchantId);
        if (enchant == null) return;

        ItemStack pickaxe = player.getInventory().getItemInMainHand();
        if (!PICKAXE_MATERIALS.contains(pickaxe.getType())) {
            player.closeInventory();
            return;
        }

        if (event.isRightClick()) {
            handleDowngrade(player, pickaxe, enchant, enchantId, event);
        } else {
            handleUpgrade(player, pickaxe, enchant, enchantId, event);
        }
    }

    private void handleUpgrade(Player player, ItemStack pickaxe, CustomEnchant enchant, String enchantId, InventoryClickEvent event) {
        int currentLevel = items.getAppliedLevel(pickaxe, enchantId);
        int nextLevel = currentLevel + 1;

        if (nextLevel > enchant.getMaxLevel()) {
            msg(player, "gui-max-level-reached", Map.of("%enchant%", enchant.getDisplayName()));
            return;
        }

        Double cost = enchant.getUpgradeCost(nextLevel);
        if (cost == null) {
            msg(player, "gui-not-configured", Map.of("%enchant%", enchant.getDisplayName()));
            return;
        }

        String currencyName = enchant.getCurrencyName() != null
                ? enchant.getCurrencyName()
                : manager.getCurrencyName();

        // Read the player's real, live balance from DustyEconomy's own
        // EconomyManager, not the legacy Skript variable - DustyEconomy no
        // longer writes balances there, so that value never changes and the
        // check below would otherwise be comparing against a frozen number
        // instead of what the player can actually afford.
        Double liveBalance = DustyEconomyBridge.getBalance(plugin.getLogger(), player.getUniqueId(), currencyName);
        double balance;
        if (liveBalance != null) {
            balance = liveBalance;
        } else {
            // Fallback for servers where DustyEconomy isn't installed/hookable -
            // matches the plugin's original behavior in that case.
            String variableKey = currencyName + "::" + player.getUniqueId();
            balance = SkriptBridge.getNumericVariable(plugin.getLogger(), variableKey);
        }

        if (balance < cost) {
            String currencyLabel = enchant.getCurrencyDisplay() != null ? enchant.getCurrencyDisplay() : currencyName;
            String costText = trimDecimal(cost) + " " + currencyLabel;
            msg(player, "gui-insufficient-funds", Map.of("%enchant%", enchant.getDisplayName(), "%cost%", costText));
            return;
        }

        // Charge via your existing /currencyadd command with a negative amount -
        // it already handles UUID resolution and creating the variable if unset.
        String chargeCommand = "currencyadd " + currencyName + " -" + trimDecimal(cost) + " " + player.getName();
        Bukkit.dispatchCommand(Bukkit.getConsoleSender(), chargeCommand);

        items.setAppliedLevel(pickaxe, enchantId, nextLevel);
        player.getInventory().setItemInMainHand(pickaxe);

        plugin.getActionExecutor().run(enchant, enchant.getActions(Trigger.ON_LEVEL_UP), player, nextLevel, null);
        msg(player, "gui-upgrade-success", Map.of("%enchant%", enchant.getDisplayName(), "%level%", RomanNumeral.toRoman(nextLevel)));

        // refresh just this slot to reflect the new level/cost
        event.getClickedInventory().setItem(event.getSlot(), buildIcon(enchant, nextLevel));
    }

    /**
     * Right-click handler: removes one level and refunds whatever the
     * upgrade-costs formula says was paid to reach the CURRENT level (the
     * same formula used to charge for it going up, just read at the level
     * about to be removed instead of the next one).
     */
    private void handleDowngrade(Player player, ItemStack pickaxe, CustomEnchant enchant, String enchantId, InventoryClickEvent event) {
        int currentLevel = items.getAppliedLevel(pickaxe, enchantId);

        if (currentLevel <= 0) {
            msg(player, "gui-no-level-to-remove", Map.of("%enchant%", enchant.getDisplayName()));
            return;
        }

        Double refund = enchant.getUpgradeCost(currentLevel);
        if (refund == null) {
            msg(player, "gui-downgrade-not-configured", Map.of("%enchant%", enchant.getDisplayName()));
            return;
        }

        String currencyName = enchant.getCurrencyName() != null
                ? enchant.getCurrencyName()
                : manager.getCurrencyName();

        // Same /currencyadd command as the upgrade path, just with a
        // positive amount this time to hand the currency back.
        String refundCommand = "currencyadd " + currencyName + " " + trimDecimal(refund) + " " + player.getName();
        Bukkit.dispatchCommand(Bukkit.getConsoleSender(), refundCommand);

        items.removeLevel(pickaxe, enchantId);
        player.getInventory().setItemInMainHand(pickaxe);
        int newLevel = currentLevel - 1;

        plugin.getActionExecutor().run(enchant, enchant.getActions(Trigger.ON_LEVEL_DOWN), player, newLevel, null);

        String currencyLabel = enchant.getCurrencyDisplay() != null ? enchant.getCurrencyDisplay() : currencyName;
        msg(player, "gui-downgrade-success", Map.of(
                "%enchant%", enchant.getDisplayName(),
                "%level%", newLevel > 0 ? RomanNumeral.toRoman(newLevel) : "None",
                "%refund%", trimDecimal(refund) + " " + currencyLabel
        ));

        // refresh just this slot to reflect the new (lower) level/cost
        event.getClickedInventory().setItem(event.getSlot(), buildIcon(enchant, newLevel));
    }

    private void msg(Player player, String key, Map<String, String> extra) {
        String raw = manager.getMessage(key);
        if (raw == null || raw.isEmpty()) return;
        player.sendMessage(Text.color(Text.placeholders(raw, new HashMap<>(extra))));
    }

    private String trimDecimal(double value) {
        if (value == Math.floor(value)) {
            return String.valueOf((long) value);
        }
        return String.valueOf(value);
    }
}
