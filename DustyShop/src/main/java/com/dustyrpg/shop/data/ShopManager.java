package com.dustyrpg.shop.data;

import com.dustyrpg.shop.DustyShop;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class ShopManager {

    private final DustyShop plugin;
    private final Map<String, ShopCategory> categories = new LinkedHashMap<>();

    public ShopManager(DustyShop plugin) {
        this.plugin = plugin;
        loadShop();
    }

    public void loadShop() {
        categories.clear();
        ConfigurationSection catsSection = plugin.getConfig().getConfigurationSection("categories");
        if (catsSection == null) {
            plugin.getLogger().warning("No 'categories' section found in config.yml");
            return;
        }

        for (String catKey : catsSection.getKeys(false)) {
            ConfigurationSection catSection = catsSection.getConfigurationSection(catKey);
            if (catSection == null) continue;

            String displayName = colorize(catSection.getString("display-name", catKey));
            Material icon = Material.matchMaterial(catSection.getString("icon", "CHEST"));
            if (icon == null) icon = Material.CHEST;
            int slot = catSection.getInt("slot", -1);

            List<ShopItem> items = new ArrayList<>();
            ConfigurationSection itemsSection = catSection.getConfigurationSection("items");
            if (itemsSection != null) {
                for (String itemKey : itemsSection.getKeys(false)) {
                    ConfigurationSection itemSection = itemsSection.getConfigurationSection(itemKey);
                    if (itemSection == null) continue;

                    Material material = Material.matchMaterial(itemSection.getString("material", "STONE"));
                    if (material == null) {
                        plugin.getLogger().warning("Unknown material in shop item: " + itemKey);
                        continue;
                    }

                    int amount = itemSection.getInt("amount", 1);
                    String itemDisplayName = colorize(itemSection.getString("display-name", itemKey));
                    List<String> lore = new ArrayList<>();
                    for (String line : itemSection.getStringList("lore")) {
                        lore.add(colorize(line));
                    }
                    String currency = itemSection.getString("currency", "money");
                    double price = itemSection.getDouble("price", 0);

                    items.add(new ShopItem(itemKey, material, amount, itemDisplayName, lore, currency, price));
                }
            }

            categories.put(catKey, new ShopCategory(catKey, displayName, icon, slot, items));
        }

        plugin.getLogger().info("Loaded " + categories.size() + " shop categories.");
    }

    public Map<String, ShopCategory> getCategories() {
        return categories;
    }

    public ShopCategory getCategory(String id) {
        return categories.get(id);
    }

    private String colorize(String text) {
        return ChatColor.translateAlternateColorCodes('&', text);
    }
}
