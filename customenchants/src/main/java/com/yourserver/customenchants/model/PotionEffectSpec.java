package com.yourserver.customenchants.model;

import com.yourserver.customenchants.util.ScaledValue;
import org.bukkit.potion.PotionEffectType;

/**
 * Describes a real vanilla {@link PotionEffectType} that should stay active
 * on a player for as long as they hold or wear an item carrying this
 * enchant - "vanilla potion effects as enchants." Both the amplifier and
 * the duration are resolved through the exact same level-scaling
 * {@link ScaledValue} DSL used everywhere else in this plugin (flat
 * number, per-level table, or formula), so a potion-effect enchant scales
 * with level exactly like a success-chance, upgrade-cost, or values: entry
 * does.
 *
 * Applied/refreshed by the on-hold task (see
 * com.yourserver.customenchants.tasks.HoldTickTask) rather than granted
 * once - this is what makes it disappear the moment the item is no longer
 * held/worn, instead of lingering like a drunk potion would.
 */
public class PotionEffectSpec {

    private final PotionEffectType type;
    private final ScaledValue amplifier;
    private final ScaledValue durationTicks;
    private final boolean ambient;
    private final boolean particles;
    private final boolean icon;

    public PotionEffectSpec(PotionEffectType type, ScaledValue amplifier, ScaledValue durationTicks,
                             boolean ambient, boolean particles, boolean icon) {
        this.type = type;
        this.amplifier = amplifier;
        this.durationTicks = durationTicks;
        this.ambient = ambient;
        this.particles = particles;
        this.icon = icon;
    }

    public PotionEffectType getType() {
        return type;
    }

    /** Bukkit's raw (0-based) amplifier - amplifier 0 is "Effect I". Resolved per-level like any other ScaledValue. */
    public ScaledValue getAmplifier() {
        return amplifier;
    }

    /** Configured duration in ticks for a single application - the on-hold task always applies at least its own tick interval so the effect never blinks off between refreshes. */
    public ScaledValue getDurationTicks() {
        return durationTicks;
    }

    public boolean isAmbient() {
        return ambient;
    }

    public boolean isParticles() {
        return particles;
    }

    public boolean isIcon() {
        return icon;
    }
}
