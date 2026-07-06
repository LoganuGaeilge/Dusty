# CustomEnchants

A fully config-driven custom enchant system for Spigot/Paper (Gradle build).

## Build

```
gradle build          # or: ./gradlew build if you generate the wrapper first
```

The jar lands in `build/libs/CustomEnchants-1.0.0.jar`. Drop it in your server's `plugins/` folder.

Edit `build.gradle` and set the `spigot-api` version to match your server (currently pinned to `1.20.4-R0.1-SNAPSHOT`). WorldGuard support (used only for the per-region `blockdrop:` toggle below) is pinned to `worldguard-bukkit:7.0.9` in the same file - bump that to match your server's WorldGuard version if you install it and see version-mismatch warnings in the console.

## How it works

### Rarities (`book-tiers` in config.yml)
Four rarities: `COMMON`, `RARE`, `LEGENDARY` and `NA`. Each entry is purely
cosmetic (a `color` + `display` name used on books). Every enchant is *classed*
as one rarity via its own `enchant-rarity` field.

`NA` is the odd one out: it's a valid rarity for enchants, but there is **no
unenchanted (mystery) book for NA** (see below), so NA-classed enchants are
never handed out by a random roll — only deliberately, e.g. via
`/ce give-book <player> <enchantId> <level>`.

### Unenchanted ("mystery") books (`unenchanted-books` in config.yml)
Three books — one each for `COMMON`, `RARE`, `LEGENDARY` (never `NA`). Unlike a
normal enchant book (which carries one pre-chosen enchant), an unenchanted book
carries none. Dragging one onto a valid item rolls a **random enchant classed
as the book's rarity that also supports that item**, rolls a **level** for it,
and applies it. Each book configures:
- `material` / `name` / `lore` — the item's appearance (detection is by hidden
  tag, so re-skinning is free).
- `level-roll` — an **editable formula** (a `min`/`max` range) for the level.
  Its `level` variable resolves to the *chosen enchant's* max-level, so
  `min: 1, max: "level"` rolls uniformly across that enchant's full range,
  while e.g. `min: "level * 0.75", max: "level"` biases toward high levels. The
  result is clamped to `[1, that enchant's max-level]`.

Give one with `/ce give-random-book <player> <COMMON|RARE|LEGENDARY> [amount]`.

### Defining an enchant (`enchants` in config.yml)
Each enchant has:
- `display-name` – shown in lore/messages
- `enchant-rarity` – `COMMON`/`RARE`/`LEGENDARY`/`NA`; which mystery book (if
  any) can roll it
- `max-level` – highest level obtainable
- `supported-items` – Bukkit Material names it can be applied to
- `success-chance` – success chance (0-100) for the direct book roll
- `actions` – what happens on each trigger (see below)

### Actions = "the code" (fully editable, no Java required)
Each trigger is a list of action lines. These prefixes are supported:

- `cmd: <console command>` — runs as console, works with **any** economy plugin
  (Vault-based, EssentialsX, custom), e.g. `cmd: eco give %player% 10`
- `msg: <text>` — sends a message straight to the player
- `skriptvar: <name> OP <value>` — writes **directly into Skript's variable
  storage** via its API (OP is `=`, `+=`, or `-=`), e.g.
  `skriptvar: economy::%player% += 10`. Any Skript script that reads
  `{economy::player}` sees the change immediately — this is the literal
  "just set the variable" hook for your Skript economy.
- `chance: <percent|value-name>: <action>[; <action>...]` — rolls a
  percentage chance and only runs the nested action(s) on success, e.g.
  `chance: 25: msg: &aBonus!; cmd: eco give %player% 10`. Use a name from
  `values:` instead of a flat number to make the chance itself scale with
  level.
- `setvar: <name> = <number|value-name>` / `randomvar: <name> = <min-max|range-name>`
  — resolve a level-scaled number (`setvar`) or a random number from a
  level-scaled range (`randomvar`), then store it both as a Skript variable
  and as a `%name%` placeholder usable by every later line in the same
  trigger firing (including nested `chance:` actions).
- `blockdrop: <amount|value-name|range-name>` — only valid on the
  `on-break-block` trigger. Actually adds that many extra units of the
  block's own drop(s) to the world at the block that was just broken, e.g.
  `blockdrop: 2` or `blockdrop: bonus_drop_amount` to pull a random amount
  from a `ranges:` entry. The bonus items are spawned and then announced
  via a real `BlockDropItemEvent` tied to the actual block, its pre-break
  state, and the player - so other plugins (auto-sell shops, drop counters,
  region-protection plugins, etc.) treat them exactly like any other item
  the block naturally dropped, instead of a disconnected item-spawn.

  You can turn `blockdrop:` off entirely, or in specific WorldGuard
  regions, from config:
  - Plugin-wide, under `settings.block-drops` — `enabled: false` disables
    bonus drops for every enchant everywhere; `disabled-regions: [...]`
    blocks them in those named regions for every enchant.
  - Per-enchant, with an optional `block-drops:` section inside that
    enchant's own config block (see `miners_fortune` in `config.yml`) —
    lets you disable (or region-restrict) just one enchant's bonus drops
    without touching the plugin-wide setting.

  WorldGuard is an optional soft dependency: region lists are simply
  ignored (never block anything) if WorldGuard isn't installed.

Placeholders: `%player% %level% %level_num% %victim% %world% %x% %y% %z%`,
plus any `%name%` set earlier in the same trigger via `setvar:`/`randomvar:`.

Triggers available: `on-apply`, `on-level-up`, `on-fail`, `on-hit`,
`on-hit-taken`, `on-kill`, `on-break-block`, `on-interact`, `on-consume`,
`on-hold`.

### Vanilla enchants at any level (`vanilla-enchant:`)
Any enchant entry can optionally set `vanilla-enchant: EFFICIENCY` (accepts
either the modern name or the legacy Bukkit enum name, e.g. `DIG_SPEED`).
This "hijacks" that real Bukkit enchantment instead of the entry being
purely custom: it's applied via `ItemMeta#addEnchant(enchant, level, true)`,
where that trailing `true` tells Bukkit to ignore the vanilla max-level
check entirely. So a book with `max-level: 200` and
`vanilla-enchant: EFFICIENCY` genuinely applies Efficiency 200 - vanilla's
own mining-speed formula just reads the stored level with no upper bound,
so the effect is real and felt, not a cosmetic lore number. Efficiency I
still works exactly like a normal vanilla Efficiency I book.

Every other part of the system - success chance, book tiers, dust,
upgrade GUI (`gui-on`), currency, `actions:`, `values:`/`ranges:` - works
completely unchanged on a vanilla-backed enchant. `hide-vanilla-tooltip`
(default `true`) hides the item's own client-rendered line for that
enchant so only this plugin's `lore-format` line is shown; set it to
`false` to keep vanilla's native display too. Leave `vanilla-enchant` out
entirely for a purely custom enchant, exactly like before. The whole
feature can be switched off plugin-wide (falling back to purely-custom
behaviour, nothing deleted) via `settings.vanilla-enchants.enabled`.

### Vanilla potion effects as enchants (`potion-effect:`)
An enchant entry can also define a `potion-effect:` section to keep a real
vanilla `PotionEffect` active on the player for as long as they hold or
wear the item - `type` (e.g. `SPEED`), `amplifier`, and `duration-ticks`
are given, and `amplifier`/`duration-ticks` each resolve through the same
level-scaling DSL as `values:`/`ranges:` (flat number, per-level table, or
formula/expression using `level`). The effect is refreshed every on-hold
tick and removed the instant the item stops being held/worn - and *only*
an effect this plugin granted is ever removed, so a real potion the player
drank, or an effect from another plugin, is never touched.

### The `on-hold` trigger and its settings
`on-hold` is different from every other trigger: instead of reacting to a
single event, it fires repeatedly - by default once a second - for as long
as an item is held in either hand or worn as armor, independent of every
other trigger. It's driven by one repeating task, configurable (and
switchable live via `/ce reload`) under `settings.on-hold`:

```yaml
settings:
  on-hold:
    enabled: true
    interval-ticks: 20   # 20 ticks = 1 second
```

Works identically for real enchants and for `global-tools:` sets. This is
also what keeps `potion-effect:` enchants alive/refreshed.

### Level-scaling values and ranges (`values:` / `ranges:` per enchant)
Anywhere an action line above takes a `value-name` or `range-name`, it's
looking up an entry defined under that enchant's own `values:` (numbers) or
`ranges:` (min/max pairs) section. Each number can be:

- a flat constant (`25`) — same at every level
- an explicit per-level table, exactly like `upgrade-costs` (`1: 10`, `2: 20`, ...)
- a `{ base, per-level, max, min, scale-with-level }` formula, where
  `scale-with-level: false` freezes it at `base` without deleting the numbers

This is what lets a percentage chance or a `blockdrop:` bonus amount
"scale like prices with the level," on a per-feature basis. See the
`values:`/`ranges:` block under `miners_fortune` in `config.yml` for a
full worked example.

### Applying an enchant book
1. Hold the **Enchant Book** in your **main hand**.
2. Hold the item you want to enchant in your **off-hand**.
3. Right-click.

The plugin checks the item is supported, charges the tier's XP cost, rolls
against the (dust-boosted) success chance, and either applies/levels-up the
enchant or fails — either way the book is consumed per your config
(`consume-book-on-fail` / `charge-xp-on-fail`).

Applied enchants are stored as PersistentDataContainer tags on the item (not
just lore), so they survive lore edits from other plugins, and are re-read
every time an action trigger fires. Lore is auto-rendered as
`EnchantName <RomanNumeral>`.

### White Dust
1. Hold **White Dust** in your main hand.
2. Hold an **Enchant Book** in your off-hand.
3. Right-click to add `bonus-per-use`% to that book's success chance, up to
   `max-bonus`%. Dust is consumed one at a time.

### Admin commands (`customenchants.admin`, default OP)
```
/ce give-book <player> <enchantId> <level>
/ce give-random-book <player> <COMMON|RARE|LEGENDARY> [amount]
/ce give-dust <player> [amount]
/ce reload
```

`enchantId` is the YAML key under `enchants:` (e.g. `venom_strike`).

### Adding a new enchant
Just add a new block under `enchants:` in config.yml and `/ce reload` — no
recompiling needed for anything expressible via `cmd:` / `msg:` / `skriptvar:`
actions. If you need something a command genuinely can't do (complex custom
mechanics), add a new `Trigger` enum value and fire it from a listener the
same way `TriggerListener` does — the config side needs no changes.
