package com.simpleah.plugin;

import org.bukkit.inventory.ItemStack;
import java.util.UUID;

public class AuctionItem {
    private final UUID id;
    private final UUID seller;
    private final ItemStack item;
    private final double price;
    private final boolean isBin;
    private final long expiresAt;

    public AuctionItem(UUID seller, ItemStack item, double price, boolean isBin, long expiresAt) {
        this.id = UUID.randomUUID();
        this.seller = seller;
        this.item = item;
        this.price = price;
        this.isBin = isBin;
        this.expiresAt = expiresAt;
    }

    public UUID getId() { return id; }
    public UUID getSeller() { return seller; }
    public ItemStack getItem() { return item; }
    public double getPrice() { return price; }
    public boolean isBin() { return isBin; }
    public long getExpiresAt() { return expiresAt; }
}
