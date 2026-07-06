package com.dustyrpg.shop;

import com.dustyrpg.shop.data.ShopManager;
import com.dustyrpg.shop.gui.EntityShopListener;
import com.dustyrpg.shop.gui.ShopGUI;
import com.dustyrpg.shop.gui.ShopGUIListener;
import com.dustyrpg.shop.util.DustyEconomyBridge;
import org.bukkit.plugin.java.JavaPlugin;

public class DustyShop extends JavaPlugin {

    private ShopManager shopManager;
    private ShopGUI shopGUI;

    @Override
    public void onEnable() {
        saveDefaultConfig();

        this.shopManager = new ShopManager(this);
        this.shopGUI = new ShopGUI(this);

        getCommand("shop").setExecutor(new ShopCommand(this));
        getServer().getPluginManager().registerEvents(new ShopGUIListener(this), this);
        getServer().getPluginManager().registerEvents(new EntityShopListener(this), this);

        getLogger().info("DustyShop enabled. DustyEconomy Bridge: "
                + DustyEconomyBridge.isAvailable(getLogger()));
    }

    @Override
    public void onDisable() {
        getLogger().info("DustyShop disabled.");
    }

    public ShopManager getShopManager() {
        return shopManager;
    }

    public ShopGUI getShopGUI() {
        return shopGUI;
    }
}
