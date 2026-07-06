package com.simpleah.plugin.util;

import org.bukkit.Bukkit;
import org.bukkit.plugin.Plugin;

import java.lang.reflect.Method;
import java.util.UUID;
import java.util.logging.Logger;

/**
 * Soft-dependency bridge to the DustyEconomy plugin, used to read a
 * player's real, current balance before charging them for an auction
 * purchase.
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
            logger.warning("[SimpleAH] DustyEconomy was detected but could not be hooked (version mismatch?)."
                    + " Balance checks will be unavailable until this is resolved.");
            available = false;
        }
    }

    public static boolean isAvailable(Logger logger) {
        init(logger);
        return available;
    }

    /**
     * Reads the player's real, current balance for the given currency key
     * (e.g. "money", "orbs", "souls", "credits") directly from DustyEconomy's
     * live EconomyManager. Returns null if the bridge isn't available or the
     * currency key isn't recognized, so callers can fall back gracefully.
     */
    public static Double getBalance(Logger logger, UUID uuid, String currencyKey) {
        if (!isAvailable(logger)) return null;
        try {
            Object economyManager = getEconomyManagerMethod.invoke(dustyEconomyPlugin);
            Object currency = fromKeyMethod.invoke(null, currencyKey);
            if (currency == null) return null;
            Object balance = getBalanceMethod.invoke(economyManager, uuid, currency);
            return (Double) balance;
        } catch (Exception ex) {
            logger.warning("[SimpleAH] Failed to read DustyEconomy balance for currency '"
                    + currencyKey + "': " + ex.getMessage());
            return null;
        }
    }

    /**
     * Adds (or removes, if amount is negative) currency to a player's balance
     * via DustyEconomy's EconomyManager.addBalance API. Returns true on
     * success, false if the bridge is unavailable or the call fails.
     */
    public static boolean addBalance(Logger logger, UUID uuid, String currencyKey, double amount) {
        if (!isAvailable(logger)) return false;
        try {
            Object economyManager = getEconomyManagerMethod.invoke(dustyEconomyPlugin);
            Object currency = fromKeyMethod.invoke(null, currencyKey);
            if (currency == null) return false;
            addBalanceMethod.invoke(economyManager, uuid, currency, amount);
            return true;
        } catch (Exception ex) {
            logger.warning("[SimpleAH] Failed to modify DustyEconomy balance for currency '"
                    + currencyKey + "': " + ex.getMessage());
            return false;
        }
    }

    /**
     * Removes currency from a player's balance. Delegates to addBalance with
     * a negated amount; EconomyManager clamps the result to zero.
     */
    public static boolean removeBalance(Logger logger, UUID uuid, String currencyKey, double amount) {
        return addBalance(logger, uuid, currencyKey, -amount);
    }
}
