package com.yourserver.customenchants.model;

import com.yourserver.customenchants.util.RangeValue;
import com.yourserver.customenchants.util.ScaledValue;
import org.bukkit.Material;

import java.util.EnumMap;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * A named group of item types (e.g. "all pickaxes") that runs "actions:"
 * on the configured triggers by itself - no book, no applied enchant, no
 * PDC data on the item at all. Defined under the top-level "global-tools:"
 * section in config.yml, structured just like an individual enchant's
 * "actions:"/"values:"/"ranges:"/"block-drops:" so the exact same action
 * lines (including "blockdrop:" and "disabled-regions:") work identically
 * here as they do inside a real enchant.
 *
 * This is what lets an admin say "every diamond and netherite pickaxe does
 * X on break, whether or not it has any custom enchant on it" without
 * inventing a fake always-applied enchant to get there.
 */
public class GlobalToolSet implements ActionSource {

    private final String id;
    private final Set<Material> materials;
    private final int level;
    private final Map<Trigger, List<String>> actions;
    private final Map<String, ScaledValue> values;
    private final Map<String, RangeValue> ranges;
    private final Boolean blockDropsEnabled;
    private final Set<String> blockDropsDisabledRegions;

    public GlobalToolSet(String id,
                          Set<Material> materials,
                          int level,
                          Map<Trigger, List<String>> actions,
                          Map<String, ScaledValue> values,
                          Map<String, RangeValue> ranges,
                          Boolean blockDropsEnabled,
                          Set<String> blockDropsDisabledRegions) {
        this.id = id;
        this.materials = materials.isEmpty() ? EnumSet.noneOf(Material.class) : EnumSet.copyOf(materials);
        this.level = level;
        this.actions = new EnumMap<>(actions);
        this.values = values != null ? new HashMap<>(values) : new HashMap<>();
        this.ranges = ranges != null ? new HashMap<>(ranges) : new HashMap<>();
        this.blockDropsEnabled = blockDropsEnabled;
        this.blockDropsDisabledRegions = blockDropsDisabledRegions != null
                ? new java.util.HashSet<>(blockDropsDisabledRegions) : Set.of();
    }

    public String getId() {
        return id;
    }

    public boolean matches(Material material) {
        return materials.contains(material);
    }

    public Set<Material> getMaterials() {
        return materials;
    }

    /** Used for %level%/%level_num% placeholders and any level-scaled values:/ranges: entries. Defaults to 1. */
    public int getLevel() {
        return level;
    }

    public List<String> getActions(Trigger trigger) {
        return actions.getOrDefault(trigger, List.of());
    }

    @Override
    public ScaledValue getScaledValue(String name) {
        return name == null ? null : values.get(name.toLowerCase());
    }

    @Override
    public RangeValue getRangeValue(String name) {
        return name == null ? null : ranges.get(name.toLowerCase());
    }

    @Override
    public Boolean getBlockDropsEnabledOverride() {
        return blockDropsEnabled;
    }

    @Override
    public Set<String> getBlockDropsDisabledRegions() {
        return blockDropsDisabledRegions;
    }
}
