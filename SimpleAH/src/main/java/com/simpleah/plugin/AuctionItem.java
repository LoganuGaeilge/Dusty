package com.simpleah.plugin;

import org.bukkit.inventory.ItemStack;
import java.util.UUID;

public class AuctionItem {
    private final UUID id;
    private final UUID seller;
    private final ItemStack item;
    private final double price;
    private final String currencyKey;
    private final boolean isBin;
    private final long expiresAt;

    private double currentBid;
    private UUID currentBidder;

    public AuctionItem(UUID seller, ItemStack item, double price, String currencyKey,
                       boolean isBin, long expiresAt) {
        this.id = UUID.randomUUID();
        this.seller = seller;
        this.item = item;
        this.price = price;
        this.currencyKey = currencyKey;
        this.isBin = isBin;
        this.expiresAt = expiresAt;
        this.currentBid = 0;
        this.currentBidder = null;
    }

    public UUID getId() { return id; }
    public UUID getSeller() { return seller; }
    public ItemStack getItem() { return item; }
    public double getPrice() { return price; }
    public String getCurrencyKey() { return currencyKey; }
    public boolean isBin() { return isBin; }
    public long getExpiresAt() { return expiresAt; }

    public double getCurrentBid() { return currentBid; }
    public void setCurrentBid(double currentBid) { this.currentBid = currentBid; }

    public UUID getCurrentBidder() { return currentBidder; }
    public void setCurrentBidder(UUID currentBidder) { this.currentBidder = currentBidder; }

    public boolean hasBid() { return currentBidder != null; }

    public double getMinimumBid() {
        if (!hasBid()) return price;
        return currentBid + Math.max(1, Math.floor(currentBid * 0.05));
    }

    public String getItemName() {
        if (item.hasItemMeta() && item.getItemMeta().hasDisplayName()) {
            return item.getItemMeta().getDisplayName();
        }
        return formatMaterialName(item.getType().name());
    }

    private static String formatMaterialName(String name) {
        StringBuilder sb = new StringBuilder();
        for (String part : name.split("_")) {
            if (sb.length() > 0) sb.append(' ');
            sb.append(part.charAt(0)).append(part.substring(1).toLowerCase());
        }
        return sb.toString();
    }
}
