package com.yourserver.customenchants.commands;

import com.yourserver.customenchants.CustomEnchantsPlugin;
import com.yourserver.customenchants.EnchantManager;
import com.yourserver.customenchants.ItemFactory;
import com.yourserver.customenchants.model.BookTier;
import com.yourserver.customenchants.model.CustomEnchant;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

public class CustomEnchantsCommand implements CommandExecutor, TabCompleter {

    private final CustomEnchantsPlugin plugin;

    public CustomEnchantsCommand(CustomEnchantsPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!sender.hasPermission("customenchants.admin")) {
            sender.sendMessage(ChatColor.RED + "You don't have permission to do that.");
            return true;
        }

        if (args.length == 0) {
            sendHelp(sender);
            return true;
        }

        switch (args[0].toLowerCase()) {
            case "reload" -> {
                plugin.getEnchantManager().load();
                plugin.restartHoldTask();
                sender.sendMessage(ChatColor.GREEN + "CustomEnchants config reloaded.");
            }
            case "give-book" -> handleGiveBook(sender, args);
            case "give-random-book" -> handleGiveRandomBook(sender, args);
            case "give-dust" -> handleGiveDust(sender, args);
            default -> sendHelp(sender);
        }
        return true;
    }

    private void handleGiveBook(CommandSender sender, String[] args) {
        // Usage: /ce give-book <player> <enchantId> <level>
        if (args.length < 4) {
            sender.sendMessage(ChatColor.RED + "Usage: /ce give-book <player> <enchantId> <level>");
            return;
        }
        
        Player target = Bukkit.getPlayerExact(args[1]);
        if (target == null) {
            sender.sendMessage(ChatColor.RED + "Player not found: " + args[1]);
            return;
        }
        
        EnchantManager manager = plugin.getEnchantManager();
        CustomEnchant enchant = manager.getEnchant(args[2]);
        if (enchant == null) {
            sender.sendMessage(ChatColor.RED + "Unknown enchant: " + args[2]);
            return;
        }
        
        int level;
        try {
            level = Integer.parseInt(args[3]);
        } catch (NumberFormatException ex) {
            sender.sendMessage(ChatColor.RED + "Level must be a number.");
            return;
        }
        
        if (level < 1 || level > enchant.getMaxLevel()) {
            sender.sendMessage(ChatColor.RED + "Level must be between 1 and " + enchant.getMaxLevel() + ".");
            return;
        }

        // Rarity is now retrieved directly from the enchantment config
        BookTier tier = enchant.getRarity();

        ItemFactory factory = plugin.getItemFactory();
        ItemStack book = factory.createEnchantBook(enchant, level, tier);
        target.getInventory().addItem(book);
        
        sender.sendMessage(ChatColor.GREEN + "Gave " + target.getName() + " a " + tier + " " + enchant.getDisplayName() + " book (level " + level + ").");
    }

    private void handleGiveRandomBook(CommandSender sender, String[] args) {
        // Usage: /ce give-random-book <player> <rarity> [amount]
        if (args.length < 3) {
            sender.sendMessage(ChatColor.RED + "Usage: /ce give-random-book <player> <COMMON|RARE|LEGENDARY> [amount]");
            return;
        }

        Player target = Bukkit.getPlayerExact(args[1]);
        if (target == null) {
            sender.sendMessage(ChatColor.RED + "Player not found: " + args[1]);
            return;
        }

        BookTier rarity = BookTier.fromString(args[2]);
        EnchantManager manager = plugin.getEnchantManager();
        com.yourserver.customenchants.model.UnenchantedBook book = rarity == null ? null : manager.getUnenchantedBook(rarity);
        if (book == null) {
            sender.sendMessage(ChatColor.RED + "No unenchanted book configured for rarity '" + args[2]
                    + "'. Available: " + manager.getUnenchantedBooks().keySet());
            return;
        }

        int amount = 1;
        if (args.length >= 4) {
            try {
                amount = Math.max(1, Integer.parseInt(args[3]));
            } catch (NumberFormatException ignored) {}
        }

        ItemStack item = plugin.getItemFactory().createUnenchantedBook(book);
        item.setAmount(amount);
        target.getInventory().addItem(item);

        sender.sendMessage(ChatColor.GREEN + "Gave " + target.getName() + " " + amount + "x "
                + rarity + " unenchanted book.");
    }

    private void handleGiveDust(CommandSender sender, String[] args) {
        // Usage: /ce give-dust <player> [amount]
        if (args.length < 2) {
            sender.sendMessage(ChatColor.RED + "Usage: /ce give-dust <player> [amount]");
            return;
        }
        Player target = Bukkit.getPlayerExact(args[1]);
        if (target == null) {
            sender.sendMessage(ChatColor.RED + "Player not found: " + args[1]);
            return;
        }
        int amount = 1;
        if (args.length >= 3) {
            try {
                amount = Math.max(1, Integer.parseInt(args[2]));
            } catch (NumberFormatException ignored) {}
        }
        ItemFactory factory = plugin.getItemFactory();
        ItemStack dust = factory.createWhiteDust();
        dust.setAmount(amount);
        target.getInventory().addItem(dust);
        sender.sendMessage(ChatColor.GREEN + "Gave " + target.getName() + " " + amount + "x White Dust.");
    }

    private void sendHelp(CommandSender sender) {
        sender.sendMessage(ChatColor.GOLD + "--- CustomEnchants ---");
        sender.sendMessage(ChatColor.YELLOW + "/ce give-book <player> <enchantId> <level>");
        sender.sendMessage(ChatColor.YELLOW + "/ce give-random-book <player> <COMMON|RARE|LEGENDARY> [amount]");
        sender.sendMessage(ChatColor.YELLOW + "/ce give-dust <player> [amount]");
        sender.sendMessage(ChatColor.YELLOW + "/ce reload");
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        List<String> options = new ArrayList<>();
        if (args.length == 1) {
            options.addAll(List.of("give-book", "give-random-book", "give-dust", "reload"));
        } else if (args.length == 2 && (args[0].equalsIgnoreCase("give-book")
                || args[0].equalsIgnoreCase("give-random-book")
                || args[0].equalsIgnoreCase("give-dust"))) {
            for (Player p : Bukkit.getOnlinePlayers()) {
                options.add(p.getName());
            }
        } else if (args.length == 3 && args[0].equalsIgnoreCase("give-book")) {
            options.addAll(plugin.getEnchantManager().getEnchants().keySet());
        } else if (args.length == 3 && args[0].equalsIgnoreCase("give-random-book")) {
            for (BookTier tier : plugin.getEnchantManager().getUnenchantedBooks().keySet()) {
                options.add(tier.name());
            }
        }

        String current = args.length > 0 ? args[args.length - 1].toLowerCase() : "";
        return options.stream()
                .filter(o -> o.toLowerCase().startsWith(current))
                .collect(Collectors.toList());
    }
}