package com.dustyrpg.economy;

import com.dustyrpg.economy.commands.CurrencyAddCommand;
import com.dustyrpg.economy.commands.CurrencySetCommand;
import com.dustyrpg.economy.commands.SellCommand;
import com.dustyrpg.economy.data.EconomyManager;
import com.dustyrpg.economy.listeners.PlayerJoinListener;
import com.dustyrpg.economy.placeholders.DustyEconomyExpansion;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

public class EconomyPlugin extends JavaPlugin {

    private EconomyManager economyManager;
    private SellCommand sellCommand;

    @Override
    public void onEnable() {
        saveDefaultConfig();
        getDataFolder().mkdirs();

        this.economyManager = new EconomyManager(this);
        this.sellCommand = new SellCommand(this);

        getServer().getPluginManager().registerEvents(new PlayerJoinListener(this), this);

        getCommand("sell").setExecutor(sellCommand);
        getCommand("currencyset").setExecutor(new CurrencySetCommand(this));
        getCommand("currencyadd").setExecutor(new CurrencyAddCommand(this));

        // In case of a /reload, make sure already-online players get set up too.
        for (Player player : getServer().getOnlinePlayers()) {
            economyManager.registerPlayer(player);
        }

        // Scoreboards are handled entirely by the TAB plugin now, driven by
        // the placeholders this expansion registers below. No more per-player
        // Bukkit Scoreboard/Objective bookkeeping or update-tick task here.
        if (getServer().getPluginManager().getPlugin("PlaceholderAPI") != null) {
            new DustyEconomyExpansion(this).register();
            getLogger().info("Hooked into PlaceholderAPI - registered %dustyeconomy_...% placeholders.");
        } else {
            getLogger().warning("PlaceholderAPI not found - currency placeholders will not be available."
                    + " Install PlaceholderAPI so TAB (or other placeholder consumers) can display balances.");
        }

        getLogger().info("DustyEconomy enabled.");
    }

    @Override
    public void onDisable() {
        if (economyManager != null) {
            economyManager.save();
        }
        getLogger().info("DustyEconomy disabled.");
    }

    public EconomyManager getEconomyManager() {
        return economyManager;
    }

    public SellCommand getSellCommand() {
        return sellCommand;
    }
}
