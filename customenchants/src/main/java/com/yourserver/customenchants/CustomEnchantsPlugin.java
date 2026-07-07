package com.yourserver.customenchants;

import com.yourserver.customenchants.commands.CustomEnchantsCommand;
import com.yourserver.customenchants.gui.UpgradeGUIListener;
import com.yourserver.customenchants.listeners.ApplyListener;
import com.yourserver.customenchants.listeners.TriggerListener;
import com.yourserver.customenchants.listeners.UnbreakingOverrideListener;
import com.yourserver.customenchants.tasks.HoldTickTask;
import com.yourserver.customenchants.util.ActionExecutor;
import com.yourserver.customenchants.util.Keys;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;

public class CustomEnchantsPlugin extends JavaPlugin {

    private EnchantManager enchantManager;
    private ItemFactory itemFactory;
    private ActionExecutor actionExecutor;
    private Keys keys;

    private HoldTickTask holdTickTask;
    private BukkitTask holdTask;

    @Override
    public void onEnable() {
        saveDefaultConfig();

        keys = new Keys(this);
        enchantManager = new EnchantManager(this);
        enchantManager.load();

        itemFactory = new ItemFactory(this);
        actionExecutor = new ActionExecutor(this);

        getServer().getPluginManager().registerEvents(new ApplyListener(this), this);
        getServer().getPluginManager().registerEvents(new TriggerListener(this), this);
        getServer().getPluginManager().registerEvents(new UpgradeGUIListener(this), this);
        getServer().getPluginManager().registerEvents(new UnbreakingOverrideListener(this), this);
        getServer().getPluginManager().registerEvents(new Listener() {
            @EventHandler
            public void onQuit(PlayerQuitEvent event) {
                if (holdTickTask != null) {
                    holdTickTask.forgetPlayer(event.getPlayer().getUniqueId());
                }
            }
        }, this);

        CustomEnchantsCommand commandExecutor = new CustomEnchantsCommand(this);
        getCommand("customenchants").setExecutor(commandExecutor);
        getCommand("customenchants").setTabCompleter(commandExecutor);

        restartHoldTask();

        getLogger().info("CustomEnchants enabled.");
    }

    @Override
    public void onDisable() {
        stopHoldTask();
        getLogger().info("CustomEnchants disabled.");
    }

    /**
     * (Re)starts the on-hold repeating task (see {@link HoldTickTask}) using
     * the current settings.on-hold config - safe to call any time, including
     * from /ce reload so interval/enabled changes take effect immediately
     * without a server restart.
     */
    public void restartHoldTask() {
        stopHoldTask();
        if (!enchantManager.isOnHoldEnabled()) return;
        int interval = enchantManager.getOnHoldIntervalTicks();
        holdTickTask = new HoldTickTask(this, interval);
        holdTask = holdTickTask.runTaskTimer(this, interval, interval);
    }

    private void stopHoldTask() {
        if (holdTask != null) {
            holdTask.cancel();
            holdTask = null;
        }
        holdTickTask = null;
    }

    public EnchantManager getEnchantManager() {
        return enchantManager;
    }

    public ItemFactory getItemFactory() {
        return itemFactory;
    }

    public ActionExecutor getActionExecutor() {
        return actionExecutor;
    }

    public Keys getKeys() {
        return keys;
    }
}
