package com.dustyrpg.shop.data;

import org.bukkit.Material;

import java.util.List;

public class ShopItem {
    private final String id;
    private final Material material;
    private final int amount;
    private final String displayName;
    private final List<String> lore;
    private final String currency;
    private final double price;

    public ShopItem(String id, Material material, int amount, String displayName,
                    List<String> lore, String currency, double price) {
        this.id = id;
        this.material = material;
        this.amount = amount;
        this.displayName = displayName;
        this.lore = lore;
        this.currency = currency;
        this.price = price;
    }

    public String getId() { return id; }
    public Material getMaterial() { return material; }
    public int getAmount() { return amount; }
    public String getDisplayName() { return displayName; }
    public List<String> getLore() { return lore; }
    public String getCurrency() { return currency; }
    public double getPrice() { return price; }
}
