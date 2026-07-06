package com.yourserver.customenchants.model;

/**
 * The events an enchant's "actions" section can hook into.
 * The config key is the lowercase-hyphenated name (e.g. "on-hit").
 */
public enum Trigger {
    ON_APPLY,
    ON_LEVEL_UP,
    // Fires when a level is refunded/removed via the GUI's downgrade click
    // (see UpgradeGUIListener) - the level passed to the action executor is
    // the NEW (post-removal) level, e.g. going from III to II fires this
    // with level = 2. Purely additive: enchants that never define an
    // "on-level-down:" action list are completely unaffected.
    ON_LEVEL_DOWN,
    ON_FAIL,
    ON_HIT,
    ON_HIT_TAKEN,
    ON_KILL,
    ON_BREAK_BLOCK,
    ON_INTERACT,
    ON_CONSUME,
    // Fires repeatedly (see settings.on-hold in config.yml for the
    // interval) for as long as the item is held in either hand or worn as
    // armor - independent of any other trigger. Purely additive: existing
    // enchants that never define an "on-hold:" action list are completely
    // unaffected.
    ON_HOLD;

    public String configKey() {
        return name().toLowerCase().replace('_', '-');
    }
}
