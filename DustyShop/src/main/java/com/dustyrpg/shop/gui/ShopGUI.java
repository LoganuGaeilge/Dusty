package com.dustyrpg.shop.gui;

import com.dustyrpg.shop.DustyShop;
import com.dustyrpg.shop.data.EntityShop;
import com.dustyrpg.shop.data.ShopCategory;
import com.dustyrpg.shop.data.ShopItem;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public class ShopGUI {

    public static final String MAIN_TITLE = "Shop";
    public static final String CATEGORY_PREFIX = "Shop - ";
    public static final String ENTITY_PREFIX = "Merchant: ";
    public static final int ITEMS_PER_PAGE = 28;

    private static final int[] ITEM_SLOTS = {
        10, 11, 12, 13, 14, 15, 16,
        19, 20, 21, 22, 23, 24, 25,
        28, 29, 30, 31, 32, 33, 34,
        37, 38, 39, 40, 41, 42, 43
    };

    private final DustyShop plugin;
    private final Map<UUID, String> playerCategory = new HashMap<>();
    private final Map<UUID, String> playerEntityShop = new HashMap<>();
    private final Map<UUID, Integer> playerPage = new HashMap<>();

    public ShopGUI(DustyShop plugin) {
        this.plugin = plugin;
    }

    public void openMainMenu(Player player) {
        playerCategory.remove(player.getUniqueId());
        playerEntityShop.remove(player.getUniqueId());
        playerPage.remove(player.getUniqueId());

        Inventory gui = Bukkit.createInventory(null, 27, MAIN_TITLE);
        fillBorder(gui, 27);

        for (ShopCategory category : plugin.getShopManager().getCategories().values()) {
            int slot = category.getSlot();
            if (slot < 0 || slot >= 27) continue;

            ItemStack icon = new ItemStack(category.getIcon());
            ItemMeta meta = icon.getItemMeta();
            if (meta != null) {
                meta.setDisplayName(category.getDisplayName());
                List<String> lore = new ArrayList<>();
                lore.add(ChatColor.GRAY + "" + category.getItems().size() + " items");
                lore.add("");
                lore.add(ChatColor.YELLOW + "Click to browse!");
                meta.setLore(lore);
                icon.setItemMeta(meta);
            }
            gui.setItem(slot, icon);
        }

        player.openInventory(gui);
    }

    public void openCategory(Player player, String categoryId, int page) {
        ShopCategory category = plugin.getShopManager().getCategory(categoryId);
        if (category == null) return;

        playerCategory.put(player.getUniqueId(), categoryId);
        playerEntityShop.remove(player.getUniqueId());
        playerPage.put(player.getUniqueId(), page);

        List<ShopItem> items = category.getItems();
        int totalPages = totalPages(items);
        page = clampPage(page, totalPages);
        playerPage.put(player.getUniqueId(), page);

        String title = truncateTitle(CATEGORY_PREFIX + ChatColor.stripColor(category.getDisplayName()));
        Inventory gui = Bukkit.createInventory(null, 54, title);
        fillBorder(gui, 54);
        renderItemsPage(gui, items, page);

        // Back button
        ItemStack back = new ItemStack(Material.ARROW);
        ItemMeta backMeta = back.getItemMeta();
        if (backMeta != null) {
            backMeta.setDisplayName(ChatColor.RED + "Back to Categories");
            back.setItemMeta(backMeta);
        }
        gui.setItem(49, back);

        addPagination(gui, page, totalPages);
        player.openInventory(gui);
    }

    public void openEntityShop(Player player, String shopId, int page) {
        EntityShop shop = plugin.getShopManager().getEntityShop(shopId);
        if (shop == null) return;

        playerEntityShop.put(player.getUniqueId(), shopId);
        playerCategory.remove(player.getUniqueId());

        List<ShopItem> items = shop.getItems();
        int totalPages = totalPages(items);
        page = clampPage(page, totalPages);
        playerPage.put(player.getUniqueId(), page);

        String title = truncateTitle(ENTITY_PREFIX + ChatColor.stripColor(shop.getDisplayName()));
        Inventory gui = Bukkit.createInventory(null, 54, title);
        fillBorder(gui, 54);
        renderItemsPage(gui, items, page);

        // Close button (entity shops are standalone - no category menu to go back to)
        ItemStack close = new ItemStack(Material.BARRIER);
        ItemMeta closeMeta = close.getItemMeta();
        if (closeMeta != null) {
            closeMeta.setDisplayName(ChatColor.RED + "Close");
            close.setItemMeta(closeMeta);
        }
        gui.setItem(49, close);

        addPagination(gui, page, totalPages);
        player.openInventory(gui);
    }

    private int totalPages(List<ShopItem> items) {
        return Math.max(1, (int) Math.ceil((double) items.size() / ITEMS_PER_PAGE));
    }

    private int clampPage(int page, int totalPages) {
        if (page >= totalPages) page = totalPages - 1;
        if (page < 0) page = 0;
        return page;
    }

    private String truncateTitle(String title) {
        return title.length() > 32 ? title.substring(0, 32) : title;
    }

    private void renderItemsPage(Inventory gui, List<ShopItem> items, int page) {
        int startIndex = page * ITEMS_PER_PAGE;
        for (int i = 0; i < ITEMS_PER_PAGE && (startIndex + i) < items.size(); i++) {
            ShopItem shopItem = items.get(startIndex + i);
            ItemStack display = new ItemStack(shopItem.getMaterial(), shopItem.getAmount());
            ItemMeta meta = display.getItemMeta();
            if (meta != null) {
                meta.setDisplayName(shopItem.getDisplayName());
                List<String> lore = new ArrayList<>(shopItem.getLore());
                lore.add("");
                lore.add(ChatColor.GRAY + "--------------------");
                lore.add(ChatColor.YELLOW + "Price: " + formatCurrency(shopItem.getCurrency(), shopItem.getPrice()));
                lore.add("");
                lore.add(ChatColor.GREEN + "Click to purchase!");
                meta.setLore(lore);
                display.setItemMeta(meta);
            }
            gui.setItem(ITEM_SLOTS[i], display);
        }
    }

    private void addPagination(Inventory gui, int page, int totalPages) {
        // Previous page
        if (page > 0) {
            ItemStack prev = new ItemStack(Material.ARROW);
            ItemMeta prevMeta = prev.getItemMeta();
            if (prevMeta != null) {
                prevMeta.setDisplayName(ChatColor.YELLOW + "Previous Page");
                List<String> lore = new ArrayList<>();
                lore.add(ChatColor.GRAY + "Page " + page + "/" + totalPages);
                prevMeta.setLore(lore);
                prev.setItemMeta(prevMeta);
            }
            gui.setItem(45, prev);
        }

        // Next page
        if (page < totalPages - 1) {
            ItemStack next = new ItemStack(Material.ARROW);
            ItemMeta nextMeta = next.getItemMeta();
            if (nextMeta != null) {
                nextMeta.setDisplayName(ChatColor.YELLOW + "Next Page");
                List<String> lore = new ArrayList<>();
                lore.add(ChatColor.GRAY + "Page " + (page + 2) + "/" + totalPages);
                nextMeta.setLore(lore);
                next.setItemMeta(nextMeta);
            }
            gui.setItem(53, next);
        }

        // Page indicator
        ItemStack pageInfo = new ItemStack(Material.PAPER);
        ItemMeta pageMeta = pageInfo.getItemMeta();
        if (pageMeta != null) {
            pageMeta.setDisplayName(ChatColor.AQUA + "Page " + (page + 1) + "/" + totalPages);
            pageInfo.setItemMeta(pageMeta);
        }
        gui.setItem(47, pageInfo);
    }

    public String getPlayerCategory(UUID uuid) {
        return playerCategory.get(uuid);
    }

    public String getPlayerEntityShop(UUID uuid) {
        return playerEntityShop.get(uuid);
    }

    public int getPlayerPage(UUID uuid) {
        return playerPage.getOrDefault(uuid, 0);
    }

    public ShopItem getItemAtSlot(String categoryId, int page, int slot) {
        ShopCategory category = plugin.getShopManager().getCategory(categoryId);
        if (category == null) return null;
        return itemAtSlot(category.getItems(), page, slot);
    }

    public ShopItem getEntityItemAtSlot(String shopId, int page, int slot) {
        EntityShop shop = plugin.getShopManager().getEntityShop(shopId);
        if (shop == null) return null;
        return itemAtSlot(shop.getItems(), page, slot);
    }

    private ShopItem itemAtSlot(List<ShopItem> items, int page, int slot) {
        int slotIndex = -1;
        for (int i = 0; i < ITEM_SLOTS.length; i++) {
            if (ITEM_SLOTS[i] == slot) {
                slotIndex = i;
                break;
            }
        }
        if (slotIndex < 0) return null;

        int itemIndex = page * ITEMS_PER_PAGE + slotIndex;
        if (itemIndex < 0 || itemIndex >= items.size()) return null;
        return items.get(itemIndex);
    }

    public void cleanup(UUID uuid) {
        playerCategory.remove(uuid);
        playerEntityShop.remove(uuid);
        playerPage.remove(uuid);
    }

    private void fillBorder(Inventory gui, int size) {
        ItemStack glass = new ItemStack(Material.BLACK_STAINED_GLASS_PANE);
        ItemMeta glassMeta = glass.getItemMeta();
        if (glassMeta != null) {
            glassMeta.setDisplayName(" ");
            glass.setItemMeta(glassMeta);
        }

        int rows = size / 9;
        for (int i = 0; i < size; i++) {
            int row = i / 9;
            int col = i % 9;
            if (row == 0 || row == rows - 1 || col == 0 || col == 8) {
                gui.setItem(i, glass.clone());
            }
        }
    }

    public static String formatCurrency(String currency, double price) {
        String formatted;
        if (price == Math.floor(price)) {
            formatted = String.valueOf((long) price);
        } else {
            formatted = String.valueOf(price);
        }

        switch (currency.toLowerCase()) {
            case "money":
                return ChatColor.GREEN + "$" + formatted;
            case "orbs":
                return ChatColor.AQUA + formatted + " Orbs";
            case "souls":
                return ChatColor.LIGHT_PURPLE + formatted + " Souls";
            case "credits":
                return ChatColor.YELLOW + formatted + " Credits";
            case "xp-levels":
                return ChatColor.GREEN + formatted + " XP Levels";
            default:
                return formatted + " " + currency;
        }
    }
}
