package com.yourserver.givehead;

import org.bukkit.plugin.java.JavaPlugin;

public final class GiveHeadPlugin extends JavaPlugin {

    private HeadConfig headConfig;
    private SkinCache skinCache;

    @Override
    public void onEnable() {
        saveDefaultConfig();
        headConfig = new HeadConfig();
        headConfig.load(getConfig());
        skinCache = new SkinCache(getLogger());

        GiveHeadCommand command = new GiveHeadCommand(this);
        getCommand("givehead").setExecutor(command);
        getCommand("givehead").setTabCompleter(command);

        warmUpSkinCache();

        getLogger().info("GiveHead enabled - " + headConfig.getDefaultPlayer() + " is the default head skin.");
    }

    public void reloadHeadConfig() {
        reloadConfig();
        headConfig.load(getConfig());
        warmUpSkinCache();
    }

    /**
     * Pre-resolves every skin name referenced in config.yml at
     * startup/reload, so the FIRST time any given mob triggers a head drop
     * in-game there's no lookup delay - it's already cached. Each
     * resolve() call is safe to fire from the main thread since the actual
     * network work happens inside PlayerProfile#update().
     */
    private void warmUpSkinCache() {
        java.util.Set<String> names = headConfig.getAllConfiguredSkinNames();
        for (String name : names) {
            skinCache.resolve(name);
        }
        getLogger().info("[GiveHead] Warming up " + names.size() + " configured skin(s).");
    }

    public HeadConfig getHeadConfig() {
        return headConfig;
    }

    public SkinCache getSkinCache() {
        return skinCache;
    }
}
