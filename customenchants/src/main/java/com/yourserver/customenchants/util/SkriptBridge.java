package com.yourserver.customenchants.util;

import org.bukkit.Bukkit;
import org.bukkit.event.Event;
import org.bukkit.plugin.Plugin;

import java.lang.reflect.Method;
import java.util.logging.Logger;

public final class SkriptBridge {

    private static boolean available = false;
    private static boolean checked = false;
    private static Method setVariableMethod;
    private static Method getVariableMethod;

    private SkriptBridge() {
    }

    private static void init(Logger logger) {
        if (checked) return;
        checked = true;
        Plugin skript = Bukkit.getPluginManager().getPlugin("Skript");
        if (skript == null) {
            return;
        }
        try {
            Class<?> variablesClass = Class.forName("ch.njol.skript.variables.Variables");
            // public static void setVariable(String name, Object value, Event e, boolean local)
            setVariableMethod = variablesClass.getMethod("setVariable", String.class, Object.class, Event.class, boolean.class);
            // public static Object getVariable(String name, Event e, boolean local)
            getVariableMethod = variablesClass.getMethod("getVariable", String.class, Event.class, boolean.class);
            available = true;
        } catch (Exception ex) {
            logger.warning("[CustomEnchants] Skript was detected but its variable API could not be hooked (version mismatch?). skriptvar: actions will be ignored.");
            available = false;
        }
    }

    public static boolean isAvailable(Logger logger) {
        init(logger);
        return available;
    }

    /** name = value (value is a number if parseable, otherwise a string) */
    public static void setVariable(Logger logger, String name, Object value) {
        if (!isAvailable(logger)) return;
        try {
            setVariableMethod.invoke(null, name, value, null, false);
        } catch (Exception ex) {
            logger.warning("[CustomEnchants] Failed to set Skript variable '" + name + "': " + ex.getMessage());
        }
    }

    /** Reads the current numeric value of a Skript variable, defaulting to 0 if unset/non-numeric. */
    public static double getNumericVariable(Logger logger, String name) {
        if (!isAvailable(logger)) return 0;
        try {
            Object current = getVariableMethod.invoke(null, name, null, false);
            if (current instanceof Number) {
                return ((Number) current).doubleValue();
            }
            if (current instanceof String) {
                try {
                    return Double.parseDouble((String) current);
                } catch (NumberFormatException ignored) {
                }
            }
            return 0;
        } catch (Exception ex) {
            logger.warning("[CustomEnchants] Failed to read Skript variable '" + name + "': " + ex.getMessage());
            return 0;
        }
    }
}
