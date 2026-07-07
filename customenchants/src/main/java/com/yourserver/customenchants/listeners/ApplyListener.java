package com.yourserver.customenchants.listeners;

import com.yourserver.customenchants.CustomEnchantsPlugin;
import com.yourserver.customenchants.EnchantManager;
import com.yourserver.customenchants.ItemFactory;
import com.yourserver.customenchants.model.BookTier;
import com.yourserver.customenchants.model.CustomEnchant;
import com.yourserver.customenchants.model.Trigger;
import com.yourserver.customenchants.util.Text;
import org.bukkit.entity.Player;
import org.bukkit.event.Event;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.ItemStack;

import java.util.HashMap;
import java.util.Map;
import java.util.Random;

public class ApplyListener implements Listener {

    private final CustomEnchantsPlugin plugin;
    private final EnchantManager manager;
    private final ItemFactory items;
    private final Random random = new Random();

    public ApplyListener(CustomEnchantsPlugin plugin) {
        this.plugin = plugin;
        this.manager = plugin.getEnchantManager();
        this.items = plugin.getItemFactory();
    }

    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player)) return;
        
        ItemStack cursor = event.getCursor();
        ItemStack current = event.getCurrentItem();

        if (cursor == null || cursor.getType().isAir() || current == null || current.getType().isAir()) {
            return;
        }

        Player player = (Player) event.getWhoClicked();

        if (items.isEnchantBook(cursor)) {
            event.setCancelled(true);
            event.setResult(Event.Result.DENY);
            handleBookApplication(player, cursor, current, event);
        } else if (items.isUnenchantedBook(cursor)) {
            event.setCancelled(true);
            event.setResult(Event.Result.DENY);
            handleUnenchantedApplication(player, cursor, current, event);
        } else if (items.isWhiteDust(cursor)) {
            event.setCancelled(true);
            event.setResult(Event.Result.DENY);
            handleDustApplication(player, cursor, current, event);
        }
    }

    private void handleBookApplication(Player player, ItemStack book, ItemStack target, InventoryClickEvent event) {
        String enchantId = items.getBookEnchantId(book);
        CustomEnchant enchant = manager.getEnchant(enchantId);
        if (enchant == null || !enchant.supports(target.getType())) return;

        int bookLevel = items.getBookLevel(book);
        BookTier tier = items.getBookTier(book);
        if (tier == null) return;

        int currentLevel = items.getAppliedLevel(target, enchantId);
        int targetLevel = Math.min(bookLevel, enchant.getMaxLevel());

        if (targetLevel <= currentLevel) {
            msg(player, "max-level", Map.of("%enchant%", enchant.getDisplayName()));
            return;
        }

        double finalChance = Math.min(100.0, enchant.getBaseChance(bookLevel) + items.getBookDustBonus(book));
        boolean success = random.nextDouble() * 100.0 < finalChance;

        if (success) {
            boolean wasLeveled = currentLevel > 0;
            items.setAppliedLevel(target, enchantId, targetLevel);
            event.setCurrentItem(target); // Update the slot

            plugin.getActionExecutor().run(enchant, enchant.getActions(wasLeveled ? Trigger.ON_LEVEL_UP : Trigger.ON_APPLY), player, targetLevel, null);
            msg(player, "apply-success", Map.of("%enchant%", enchant.getDisplayName(), "%level%", com.yourserver.customenchants.util.RomanNumeral.toRoman(targetLevel)));
            
            consumeCursor(player, true);
        } else {
            plugin.getActionExecutor().run(enchant, enchant.getActions(Trigger.ON_FAIL), player, targetLevel, null);
            msg(player, "apply-fail", Map.of("%enchant%", enchant.getDisplayName()));
            consumeCursor(player, manager.isConsumeBookOnFail());
        }
    }

    private void handleUnenchantedApplication(Player player, ItemStack book, ItemStack target, InventoryClickEvent event) {
        BookTier tier = items.getUnenchantedBookTier(book);
        if (tier == null) return;

        String rarityName = manager.getTierDisplay(tier);

        java.util.List<CustomEnchant> candidates = manager.getEnchantsByRarityFor(tier, target.getType());
        if (candidates.isEmpty()) {
            // No matching enchant for this item - nothing happens, book kept.
            msg(player, "unenchanted-no-enchant", Map.of("%rarity%", rarityName));
            return;
        }

        CustomEnchant enchant = candidates.get(random.nextInt(candidates.size()));
        int rolledLevel = manager.getUnenchantedBook(tier).rollLevel(enchant.getMaxLevel(), random);

        int currentLevel = items.getAppliedLevel(target, enchant.getId());
        if (rolledLevel <= currentLevel) {
            // The gamble landed on an enchant/level that wouldn't improve the
            // item - the book is still consumed (it was a roll), no downgrade.
            msg(player, "unenchanted-no-upgrade", Map.of(
                    "%enchant%", enchant.getDisplayName(),
                    "%level%", com.yourserver.customenchants.util.RomanNumeral.toRoman(rolledLevel)));
            consumeCursor(player, true);
            return;
        }

        boolean wasLeveled = currentLevel > 0;
        items.setAppliedLevel(target, enchant.getId(), rolledLevel);
        event.setCurrentItem(target);

        plugin.getActionExecutor().run(enchant, enchant.getActions(wasLeveled ? Trigger.ON_LEVEL_UP : Trigger.ON_APPLY), player, rolledLevel, null);
        msg(player, "unenchanted-applied", Map.of(
                "%rarity%", rarityName,
                "%enchant%", enchant.getDisplayName(),
                "%level%", com.yourserver.customenchants.util.RomanNumeral.toRoman(rolledLevel)));
        consumeCursor(player, true);
    }

    private void handleDustApplication(Player player, ItemStack dust, ItemStack targetBook, InventoryClickEvent event) {
        if (!items.isEnchantBook(targetBook)) return;

        if (!items.applyDust(targetBook)) {
            msg(player, "dust-max", Map.of());
            return;
        }
        
        event.setCurrentItem(targetBook);
        msg(player, "dust-applied", Map.of("%chance%", String.valueOf(Math.min(100.0, manager.getEnchant(items.getBookEnchantId(targetBook)).getBaseChance(items.getBookLevel(targetBook)) + items.getBookDustBonus(targetBook)))));

        consumeCursor(player, true);
    }

    private void consumeCursor(Player player, boolean consume) {
        if (!consume) return;
        ItemStack cursor = player.getItemOnCursor();
        if (cursor.getAmount() <= 1) {
            player.setItemOnCursor(null);
        } else {
            cursor.setAmount(cursor.getAmount() - 1);
            player.setItemOnCursor(cursor);
        }
        player.updateInventory();
    }

    private void msg(Player player, String key, Map<String, String> extra) {
        String raw = manager.getMessage(key);
        if (raw == null || raw.isEmpty()) return;
        player.sendMessage(Text.color(Text.placeholders(raw, new HashMap<>(extra))));
    }
}
