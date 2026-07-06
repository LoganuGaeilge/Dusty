package com.dustyrpg.economy.data;

import com.dustyrpg.economy.Currency;
import com.dustyrpg.economy.EconomyPlugin;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;

import java.io.File;
import java.io.IOException;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.logging.Level;

/**
 * Persists per-player currency balances. This replaces the Skript
 * {@code {money::%uuid%}} / {@code {orbs::%uuid%}} / ... global variables,
 * which Skript itself would have stored in variables.csv.
 */
public class EconomyManager {

    private final EconomyPlugin plugin;
    private final File dataFile;
    private final Map<UUID, Map<Currency, Double>> balances = new HashMap<>();
    private final Map<UUID, String> knownNames = new HashMap<>();

    public EconomyManager(EconomyPlugin plugin) {
        this.plugin = plugin;
        this.dataFile = new File(plugin.getDataFolder(), "data.yml");
        load();
    }

    public void load() {
        if (!dataFile.exists()) {
            return;
        }

        YamlConfiguration yaml = YamlConfiguration.loadConfiguration(dataFile);
        ConfigurationSection playersSection = yaml.getConfigurationSection("players");
        if (playersSection == null) {
            return;
        }

        for (String uuidStr : playersSection.getKeys(false)) {
            UUID uuid;
            try {
                uuid = UUID.fromString(uuidStr);
            } catch (IllegalArgumentException ex) {
                continue;
            }

            String base = "players." + uuidStr + ".";
            Map<Currency, Double> currencyMap = new EnumMap<>(Currency.class);
            for (Currency currency : Currency.values()) {
                currencyMap.put(currency, yaml.getDouble(base + currency.getKey(), 0));
            }
            balances.put(uuid, currencyMap);

            String name = yaml.getString(base + "name");
            if (name != null) {
                knownNames.put(uuid, name);
            }
        }
    }

    public void save() {
        YamlConfiguration yaml = new YamlConfiguration();

        for (Map.Entry<UUID, Map<Currency, Double>> entry : balances.entrySet()) {
            String base = "players." + entry.getKey() + ".";
            for (Map.Entry<Currency, Double> currencyEntry : entry.getValue().entrySet()) {
                yaml.set(base + currencyEntry.getKey().getKey(), currencyEntry.getValue());
            }
            String name = knownNames.get(entry.getKey());
            if (name != null) {
                yaml.set(base + "name", name);
            }
        }

        try {
            yaml.save(dataFile);
        } catch (IOException e) {
            plugin.getLogger().log(Level.SEVERE, "Failed to save economy data", e);
        }
    }

    /** Mirrors the Skript "on join" data-generator block. */
    public void registerPlayer(Player player) {
        knownNames.put(player.getUniqueId(), player.getName());
        Map<Currency, Double> currencyMap = balances.computeIfAbsent(player.getUniqueId(), id -> new EnumMap<>(Currency.class));
        for (Currency currency : Currency.values()) {
            currencyMap.putIfAbsent(currency, 0.0);
        }
    }

    public double getBalance(UUID uuid, Currency currency) {
        return balances
                .computeIfAbsent(uuid, id -> new EnumMap<>(Currency.class))
                .getOrDefault(currency, 0.0);
    }

    public void setBalance(UUID uuid, Currency currency, double amount) {
        // Never allow a balance to go negative. Any operation (set, add, or a
        // subtraction via addBalance with a negative amount) that would push a
        // player's balance below zero is instead clamped to exactly zero,
        // rather than letting the balance go negative.
        double clamped = Math.max(0.0, amount);
        balances.computeIfAbsent(uuid, id -> new EnumMap<>(Currency.class)).put(currency, clamped);
    }

    public void addBalance(UUID uuid, Currency currency, double amount) {
        setBalance(uuid, currency, getBalance(uuid, currency) + amount);
    }

    public String getKnownName(UUID uuid) {
        return knownNames.getOrDefault(uuid, uuid.toString());
    }
}
