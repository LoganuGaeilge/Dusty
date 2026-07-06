package com.dustyrpg.economy.listeners;

import com.dustyrpg.economy.EconomyPlugin;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;

public class PlayerJoinListener implements Listener {

    private final EconomyPlugin plugin;

    public PlayerJoinListener(EconomyPlugin plugin) {
        this.plugin = plugin;
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        plugin.getEconomyManager().registerPlayer(event.getPlayer());
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        // No per-player scoreboard object to tear down anymore - TAB owns
        // the scoreboard lifecycle now.
    }
}
