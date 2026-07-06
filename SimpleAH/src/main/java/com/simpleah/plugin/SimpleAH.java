package com.simpleah.plugin;

import com.simpleah.plugin.util.DustyEconomyBridge;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.plugin.java.JavaPlugin;

public class SimpleAH extends JavaPlugin implements Listener {

    private AuctionManager auctionManager;

    @Override
    public void onEnable() {
        getConfig().addDefault("bin-duration-hours", 48);
        getConfig().options().copyDefaults(true);
        saveConfig();

        this.auctionManager = new AuctionManager(this);

        AHCommand ahCommand = new AHCommand(this, auctionManager);
        getCommand("ah").setExecutor(ahCommand);
        getCommand("ah").setTabCompleter(ahCommand);
        getServer().getPluginManager().registerEvents(
                new AHGUIListener(this, auctionManager, ahCommand), this);
        getServer().getPluginManager().registerEvents(this, this);

        auctionManager.startExpiryTask();

        getLogger().info("SimpleAH initialized. DustyEconomy Bridge: "
                + DustyEconomyBridge.isAvailable(getLogger()));
    }

    @Override
    public void onDisable() {
        if (auctionManager != null) {
            auctionManager.processShutdown();
        }
    }

    @EventHandler
    public void onPlayerJoin(PlayerJoinEvent event) {
        if (auctionManager != null) {
            auctionManager.claimOfflineEarnings(event.getPlayer());
        }
    }
}
