package com.dustyrpg.shop.util;

import org.bukkit.Bukkit;
import org.bukkit.plugin.Plugin;

import java.lang.reflect.Method;
import java.util.UUID;
import java.util.logging.Logger;

/**
 * Soft-dependency bridge to the DustyEconomy plugin for DustyShop.
 * Provides getBalance, addBalance, and removeBalance via reflection.
 */
public final class DustyEconomyBridge {

    private static boolean checked = false;
    private static boolean available = false;
    private static Plugin dustyEconomyPlugin;
    private static Method getEconomyManagerMethod;
    private static Method getBalanceMethod;
    private static Method addBalanceMethod;
    private static Method fromKeyMethod;

    private DustyEconomyBridge() {
    }

    private static void init(Logger logger) {
        if (checked) return;
        checked = true;

        Plugin plugin = Bukkit.getPluginManager().getPlugin("DustyEconomy");
        if (plugin == null || !plugin.isEnabled()) {
            return;
        }

        try {
            ClassLoader loader = plugin.getClass().getClassLoader();
            Class<?> economyPluginClass = Class.forName("com.dustyrpg.economy.EconomyPlugin", true, loader);
            Class<?> economyManagerClass = Class.forName("com.dustyrpg.economy.data.EconomyManager", true, loader);
            Class<?> currencyClass = Class.forName("com.dustyrpg.economy.Currency", true, loader);

            getEconomyManagerMethod = economyPluginClass.getMethod("getEconomyManager");
            getBalanceMethod = economyManagerClass.getMethod("getBalance", UUID.class, currencyClass);
            addBalanceMethod = economyManagerClass.getMethod("addBalance", UUID.class, currencyClass, double.class);
            fromKeyMethod = currencyClass.getMethod("fromKey", String.class);

            dustyEconomyPlugin = plugin;
            available = true;
        } catch (Exception ex) {
            logger.warning("[DustyShop] DustyEconomy was detected but could not be hooked (version mismatch?).");
            available = false;
        }
    }

    public static boolean isAvailable(Logger logger) {
        init(logger);
        return available;
    }

    public static Double getBalance(Logger logger, UUID uuid, String currencyKey) {
        if (!isAvailable(logger)) return null;
        try {
            Object economyManager = getEconomyManagerMethod.invoke(dustyEconomyPlugin);
            Object currency = fromKeyMethod.invoke(null, currencyKey);
            if (currency == null) return null;
            Object balance = getBalanceMethod.invoke(economyManager, uuid, currency);
            return (Double) balance;
        } catch (Exception ex) {
            logger.warning("[DustyShop] Failed to read DustyEconomy balance for currency '"
                    + currencyKey + "': " + ex.getMessage());
            return null;
        }
    }

    public static boolean addBalance(Logger logger, UUID uuid, String currencyKey, double amount) {
        if (!isAvailable(logger)) return false;
        try {
            Object economyManager = getEconomyManagerMethod.invoke(dustyEconomyPlugin);
            Object currency = fromKeyMethod.invoke(null, currencyKey);
            if (currency == null) return false;
            addBalanceMethod.invoke(economyManager, uuid, currency, amount);
            return true;
        } catch (Exception ex) {
            logger.warning("[DustyShop] Failed to modify DustyEconomy balance for currency '"
                    + currencyKey + "': " + ex.getMessage());
            return false;
        }
    }

    public static boolean removeBalance(Logger logger, UUID uuid, String currencyKey, double amount) {
        return addBalance(logger, uuid, currencyKey, -amount);
    }
}
