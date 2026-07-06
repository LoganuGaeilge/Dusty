package com.dustyrpg.economy.commands;

import com.dustyrpg.economy.Currency;
import com.dustyrpg.economy.EconomyPlugin;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;

import java.util.UUID;

public class CurrencySetCommand implements CommandExecutor {

    private final EconomyPlugin plugin;

    public CurrencySetCommand(EconomyPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    @SuppressWarnings("deprecation")
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (args.length != 3) {
            sender.sendMessage(ChatColor.RED + "Usage: /currencyset <currency> <amount> <player>");
            return true;
        }

        Currency currency = Currency.fromKey(args[0]);
        if (currency == null) {
            sender.sendMessage(ChatColor.RED + "Unknown currency: " + args[0]);
            return true;
        }

        double amount;
        try {
            amount = Double.parseDouble(args[1]);
        } catch (NumberFormatException ex) {
            sender.sendMessage(ChatColor.RED + args[1] + " is not a valid number.");
            return true;
        }

        OfflinePlayer target = Bukkit.getOfflinePlayer(args[2]);
        UUID uuid = target.getUniqueId();
        plugin.getEconomyManager().setBalance(uuid, currency, amount);

        String name = target.getName() != null ? target.getName() : args[2];
        sender.sendMessage(ChatColor.translateAlternateColorCodes('&',
                "&aSet " + name + "'s " + currency.getKey() + " to " + formatAmount(amount) + "."));
        return true;
    }

    private String formatAmount(double amount) {
        return amount == Math.floor(amount) ? String.valueOf((long) amount) : String.valueOf(amount);
    }
}
