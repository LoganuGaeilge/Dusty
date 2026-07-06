package com.yourserver.customenchants.util;

import org.bukkit.configuration.ConfigurationSection;

import java.util.Random;
import java.util.logging.Logger;

/**
 * A random-amount range whose min and max bounds are each a {@link ScaledValue},
 * so the whole range can grow (or shrink) with enchant level - "a random
 * amount variable set between a range, modifiable by level".
 *
 * Accepted YAML shapes for a range entry:
 *
 *   my-range: "3-6"
 *   # -> flat range, identical bounds at every level.
 *
 *   my-range:
 *     min: 2
 *     max: 5
 *   # -> flat bounds written the plain way.
 *
 *   my-range:
 *     min: { base: 2, per-level: 1 }
 *     max: { base: 5, per-level: 2, max: 20 }
 *   # -> bounds that scale with level, each side using the full
 *   #    ScaledValue DSL (flat number, per-level table, or formula).
 *
 * Ranges can also be written inline directly in an action line as a plain
 * "min-max" literal (see RangeValue.parseLiteral), for one-off cases that
 * don't need a named, reusable, level-scaled definition.
 */
public final class RangeValue {

    private final ScaledValue min;
    private final ScaledValue max;

    private RangeValue(ScaledValue min, ScaledValue max) {
        this.min = min;
        this.max = max;
    }

    public static RangeValue flat(double min, double max) {
        return new RangeValue(ScaledValue.flat(min), ScaledValue.flat(max));
    }

    /** Builds a range directly from two (possibly level-scaled) bounds. */
    public static RangeValue of(ScaledValue min, ScaledValue max) {
        return new RangeValue(min, max);
    }

    public static RangeValue parse(Object raw, Logger logger, String context) {
        if (raw instanceof String) {
            return parseLiteral((String) raw, logger, context);
        }
        if (raw instanceof ConfigurationSection) {
            ConfigurationSection section = (ConfigurationSection) raw;
            ScaledValue min = ScaledValue.parse(section.get("min"), logger, context + ".min");
            ScaledValue max = ScaledValue.parse(section.get("max"), logger, context + ".max");
            return new RangeValue(min, max);
        }
        if (logger != null) {
            logger.warning("[CustomEnchants] Unrecognised range shape for " + context + ". Defaulting to 0-0.");
        }
        return flat(0, 0);
    }

    /** Parses a bare "min-max" literal (e.g. used inline in an action line). */
    public static RangeValue parseLiteral(String str, Logger logger, String context) {
        String trimmed = str.trim();
        int searchFrom = trimmed.startsWith("-") ? 1 : 0;
        int dash = trimmed.indexOf('-', searchFrom);

        if (dash == -1) {
            try {
                double flat = Double.parseDouble(trimmed);
                return flat(flat, flat);
            } catch (NumberFormatException ex) {
                if (logger != null) {
                    logger.warning("[CustomEnchants] Invalid range literal '" + str + "' for " + context + ". Defaulting to 0-0.");
                }
                return flat(0, 0);
            }
        }

        try {
            double lo = Double.parseDouble(trimmed.substring(0, dash).trim());
            double hi = Double.parseDouble(trimmed.substring(dash + 1).trim());
            return flat(lo, hi);
        } catch (NumberFormatException ex) {
            if (logger != null) {
                logger.warning("[CustomEnchants] Invalid range literal '" + str + "' for " + context + ". Defaulting to 0-0.");
            }
            return flat(0, 0);
        }
    }

    /** Resolves a random double within this range at the given level (inclusive bounds). */
    public double resolveRandom(int level, Random random) {
        double lo = min.resolve(level);
        double hi = max.resolve(level);
        if (hi < lo) {
            double t = lo;
            lo = hi;
            hi = t;
        }
        if (hi == lo) return lo;
        return lo + random.nextDouble() * (hi - lo);
    }

    /** Resolves a random integer within this range at the given level (inclusive bounds). */
    public int resolveRandomInt(int level, Random random) {
        return (int) Math.round(resolveRandom(level, random));
    }
}
