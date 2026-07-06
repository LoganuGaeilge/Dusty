package com.yourserver.customenchants.listeners;

// Import your main class (CustomEnchantsPlugin)
import com.yourserver.customenchants.CustomEnchantsPlugin; 
import com.yourserver.customenchants.util.SkriptBridge;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;

public class BlockEventListener implements Listener {

    private final CustomEnchantsPlugin plugin;

    // The constructor now correctly references CustomEnchantsPlugin
    public BlockEventListener(CustomEnchantsPlugin plugin) {
        this.plugin = plugin;
    }

    // MONITOR + ignoreCancelled so a break that WorldGuard or a Skript
    // "cancel event" already blocked doesn't still run the skriptvar
    // adjustment below - see the same fix in TriggerListener.onBlockBreak.
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onBlockBreak(BlockBreakEvent event) {
        String skriptVarLine = plugin.getConfig().getString("on-break-block.skriptvar");
        
        if (skriptVarLine != null && !skriptVarLine.isEmpty()) {
            processSkriptMath(skriptVarLine, event.getPlayer());
        }
    }

    private void processSkriptMath(String configLine, Player player) {
        String action = configLine.replace("%uuid%", player.getUniqueId().toString());
        
        try {
            String varName;
            double amount;
            
            if (action.contains(" += ")) {
                String[] parts = action.split(" \\+= ");
                varName = parts[0].trim();
                amount = Double.parseDouble(parts[1].trim());
                double current = SkriptBridge.getNumericVariable(plugin.getLogger(), varName);
                SkriptBridge.setVariable(plugin.getLogger(), varName, current + amount);
            } 
            else if (action.contains(" -= ")) {
                String[] parts = action.split(" -= ");
                varName = parts[0].trim();
                amount = Double.parseDouble(parts[1].trim());
                double current = SkriptBridge.getNumericVariable(plugin.getLogger(), varName);
                SkriptBridge.setVariable(plugin.getLogger(), varName, current - amount);
            } 
            else if (action.contains(" = ")) {
                String[] parts = action.split(" = ");
                varName = parts[0].trim();
                amount = Double.parseDouble(parts[1].trim());
                SkriptBridge.setVariable(plugin.getLogger(), varName, amount);
            }
        } catch (Exception e) {
            plugin.getLogger().warning("[CustomEnchants] Error processing skriptvar: " + configLine);
        }
    }
}