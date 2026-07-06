package com.simpleah.plugin;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

public class AHCommand implements CommandExecutor {

    public static final String AH_TITLE_PREFIX = "Auction House";
    public static final String CANCELLED_TITLE = "AH - Cancelled Items";
    public static final int ITEMS_PER_PAGE = 28;

    static final int[] ITEM_SLOTS = {
        10, 11, 12, 13, 14, 15, 16,
        19, 20, 21, 22, 23, 24, 25,
        28, 29, 30, 31, 32, 33, 34,
        37, 38, 39, 40, 41, 42, 43
    };

    static final int SLOT_PREV = 45;
    static final int SLOT_SORT = 47;
    static final int SLOT_CANCELLED = 49;
    static final int SLOT_SEARCH = 51;
    static final int SLOT_NEXT = 53;

    private static final String[] VALID_CURRENCIES = {"money", "orbs", "credits"};

    private final SimpleAH plugin;
    private final AuctionManager manager;
    private final Map<UUID, AHSession> sessions = new HashMap<>();

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
            openMainGUI(player, 0);
            return true;
        }

        if (args[0].equalsIgnoreCase("sell") || args[0].equalsIgnoreCase("bin")) {
            return handleSell(player, args);
        }

        if (args[0].equalsIgnoreCase("cancelled") || args[0].equalsIgnoreCase("expired")) {
            openCancelledGUI(player);
            return true;
        }

        if (args[0].equalsIgnoreCase("search")) {
            if (args.length < 2) {
                player.sendMessage(ChatColor.RED + "Usage: /ah search <query>");
                return true;
            }
            StringBuilder query = new StringBuilder();
            for (int i = 1; i < args.length; i++) {
                if (query.length() > 0) query.append(' ');
                query.append(args[i]);
            }
            AHSession session = getOrCreateSession(player.getUniqueId());
            session.setSearchQuery(query.toString());
            session.setPage(0);
            openMainGUI(player, 0);
            return true;
        }

        player.sendMessage(ChatColor.RED + "Usage: /ah [sell|bin <price> [currency]|cancelled|search <query>]");
        return true;
    }

    private boolean handleSell(Player player, String[] args) {
        if (args.length < 2) {
            player.sendMessage(ChatColor.RED + "Usage: /ah " + args[0] + " <price> [currency]");
            player.sendMessage(ChatColor.GRAY + "Currencies: money (default), orbs, credits");
            return true;
        }

        ItemStack item = player.getInventory().getItemInMainHand();
        if (item == null || item.getType() == Material.AIR) {
            player.sendMessage(ChatColor.RED + "You cannot auction empty air!");
            return true;
        }

        double price;
        try {
            price = Double.parseDouble(args[1]);
        } catch (NumberFormatException e) {
            player.sendMessage(ChatColor.RED + "Invalid number value for price.");
            return true;
        }

        if (price <= 0) {
            player.sendMessage(ChatColor.RED + "Price must be greater than 0!");
            return true;
        }

        String currencyKey = "money";
        if (args.length >= 3) {
            currencyKey = args[2].toLowerCase();
            boolean valid = false;
            for (String c : VALID_CURRENCIES) {
                if (c.equals(currencyKey)) {
                    valid = true;
                    break;
                }
            }
            if (!valid) {
                player.sendMessage(ChatColor.RED + "Unknown currency: " + args[2]);
                player.sendMessage(ChatColor.GRAY + "Valid currencies: money, orbs, credits");
                return true;
            }
        }

        boolean isBIN = args[0].equalsIgnoreCase("bin");
        int configHours = plugin.getConfig().getInt("bin-duration-hours", 48);
        long expiresAt = System.currentTimeMillis() + (configHours * 3600000L);

        ItemStack listedItem = item.clone();
        listedItem.setAmount(1);

        AuctionItem auction = new AuctionItem(player.getUniqueId(), listedItem, price,
                currencyKey, isBIN, expiresAt);
        manager.addAuction(auction);

        if (item.getAmount() > 1) {
            item.setAmount(item.getAmount() - 1);
            player.getInventory().setItemInMainHand(item);
        } else {
            player.getInventory().setItemInMainHand(null);
        }

        player.sendMessage(ChatColor.GREEN + "Listed on the AH for "
                + formatCurrency(currencyKey, price) + ChatColor.GREEN + "!");
        return true;
    }

    public void openMainGUI(Player player, int page) {
        AHSession session = getOrCreateSession(player.getUniqueId());
        session.setPage(page);

        List<AuctionItem> filtered = getFilteredAuctions(session);
        int totalPages = Math.max(1, (int) Math.ceil((double) filtered.size() / ITEMS_PER_PAGE));
        if (page >= totalPages) page = totalPages - 1;
        if (page < 0) page = 0;
        session.setPage(page);

        String title = AH_TITLE_PREFIX;
        if (session.hasSearch()) {
            String suffix = " [" + session.getSearchQuery() + "]";
            if ((title + suffix).length() <= 32) {
                title += suffix;
            } else {
                title += " [Search]";
            }
        }

        Inventory gui = Bukkit.createInventory(null, 54, title);
        fillBorder(gui);

        int startIndex = page * ITEMS_PER_PAGE;
        for (int i = 0; i < ITEMS_PER_PAGE && (startIndex + i) < filtered.size(); i++) {
            AuctionItem auctionItem = filtered.get(startIndex + i);
            ItemStack display = auctionItem.getItem().clone();
            ItemMeta meta = display.getItemMeta();
            if (meta != null) {
                List<String> lore = meta.hasLore() ? new ArrayList<>(meta.getLore()) : new ArrayList<>();
                lore.add("");
                lore.add(ChatColor.DARK_GRAY + "--------------------");
                lore.add(ChatColor.YELLOW + "Seller: " + ChatColor.WHITE
                        + Bukkit.getOfflinePlayer(auctionItem.getSeller()).getName());
                lore.add(ChatColor.YELLOW + "Price: "
                        + formatCurrency(auctionItem.getCurrencyKey(), auctionItem.getPrice()));
                lore.add(ChatColor.YELLOW + "Type: " + ChatColor.AQUA
                        + (auctionItem.isBin() ? "BIN (Buy It Now)" : "Auction"));
                lore.add("");
                if (auctionItem.getSeller().equals(player.getUniqueId())) {
                    lore.add(ChatColor.RED + "Click to cancel listing");
                } else {
                    lore.add(ChatColor.GREEN + "Click to purchase!");
                }
                meta.setLore(lore);
                display.setItemMeta(meta);
            }
            gui.setItem(ITEM_SLOTS[i], display);
        }

        // Bottom navigation bar
        // Previous page
        if (page > 0) {
            gui.setItem(SLOT_PREV, createNavItem(Material.ARROW,
                    ChatColor.YELLOW + "Previous Page",
                    ChatColor.GRAY + "Page " + page + "/" + totalPages));
        }

        // Sort button
        gui.setItem(SLOT_SORT, createNavItem(Material.HOPPER,
                ChatColor.GOLD + "Sort: " + session.getSortMode().getDisplay(),
                ChatColor.GRAY + "Click to change sort"));

        // Cancelled items button
        gui.setItem(SLOT_CANCELLED, createNavItem(Material.BARRIER,
                ChatColor.RED + "Cancelled/Expired Items",
                ChatColor.GRAY + "View your cancelled listings"));

        // Search button
        if (session.hasSearch()) {
            gui.setItem(SLOT_SEARCH, createNavItem(Material.NAME_TAG,
                    ChatColor.AQUA + "Search: " + session.getSearchQuery(),
                    ChatColor.GRAY + "Click to clear search"));
        } else {
            gui.setItem(SLOT_SEARCH, createNavItem(Material.NAME_TAG,
                    ChatColor.AQUA + "Search",
                    ChatColor.GRAY + "Use /ah search <query>"));
        }

        // Next page
        if (page < totalPages - 1) {
            gui.setItem(SLOT_NEXT, createNavItem(Material.ARROW,
                    ChatColor.YELLOW + "Next Page",
                    ChatColor.GRAY + "Page " + (page + 2) + "/" + totalPages));
        }

        // Page indicator in the title area (slot 4)
        gui.setItem(4, createNavItem(Material.PAPER,
                ChatColor.WHITE + "Page " + (page + 1) + "/" + totalPages,
                ChatColor.GRAY + "" + filtered.size() + " listings"));

        player.openInventory(gui);
    }

    List<AuctionItem> getFilteredAuctions(AHSession session) {
        List<AuctionItem> result = new ArrayList<>(manager.getActiveAuctions());

        if (session.hasSearch()) {
            String query = session.getSearchQuery().toLowerCase();
            result = result.stream()
                    .filter(a -> ChatColor.stripColor(a.getItemName()).toLowerCase().contains(query)
                            || a.getItem().getType().name().toLowerCase().contains(query.replace(' ', '_')))
                    .collect(Collectors.toList());
        }

        if (session.hasCurrencyFilter()) {
            String filter = session.getCurrencyFilter();
            result = result.stream()
                    .filter(a -> a.getCurrencyKey().equals(filter))
                    .collect(Collectors.toList());
        }

        switch (session.getSortMode()) {
            case PRICE_LOW:
                result.sort(Comparator.comparingDouble(AuctionItem::getPrice));
                break;
            case PRICE_HIGH:
                result.sort(Comparator.comparingDouble(AuctionItem::getPrice).reversed());
                break;
            case OLDEST:
                result.sort(Comparator.comparingLong(AuctionItem::getExpiresAt));
                break;
            case NEWEST:
            default:
                result.sort(Comparator.comparingLong(AuctionItem::getExpiresAt).reversed());
                break;
        }

        return result;
    }

    void openCancelledGUI(Player player) {
        List<ItemStack> cancelled = manager.getCancelledItems(player.getUniqueId());
        int size = Math.max(27, ((cancelled.size() / 9) + 1) * 9);
        if (size > 54) size = 54;

        Inventory gui = Bukkit.createInventory(null, size, CANCELLED_TITLE);
        int slot = 0;
        for (ItemStack item : cancelled) {
            if (slot >= size) break;
            gui.setItem(slot++, item);
        }
        player.openInventory(gui);
    }

    public AHSession getOrCreateSession(UUID uuid) {
        return sessions.computeIfAbsent(uuid, k -> new AHSession());
    }

    public void removeSession(UUID uuid) {
        sessions.remove(uuid);
    }

    public AHSession getSession(UUID uuid) {
        return sessions.get(uuid);
    }

    private void fillBorder(Inventory gui) {
        ItemStack glass = new ItemStack(Material.BLACK_STAINED_GLASS_PANE);
        ItemMeta glassMeta = glass.getItemMeta();
        if (glassMeta != null) {
            glassMeta.setDisplayName(" ");
            glass.setItemMeta(glassMeta);
        }

        for (int i = 0; i < 54; i++) {
            int row = i / 9;
            int col = i % 9;
            if (row == 0 || row == 5 || col == 0 || col == 8) {
                gui.setItem(i, glass.clone());
            }
        }
    }

    private ItemStack createNavItem(Material material, String name, String... loreLines) {
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(name);
            List<String> lore = new ArrayList<>();
            Collections.addAll(lore, loreLines);
            meta.setLore(lore);
            item.setItemMeta(meta);
        }
        return item;
    }

    public static String formatCurrency(String currencyKey, double price) {
        String formatted;
        if (price == Math.floor(price)) {
            formatted = String.valueOf((long) price);
        } else {
            formatted = String.valueOf(price);
        }

        switch (currencyKey.toLowerCase()) {
            case "money":
                return ChatColor.GREEN + "$" + formatted;
            case "orbs":
                return ChatColor.AQUA + formatted + " Orbs";
            case "souls":
                return ChatColor.LIGHT_PURPLE + formatted + " Souls";
            case "credits":
                return ChatColor.YELLOW + formatted + " Credits";
            default:
                return formatted + " " + currencyKey;
        }
    }
}
