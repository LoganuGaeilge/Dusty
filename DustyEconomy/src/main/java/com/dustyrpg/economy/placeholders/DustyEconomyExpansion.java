package com.dustyrpg.economy.placeholders;

import com.dustyrpg.economy.Currency;
import com.dustyrpg.economy.EconomyPlugin;
import com.dustyrpg.economy.util.CurrencyFormatter;
import me.clip.placeholderapi.expansion.PlaceholderExpansion;
import org.bukkit.OfflinePlayer;
import org.jetbrains.annotations.NotNull;

/**
 * Exposes DustyEconomy balances to PlaceholderAPI so any consumer plugin
 * (TAB, DeluxeMenus, etc.) can display them without a direct dependency on
 * this plugin.
 *
 * Registers, per currency (money / orbs / souls / credits):
 *   %dustyeconomy_<currency>%            raw balance, e.g. "1532.0"
 *   %dustyeconomy_<currency>_formatted%  abbreviated with letter suffix,
 *                                        e.g. "1.5k" (uses formatCurrency(),
 *                                        matching the old sidebar's Money/
 *                                        Orbs/Souls lines)
 *   %dustyeconomy_<currency>_prefix%     the currency's color code prefix,
 *                                        e.g. "&a$" for money
 *
 * Credits additionally get %dustyeconomy_credits_formatted% using
 * formatCredits() (rounded, one decimal place), matching the original
 * sidebar behavior where credits were never floored/trimmed the same way
 * as the other three currencies.
 */
public class DustyEconomyExpansion extends PlaceholderExpansion {

    private final EconomyPlugin plugin;

    public DustyEconomyExpansion(EconomyPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public @NotNull String getIdentifier() {
        return "dustyeconomy";
    }

    @Override
    public @NotNull String getAuthor() {
        return "YourName";
    }

    @Override
    public @NotNull String getVersion() {
        return plugin.getDescription().getVersion();
    }

    /** Keeps the expansion registered across PlaceholderAPI reloads. */
    @Override
    public boolean persist() {
        return true;
    }

    @Override
    public String onRequest(OfflinePlayer player, @NotNull String params) {
        if (player == null) {
            return "";
        }

        for (Currency currency : Currency.values()) {
            String key = currency.getKey();

            if (params.equalsIgnoreCase(key)) {
                return trim(plugin.getEconomyManager().getBalance(player.getUniqueId(), currency));
            }

            if (params.equalsIgnoreCase(key + "_formatted")) {
                double balance = plugin.getEconomyManager().getBalance(player.getUniqueId(), currency);
                return currency == Currency.CREDITS
                        ? CurrencyFormatter.formatCredits(balance)
                        : CurrencyFormatter.formatCurrency(balance);
            }

            if (params.equalsIgnoreCase(key + "_prefix")) {
                return currency.getColorPrefix();
            }

            if (params.equalsIgnoreCase(key + "_displayname")) {
                return currency.getDisplayName();
            }
        }

        return null;
    }

    private String trim(double value) {
        if (value == Math.floor(value) && !Double.isInfinite(value)) {
            return String.valueOf((long) value);
        }
        return String.valueOf(value);
    }
}
