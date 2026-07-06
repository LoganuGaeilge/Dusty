package com.yourserver.givehead;

import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.EntityType;

import java.util.HashMap;
import java.util.Map;

/**
 * Loads and resolves the "mob-heads:" mapping and "default-player:" fallback
 * from config.yml.
 *
 * Each mob-heads value can be either:
 *   a) A player/skin name (e.g. "MHF_Zombie") resolved via Mojang API
 *   b) A base64 texture-property value (the eyJ... string from sites like
 *      minecraft-heads.com) applied directly to the skull's GameProfile
 */
public final class HeadConfig {

    private final Map<String, String> mobHeads = new HashMap<>();
    private final Map<String, String> mobTextures = new HashMap<>();
    private String defaultPlayer = "MHF_Steve";
    private boolean prettyPrintHeadNames = true;

    private String msgUsage = "&cUsage: /givehead <player> <mob-or-skin> <head name...>";
    private String msgPlayerNotFound = "&cThat player is not online.";
    private String msgGivenToTarget = "&aGave %target% a %head% head.";
    private String msgGivenConfirm = "&aYou received a %head% head.";
    private String msgReloaded = "&aGiveHead config reloaded.";

    public void load(FileConfiguration config) {
        mobHeads.clear();
        mobTextures.clear();

        defaultPlayer = config.getString("default-player", defaultPlayer);
        prettyPrintHeadNames = config.getBoolean("pretty-print-head-names", prettyPrintHeadNames);

        if (config.isConfigurationSection("mob-heads")) {
            for (String key : config.getConfigurationSection("mob-heads").getKeys(false)) {
                String value = config.getString("mob-heads." + key);
                if (value != null && !value.isBlank()) {
                    if (isTextureValue(value)) {
                        mobTextures.put(key.toUpperCase(), value);
                    } else {
                        mobHeads.put(key.toUpperCase(), value);
                    }
                }
            }
        }

        msgUsage = config.getString("messages.usage", msgUsage);
        msgPlayerNotFound = config.getString("messages.player-not-found", msgPlayerNotFound);
        msgGivenToTarget = config.getString("messages.given-to-target", msgGivenToTarget);
        msgGivenConfirm = config.getString("messages.given-confirm", msgGivenConfirm);
        msgReloaded = config.getString("messages.reloaded", msgReloaded);
    }

    /**
     * Resolves the "mob-or-skin" command argument into the offline-player
     * name whose texture should be used for the head. Returns null if this
     * token maps to a texture-property value instead (use resolveTexture).
     *
     * Resolution order:
     *   1. If the token has a texture-property mapping, returns null (caller
     *      should use resolveTexture() instead).
     *   2. An explicit player-name entry in "mob-heads:" (case-insensitive).
     *   3. If the token is a recognised entity/mob type but has no explicit
     *      entry, falls back to "default-player:".
     *   4. Otherwise the token is treated literally as an offline-player /
     *      skin name.
     */
    public String resolveSkinName(String token) {
        String upper = token.toUpperCase();

        if (mobTextures.containsKey(upper)) {
            return null;
        }

        String mapped = mobHeads.get(upper);
        if (mapped != null) {
            return mapped;
        }

        if (isKnownEntityType(upper)) {
            return defaultPlayer;
        }

        return token;
    }

    /**
     * Returns the base64 texture-property value for this token, or null if
     * it maps to a player name instead.
     */
    public String resolveTexture(String token) {
        return mobTextures.get(token.toUpperCase());
    }

    /**
     * Detects whether a config value is a base64 texture-property string
     * rather than a player name. Texture values are the eyJ... base64 blobs
     * from Minecraft head databases; player names are at most 16 characters.
     */
    private static boolean isTextureValue(String value) {
        return value.length() > 20 && value.startsWith("eyJ");
    }

    private boolean isKnownEntityType(String upper) {
        try {
            EntityType.valueOf(upper);
            return true;
        } catch (IllegalArgumentException ex) {
            return false;
        }
    }

    public String getDefaultPlayer() {
        return defaultPlayer;
    }

    public boolean isPrettyPrintHeadNames() {
        return prettyPrintHeadNames;
    }

    public java.util.Set<String> getAllConfiguredSkinNames() {
        java.util.Set<String> names = new java.util.HashSet<>(mobHeads.values());
        names.add(defaultPlayer);
        return names;
    }

    public String msgUsage() {
        return msgUsage;
    }

    public String msgPlayerNotFound() {
        return msgPlayerNotFound;
    }

    public String msgGivenToTarget() {
        return msgGivenToTarget;
    }

    public String msgGivenConfirm() {
        return msgGivenConfirm;
    }

    public String msgReloaded() {
        return msgReloaded;
    }
}
