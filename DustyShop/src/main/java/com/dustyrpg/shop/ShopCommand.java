package com.dustyrpg.shop;

import org.bukkit.ChatColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

public class ShopCommand implements CommandExecutor {

    private final DustyShop plugin;

    public ShopCommand(DustyShop plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (args.length > 0 && args[0].equalsIgnoreCase("reload")) {
            if (!sender.hasPermission("dustyshop.admin")) {
                sender.sendMessage(ChatColor.RED + "You don't have permission to do that.");
                return true;
            }
            plugin.reloadConfig();
            plugin.getShopManager().loadShop();
            sender.sendMessage(ChatColor.GREEN + "DustyShop config reloaded!");
            return true;
        }

        if (!(sender instanceof Player)) {
            sender.sendMessage("Only players can open the shop.");
            return true;
        }

        plugin.getShopGUI().openMainMenu((Player) sender);
        return true;
    }
}
