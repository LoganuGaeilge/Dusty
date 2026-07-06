package com.dustyrpg.shop.data;

import org.bukkit.Material;

import java.util.List;

public class ShopCategory {
    private final String id;
    private final String displayName;
    private final Material icon;
    private final int slot;
    private final List<ShopItem> items;

    public ShopCategory(String id, String displayName, Material icon, int slot, List<ShopItem> items) {
        this.id = id;
        this.displayName = displayName;
        this.icon = icon;
        this.slot = slot;
        this.items = items;
    }

    public String getId() { return id; }
    public String getDisplayName() { return displayName; }
    public Material getIcon() { return icon; }
    public int getSlot() { return slot; }
    public List<ShopItem> getItems() { return items; }
}
