package com.dustyrpg.shop.gui;

import com.dustyrpg.shop.DustyShop;
import com.dustyrpg.shop.data.EntityShop;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.inventory.EquipmentSlot;

/**
 * Opens an {@link EntityShop} when a player right-clicks the entity it is
 * bound to (matching entity type + location, per config.yml's
 * {@code entity-shops:} section).
 */
public class EntityShopListener implements Listener {

    private final DustyShop plugin;

    public EntityShopListener(DustyShop plugin) {
        this.plugin = plugin;
    }

    @EventHandler
    public void onPlayerInteractEntity(PlayerInteractEntityEvent event) {
        // Only react to the main hand so the shop opens exactly once.
        if (event.getHand() != EquipmentSlot.HAND) return;

        Entity clicked = event.getRightClicked();
        for (EntityShop shop : plugin.getShopManager().getEntityShops().values()) {
            if (shop.matches(clicked)) {
                event.setCancelled(true);
                Player player = event.getPlayer();
                plugin.getShopGUI().openEntityShop(player, shop.getId(), 0);
                return;
            }
        }
    }
}
