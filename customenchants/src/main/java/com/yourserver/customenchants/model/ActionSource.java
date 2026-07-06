package com.yourserver.customenchants.model;

import com.yourserver.customenchants.util.RangeValue;
import com.yourserver.customenchants.util.ScaledValue;

import java.util.Set;

/**
 * Anything that can own an "actions:" list run through ActionExecutor and
 * supply the named "values:"/"ranges:" entries and blockdrop-region
 * settings those actions may reference.
 *
 * {@link CustomEnchant} is the original implementation (a book-applied
 * enchant). {@link GlobalToolSet} is the second one - a plain tool/material
 * match with no enchant or book involved at all - added so pickaxe types
 * (or any item type) can be configured to run actions on their own,
 * without ever being "enchanted". ActionExecutor only ever depends on this
 * interface, so it treats both exactly the same way.
 */
public interface ActionSource {

    /** A named, level-scalable numeric value from this source's "values:" section, or null if not defined. */
    ScaledValue getScaledValue(String name);

    /** A named, level-scalable random range from this source's "ranges:" section, or null if not defined. */
    RangeValue getRangeValue(String name);

    /**
     * Per-source override for whether the "blockdrop:" action is allowed to
     * run at all. Null means "not overridden" - the plugin-wide
     * settings.block-drops.enabled value is used instead.
     */
    Boolean getBlockDropsEnabledOverride();

    /**
     * WorldGuard region ids (lowercase) in which this source's "blockdrop:"
     * action should never run, on top of the plugin-wide
     * settings.block-drops.disabled-regions list.
     */
    Set<String> getBlockDropsDisabledRegions();
}
