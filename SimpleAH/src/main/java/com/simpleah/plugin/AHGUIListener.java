package com.simpleah.plugin;

import com.simpleah.plugin.util.DustyEconomyBridge;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;

import java.util.List;

public class AHGUIListener implements Listener {

    private final SimpleAH plugin;
    private final AuctionManager manager;
    private final AHCommand ahCommand;

    public AHGUIListener(SimpleAH plugin, AuctionManager manager, AHCommand ahCommand) {
        this.plugin = plugin;
        this.manager = manager;
        this.ahCommand = ahCommand;
    }

    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        String title = event.getView().getTitle();

        boolean isAH = title.startsWith(AHCommand.AH_TITLE_PREFIX);
        boolean isCancelled = title.equals(AHCommand.CANCELLED_TITLE);

        if (!isAH && !isCancelled) return;

        event.setCancelled(true);

        Inventory clickedInventory = event.getClickedInventory();
        if (clickedInventory == null || !clickedInventory.equals(event.getView().getTopInventory())) return;

        ItemStack clicked = event.getCurrentItem();
        if (clicked == null || clicked.getType() == Material.AIR) return;
        if (clicked.getType() == Material.BLACK_STAINED_GLASS_PANE) return;

        Player player = (Player) event.getWhoClicked();

        if (isAH) {
            handleAHClick(player, event.getSlot(), clicked);
        } else {
            handleCancelledClick(player, clicked);
        }
    }

    private void handleAHClick(Player player, int slot, ItemStack clicked) {
        AHSession session = ahCommand.getOrCreateSession(player.getUniqueId());

        // Page indicator (paper at slot 4) — ignore
        if (slot == 4 && clicked.getType() == Material.PAPER) return;

        // Previous page
        if (slot == AHCommand.SLOT_PREV && clicked.getType() == Material.ARROW) {
            ahCommand.openMainGUI(player, session.getPage() - 1);
            return;
        }

        // Next page
        if (slot == AHCommand.SLOT_NEXT && clicked.getType() == Material.ARROW) {
            ahCommand.openMainGUI(player, session.getPage() + 1);
            return;
        }

        // Sort button
        if (slot == AHCommand.SLOT_SORT && clicked.getType() == Material.HOPPER) {
            session.setSortMode(session.getSortMode().next());
            session.setPage(0);
            ahCommand.openMainGUI(player, 0);
            return;
        }

        // Cancelled items button
        if (slot == AHCommand.SLOT_CANCELLED && clicked.getType() == Material.BARRIER) {
            player.closeInventory();
            ahCommand.openCancelledGUI(player);
            return;
        }

        // Search button (click clears search)
        if (slot == AHCommand.SLOT_SEARCH && clicked.getType() == Material.NAME_TAG) {
            if (session.hasSearch()) {
                session.clearSearch();
                session.setPage(0);
                ahCommand.openMainGUI(player, 0);
            } else {
                player.closeInventory();
                player.sendMessage(ChatColor.YELLOW + "Type " + ChatColor.WHITE
                        + "/ah search <query>" + ChatColor.YELLOW + " to search the auction house.");
            }
            return;
        }

        // Item slot — try to buy or cancel
        int itemIndex = getItemSlotIndex(slot);
        if (itemIndex < 0) return;

        List<AuctionItem> filtered = ahCommand.getFilteredAuctions(session);
        int actualIndex = session.getPage() * AHCommand.ITEMS_PER_PAGE + itemIndex;
        if (actualIndex >= filtered.size()) return;

        AuctionItem target = filtered.get(actualIndex);

        if (target.getSeller().equals(player.getUniqueId())) {
            manager.cancelAuction(target);
            player.closeInventory();
            player.sendMessage(ChatColor.YELLOW
                    + "You cancelled your listing. It's safe inside your /ah cancelled bin.");
            return;
        }

        // Purchase flow
        String currency = target.getCurrencyKey();
        double price = target.getPrice();

        Double balance = DustyEconomyBridge.getBalance(plugin.getLogger(), player.getUniqueId(), currency);
        if (balance == null) {
            player.sendMessage(ChatColor.RED + "Unable to reach the economy plugin. Try again.");
            return;
        }

        if (balance < price) {
            player.sendMessage(ChatColor.RED + "You cannot afford this item! You need "
                    + AHCommand.formatCurrency(currency, price) + ChatColor.RED + " but have "
                    + AHCommand.formatCurrency(currency, balance) + ChatColor.RED + ".");
            return;
        }

        if (!DustyEconomyBridge.removeBalance(plugin.getLogger(), player.getUniqueId(), currency, price)) {
            player.sendMessage(ChatColor.RED + "Unable to process payment. Try again.");
            return;
        }

        manager.getActiveAuctions().remove(target);
        player.getInventory().addItem(target.getItem());
        manager.processSale(target.getSeller(), target.getCurrencyKey(), price);

        player.closeInventory();
        player.sendMessage(ChatColor.GREEN + "Purchased for "
                + AHCommand.formatCurrency(currency, price) + ChatColor.GREEN + "!");
    }

    private void handleCancelledClick(Player player, ItemStack clicked) {
        manager.removeCancelledItem(player.getUniqueId(), clicked);
        player.getInventory().addItem(clicked);
        player.closeInventory();
        player.sendMessage(ChatColor.GREEN + "Recovered listing back to your inventory!");
    }

    private int getItemSlotIndex(int slot) {
        for (int i = 0; i < AHCommand.ITEM_SLOTS.length; i++) {
            if (AHCommand.ITEM_SLOTS[i] == slot) return i;
        }
        return -1;
    }

    @EventHandler
    public void onInventoryClose(InventoryCloseEvent event) {
        // Don't remove session on close so search/sort state persists across opens
    }
}
