package com.yourserver.customenchants.util;

import org.bukkit.configuration.ConfigurationSection;

import java.util.HashMap;
import java.util.Map;
import java.util.logging.Logger;

/**
 * A numeric value that can either be a flat constant, an explicit per-level
 * lookup table (the exact same style already used by "upgrade-costs", e.g.
 * "2: 100" / "3: 250"), or a base + per-level formula with an optional cap.
 *
 * This is the ONE shared building block behind every numeric field in the
 * config - success-chance, upgrade-costs, values:, and ranges: min/max all
 * resolve through this exact same class now, so there is a single,
 * consistent syntax no matter where a number appears.
 *
 * Accepted YAML shapes for a value entry:
 *
 *   my-value: 25.0
 *   # -> flat constant, identical at every level.
 *
 *   my-value: "level"
 *   # -> literal shorthand for "equal to the current level".
 *
 *   my-value: "level * 150 + 50"
 *   my-value: "100 * (1.15 ^ (level - 1))"
 *   # -> any formula, using "level" as a variable. See MathExpr for the
 *   #    full supported syntax (+ - * / % ^, parentheses, min/max/floor/
 *   #    ceil/round/abs/sqrt). This is the "complex math" escape hatch -
 *   #    reach for it whenever base/per-level below isn't expressive enough.
 *
 *   my-value:
 *     1: 10
 *     2: 20
 *     3: 35
 *   # -> explicit per-level table, exactly like upgrade-costs always has
 *   #    been. A level not listed falls back to the closest lower defined
 *   #    level (so you only need to list the levels where the value
 *   #    actually changes). Upgrade-costs alone keeps the older, stricter
 *   #    "exact level or not-configured" behaviour where that distinction
 *   #    matters - see CustomEnchant.getUpgradeCost().
 *
 *   my-value:
 *     scale-with-level: true
 *     base: 10
 *     per-level: 5
 *     max: 60      # optional cap - can itself be a number OR an expression
 *     min: 0       # optional floor - same
 *   # -> formula: base + per-level * (level - 1), clamped to [min, max].
 *   #    Set scale-with-level: false to freeze the result at "base"
 *   #    regardless of level, without deleting your base/per-level numbers -
 *   #    this is the on/off switch for "if enabled, scale like prices".
 *   #    "base"/"per-level"/"min"/"max" each accept a plain number OR a
 *   #    "level"-aware expression string, so this table form and the plain
 *   #    expression form above can be freely mixed.
 */
public final class ScaledValue {

    private enum Kind { FLAT, TABLE, FORMULA, EXPRESSION }

    private final Kind kind;
    private final double flat;
    private final Map<Integer, Double> table;
    private final ScaledValue base;
    private final ScaledValue perLevel;
    private final ScaledValue min;
    private final ScaledValue max;
    private final boolean scaleWithLevel;
    private final String expression;

    private ScaledValue(Kind kind, double flat, Map<Integer, Double> table,
                         ScaledValue base, ScaledValue perLevel, ScaledValue min, ScaledValue max,
                         boolean scaleWithLevel, String expression) {
        this.kind = kind;
        this.flat = flat;
        this.table = table;
        this.base = base;
        this.perLevel = perLevel;
        this.min = min;
        this.max = max;
        this.scaleWithLevel = scaleWithLevel;
        this.expression = expression;
    }

    public static ScaledValue flat(double value) {
        return new ScaledValue(Kind.FLAT, value, null, null, null, null, null, false, null);
    }

    private static ScaledValue expression(String expr) {
        return new ScaledValue(Kind.EXPRESSION, 0, null, null, null, null, null, true, expr);
    }

    /** Parses any of the shapes documented on this class from a raw config value. */
    public static ScaledValue parse(Object raw, Logger logger, String context) {
        if (raw == null) {
            return flat(0);
        }

        if (raw instanceof Number) {
            return flat(((Number) raw).doubleValue());
        }

        if (raw instanceof String) {
            String str = ((String) raw).trim();
            try {
                return flat(Double.parseDouble(str));
            } catch (NumberFormatException ex) {
                // Not a plain number - try it as a "level"-aware math expression
                // before giving up, so any scalar field in the config (not just
                // the formula-table shape below) can hold a real formula.
                if (MathExpr.looksLikeExpression(str) && MathExpr.tryEvaluate(str, 1) != null) {
                    return expression(str);
                }
                if (logger != null) {
                    logger.warning("[CustomEnchants] Could not parse numeric value '" + str + "' for " + context + ". Defaulting to 0.");
                }
                return flat(0);
            }
        }

        if (raw instanceof ConfigurationSection) {
            ConfigurationSection section = (ConfigurationSection) raw;
            boolean looksLikeFormula = section.contains("base") || section.contains("per-level") || section.contains("scale-with-level");

            if (looksLikeFormula) {
                ScaledValue base = ScaledValue.parse(section.get("base"), logger, context + ".base");
                ScaledValue perLevel = ScaledValue.parse(section.get("per-level"), logger, context + ".per-level");
                boolean scale = section.getBoolean("scale-with-level", true);
                ScaledValue min = section.contains("min") ? ScaledValue.parse(section.get("min"), logger, context + ".min") : null;
                ScaledValue max = section.contains("max") ? ScaledValue.parse(section.get("max"), logger, context + ".max") : null;
                return new ScaledValue(Kind.FORMULA, 0, null, base, perLevel, min, max, scale, null);
            }

            // Otherwise treat it as an explicit per-level table, same style as upgrade-costs.
            Map<Integer, Double> table = new HashMap<>();
            for (String key : section.getKeys(false)) {
                try {
                    int level = Integer.parseInt(key.trim());
                    table.put(level, section.getDouble(key));
                } catch (NumberFormatException ex) {
                    if (logger != null) {
                        logger.warning("[CustomEnchants] Ignoring non-numeric level key '" + key + "' for " + context);
                    }
                }
            }
            if (table.isEmpty()) {
                return flat(0);
            }
            return new ScaledValue(Kind.TABLE, 0, table, null, null, null, null, true, null);
        }

        if (logger != null) {
            logger.warning("[CustomEnchants] Unrecognised value shape for " + context + ". Defaulting to 0.");
        }
        return flat(0);
    }

    /** Resolves this value for the given enchant level. */
    public double resolve(int level) {
        switch (kind) {
            case FLAT:
                return flat;
            case EXPRESSION: {
                Double result = MathExpr.tryEvaluate(expression, level);
                return result != null ? result : 0.0;
            }
            case FORMULA: {
                if (!scaleWithLevel) {
                    return clamp(base.resolve(level), level);
                }
                double value = base.resolve(level) + perLevel.resolve(level) * (level - 1);
                return clamp(value, level);
            }
            case TABLE: {
                if (table.containsKey(level)) {
                    return table.get(level);
                }
                // Fall back to the closest lower defined level, mirroring how
                // upgrade-costs is read (only list the levels that change).
                Integer bestKey = null;
                for (Integer key : table.keySet()) {
                    if (key <= level && (bestKey == null || key > bestKey)) {
                        bestKey = key;
                    }
                }
                if (bestKey != null) {
                    return table.get(bestKey);
                }
                // Level is below every defined key - use the lowest defined entry.
                Integer lowest = null;
                for (Integer key : table.keySet()) {
                    if (lowest == null || key < lowest) lowest = key;
                }
                return lowest != null ? table.get(lowest) : 0.0;
            }
            default:
                return 0.0;
        }
    }

    public int resolveInt(int level) {
        return (int) Math.round(resolve(level));
    }

    /** True if this value is an explicit per-level table (used by upgrade-costs to preserve its strict "exact level or not-configured" behaviour). */
    public boolean isTable() {
        return kind == Kind.TABLE;
    }

    /** For TABLE values only: the cost exactly defined for this level, or null if this level has no entry (no fallback). */
    public Double tableValueExact(int level) {
        return kind == Kind.TABLE ? table.get(level) : null;
    }

    private double clamp(double value, int level) {
        if (min != null) value = Math.max(min.resolve(level), value);
        if (max != null) value = Math.min(max.resolve(level), value);
        return value;
    }
}
