package com.simpleah.plugin;

import com.simpleah.plugin.util.DustyEconomyBridge;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;

public class AHGUIListener implements Listener {

    private final SimpleAH plugin;
    private final AuctionManager manager;

    public AHGUIListener(SimpleAH plugin, AuctionManager manager) {
        this.plugin = plugin;
        this.manager = manager;
    }

    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        String title = event.getView().getTitle();
        if (!title.equals("Auction House") && !title.equals("AH - Cancelled Items")) return;

        // Always cancel clicks while one of our GUIs is open, so players
        // can't shift-click/drag items out of their own inventory into it.
        event.setCancelled(true);

        // The previous version never checked which inventory was clicked,
        // so clicking in the player's OWN inventory (the bottom half of the
        // screen, which shares the same InventoryView/title) was treated as
        // clicking a slot in the Auction House GUI itself. That let players
        // "buy" or "cancel" auctions based on whatever happened to be
        // sitting in their own inventory slot 0-44.
        Inventory clickedInventory = event.getClickedInventory();
        if (clickedInventory == null || !clickedInventory.equals(event.getView().getTopInventory())) {
            return;
        }

        ItemStack clicked = event.getCurrentItem();
        if (clicked == null || clicked.getType() == Material.AIR) return;

        Player player = (Player) event.getWhoClicked();

        if (title.equals("Auction House")) {
            if (event.getSlot() == 49 && clicked.getType() == Material.BARRIER) {
                player.closeInventory();
                player.performCommand("ah cancelled");
                return;
            }

            int slot = event.getSlot();
            if (slot >= manager.getActiveAuctions().size()) return;

            AuctionItem target = manager.getActiveAuctions().get(slot);
            if (target == null) return;

            if (target.getSeller().equals(player.getUniqueId())) {
                manager.cancelAuction(target);
                player.closeInventory();
                player.sendMessage("§eYou cancelled your listing. It's safe inside your /ah cancelled bin.");
                return;
            }

            Double balance = DustyEconomyBridge.getBalance(plugin.getLogger(), player.getUniqueId(), "money");
            if (balance == null) {
                player.sendMessage("§cUnable to reach the economy plugin. Try again.");
                return;
            }

            if (balance < target.getPrice()) {
                player.sendMessage("§cYou cannot afford this item!");
                return;
            }

            if (!DustyEconomyBridge.removeBalance(plugin.getLogger(), player.getUniqueId(), "money", target.getPrice())) {
                player.sendMessage("§cUnable to process payment. Try again.");
                return;
            }

            manager.getActiveAuctions().remove(target);
            player.getInventory().addItem(target.getItem());
            manager.processSale(target.getSeller(), target.getPrice());

            player.closeInventory();
            player.sendMessage("§aPurchased successfully!");
        } else {
            manager.removeCancelledItem(player.getUniqueId(), clicked);
            player.getInventory().addItem(clicked);
            player.closeInventory();
            player.sendMessage("§aRecovered listing back to your inventory storage!");
        }
    }
}
