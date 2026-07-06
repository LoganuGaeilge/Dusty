package com.simpleah.plugin;

import com.simpleah.plugin.util.DustyEconomyBridge;
import org.bukkit.Bukkit;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.io.File;
import java.io.IOException;
import java.util.*;

public class AuctionManager {
    private final SimpleAH plugin;
    private final File dataFile;
    private FileConfiguration data;

    private final List<AuctionItem> activeAuctions = new ArrayList<>();
    private final Map<UUID, List<ItemStack>> cancelledItems = new HashMap<>();
    private final Map<UUID, Double> offlineEarnings = new HashMap<>();

    public AuctionManager(SimpleAH plugin) {
        this.plugin = plugin;
        if (!plugin.getDataFolder().exists()) {
            plugin.getDataFolder().mkdirs();
        }
        this.dataFile = new File(plugin.getDataFolder(), "data.yml");
        this.data = YamlConfiguration.loadConfiguration(dataFile);
        loadData();
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

    public void processSale(UUID sellerId, double amount) {
        Player seller = Bukkit.getPlayer(sellerId);
        if (seller != null && seller.isOnline()) {
            if (!DustyEconomyBridge.addBalance(plugin.getLogger(), sellerId, "money", amount)) {
                plugin.getLogger().warning("[SimpleAH] Could not credit seller " + seller.getName()
                        + " $" + amount + " – queuing as offline earnings.");
                double current = offlineEarnings.getOrDefault(sellerId, 0.0);
                offlineEarnings.put(sellerId, current + amount);
                return;
            }
            seller.sendMessage("§aYour item sold on the AH for $" + amount + "!");
        } else {
            double current = offlineEarnings.getOrDefault(sellerId, 0.0);
            offlineEarnings.put(sellerId, current + amount);
        }
    }

    public void claimOfflineEarnings(Player player) {
        UUID id = player.getUniqueId();
        if (offlineEarnings.containsKey(id)) {
            double amount = offlineEarnings.get(id);
            if (!DustyEconomyBridge.addBalance(plugin.getLogger(), id, "money", amount)) {
                plugin.getLogger().warning("[SimpleAH] Could not pay offline earnings of $" + amount
                        + " to " + player.getName() + " – will retry on next login.");
                return;
            }
            player.sendMessage("§aWhile you were offline, your AH items sold for $" + amount + "!");
            offlineEarnings.remove(id);
        }
    }

    /**
     * Loads active auctions, cancelled/unclaimed items, and pending offline
     * earnings back out of data.yml. Previously this was a no-op, so every
     * server restart silently wiped all outstanding auctions and earnings.
     */
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
                    boolean isBin = entry.getBoolean("bin");
                    long expiresAt = entry.getLong("expiresAt");
                    if (item != null) {
                        activeAuctions.add(new AuctionItem(seller, item, price, isBin, expiresAt));
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
                    offlineEarnings.put(uuid, earningsSection.getDouble(uuidKey));
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
            data.set(path + ".bin", auction.isBin());
            data.set(path + ".expiresAt", auction.getExpiresAt());
        }

        for (Map.Entry<UUID, List<ItemStack>> entry : cancelledItems.entrySet()) {
            data.set("cancelled." + entry.getKey(), entry.getValue());
        }

        for (Map.Entry<UUID, Double> entry : offlineEarnings.entrySet()) {
            data.set("offlineEarnings." + entry.getKey(), entry.getValue());
        }

        try {
            data.save(dataFile);
        } catch (IOException e) {
            plugin.getLogger().severe("Could not save SimpleAH data.yml: " + e.getMessage());
        }
    }
}
