package com.yourserver.customenchants.util;

import org.bukkit.Bukkit;
import org.bukkit.plugin.Plugin;

import java.lang.reflect.Method;
import java.util.UUID;
import java.util.logging.Logger;

/**
 * Soft-dependency bridge to the DustyEconomy plugin, used to read a
 * player's real, current balance before charging them for a GUI upgrade.
 *
 * DustyEconomy used to be a Skript script that stored balances in Skript's
 * own variable storage (e.g. {@code {orbs::<uuid>}}), which is why
 * UpgradeGUIListener originally read balances via SkriptBridge. DustyEconomy
 * has since been rewritten as a real plugin with its own EconomyManager/
 * data.yml, and it no longer writes to those Skript variables at all - so a
 * balance check that still reads them is checking a value that's frozen at
 * whatever it was before the migration and never changes again. That let
 * players buy upgrades their real balance couldn't cover, since the /charge
 * command (which does hit the real balance) would just clamp to zero instead
 * of the purchase ever being rejected.
 *
 * This bridge reflects into DustyEconomy's actual EconomyManager to read the
 * true, live balance, so the affordability check here matches what /currencyadd
 * will actually charge against. DustyEconomy is not a compile-time dependency
 * of this plugin (there's no published artifact for it), so - same as
 * SkriptBridge does for Skript - all lookups happen via reflection and fail
 * safe: if DustyEconomy isn't installed/enabled or its internals ever change
 * shape, isAvailable() reports false and callers should fall back to their
 * previous behavior rather than block purchases outright.
 */
public final class DustyEconomyBridge {

    private static boolean checked = false;
    private static boolean available = false;
    private static Plugin dustyEconomyPlugin;
    private static Method getEconomyManagerMethod;
    private static Method getBalanceMethod;
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
            fromKeyMethod = currencyClass.getMethod("fromKey", String.class);

            dustyEconomyPlugin = plugin;
            available = true;
        } catch (Exception ex) {
            logger.warning("[CustomEnchants] DustyEconomy was detected but could not be hooked (version mismatch?)."
                    + " Upgrade GUI affordability checks will fall back to the legacy Skript variable.");
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
            logger.warning("[CustomEnchants] Failed to read DustyEconomy balance for currency '"
                    + currencyKey + "': " + ex.getMessage());
            return null;
        }
    }
}
