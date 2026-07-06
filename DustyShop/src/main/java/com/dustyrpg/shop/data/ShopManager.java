package com.dustyrpg.shop.data;

import com.dustyrpg.shop.DustyShop;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.EntityType;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class ShopManager {

    private final DustyShop plugin;
    private final Map<String, ShopCategory> categories = new LinkedHashMap<>();
    private final Map<String, EntityShop> entityShops = new LinkedHashMap<>();

    public ShopManager(DustyShop plugin) {
        this.plugin = plugin;
        loadShop();
    }

    public void loadShop() {
        loadCategories();
        loadEntityShops();
    }

    private void loadCategories() {
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

            List<ShopItem> items = parseItems(catSection.getConfigurationSection("items"));
            categories.put(catKey, new ShopCategory(catKey, displayName, icon, slot, items));
        }

        plugin.getLogger().info("Loaded " + categories.size() + " shop categories.");
    }

    private void loadEntityShops() {
        entityShops.clear();
        ConfigurationSection shopsSection = plugin.getConfig().getConfigurationSection("entity-shops");
        if (shopsSection == null) {
            return;
        }

        for (String shopKey : shopsSection.getKeys(false)) {
            ConfigurationSection shopSection = shopsSection.getConfigurationSection(shopKey);
            if (shopSection == null) continue;

            String displayName = colorize(shopSection.getString("display-name", shopKey));

            ConfigurationSection entitySection = shopSection.getConfigurationSection("entity");
            if (entitySection == null) {
                plugin.getLogger().warning("Entity shop '" + shopKey + "' has no 'entity' section; skipping.");
                continue;
            }

            EntityType entityType = null;
            String typeName = entitySection.getString("type");
            if (typeName != null && !typeName.trim().isEmpty()) {
                try {
                    entityType = EntityType.valueOf(typeName.trim().toUpperCase());
                } catch (IllegalArgumentException ex) {
                    plugin.getLogger().warning("Entity shop '" + shopKey + "' has unknown entity type '"
                            + typeName + "'; it will match any entity type at the location.");
                }
            }

            String world = entitySection.getString("world");
            if (world == null || world.trim().isEmpty()) {
                plugin.getLogger().warning("Entity shop '" + shopKey + "' has no 'world'; skipping.");
                continue;
            }
            double x = entitySection.getDouble("x");
            double y = entitySection.getDouble("y");
            double z = entitySection.getDouble("z");
            double radius = entitySection.getDouble("radius", 2.0);

            List<ShopItem> items = parseItems(shopSection.getConfigurationSection("items"));
            entityShops.put(shopKey, new EntityShop(shopKey, displayName, entityType,
                    world, x, y, z, radius, items));
        }

        plugin.getLogger().info("Loaded " + entityShops.size() + " entity shops.");
    }

    private List<ShopItem> parseItems(ConfigurationSection itemsSection) {
        List<ShopItem> items = new ArrayList<>();
        if (itemsSection == null) return items;

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
        return items;
    }

    public Map<String, ShopCategory> getCategories() {
        return categories;
    }

    public ShopCategory getCategory(String id) {
        return categories.get(id);
    }

    public Map<String, EntityShop> getEntityShops() {
        return entityShops;
    }

    public EntityShop getEntityShop(String id) {
        return entityShops.get(id);
    }

    private String colorize(String text) {
        return ChatColor.translateAlternateColorCodes('&', text);
    }
}
