package com.yourserver.customenchants.gui;

import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;

import java.util.Map;

/**
 * Marks an Inventory as being the pickaxe enchant-upgrade GUI, and maps
 * each slot back to the enchant id it represents so click handling knows
 * what was purchased.
 */
public class UpgradeGUIHolder implements InventoryHolder {

    private final Player owner;
    private final Map<Integer, String> slotToEnchantId;
    private Inventory inventory;

    public UpgradeGUIHolder(Player owner, Map<Integer, String> slotToEnchantId) {
        this.owner = owner;
        this.slotToEnchantId = slotToEnchantId;
    }

    public void setInventory(Inventory inventory) {
        this.inventory = inventory;
    }

    @Override
    public Inventory getInventory() {
        return inventory;
    }

    public Player getOwner() {
        return owner;
    }

    public String getEnchantIdForSlot(int slot) {
        return slotToEnchantId.get(slot);
    }
}
