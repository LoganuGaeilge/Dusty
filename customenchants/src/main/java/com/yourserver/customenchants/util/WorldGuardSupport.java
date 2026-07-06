package com.yourserver.customenchants.util;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.plugin.Plugin;

import java.util.Collection;
import java.util.logging.Logger;

/**
 * Soft-dependency bridge to WorldGuard, used only to check whether a
 * location falls inside one of a configured list of region names (so
 * "blockdrop:" bonus drops can be disabled per-region).
 *
 * WorldGuard is declared as a softdepend, NOT a hard dependency - the
 * server may not have it installed at all. All actual WorldGuard/WorldEdit
 * class references live inside the private {@link Bridge} class below,
 * which the JVM only links/loads the first time one of its methods is
 * actually invoked. As long as we never touch Bridge unless
 * {@link #isAvailable()} has already confirmed WorldGuard is present and
 * enabled, a server without WorldGuard installed will never hit a
 * NoClassDefFoundError - the region checks just always return false.
 */
public final class WorldGuardSupport {

    private static Boolean available;

    private WorldGuardSupport() {
    }

    /** Whether the WorldGuard plugin is installed and enabled on this server. */
    public static boolean isAvailable() {
        if (available == null) {
            Plugin worldGuard = Bukkit.getPluginManager().getPlugin("WorldGuard");
            available = worldGuard != null && worldGuard.isEnabled();
        }
        return available;
    }

    /**
     * Returns true if {@code location} is inside any WorldGuard region whose
     * (case-insensitive) id is in {@code regionNames}. Always false if
     * WorldGuard isn't installed, the location's world is null, or the
     * region list is empty - so this is always safe to call unconditionally.
     */
    public static boolean isInAnyRegion(Location location, Collection<String> regionNames, Logger logger) {
        if (regionNames == null || regionNames.isEmpty()) return false;
        if (location == null || location.getWorld() == null) return false;
        if (!isAvailable()) return false;

        try {
            return Bridge.isInAnyRegion(location, regionNames);
        } catch (Throwable t) {
            // WorldGuard is present but something about the installed version's
            // API didn't match what we expected - fail safe (treat as "not in
            // a disabled region") rather than breaking bonus drops entirely.
            if (logger != null) {
                logger.warning("[CustomEnchants] WorldGuard region check failed (mismatched WorldGuard version?): " + t);
            }
            return false;
        }
    }

    /** All real WorldGuard/WorldEdit class usage is isolated here. */
    private static final class Bridge {
        static boolean isInAnyRegion(Location location, Collection<String> regionNames) {
            com.sk89q.worldguard.WorldGuard worldGuard = com.sk89q.worldguard.WorldGuard.getInstance();
            com.sk89q.worldguard.protection.regions.RegionContainer container = worldGuard.getPlatform().getRegionContainer();
            com.sk89q.worldguard.protection.regions.RegionQuery query = container.createQuery();

            com.sk89q.worldedit.util.Location weLocation = com.sk89q.worldedit.bukkit.BukkitAdapter.adapt(location);
            com.sk89q.worldguard.protection.ApplicableRegionSet regions = query.getApplicableRegions(weLocation);

            for (com.sk89q.worldguard.protection.regions.ProtectedRegion region : regions) {
                for (String name : regionNames) {
                    if (region.getId().equalsIgnoreCase(name)) {
                        return true;
                    }
                }
            }
            return false;
        }
    }
}
