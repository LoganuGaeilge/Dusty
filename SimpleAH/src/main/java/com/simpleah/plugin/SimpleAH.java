package com.simpleah.plugin;

import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.plugin.java.JavaPlugin;
import com.simpleah.plugin.util.DustyEconomyBridge;

public class SimpleAH extends JavaPlugin implements Listener {

    private AuctionManager auctionManager;

    @Override
    public void onEnable() {
        // Handle config
        getConfig().addDefault("bin-duration-hours", 48);
        getConfig().options().copyDefaults(true);
        saveConfig();

        this.auctionManager = new AuctionManager(this);

        // Register commands & events
        getCommand("ah").setExecutor(new AHCommand(this, auctionManager));
        getServer().getPluginManager().registerEvents(new AHGUIListener(this, auctionManager), this);
        getServer().getPluginManager().registerEvents(this, this);

        getLogger().info("SimpleAH initialized successfully. DustyEconomy Bridge status: "
                + DustyEconomyBridge.isAvailable(getLogger()));
    }

    @Override
    public void onDisable() {
        if (auctionManager != null) {
            auctionManager.saveData();
        }
    }

    @EventHandler
    public void onPlayerJoin(PlayerJoinEvent event) {
        // Claim any earnings accumulated while offline
        if (auctionManager != null) {
            auctionManager.claimOfflineEarnings(event.getPlayer());
        }
    }
}
