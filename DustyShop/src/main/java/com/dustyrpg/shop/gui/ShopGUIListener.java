package com.dustyrpg.shop.gui;

import com.dustyrpg.shop.DustyShop;
import com.dustyrpg.shop.data.ShopCategory;
import com.dustyrpg.shop.data.ShopItem;
import com.dustyrpg.shop.util.DustyEconomyBridge;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;

public class ShopGUIListener implements Listener {

    private final DustyShop plugin;

    public ShopGUIListener(DustyShop plugin) {
        this.plugin = plugin;
    }

    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        String title = event.getView().getTitle();
        if (!title.equals(ShopGUI.MAIN_TITLE) && !title.startsWith(ShopGUI.CATEGORY_PREFIX)) return;

        event.setCancelled(true);

        Inventory clickedInventory = event.getClickedInventory();
        if (clickedInventory == null || !clickedInventory.equals(event.getView().getTopInventory())) return;

        ItemStack clicked = event.getCurrentItem();
        if (clicked == null || clicked.getType() == Material.AIR) return;
        if (clicked.getType() == Material.BLACK_STAINED_GLASS_PANE) return;

        Player player = (Player) event.getWhoClicked();
        ShopGUI gui = plugin.getShopGUI();

        if (title.equals(ShopGUI.MAIN_TITLE)) {
            handleMainMenuClick(player, event.getSlot());
        } else if (title.startsWith(ShopGUI.CATEGORY_PREFIX)) {
            handleCategoryClick(player, event.getSlot(), clicked);
        }
    }

    private void handleMainMenuClick(Player player, int slot) {
        ShopGUI gui = plugin.getShopGUI();
        for (ShopCategory category : plugin.getShopManager().getCategories().values()) {
            if (category.getSlot() == slot) {
                gui.openCategory(player, category.getId(), 0);
                return;
            }
        }
    }

    private void handleCategoryClick(Player player, int slot, ItemStack clicked) {
        ShopGUI gui = plugin.getShopGUI();
        String categoryId = gui.getPlayerCategory(player.getUniqueId());
        int page = gui.getPlayerPage(player.getUniqueId());

        // Back button
        if (slot == 49 && clicked.getType() == Material.ARROW) {
            gui.openMainMenu(player);
            return;
        }

        // Previous page
        if (slot == 45 && clicked.getType() == Material.ARROW) {
            gui.openCategory(player, categoryId, page - 1);
            return;
        }

        // Next page
        if (slot == 53 && clicked.getType() == Material.ARROW) {
            gui.openCategory(player, categoryId, page + 1);
            return;
        }

        // Page info (paper) - ignore
        if (clicked.getType() == Material.PAPER) return;

        // Try to buy the item at this slot
        ShopItem shopItem = gui.getItemAtSlot(categoryId, page, slot);
        if (shopItem == null) return;

        processPurchase(player, shopItem);
    }

    private void processPurchase(Player player, ShopItem shopItem) {
        String currency = shopItem.getCurrency().toLowerCase();
        double price = shopItem.getPrice();

        if (currency.equals("xp-levels")) {
            int levelsNeeded = (int) price;
            if (player.getLevel() < levelsNeeded) {
                player.sendMessage(ChatColor.RED + "You need " + levelsNeeded
                        + " XP levels but only have " + player.getLevel() + "!");
                return;
            }
            player.setLevel(player.getLevel() - levelsNeeded);
        } else {
            // DustyEconomy currencies: money, orbs, souls, credits
            Double balance = DustyEconomyBridge.getBalance(plugin.getLogger(), player.getUniqueId(), currency);
            if (balance == null) {
                player.sendMessage(ChatColor.RED + "Unable to reach the economy plugin. Try again.");
                return;
            }
            if (balance < price) {
                player.sendMessage(ChatColor.RED + "You cannot afford this! You need "
                        + ShopGUI.formatCurrency(currency, price) + ChatColor.RED + " but have "
                        + ShopGUI.formatCurrency(currency, balance) + ChatColor.RED + ".");
                return;
            }
            if (!DustyEconomyBridge.removeBalance(plugin.getLogger(), player.getUniqueId(), currency, price)) {
                player.sendMessage(ChatColor.RED + "Unable to process payment. Try again.");
                return;
            }
        }

        ItemStack purchased = new ItemStack(shopItem.getMaterial(), shopItem.getAmount());
        player.getInventory().addItem(purchased);
        player.sendMessage(ChatColor.GREEN + "Purchased " + shopItem.getDisplayName()
                + ChatColor.GREEN + " for " + ShopGUI.formatCurrency(currency, price)
                + ChatColor.GREEN + "!");
    }

    @EventHandler
    public void onInventoryClose(InventoryCloseEvent event) {
        String title = event.getView().getTitle();
        if (title.equals(ShopGUI.MAIN_TITLE) || title.startsWith(ShopGUI.CATEGORY_PREFIX)) {
            plugin.getShopGUI().cleanup(event.getPlayer().getUniqueId());
        }
    }
}
