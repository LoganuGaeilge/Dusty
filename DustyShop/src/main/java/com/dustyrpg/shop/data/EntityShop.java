package com.dustyrpg.shop.data;

import org.bukkit.Location;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;

import java.util.List;

/**
 * A shop bound to a specific entity standing at a configured location.
 *
 * Unlike {@link ShopCategory}, entity shops never appear in the {@code /shop}
 * category menu - they are opened only by right-clicking the matching entity.
 * They are still loaded from (and edited in) the same config.yml under
 * {@code entity-shops:}.
 */
public class EntityShop {
    private final String id;
    private final String displayName;
    private final EntityType entityType;
    private final String world;
    private final double x;
    private final double y;
    private final double z;
    private final double radius;
    private final List<ShopItem> items;

    public EntityShop(String id, String displayName, EntityType entityType,
                      String world, double x, double y, double z, double radius,
                      List<ShopItem> items) {
        this.id = id;
        this.displayName = displayName;
        this.entityType = entityType;
        this.world = world;
        this.x = x;
        this.y = y;
        this.z = z;
        this.radius = radius;
        this.items = items;
    }

    public String getId() { return id; }
    public String getDisplayName() { return displayName; }
    public EntityType getEntityType() { return entityType; }
    public String getWorld() { return world; }
    public double getRadius() { return radius; }
    public List<ShopItem> getItems() { return items; }

    /**
     * Returns true if {@code entity} is the entity this shop is bound to:
     * its type matches (when a type is configured) and it stands within
     * {@code radius} blocks of the configured location in the configured
     * world.
     */
    public boolean matches(Entity entity) {
        if (entityType != null && entity.getType() != entityType) {
            return false;
        }
        Location loc = entity.getLocation();
        if (loc.getWorld() == null || !loc.getWorld().getName().equalsIgnoreCase(world)) {
            return false;
        }
        double dx = loc.getX() - x;
        double dy = loc.getY() - y;
        double dz = loc.getZ() - z;
        return (dx * dx + dy * dy + dz * dz) <= radius * radius;
    }
}
