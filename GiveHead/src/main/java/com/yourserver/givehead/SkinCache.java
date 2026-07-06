package com.yourserver.givehead;

import org.bukkit.Bukkit;
import org.bukkit.profile.PlayerProfile;

import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Logger;

/**
 * Resolves and caches texture-complete {@link PlayerProfile}s for skin
 * names.
 *
 * This is the piece that was missing originally: {@code
 * Bukkit.getOfflinePlayer(name)} does NOT reliably do a live Mojang name ->
 * UUID -> texture lookup on modern Spigot/Paper for a name the server has
 * never seen (e.g. "MHF_Zombie") - it just fabricates a fake offline UUID
 * with no texture data, which renders as the default Steve/Alex skin.
 *
 * The real Bukkit API for this is {@link PlayerProfile#update()}, which
 * asynchronously produces an updated, texture-complete profile (the same
 * mechanism vanilla's "/give ... SkullOwner:" and Skript's "skull of
 * <offline player>" ultimately rely on). update() already does its network
 * work off the main thread and hands back a CompletableFuture, so callers
 * never need to (and must not) block the main thread waiting on it - see
 * {@link #resolve(String)}.
 *
 * Results are cached indefinitely (skins essentially never change), so
 * repeated requests for the same name - e.g. the same mob dying over and
 * over - are instant after the first resolution.
 */
public final class SkinCache {

    private final Map<String, PlayerProfile> cache = new ConcurrentHashMap<>();
    private final Logger logger;

    public SkinCache(Logger logger) {
        this.logger = logger;
    }

    /** Fast, non-blocking lookup - returns null if this name hasn't been resolved yet. */
    public PlayerProfile getCached(String skinName) {
        return cache.get(skinName.toLowerCase());
    }

    /**
     * Resolves (and caches) a texture-complete profile for skinName.
     * Safe to call from the main thread - the actual network lookup
     * happens asynchronously inside PlayerProfile#update().
     */
    public CompletableFuture<PlayerProfile> resolve(String skinName) {
        String key = skinName.toLowerCase();
        PlayerProfile cached = cache.get(key);
        if (cached != null) {
            return CompletableFuture.completedFuture(cached);
        }

        PlayerProfile base = Bukkit.createPlayerProfile(skinName);
        return base.update().handle((updated, ex) -> {
            if (ex != null) {
                logger.warning("[GiveHead] Failed to resolve skin textures for '" + skinName + "': " + ex.getMessage());
                cache.put(key, base);
                return base;
            }
            if (!updated.isComplete()) {
                logger.warning("[GiveHead] Could not fully resolve skin '" + skinName
                        + "' (name may not exist, or Mojang's API is unreachable) - "
                        + "the head may render with a default skin.");
            }
            cache.put(key, updated);
            return updated;
        });
    }

    public void clear() {
        cache.clear();
    }
}
