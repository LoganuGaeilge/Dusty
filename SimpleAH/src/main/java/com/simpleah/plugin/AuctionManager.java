package com.simpleah.plugin;

import com.simpleah.plugin.util.DustyEconomyBridge;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.scheduler.BukkitRunnable;

import java.io.File;
import java.io.IOException;
import java.util.*;

public class AuctionManager {
    private final SimpleAH plugin;
    private final File dataFile;
    private FileConfiguration data;

    private final List<AuctionItem> activeAuctions = new ArrayList<>();
    private final Map<UUID, List<ItemStack>> cancelledItems = new HashMap<>();
    private final Map<UUID, List<OfflineEarning>> offlineEarnings = new HashMap<>();

    public AuctionManager(SimpleAH plugin) {
        this.plugin = plugin;
        if (!plugin.getDataFolder().exists()) {
            plugin.getDataFolder().mkdirs();
        }
        this.dataFile = new File(plugin.getDataFolder(), "data.yml");
        this.data = YamlConfiguration.loadConfiguration(dataFile);
        loadData();
    }

    public void startExpiryTask() {
        new BukkitRunnable() {
            @Override
            public void run() {
                processExpiredAuctions();
            }
        }.runTaskTimer(plugin, 600L, 600L); // every 30 seconds
    }

    private void processExpiredAuctions() {
        long now = System.currentTimeMillis();
        List<AuctionItem> expired = new ArrayList<>();

        for (AuctionItem auction : activeAuctions) {
            if (auction.getExpiresAt() <= now) {
                expired.add(auction);
            }
        }

        for (AuctionItem auction : expired) {
            activeAuctions.remove(auction);

            if (!auction.isBin() && auction.hasBid()) {
                // Auction with bids: winner gets item, seller gets paid
                UUID winnerId = auction.getCurrentBidder();
                double finalBid = auction.getCurrentBid();

                // Give item to winner
                Player winner = Bukkit.getPlayer(winnerId);
                if (winner != null && winner.isOnline()) {
                    winner.getInventory().addItem(auction.getItem());
                    winner.sendMessage(ChatColor.GREEN + "You won the auction for "
                            + auction.getItemName() + ChatColor.GREEN + " at "
                            + AHCommand.formatCurrency(auction.getCurrencyKey(), finalBid)
                            + ChatColor.GREEN + "!");
                } else {
                    // Item goes to cancelled bin for winner to claim
                    cancelledItems.computeIfAbsent(winnerId, k -> new ArrayList<>())
                            .add(auction.getItem());
                }

                // Pay seller
                processSale(auction.getSeller(), auction.getCurrencyKey(), finalBid);
            } else {
                // BIN expired or auction with no bids: return to seller's cancelled bin
                cancelledItems.computeIfAbsent(auction.getSeller(), k -> new ArrayList<>())
                        .add(auction.getItem());

                Player seller = Bukkit.getPlayer(auction.getSeller());
                if (seller != null && seller.isOnline()) {
                    seller.sendMessage(ChatColor.YELLOW + "Your listing for "
                            + auction.getItemName() + ChatColor.YELLOW
                            + " has expired. Claim it from /ah cancelled.");
                }
            }
        }
    }

    public void addAuction(AuctionItem item) {
        activeAuctions.add(item);
    }

    public void cancelAuction(AuctionItem item) {
        activeAuctions.remove(item);
        cancelledItems.computeIfAbsent(item.getSeller(), k -> new ArrayList<>()).add(item.getItem());
    }

    public void removeCancelledItem(UUID uuid, ItemStack item) {
        if (cancelledItems.containsKey(uuid)) {
            cancelledItems.get(uuid).remove(item);
        }
    }

    public List<AuctionItem> getActiveAuctions() {
        return activeAuctions;
    }

    public List<ItemStack> getCancelledItems(UUID uuid) {
        return cancelledItems.getOrDefault(uuid, new ArrayList<>());
    }

    public void refundBidder(AuctionItem auction) {
        if (!auction.hasBid()) return;
        UUID bidderId = auction.getCurrentBidder();
        String currency = auction.getCurrencyKey();
        double amount = auction.getCurrentBid();

        Player bidder = Bukkit.getPlayer(bidderId);
        if (bidder != null && bidder.isOnline()) {
            if (DustyEconomyBridge.addBalance(plugin.getLogger(), bidderId, currency, amount)) {
                bidder.sendMessage(ChatColor.YELLOW + "Your bid of "
                        + AHCommand.formatCurrency(currency, amount) + ChatColor.YELLOW
                        + " has been refunded.");
            } else {
                addOfflineEarning(bidderId, currency, amount);
            }
        } else {
            addOfflineEarning(bidderId, currency, amount);
        }
    }

    public void processSale(UUID sellerId, String currencyKey, double amount) {
        Player seller = Bukkit.getPlayer(sellerId);
        if (seller != null && seller.isOnline()) {
            if (!DustyEconomyBridge.addBalance(plugin.getLogger(), sellerId, currencyKey, amount)) {
                plugin.getLogger().warning("[SimpleAH] Could not credit seller " + seller.getName()
                        + " " + amount + " " + currencyKey + " - queuing as offline earnings.");
                addOfflineEarning(sellerId, currencyKey, amount);
                return;
            }
            seller.sendMessage("\u00a7aYour item sold on the AH for "
                    + AHCommand.formatCurrency(currencyKey, amount) + "\u00a7a!");
        } else {
            addOfflineEarning(sellerId, currencyKey, amount);
        }
    }

    public void claimOfflineEarnings(Player player) {
        UUID id = player.getUniqueId();
        List<OfflineEarning> earnings = offlineEarnings.get(id);
        if (earnings == null || earnings.isEmpty()) return;

        Iterator<OfflineEarning> it = earnings.iterator();
        double totalMoney = 0;
        while (it.hasNext()) {
            OfflineEarning earning = it.next();
            if (DustyEconomyBridge.addBalance(plugin.getLogger(), id, earning.currencyKey, earning.amount)) {
                if (earning.currencyKey.equals("money")) {
                    totalMoney += earning.amount;
                } else {
                    player.sendMessage("\u00a7aWhile you were offline, an AH sale earned you "
                            + AHCommand.formatCurrency(earning.currencyKey, earning.amount) + "\u00a7a!");
                }
                it.remove();
            } else {
                plugin.getLogger().warning("[SimpleAH] Could not pay offline earnings of "
                        + earning.amount + " " + earning.currencyKey + " to " + player.getName()
                        + " - will retry on next login.");
            }
        }

        if (totalMoney > 0) {
            player.sendMessage("\u00a7aWhile you were offline, your AH items sold for "
                    + AHCommand.formatCurrency("money", totalMoney) + "\u00a7a!");
        }

        if (earnings.isEmpty()) {
            offlineEarnings.remove(id);
        }
    }

    private void addOfflineEarning(UUID uuid, String currencyKey, double amount) {
        offlineEarnings.computeIfAbsent(uuid, k -> new ArrayList<>())
                .add(new OfflineEarning(currencyKey, amount));
    }

    public void processShutdown() {
        // Refund all active auction bids on shutdown to prevent loss
        for (AuctionItem auction : activeAuctions) {
            if (!auction.isBin() && auction.hasBid()) {
                addOfflineEarning(auction.getCurrentBidder(), auction.getCurrencyKey(), auction.getCurrentBid());
            }
        }
        saveData();
    }

    private void loadData() {
        if (!dataFile.exists()) return;

        activeAuctions.clear();
        cancelledItems.clear();
        offlineEarnings.clear();

        ConfigurationSection activeSection = data.getConfigurationSection("active");
        if (activeSection != null) {
            for (String key : activeSection.getKeys(false)) {
                ConfigurationSection entry = activeSection.getConfigurationSection(key);
                if (entry == null) continue;
                try {
                    UUID seller = UUID.fromString(entry.getString("seller"));
                    ItemStack item = entry.getItemStack("item");
                    double price = entry.getDouble("price");
                    String currencyKey = entry.getString("currencyKey", "money");
                    boolean isBin = entry.getBoolean("bin");
                    long expiresAt = entry.getLong("expiresAt");
                    if (item != null) {
                        AuctionItem auctionItem = new AuctionItem(seller, item, price, currencyKey, isBin, expiresAt);
                        double currentBid = entry.getDouble("currentBid", 0);
                        String bidderStr = entry.getString("currentBidder");
                        if (currentBid > 0 && bidderStr != null && !bidderStr.isEmpty()) {
                            auctionItem.setCurrentBid(currentBid);
                            auctionItem.setCurrentBidder(UUID.fromString(bidderStr));
                        }
                        activeAuctions.add(auctionItem);
                    }
                } catch (Exception e) {
                    plugin.getLogger().warning("Skipped a corrupt active auction entry '" + key + "' in data.yml");
                }
            }
        }

        ConfigurationSection cancelledSection = data.getConfigurationSection("cancelled");
        if (cancelledSection != null) {
            for (String uuidKey : cancelledSection.getKeys(false)) {
                try {
                    UUID uuid = UUID.fromString(uuidKey);
                    List<?> rawItems = cancelledSection.getList(uuidKey);
                    if (rawItems == null) continue;
                    List<ItemStack> items = new ArrayList<>();
                    for (Object obj : rawItems) {
                        if (obj instanceof ItemStack) {
                            items.add((ItemStack) obj);
                        }
                    }
                    cancelledItems.put(uuid, items);
                } catch (IllegalArgumentException e) {
                    plugin.getLogger().warning("Skipped a corrupt cancelled-items entry '" + uuidKey + "' in data.yml");
                }
            }
        }

        ConfigurationSection earningsSection = data.getConfigurationSection("offlineEarnings");
        if (earningsSection != null) {
            for (String uuidKey : earningsSection.getKeys(false)) {
                try {
                    UUID uuid = UUID.fromString(uuidKey);
                    ConfigurationSection playerEarnings = earningsSection.getConfigurationSection(uuidKey);
                    if (playerEarnings == null) {
                        double amount = earningsSection.getDouble(uuidKey);
                        if (amount > 0) {
                            addOfflineEarning(uuid, "money", amount);
                        }
                        continue;
                    }
                    for (String earningKey : playerEarnings.getKeys(false)) {
                        ConfigurationSection entry = playerEarnings.getConfigurationSection(earningKey);
                        if (entry == null) continue;
                        String currencyKey = entry.getString("currency", "money");
                        double amount = entry.getDouble("amount", 0);
                        if (amount > 0) {
                            addOfflineEarning(uuid, currencyKey, amount);
                        }
                    }
                } catch (IllegalArgumentException e) {
                    plugin.getLogger().warning("Skipped a corrupt offline-earnings entry '" + uuidKey + "' in data.yml");
                }
            }
        }
    }

    public void saveData() {
        this.data = new YamlConfiguration();

        int index = 0;
        for (AuctionItem auction : activeAuctions) {
            String path = "active." + (index++);
            data.set(path + ".seller", auction.getSeller().toString());
            data.set(path + ".item", auction.getItem());
            data.set(path + ".price", auction.getPrice());
            data.set(path + ".currencyKey", auction.getCurrencyKey());
            data.set(path + ".bin", auction.isBin());
            data.set(path + ".expiresAt", auction.getExpiresAt());
            if (auction.hasBid()) {
                data.set(path + ".currentBid", auction.getCurrentBid());
                data.set(path + ".currentBidder", auction.getCurrentBidder().toString());
            }
        }

        for (Map.Entry<UUID, List<ItemStack>> entry : cancelledItems.entrySet()) {
            data.set("cancelled." + entry.getKey(), entry.getValue());
        }

        for (Map.Entry<UUID, List<OfflineEarning>> entry : offlineEarnings.entrySet()) {
            int earningIdx = 0;
            for (OfflineEarning earning : entry.getValue()) {
                String path = "offlineEarnings." + entry.getKey() + "." + (earningIdx++);
                data.set(path + ".currency", earning.currencyKey);
                data.set(path + ".amount", earning.amount);
            }
        }

        try {
            data.save(dataFile);
        } catch (IOException e) {
            plugin.getLogger().severe("Could not save SimpleAH data.yml: " + e.getMessage());
        }
    }

    static class OfflineEarning {
        final String currencyKey;
        final double amount;

        OfflineEarning(String currencyKey, double amount) {
            this.currencyKey = currencyKey;
            this.amount = amount;
        }
    }
}
