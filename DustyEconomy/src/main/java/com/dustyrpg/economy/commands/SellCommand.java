package com.dustyrpg.economy.commands;

import com.dustyrpg.economy.Currency;
import com.dustyrpg.economy.EconomyPlugin;
import com.dustyrpg.economy.util.CurrencyFormatter;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;

import java.util.EnumMap;
import java.util.Map;

/**
 * Reimplements the "/sell" trigger: loops the player's inventory, matches
 * items against the configured sell list, removes sold items, and pays out
 * the corresponding currencies.
 */
public class SellCommand implements CommandExecutor {

    private final EconomyPlugin plugin;
    private final Map<Material, SellEntry> sellPrices = new EnumMap<>(Material.class);

    public SellCommand(EconomyPlugin plugin) {
        this.plugin = plugin;
        loadPrices();
    }

    /** Loads (or reloads) sell prices from config.yml's "sell-prices" section. */
    public void loadPrices() {
        sellPrices.clear();
        ConfigurationSection section = plugin.getConfig().getConfigurationSection("sell-prices");
        if (section == null) {
            return;
        }

        for (String key : section.getKeys(false)) {
            Material material = Material.matchMaterial(key);
            if (material == null) {
                plugin.getLogger().warning("Unknown material in sell-prices: " + key);
                continue;
            }

            double price = section.getDouble(key + ".price", 0);
            String currencyKey = section.getString(key + ".currency", "money");
            Currency currency = Currency.fromKey(currencyKey);
            if (currency == null) {
                plugin.getLogger().warning("Unknown currency for " + key + ": " + currencyKey);
                continue;
            }

            sellPrices.put(material, new SellEntry(price, currency));
        }
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(ChatColor.RED + "This command can only be used by players.");
            return true;
        }

        Map<Currency, Double> totals = new EnumMap<>(Currency.class);
        int itemsSold = 0;
        boolean soldAnything = false;

        Inventory inventory = player.getInventory();
        ItemStack[] contents = inventory.getStorageContents();

        for (int slot = 0; slot < contents.length; slot++) {
            ItemStack item = contents[slot];
            if (item == null) {
                continue;
            }

            SellEntry entry = sellPrices.get(item.getType());
            if (entry == null) {
                continue; // not a sellable item, matches Skript's "continue"
            }

            int amount = item.getAmount();
            double itemTotal = entry.price() * amount;

            totals.merge(entry.currency(), itemTotal, Double::sum);
            itemsSold += amount;
            soldAnything = true;

            contents[slot] = null; // remove the item from inventory
        }

        if (!soldAnything) {
            player.sendMessage(colorize("&cYou have no sellable items in your inventory."));
            return true;
        }

        inventory.setStorageContents(contents);

        for (Map.Entry<Currency, Double> entry : totals.entrySet()) {
            plugin.getEconomyManager().addBalance(player.getUniqueId(), entry.getKey(), entry.getValue());
        }

        for (Currency currency : Currency.values()) {
            double total = totals.getOrDefault(currency, 0.0);
            if (total > 0) {
                // colorPrefix already includes the "$" for MONEY, so just append the formatted amount.
                player.sendMessage(colorize("You sold " + itemsSold + " items for "
                        + currency.getColorPrefix() + CurrencyFormatter.formatCurrency(total)
                        + " " + currency.getDisplayName()));
            }
        }

        return true;
    }

    private String colorize(String text) {
        return ChatColor.translateAlternateColorCodes('&', text);
    }

    private record SellEntry(double price, Currency currency) {
    }
}
