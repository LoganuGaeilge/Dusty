package com.simpleah.plugin;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.ArrayList;
import java.util.List;

public class AHCommand implements CommandExecutor {

    private final SimpleAH plugin;
    private final AuctionManager manager;

    public AHCommand(SimpleAH plugin, AuctionManager manager) {
        this.plugin = plugin;
        this.manager = manager;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player)) {
            sender.sendMessage("Only players can execute Auction House commands.");
            return true;
        }
        Player player = (Player) sender;

        if (args.length == 0) {
            openMainGUI(player);
            return true;
        }

        if (args[0].equalsIgnoreCase("sell") || args[0].equalsIgnoreCase("bin")) {
            if (args.length < 2) {
                player.sendMessage("§cUsage: /ah " + args[0] + " <price>");
                return true;
            }

            ItemStack item = player.getInventory().getItemInMainHand();
            if (item == null || item.getType() == Material.AIR) {
                player.sendMessage("§cYou cannot auction empty air!");
                return true;
            }

            double price;
            try {
                price = Double.parseDouble(args[1]);
            } catch (NumberFormatException e) {
                player.sendMessage("§cInvalid number value for price.");
                return true;
            }

            if (price <= 0) {
                player.sendMessage("§cPrice must be greater than 0!");
                return true;
            }

            boolean isBIN = args[0].equalsIgnoreCase("bin");
            int configHours = plugin.getConfig().getInt("bin-duration-hours", 48);
            long expiresAt = System.currentTimeMillis() + (configHours * 3600000L);

            // List a single copy of the item, then remove exactly one from the
            // held stack. The previous version cleared the whole main-hand
            // slot with setItemInMainHand(null), which destroyed the rest of
            // the stack (e.g. listing 1 out of a stack of 64 deleted all 64).
            ItemStack listedItem = item.clone();
            listedItem.setAmount(1);

            AuctionItem auction = new AuctionItem(player.getUniqueId(), listedItem, price, isBIN, expiresAt);
            manager.addAuction(auction);

            if (item.getAmount() > 1) {
                item.setAmount(item.getAmount() - 1);
                player.getInventory().setItemInMainHand(item);
            } else {
                player.getInventory().setItemInMainHand(null);
            }

            player.sendMessage("§aSuccessfully listed your item on the AH for $" + price + "!");
            return true;
        }

        if (args[0].equalsIgnoreCase("cancelled") || args[0].equalsIgnoreCase("expired")) {
            openCancelledGUI(player);
            return true;
        }

        return true;
    }

    private void openMainGUI(Player player) {
        Inventory gui = Bukkit.createInventory(null, 54, "Auction House");

        int slot = 0;
        for (AuctionItem item : manager.getActiveAuctions()) {
            if (slot >= 45) break; // Keep bottom row empty for controls
            ItemStack display = item.getItem().clone();
            ItemMeta meta = display.getItemMeta();
            if (meta != null) {
                // meta.getLore() can return an immutable list (or null), so
                // Arrays.asList(...) here used to throw
                // UnsupportedOperationException the moment we called
                // lore.add(...) on an item that had no existing lore.
                List<String> lore = meta.hasLore() ? new ArrayList<>(meta.getLore()) : new ArrayList<>();
                lore.add("§7--------------------");
                lore.add("§eSeller: §f" + Bukkit.getOfflinePlayer(item.getSeller()).getName());
                lore.add("§ePrice: §a$" + item.getPrice());
                lore.add("§eType: §b" + (item.isBin() ? "BIN (Buy It Now)" : "Auction"));
                meta.setLore(lore);
                display.setItemMeta(meta);
            }
            gui.setItem(slot++, display);
        }

        ItemStack panelButton = new ItemStack(Material.BARRIER);
        ItemMeta bMeta = panelButton.getItemMeta();
        if (bMeta != null) {
            bMeta.setDisplayName("§cView Cancelled/Expired Items");
            panelButton.setItemMeta(bMeta);
        }
        gui.setItem(49, panelButton);

        player.openInventory(gui);
    }

    private void openCancelledGUI(Player player) {
        Inventory gui = Bukkit.createInventory(null, 27, "AH - Cancelled Items");
        List<ItemStack> cancelled = manager.getCancelledItems(player.getUniqueId());

        int slot = 0;
        for (ItemStack item : cancelled) {
            if (slot >= 27) break;
            gui.setItem(slot++, item);
        }
        player.openInventory(gui);
    }
}
